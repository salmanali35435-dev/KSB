package com.example.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.model.QuizCatalog
import com.example.model.QuizQuestion
import com.example.model.StudioSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class VideoExporter(
    private val renderer: SlideRenderer,
    private val audioManager: TtsAudioManager
) {
    companion object {
        const val FPS = 25
        const val WIDTH = SlideRenderer.CW
        const val HEIGHT = SlideRenderer.CH
        const val SAMPLE_RATE = TtsAudioManager.SAMPLE_RATE
    }

    private sealed class Segment {
        abstract val startSec: Float
        abstract val endSec: Float

        data class IntroOutro(
            val kind: String,
            val text: String,
            val anim: String,
            val themeIndex: Int,
            val clip: TtsAudioManager.PcmClip,
            override val startSec: Float,
            val qsSec: Float,
            val durSec: Float,
            override val endSec: Float
        ) : Segment()

        data class QuestionSeg(
            val question: QuizQuestion,
            val index: Int,
            val qClip: TtsAudioManager.PcmClip,
            val aClip: TtsAudioManager.PcmClip,
            override val startSec: Float,
            val qsSec: Float,
            val bsSec: Float,
            val beSec: Float,
            val asSec: Float,
            override val endSec: Float,
            val optTimes: FloatArray
        ) : Segment()
    }

    suspend fun exportVideo(
        questions: List<QuizQuestion>,
        fromIdx: Int,
        toIdx: Int,
        settings: StudioSettings,
        customBgPath: String?,
        musicFile: File?,
        outputFile: File,
        onProgress: (String, Float) -> Unit
    ): Pair<File, Float> = withContext(Dispatchers.Default) {
        val list = questions.subList(fromIdx, toIdx)
        require(list.isNotEmpty()) { "Question range is empty" }

        val lang = QuizCatalog.langByKey(settings.lang)
        val useIntro = settings.introOn && settings.introText.isNotBlank()
        val useOutro = settings.outroOn && settings.outroText.isNotBlank()
        val totalItems = list.size + (if (useIntro) 1 else 0) + (if (useOutro) 1 else 0)
        val segments = mutableListOf<Segment>()
        var tt = 0.5f
        var done = 0
        val delaySec = settings.delay.coerceIn(2, 60).toFloat()

        val voiceChar = QuizCatalog.charOf(settings.voice)

        if (useIntro) {
            coroutineContext.ensureActive()
            onProgress("Generating voice (${voiceChar.name}): Intro", done++.toFloat() / totalItems)
            val clip = audioManager.getSpeechClip(settings.introText.trim(), settings.lang, settings.voice, settings.rate) { msg ->
                onProgress(msg, 0f)
            }
            val qs = tt + 0.4f
            val end = qs + clip.durationSec + 1.4f
            segments.add(Segment.IntroOutro("intro", settings.introText.trim(), settings.introAnim, fromIdx, clip, tt, qs, clip.durationSec, end))
            tt = end
        }

        list.forEachIndexed { idx, q ->
            coroutineContext.ensureActive()
            onProgress("Generating voice (${voiceChar.name}): Q${idx + 1} / ${list.size}", done++.toFloat() / totalItems)
            val qStr = "${q.q}. ${lang.optLabel} A, ${q.a}. ${lang.optLabel} B, ${q.b}. ${lang.optLabel} C, ${q.c}. ${lang.optLabel} D, ${q.d}."
            val aStr = "${lang.ansLabel} ${q.normalizedAns()}, ${q.correctOptionText()}."

            val qClip = audioManager.getSpeechClip(qStr, settings.lang, settings.voice, settings.rate) { msg ->
                onProgress(msg, done.toFloat() / totalItems)
            }
            val aClip = audioManager.getSpeechClip(aStr, settings.lang, settings.voice, settings.rate) { msg ->
                onProgress(msg, done.toFloat() / totalItems)
            }
            val qs = tt + 0.3f
            val bs = qs + qClip.durationSec + 0.4f
            val be = bs + delaySec
            val asSec = be + 0.2f
            val end = asSec + aClip.durationSec + 1.8f
            val optFracs = SlideRenderer.optFractions(q, lang.optLabel)
            val ot = FloatArray(4) { k -> qs - tt + optFracs[k] * qClip.durationSec - 0.1f }
            segments.add(Segment.QuestionSeg(q, fromIdx + idx, qClip, aClip, tt, qs, bs, be, asSec, end, ot))
            tt = end
        }

        if (useOutro) {
            coroutineContext.ensureActive()
            onProgress("Generating voice (${voiceChar.name}): Outro", done++.toFloat() / totalItems)
            val clip = audioManager.getSpeechClip(settings.outroText.trim(), settings.lang, settings.voice, settings.rate) { msg ->
                onProgress(msg, 0.9f)
            }
            val qs = tt + 0.4f
            val end = qs + clip.durationSec + 1.4f
            segments.add(Segment.IntroOutro("outro", settings.outroText.trim(), settings.outroAnim, toIdx - 1, clip, tt, qs, clip.durationSec, end))
            tt = end
        }

        val totalDur = tt + 0.3f
        onProgress("Preparing audio mix (${formatSec(totalDur)})...", 0.05f)

        val musicClip = if (settings.musicOn && musicFile != null && musicFile.exists()) {
            audioManager.decodeAudioFileToMono48k(musicFile)
        } else null

        val mixedAudio = mixTimelineAudio(segments, totalDur, settings, musicClip)

        coroutineContext.ensureActive()
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        encodeMp4(
            segments = segments,
            totalQuestions = questions.size,
            delaySec = delaySec,
            totalDur = totalDur,
            mixedPcm = mixedAudio,
            settings = settings,
            customBgPath = customBgPath,
            outputFile = outputFile,
            onProgress = onProgress
        )

        outputFile to totalDur
    }

    private fun mixTimelineAudio(
        segments: List<Segment>,
        totalDur: Float,
        settings: StudioSettings,
        musicClip: TtsAudioManager.PcmClip?
    ): FloatArray {
        val totalSamples = (totalDur * SAMPLE_RATE).roundToInt().coerceAtLeast(SAMPLE_RATE)
        val mix = FloatArray(totalSamples)

        fun addClip(src: FloatArray, atSec: Float) {
            val startIdx = (atSec * SAMPLE_RATE).roundToInt()
            for (i in src.indices) {
                val dst = startIdx + i
                if (dst in 0 until totalSamples) {
                    mix[dst] += src[i]
                }
            }
        }

        val tickHi = audioManager.buildTickSamples(true)
        val tickLo = audioManager.buildTickSamples(false)
        val delayTicks = (settings.delay.coerceIn(2, 60) * 2)

        val voiceIntervals = mutableListOf<Pair<Float, Float>>()
        for (seg in segments) {
            when (seg) {
                is Segment.IntroOutro -> {
                    addClip(seg.clip.samples, seg.qsSec)
                    voiceIntervals.add(seg.qsSec to (seg.qsSec + seg.durSec))
                }
                is Segment.QuestionSeg -> {
                    addClip(seg.qClip.samples, seg.qsSec)
                    addClip(seg.aClip.samples, seg.asSec)
                    voiceIntervals.add(seg.qsSec to (seg.qsSec + seg.qClip.durationSec))
                    voiceIntervals.add(seg.asSec to (seg.asSec + seg.aClip.durationSec))
                    for (k in 0 until delayTicks) {
                        val tk = seg.bsSec + k / 2f
                        addClip(if (k % 2 == 0) tickHi else tickLo, tk)
                    }
                }
            }
        }

        // Merge close voice intervals (< 0.8s gap) for smooth ducking
        val mergedIntervals = mutableListOf<Pair<Float, Float>>()
        voiceIntervals.sortedBy { it.first }.forEach { (a, b) ->
            val last = mergedIntervals.lastOrNull()
            if (last != null && a - last.second < 0.8f) {
                mergedIntervals[mergedIntervals.lastIndex] = last.first to max(last.second, b)
            } else {
                mergedIntervals.add(a to b)
            }
        }

        if (musicClip != null && musicClip.samples.isNotEmpty()) {
            val vol = settings.musicVol.coerceIn(1, 100) / 100f
            val duckOn = settings.musicDuck
            val duckLvl = settings.duckLvl.coerceIn(1, 100) / 100f
            val lenCfg = if (settings.musicLen == "custom") settings.musicSecs else (settings.musicLen.toIntOrNull() ?: 0)
            val musicT = if (lenCfg > 0) min(lenCfg.toFloat(), totalDur) else totalDur
            val fromSamples = (settings.musicFrom.coerceAtLeast(0) * SAMPLE_RATE) % musicClip.samples.size
            val loop = settings.musicLoop
            val endMusicSample = (musicT * SAMPLE_RATE).roundToInt().coerceAtMost(totalSamples)

            var ivIdx = 0
            for (i in 0 until endMusicSample) {
                val t = i.toFloat() / SAMPLE_RATE
                val mSampleIdx = if (loop) {
                    (fromSamples + i) % musicClip.samples.size
                } else {
                    val idx = fromSamples + i
                    if (idx >= musicClip.samples.size) break
                    idx
                }
                var duckMul = 1f
                if (duckOn) {
                    while (ivIdx < mergedIntervals.size && mergedIntervals[ivIdx].second + 0.8f < t) {
                        ivIdx++
                    }
                    var j = ivIdx
                    while (j < mergedIntervals.size) {
                        val (a, b) = mergedIntervals[j]
                        if (t < a - 0.3f) break
                        if (t <= b + 0.8f) {
                            val d = when {
                                t < a -> 1f - (1f - duckLvl) * ((t - (a - 0.3f)) / 0.3f)
                                t <= b -> duckLvl
                                else -> duckLvl + (1f - duckLvl) * ((t - b) / 0.8f)
                            }
                            duckMul = min(duckMul, d)
                        }
                        j++
                    }
                }
                val fade = min(1f, min(t / 1.5f, (musicT - t) / 2.0f)).coerceAtLeast(0f)
                mix[i] += musicClip.samples[mSampleIdx] * vol * duckMul * fade
            }
        }

        // Soft limiter to prevent clipping
        for (i in mix.indices) {
            mix[i] = mix[i].coerceIn(-0.98f, 0.98f)
        }
        return mix
    }

    private suspend fun encodeMp4(
        segments: List<Segment>,
        totalQuestions: Int,
        delaySec: Float,
        totalDur: Float,
        mixedPcm: FloatArray,
        settings: StudioSettings,
        customBgPath: String?,
        outputFile: File,
        onProgress: (String, Float) -> Unit
    ) {
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false

        try {
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoCodec.createInputSurface()
            videoCodec.start()

            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec.start()

            val vInfo = MediaCodec.BufferInfo()
            val aInfo = MediaCodec.BufferInfo()

            fun tryStartMuxer() {
                if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
                    muxer.start()
                    muxerStarted = true
                }
            }

            fun drainVideo(eos: Boolean): Boolean {
                if (eos) videoCodec.signalEndOfInputStream()
                while (true) {
                    val outIdx = videoCodec.dequeueOutputBuffer(vInfo, if (eos) 10_000 else 0)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = muxer.addTrack(videoCodec.outputFormat)
                            tryStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val encoded = videoCodec.getOutputBuffer(outIdx)!!
                            if ((vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vInfo.size > 0 && muxerStarted) {
                                encoded.position(vInfo.offset)
                                encoded.limit(vInfo.offset + vInfo.size)
                                muxer.writeSampleData(videoTrack, encoded, vInfo)
                            }
                            val isEos = (vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            videoCodec.releaseOutputBuffer(outIdx, false)
                            if (isEos) return true
                        }
                        else -> return false
                    }
                }
            }

            fun drainAudio(eos: Boolean): Boolean {
                while (true) {
                    val outIdx = audioCodec.dequeueOutputBuffer(aInfo, if (eos) 10_000 else 0)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrack = muxer.addTrack(audioCodec.outputFormat)
                            tryStartMuxer()
                        }
                        outIdx >= 0 -> {
                            val encoded = audioCodec.getOutputBuffer(outIdx)!!
                            if ((aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && aInfo.size > 0 && muxerStarted) {
                                encoded.position(aInfo.offset)
                                encoded.limit(aInfo.offset + aInfo.size)
                                muxer.writeSampleData(audioTrack, encoded, aInfo)
                            }
                            val isEos = (aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioCodec.releaseOutputBuffer(outIdx, false)
                            if (isEos) return true
                        }
                        else -> return false
                    }
                }
            }

            // First feed initial audio chunks so both videoTrack & audioTrack format headers arrive quickly
            var audioOffset = 0
            val audioShorts = ShortArray(mixedPcm.size) { i ->
                (mixedPcm[i] * 32767f).toInt().toShort()
            }
            var audioInputEos = false

            fun feedAudioStep(maxChunks: Int) {
                if (audioInputEos) return
                var fed = 0
                while (fed < maxChunks && !audioInputEos) {
                    val inIdx = audioCodec.dequeueInputBuffer(5_000)
                    if (inIdx < 0) break
                    val buf = audioCodec.getInputBuffer(inIdx)!!
                    buf.clear()
                    if (audioOffset >= audioShorts.size) {
                        val ptsUs = (audioOffset.toLong() * 1_000_000L) / SAMPLE_RATE
                        audioCodec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        audioInputEos = true
                    } else {
                        val chunkSamples = min(1024, audioShorts.size - audioOffset)
                        val bytes = ByteArray(chunkSamples * 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            .put(audioShorts, audioOffset, chunkSamples)
                        buf.put(bytes)
                        val ptsUs = (audioOffset.toLong() * 1_000_000L) / SAMPLE_RATE
                        audioCodec.queueInputBuffer(inIdx, 0, bytes.size, ptsUs, 0)
                        audioOffset += chunkSamples
                    }
                    fed++
                }
            }

            // Prime audio format
            feedAudioStep(16)
            drainAudio(false)

            val totalFrames = (totalDur * FPS).toInt() + 1
            val startWallMs = System.currentTimeMillis()
            var segIdx = 0

            for (frame in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val t = frame.toFloat() / FPS
                while (segIdx < segments.lastIndex && t >= segments[segIdx].endSec) {
                    segIdx++
                }
                val seg = segments[segIdx]

                val canvas = inputSurface.lockCanvas(null)
                try {
                    when (seg) {
                        is Segment.IntroOutro -> {
                            renderer.drawIOFrame(
                                canvas,
                                settings,
                                customBgPath,
                                SlideRenderer.IOState(seg.text, seg.anim, seg.themeIndex, seg.durSec, t - seg.qsSec)
                            )
                        }
                        is Segment.QuestionSeg -> {
                            val reveal = t >= seg.beSec
                            val prog = if (t >= seg.bsSec && t < seg.beSec) (t - seg.bsSec) / delaySec else null
                            renderer.drawQuizFrame(
                                canvas,
                                settings,
                                customBgPath,
                                SlideRenderer.FrameState(
                                    question = seg.question,
                                    questionIndex = seg.index,
                                    totalQuestions = totalQuestions,
                                    reveal = reveal,
                                    progress = prog,
                                    animElapsed = max(0f, t - seg.startSec),
                                    optTimes = seg.optTimes,
                                    revealElapsed = max(0f, t - seg.beSec),
                                    globalTime = t
                                )
                            )
                        }
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Feed enough audio to keep muxer interleaved cleanly
                val targetAudioSamples = ((t + 0.5f) * SAMPLE_RATE).toInt().coerceAtMost(audioShorts.size)
                while (audioOffset < targetAudioSamples && !audioInputEos) {
                    feedAudioStep(4)
                    drainAudio(false)
                }
                drainVideo(false)

                if (frame % 12 == 0) {
                    val elapsedWall = max(0.1f, (System.currentTimeMillis() - startWallMs) / 1000f)
                    val speedX = t / elapsedWall
                    onProgress(
                        "Encoding MP4: ${formatSec(t)} / ${formatSec(totalDur)} (${"%.1f".format(speedX)}×)",
                        (t / totalDur).coerceIn(0f, 0.99f)
                    )
                }
            }

            onProgress("Finalizing video...", 0.99f)
            while (!audioInputEos) {
                feedAudioStep(8)
                drainAudio(false)
            }
            var videoDone = false
            var audioDone = false
            videoCodec.signalEndOfInputStream()
            while (!videoDone || !audioDone) {
                if (!videoDone) videoDone = drainVideo(false)
                if (!audioDone) audioDone = drainAudio(false)
            }
            inputSurface.release()
        } catch (e: CancellationException) {
            outputFile.delete()
            throw e
        } finally {
            runCatching { videoCodec.stop() }
            runCatching { videoCodec.release() }
            runCatching { audioCodec.stop() }
            runCatching { audioCodec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun formatSec(sec: Float): String {
        val s = max(0, sec.roundToInt())
        val m = s / 60
        val r = s % 60
        return "%d:%02d".format(m, r)
    }
}
