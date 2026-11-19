package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.QuizStorage
import com.example.model.QuizCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class TtsAudioManager(
    private val context: Context,
    private val storage: QuizStorage
) {
    companion object {
        const val SAMPLE_RATE = 48000
        private val WAITS = listOf(2, 5, 10, 20, 30, 45, 60)
        private val CHUNK_SPLIT = Regex("""(?<=[.!?,;:،۔]\s)\s*|(?<=[，。！？；：、])""")
    }

    data class PcmClip(
        val samples: FloatArray,
        val sampleRate: Int = SAMPLE_RATE
    ) {
        val durationSec: Float get() = samples.size.toFloat() / sampleRate
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val pcmCache = ConcurrentHashMap<String, PcmClip>()
    private val paceMutex = Mutex()
    private var lastReqMs = 0L
    private var paceGapMs = 350L

    private var androidTts: TextToSpeech? = null
    private val ttsReady = CompletableDeferred<Boolean>()
    private val synthCallbacks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Volatile
    private var currentVoiceTrack: AudioTrack? = null

    @Volatile
    private var currentMusicTrack: AudioTrack? = null
    private var musicJob: Job? = null

    @Volatile
    private var musicVolTarget = 0.2f

    @Volatile
    private var musicDuckActive = false

    @Volatile
    private var musicDuckOn = true

    @Volatile
    private var musicDuckLvl = 0.25f

    init {
        androidTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { synthCallbacks.remove(it)?.complete(true) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { synthCallbacks.remove(it)?.complete(false) }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { synthCallbacks.remove(it)?.complete(false) }
                    }
                })
                ttsReady.complete(true)
            } else {
                ttsReady.complete(false)
            }
        }
    }

    fun getSystemVoicesForLang(langKey: String): List<Pair<String, String>> {
        if (!ttsReady.isCompleted) return emptyList()
        val tts = androidTts ?: return emptyList()
        val langCfg = QuizCatalog.langByKey(langKey)
        val prefix = langCfg.localeCode.substringBefore('-').lowercase()
        return runCatching {
            tts.voices
                ?.filter { it.locale.language.lowercase() == prefix }
                ?.sortedBy { it.name }
                ?.map { "sys:${it.name}" to "${it.name} (${it.locale.displayLanguage})" }
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    fun chunkText(text: String): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (raw in text.split(CHUNK_SPLIT)) {
            val p = raw.trim()
            if (p.isEmpty()) continue
            if (cur.isNotEmpty() && (cur.length + 1 + p.length) > 180) {
                out.add(cur)
                cur = p
            } else {
                cur = if (cur.isEmpty()) p else "$cur $p"
            }
            while (cur.length > 180) {
                out.add(cur.substring(0, 180))
                cur = cur.substring(180)
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun pace() {
        paceMutex.withLock {
            val now = System.currentTimeMillis()
            val waitUntil = maxOf(now, lastReqMs + paceGapMs)
            lastReqMs = waitUntil
            val diff = waitUntil - now
            if (diff > 0) delay(diff)
        }
    }

    private suspend fun fetchGoogleTtsChunk(
        chunk: String,
        langKey: String,
        onStatus: ((String) -> Unit)? = null
    ): PcmClip = withContext(Dispatchers.IO) {
        val langCfg = QuizCatalog.langByKey(langKey)
        val cacheFile = File(storage.ttsCacheDir, "${langKey}_${md5(chunk)}.mp3")
        if (cacheFile.exists() && cacheFile.length() > 100) {
            runCatching { return@withContext decodeAudioFileToMono48k(cacheFile) }
        }

        val q = URLEncoder.encode(chunk, "UTF-8")
        val common = "ie=UTF-8&q=$q&tl=${langCfg.ttsCode}&total=1&idx=0&textlen=${chunk.length}&prev=input"
        val urls = listOf(
            "https://translate.google.com/translate_tts?$common&client=tw-ob",
            "https://translate.googleapis.com/translate_tts?$common&client=gtx",
            "https://translate.google.com/translate_tts?$common&client=gtx"
        )

        var lastErr = "network"
        for (k in 0..WAITS.size) {
            pace()
            val req = Request.Builder()
                .url(urls[k % urls.size])
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = runCatching { http.newCall(req).execute() }.getOrNull()
            if (resp != null && resp.isSuccessful) {
                val bytes = resp.body?.bytes()
                resp.close()
                if (bytes != null && bytes.size > 100) {
                    runCatching {
                        cacheFile.writeBytes(bytes)
                        val decoded = decodeAudioFileToMono48k(cacheFile)
                        paceGapMs = maxOf(350L, (paceGapMs * 0.8).toLong())
                        return@withContext decoded
                    }.onFailure { lastErr = "decode" }
                }
            } else {
                lastErr = resp?.code?.toString() ?: "network"
                resp?.close()
            }
            if (k == WAITS.size) break
            paceGapMs = minOf(paceGapMs * 2L, 1500L)
            val waitSec = WAITS[k]
            for (s in waitSec downTo 1) {
                onStatus?.invoke("Voice server rate-limited — retrying in ${s}s (${k + 1}/${WAITS.size})...")
                delay(1000)
            }
        }
        throw IllegalStateException("Google TTS failed ($lastErr) for ${langCfg.name}")
    }

    private suspend fun synthSystemTtsToClip(
        text: String,
        langKey: String,
        voiceId: String
    ): PcmClip = withContext(Dispatchers.IO) {
        val ready = ttsReady.await()
        if (!ready) throw IllegalStateException("System TTS unavailable")
        val tts = androidTts ?: throw IllegalStateException("System TTS null")
        val langCfg = QuizCatalog.langByKey(langKey)
        val locale = Locale.forLanguageTag(langCfg.localeCode)
        tts.language = locale
        if (voiceId.startsWith("sys:")) {
            val targetName = voiceId.removePrefix("sys:")
            tts.voices?.find { it.name == targetName }?.let { tts.voice = it }
        }
        val wavFile = File(storage.ttsCacheDir, "sys_${UUID.randomUUID()}.wav")
        val uttId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        synthCallbacks[uttId] = deferred
        val params = Bundle()
        val rc = tts.synthesizeToFile(text, params, wavFile, uttId)
        if (rc != TextToSpeech.SUCCESS) {
            synthCallbacks.remove(uttId)
            throw IllegalStateException("System TTS synthesis failed")
        }
        val ok = deferred.await()
        if (!ok || !wavFile.exists()) {
            wavFile.delete()
            throw IllegalStateException("System TTS output missing")
        }
        try {
            decodeAudioFileToMono48k(wavFile)
        } finally {
            wavFile.delete()
        }
    }

    suspend fun getSpeechClip(
        text: String,
        langKey: String,
        voiceId: String,
        rate: Float,
        onStatus: ((String) -> Unit)? = null
    ): PcmClip {
        val clean = text.trim()
        if (clean.isEmpty()) return PcmClip(FloatArray(SAMPLE_RATE / 4))

        val effectiveRate = if (voiceId.startsWith("sys:")) {
            rate
        } else {
            QuizCatalog.charOf(voiceId).rateMultiplier * rate
        }.coerceIn(0.5f, 2.0f)

        val cacheKey = "$langKey|${if (voiceId.startsWith("sys:")) voiceId else "g"}|${"%.2f".format(effectiveRate)}|$clean"
        pcmCache[cacheKey]?.let { return it }

        val rawClip = if (voiceId.startsWith("sys:")) {
            synthSystemTtsToClip(clean, langKey, voiceId)
        } else {
            runCatching {
                val parts = chunkText(clean).map { fetchGoogleTtsChunk(it, langKey, onStatus) }
                val totalLen = parts.sumOf { it.samples.size }
                val merged = FloatArray(totalLen)
                var pos = 0
                for (p in parts) {
                    p.samples.copyInto(merged, pos)
                    pos += p.samples.size
                }
                PcmClip(merged, SAMPLE_RATE)
            }.getOrElse {
                // Fallback to offline Android TextToSpeech if online TTS is blocked/offline
                runCatching { synthSystemTtsToClip(clean, langKey, voiceId) }
                    .getOrElse { PcmClip(FloatArray((SAMPLE_RATE * maxOf(1.5f, clean.length * 0.06f)).toInt())) }
            }
        }

        val resampled = if (kotlin.math.abs(effectiveRate - 1.0f) > 0.01f) {
            resampleLinear(rawClip.samples, effectiveRate)
        } else {
            rawClip.samples
        }
        val finalClip = PcmClip(resampled, SAMPLE_RATE)
        if (pcmCache.size > 120) pcmCache.clear()
        pcmCache[cacheKey] = finalClip
        return finalClip
    }

    fun resampleLinear(src: FloatArray, speedFactor: Float): FloatArray {
        if (src.isEmpty() || speedFactor <= 0f) return src
        val newLen = (src.size / speedFactor).roundToInt().coerceAtLeast(1)
        val out = FloatArray(newLen)
        for (i in 0 until newLen) {
            val pos = i * speedFactor
            val i0 = pos.toInt().coerceIn(0, src.lastIndex)
            val i1 = (i0 + 1).coerceIn(0, src.lastIndex)
            val frac = pos - i0
            out[i] = src[i0] * (1f - frac) + src[i1] * frac
        }
        return out
    }

    fun decodeAudioFileToMono48k(file: File): PcmClip {
        val extractor = MediaExtractor()
        extractor.setDataSource(FileInputStream(file).fd)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in ${file.name}")
        }
        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmBytes = ByteArrayOutputStream()
        var outSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(chunk)
                        pcmBytes.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFmt = codec.outputFormat
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        outSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        outChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val raw = pcmBytes.toByteArray()
        val shortCount = raw.size / 2
        val shorts = ShortArray(shortCount)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val frames = shortCount / outChannels.coerceAtLeast(1)
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until outChannels) {
                sum += shorts[i * outChannels + ch] / 32768f
            }
            mono[i] = sum / outChannels
        }

        val resampled = if (outSampleRate != SAMPLE_RATE && frames > 0) {
            resampleLinear(mono, outSampleRate.toFloat() / SAMPLE_RATE.toFloat())
        } else {
            mono
        }
        return PcmClip(resampled, SAMPLE_RATE)
    }

    suspend fun playClipLive(clip: PcmClip) = withContext(Dispatchers.IO) {
        stopVoice()
        if (clip.samples.isEmpty()) return@withContext
        val shorts = ShortArray(clip.samples.size) { i ->
            (clip.samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentVoiceTrack = track
        setMusicDuck(true)
        try {
            track.play()
            var offset = 0
            while (offset < shorts.size && currentVoiceTrack === track) {
                val len = min(2048, shorts.size - offset)
                val written = track.write(shorts, offset, len)
                if (written <= 0) break
                offset += written
            }
            // Wait slightly so tail drains smoothly
            delay(80)
        } catch (e: Exception) {
            Log.w("TtsAudioManager", "Audio playback stopped: ${e.message}")
        } finally {
            if (currentVoiceTrack === track) {
                currentVoiceTrack = null
            }
            setMusicDuck(false)
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    fun buildTickSamples(high: Boolean): FloatArray {
        val durSamples = (0.065f * SAMPLE_RATE).toInt()
        val freq = if (high) 1500.0 else 1000.0
        return FloatArray(durSamples) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val sq = if (sin(2.0 * PI * freq * t) >= 0) 1f else -1f
            val env = 0.18f * kotlin.math.exp(-t * 60.0).toFloat()
            sq * env
        }
    }

    private val tickHi by lazy { buildTickSamples(true) }
    private val tickLo by lazy { buildTickSamples(false) }

    fun playTickAsync(scope: CoroutineScope, high: Boolean) {
        scope.launch(Dispatchers.IO) {
            val samples = if (high) tickHi else tickLo
            val shorts = ShortArray(samples.size) { i ->
                (samples[i] * 32767f).toInt().toShort()
            }
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(shorts.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch

            runCatching {
                track.write(shorts, 0, shorts.size)
                track.play()
                delay(90)
                track.stop()
                track.release()
            }
        }
    }

    fun updateLiveMusicConfig(volPct: Int, duck: Boolean, duckLvlPct: Int) {
        musicVolTarget = volPct.coerceIn(1, 100) / 100f
        musicDuckOn = duck
        musicDuckLvl = duckLvlPct.coerceIn(1, 100) / 100f
        applyLiveMusicVolume()
    }

    private fun setMusicDuck(duck: Boolean) {
        musicDuckActive = duck
        applyLiveMusicVolume()
    }

    private fun applyLiveMusicVolume() {
        val factor = if (musicDuckActive && musicDuckOn) musicDuckLvl else 1f
        val gain = (musicVolTarget * factor).coerceIn(0f, 1f)
        runCatching { currentMusicTrack?.setVolume(gain) }
    }

    fun startLiveMusic(
        scope: CoroutineScope,
        file: File,
        volPct: Int,
        duck: Boolean,
        duckLvlPct: Int,
        fromSec: Int,
        lenSec: Int,
        loop: Boolean,
        onEnded: () -> Unit
    ) {
        stopLiveMusic()
        updateLiveMusicConfig(volPct, duck, duckLvlPct)
        musicJob = scope.launch(Dispatchers.IO) {
            try {
                val clip = decodeAudioFileToMono48k(file)
                if (clip.samples.isEmpty()) {
                    onEnded()
                    return@launch
                }
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                currentMusicTrack = track
                applyLiveMusicVolume()
                track.play()

                val startSample = ((fromSec * SAMPLE_RATE) % clip.samples.size).coerceAtLeast(0)
                val maxPlaySamples = if (lenSec > 0) lenSec * SAMPLE_RATE else Int.MAX_VALUE
                var played = 0
                var pos = startSample
                val chunk = ShortArray(2048)

                while (isActive && currentMusicTrack === track && played < maxPlaySamples) {
                    val toWrite = min(chunk.size, maxPlaySamples - played)
                    var n = 0
                    while (n < toWrite) {
                        if (pos >= clip.samples.size) {
                            if (loop) pos = 0 else break
                        }
                        chunk[n++] = (clip.samples[pos++].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    if (n == 0) break
                    val written = track.write(chunk, 0, n)
                    if (written <= 0) break
                    played += written
                }
            } finally {
                stopLiveMusic()
                onEnded()
            }
        }
    }

    fun isMusicPlaying(): Boolean = currentMusicTrack != null

    fun stopLiveMusic() {
        musicJob?.cancel()
        musicJob = null
        val t = currentMusicTrack
        currentMusicTrack = null
        runCatching { t?.stop() }
        runCatching { t?.release() }
    }

    fun stopVoice() {
        val t = currentVoiceTrack
        currentVoiceTrack = null
        runCatching { t?.stop() }
        runCatching { t?.release() }
        setMusicDuck(false)
    }

    fun stopAll() {
        stopVoice()
        stopLiveMusic()
    }

    fun release() {
        stopAll()
        runCatching { androidTts?.shutdown() }
    }
}
