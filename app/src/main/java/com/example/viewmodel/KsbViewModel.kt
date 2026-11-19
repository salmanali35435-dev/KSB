package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QuizStorage
import com.example.media.SlideRenderer
import com.example.media.TtsAudioManager
import com.example.media.VideoExporter
import com.example.model.MainTab
import com.example.model.MusicTrack
import com.example.model.QuizCatalog
import com.example.model.QuizQuestion
import com.example.model.StudioSettings
import com.example.util.BulkImportParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class KsbUiState(
    val activeTab: MainTab = MainTab.EDITOR,
    val questions: List<QuizQuestion> = QuizCatalog.sampleQuestions,
    val settings: StudioSettings = StudioSettings(),
    val curIndex: Int = 0,
    val customBgPath: String? = null,
    val musicTracks: List<MusicTrack> = emptyList(),
    val systemVoices: List<Pair<String, String>> = emptyList(),
    val isMusicPreviewPlaying: Boolean = false,
    val previewBitmap: Bitmap? = null,
    val isPlaying: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportStatus: String = "",
    val exportedVideoFile: File? = null,
    val exportedDurationSec: Float = 0f,
    val toastMessage: String? = null
)

class KsbViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = QuizStorage(application)
    private val renderer = SlideRenderer(application)
    private val audioManager = TtsAudioManager(application, storage)
    private val exporter = VideoExporter(renderer, audioManager)

    private val _uiState = MutableStateFlow(KsbUiState())
    val uiState: StateFlow<KsbUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            val qs = storage.loadQuestions()
            val st = storage.loadSettings()
            val bgPath = storage.loadCustomBgPath()
            val tracks = storage.loadMusicTracks()
            _uiState.update {
                it.copy(
                    questions = qs,
                    settings = st,
                    customBgPath = bgPath,
                    musicTracks = tracks,
                    systemVoices = audioManager.getSystemVoicesForLang(st.lang)
                )
            }
            refreshPreviewFrame()
        }
    }

    /* ---------- Navigation & Theme ---------- */

    fun selectTab(tab: MainTab) {
        if (tab != MainTab.STUDIO) stopPlayback()
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == MainTab.STUDIO) refreshPreviewFrame()
    }

    fun toggleUiTheme(currentlyDark: Boolean) {
        val next = if (currentlyDark) "light" else "dark"
        updateSettings { it.copy(uiTheme = next) }
    }

    /* ---------- Editor CRUD ---------- */

    fun addRows(count: Int) {
        val updated = _uiState.value.questions + List(count) { QuizQuestion() }
        persistQuestions(updated)
    }

    fun updateQuestion(index: Int, transform: (QuizQuestion) -> QuizQuestion) {
        val list = _uiState.value.questions.toMutableList()
        if (index !in list.indices) return
        list[index] = transform(list[index])
        persistQuestions(list)
    }

    fun deleteQuestion(index: Int) {
        val list = _uiState.value.questions.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        val newCur = _uiState.value.curIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(curIndex = newCur) }
        persistQuestions(list)
    }

    fun deleteAllQuestions() {
        _uiState.update { it.copy(curIndex = 0) }
        persistQuestions(emptyList())
    }

    fun bulkImport(rawText: String): Pair<Boolean, String> {
        val info = BulkImportParser.ParseInfo()
        val parsed = BulkImportParser.parse(rawText, info)
        if (parsed.isEmpty()) {
            return false to "No questions found. Use at least 6 columns: Question | A | B | C | D | Answer"
        }
        val list = _uiState.value.questions.toMutableList()
        while (list.isNotEmpty() && list.last().let {
                it.q.isBlank() && it.a.isBlank() && it.b.isBlank() && it.c.isBlank() && it.d.isBlank()
            }) {
            list.removeAt(list.lastIndex)
        }
        list.addAll(parsed)
        persistQuestions(list)
        val warn = if (info.unsureCount > 0) {
            "\n⚠ ${info.unsureCount} answer(s) were not recognized (highlighted yellow) — please verify them."
        } else ""
        return true to "Added ${parsed.size} question(s). Total: ${list.size}.$warn"
    }

    fun resetPerQuestionFonts() {
        val list = _uiState.value.questions.map { it.copy(font = "") }
        persistQuestions(list)
        refreshPreviewFrame()
        showToast("Per-question fonts reset")
    }

    private fun persistQuestions(list: List<QuizQuestion>) {
        _uiState.update { it.copy(questions = list) }
        viewModelScope.launch { storage.saveQuestions(list) }
        if (_uiState.value.activeTab == MainTab.STUDIO) refreshPreviewFrame()
    }

    /* ---------- Settings & Media Assets ---------- */

    fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        val next = transform(_uiState.value.settings)
        val voices = if (next.lang != _uiState.value.settings.lang) {
            audioManager.getSystemVoicesForLang(next.lang)
        } else {
            _uiState.value.systemVoices
        }
        _uiState.update { it.copy(settings = next, systemVoices = voices) }
        audioManager.updateLiveMusicConfig(next.musicVol, next.musicDuck, next.duckLvl)
        viewModelScope.launch { storage.saveSettings(next) }
        if (_uiState.value.activeTab == MainTab.STUDIO && !_uiState.value.isPlaying) {
            refreshPreviewFrame()
        }
    }

    fun setCustomBackground(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val dest = storage.customBgFile
            val ok = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0
            }.getOrDefault(false)
            if (ok) {
                storage.saveCustomBgPath(dest.absolutePath)
                _uiState.update { it.copy(customBgPath = dest.absolutePath) }
                refreshPreviewFrame()
                showToast("Custom background updated")
            } else {
                showToast("Could not load selected image")
            }
        }
    }

    fun resetCustomBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.customBgFile.delete()
            storage.saveCustomBgPath(null)
            _uiState.update { it.copy(customBgPath = null) }
            refreshPreviewFrame()
            showToast("Background image reset")
        }
    }

    fun importMusicFile(uri: Uri, customTitle: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val resolver = ctx.contentResolver
            var displayName = "Music Track"
            var sizeBytes = 0L
            resolver.query(uri, null, null, null, null)?.use { c ->
                val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameCol >= 0) displayName = c.getString(nameCol) ?: displayName
                    if (sizeCol >= 0) sizeBytes = c.getLong(sizeCol)
                }
            }
            if (sizeBytes > 40L * 1024L * 1024L) {
                showToast("File is larger than 40 MB. Please choose a smaller audio file.")
                return@launch
            }
            val id = UUID.randomUUID().toString().take(8)
            val ext = displayName.substringAfterLast('.', "mp3").take(6)
            val fileName = "track_$id.$ext"
            val dest = File(storage.musicDir, fileName)
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                dest.exists() && dest.length() > 0
            }.getOrDefault(false)
            if (!copied) {
                showToast("Could not read selected audio file.")
                return@launch
            }

            val clip = runCatching { audioManager.decodeAudioFileToMono48k(dest) }.getOrNull()
            if (clip == null || clip.samples.isEmpty()) {
                dest.delete()
                showToast("Unsupported audio format. Choose MP3, M4A, or WAV.")
                return@launch
            }

            val title = (customTitle?.trim()?.takeIf { it.isNotEmpty() }
                ?: displayName.substringBeforeLast('.').ifBlank { "Track $id" })
            val track = MusicTrack(
                id = id,
                name = title,
                durationSec = clip.durationSec.toDouble(),
                sizeBytes = dest.length(),
                fileName = fileName
            )
            val updatedTracks = (_uiState.value.musicTracks + track).sortedBy { it.name.lowercase() }
            storage.saveMusicTracks(updatedTracks)
            val nextSettings = _uiState.value.settings.copy(musicId = track.id)
            storage.saveSettings(nextSettings)
            _uiState.update { it.copy(musicTracks = updatedTracks, settings = nextSettings) }
            showToast("Added music: $title")
        }
    }

    fun deleteSelectedMusic() {
        val st = _uiState.value.settings
        val id = st.musicId
        if (id.isBlank()) return
        audioManager.stopLiveMusic()
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = _uiState.value.musicTracks
            val target = tracks.find { it.id == id }
            if (target != null) {
                File(storage.musicDir, target.fileName).delete()
            }
            val remaining = tracks.filterNot { it.id == id }
            val nextId = remaining.firstOrNull()?.id.orEmpty()
            val nextSettings = st.copy(musicId = nextId)
            storage.saveMusicTracks(remaining)
            storage.saveSettings(nextSettings)
            _uiState.update {
                it.copy(
                    musicTracks = remaining,
                    settings = nextSettings,
                    isMusicPreviewPlaying = false
                )
            }
            showToast("Music deleted")
        }
    }

    fun toggleMusicPreview() {
        if (audioManager.isMusicPlaying()) {
            audioManager.stopLiveMusic()
            _uiState.update { it.copy(isMusicPreviewPlaying = false) }
            return
        }
        val st = _uiState.value.settings
        val track = _uiState.value.musicTracks.find { it.id == st.musicId }
        if (track == null) {
            showToast("Add and select a music file first.")
            return
        }
        val file = File(storage.musicDir, track.fileName)
        if (!file.exists()) {
            showToast("Music file missing.")
            return
        }
        _uiState.update { it.copy(isMusicPreviewPlaying = true) }
        audioManager.startLiveMusic(
            scope = viewModelScope,
            file = file,
            volPct = st.musicVol,
            duck = st.musicDuck,
            duckLvlPct = st.duckLvl,
            fromSec = st.musicFrom,
            lenSec = 90,
            loop = st.musicLoop
        ) {
            _uiState.update { it.copy(isMusicPreviewPlaying = false) }
        }
    }

    /* ---------- Studio Playback & Slide Rendering ---------- */

    fun jumpToQuestion(index: Int) {
        stopPlayback()
        val maxIdx = (_uiState.value.questions.size - 1).coerceAtLeast(0)
        _uiState.update { it.copy(curIndex = index.coerceIn(0, maxIdx)) }
        refreshPreviewFrame()
    }

    fun prevQuestion() {
        val cur = _uiState.value.curIndex
        if (cur > 0) jumpToQuestion(cur - 1)
    }

    fun nextQuestion() {
        val cur = _uiState.value.curIndex
        if (cur < _uiState.value.questions.lastIndex) jumpToQuestion(cur + 1)
    }

    fun refreshPreviewFrame(
        reveal: Boolean = false,
        progress: Float? = null,
        animElapsed: Float = 10f,
        optTimes: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        revealElapsed: Float = 0f,
        globalTime: Float = 0f
    ) {
        val state = _uiState.value
        val qs = state.questions
        val q = qs.getOrNull(state.curIndex) ?: QuizQuestion(
            q = "No questions. Add in Editor.",
            a = "Option A", b = "Option B", c = "Option C", d = "Option D",
            ans = "A"
        )
        val idx = if (qs.isEmpty()) 0 else state.curIndex
        val total = qs.size.coerceAtLeast(1)
        val bmp = renderQuizBitmap(
            SlideRenderer.FrameState(q, idx, total, reveal, progress, animElapsed, optTimes, revealElapsed, globalTime)
        )
        _uiState.update { it.copy(previewBitmap = bmp) }
    }

    private fun renderQuizBitmap(frameState: SlideRenderer.FrameState): Bitmap {
        val st = _uiState.value
        val bmp = Bitmap.createBitmap(SlideRenderer.CW, SlideRenderer.CH, Bitmap.Config.ARGB_8888)
        renderer.drawQuizFrame(Canvas(bmp), st.settings, st.customBgPath, frameState)
        return bmp
    }

    private fun renderIOBitmap(ioState: SlideRenderer.IOState): Bitmap {
        val st = _uiState.value
        val bmp = Bitmap.createBitmap(SlideRenderer.CW, SlideRenderer.CH, Bitmap.Config.ARGB_8888)
        renderer.drawIOFrame(Canvas(bmp), st.settings, st.customBgPath, ioState)
        return bmp
    }

    fun readCurrentQuestion() {
        val q = _uiState.value.questions.getOrNull(_uiState.value.curIndex) ?: return
        stopPlayback()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlaying = true) }
            try {
                playQuestionPhase(q, _uiState.value.curIndex)
            } finally {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun showAndReadAnswer() {
        val q = _uiState.value.questions.getOrNull(_uiState.value.curIndex) ?: return
        stopPlayback()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlaying = true) }
            try {
                playAnswerPhase(q, _uiState.value.curIndex)
            } finally {
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun previewIO(kind: String) {
        val st = _uiState.value.settings
        val text = (if (kind == "intro") st.introText else st.outroText).trim()
        if (text.isEmpty()) {
            showToast("Please enter ${if (kind == "intro") "Intro" else "Outro"} text first.")
            return
        }
        stopPlayback()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlaying = true) }
            try {
                val anim = if (kind == "intro") st.introAnim else st.outroAnim
                val themeIdx = if (kind == "intro") 0 else (_uiState.value.questions.size - 1).coerceAtLeast(0)
                playIOPhase(text, anim, themeIdx)
            } finally {
                _uiState.update { it.copy(isPlaying = false) }
                refreshPreviewFrame()
            }
        }
    }

    fun startAutoPlay() {
        val qs = _uiState.value.questions
        if (qs.isEmpty()) {
            showToast("Please add questions in Editor first.")
            return
        }
        stopPlayback()
        playJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlaying = true) }
            try {
                val st = _uiState.value.settings
                if (st.musicOn && st.musicId.isNotBlank()) {
                    val track = _uiState.value.musicTracks.find { it.id == st.musicId }
                    val file = track?.let { File(storage.musicDir, it.fileName) }
                    if (file != null && file.exists()) {
                        val len = if (st.musicLen == "custom") st.musicSecs else (st.musicLen.toIntOrNull() ?: 0)
                        audioManager.startLiveMusic(
                            scope = viewModelScope,
                            file = file,
                            volPct = st.musicVol,
                            duck = st.musicDuck,
                            duckLvlPct = st.duckLvl,
                            fromSec = st.musicFrom,
                            lenSec = len,
                            loop = st.musicLoop
                        ) {}
                    }
                }

                val fromIdx = ((st.fromQ.toIntOrNull() ?: 1) - 1).coerceIn(0, qs.lastIndex)
                val toIdx = (st.toQ.toIntOrNull() ?: qs.size).coerceIn(fromIdx + 1, qs.size)

                var cur = _uiState.value.curIndex.coerceIn(0, qs.lastIndex)
                if ((cur == 0 || cur == fromIdx) && st.introOn && st.introText.isNotBlank()) {
                    playIOPhase(st.introText.trim(), st.introAnim, fromIdx)
                }

                while (cur < toIdx && isActive) {
                    _uiState.update { it.copy(curIndex = cur) }
                    val q = qs[cur]
                    playQuestionPhase(q, cur)
                    if (!isActive) break
                    playWaitBarPhase(q, cur, st.delay.coerceIn(2, 60))
                    if (!isActive) break
                    playAnswerPhase(q, cur)
                    if (!isActive) break
                    delay(1800)
                    cur++
                }

                if (isActive && st.outroOn && st.outroText.isNotBlank()) {
                    playIOPhase(st.outroText.trim(), st.outroAnim, (toIdx - 1).coerceAtLeast(0))
                }
                if (isActive) {
                    _uiState.update { it.copy(curIndex = (toIdx - 1).coerceAtLeast(0)) }
                    refreshPreviewFrame(reveal = true)
                }
            } finally {
                audioManager.stopAll()
                _uiState.update { it.copy(isPlaying = false, isMusicPreviewPlaying = false) }
            }
        }
    }

    private suspend fun playIOPhase(text: String, anim: String, themeIdx: Int) {
        val st = _uiState.value.settings
        val clip = withContext(Dispatchers.IO) {
            audioManager.getSpeechClip(text, st.lang, st.voice, st.rate)
        }
        val dur = clip.durationSec.coerceAtLeast(1.5f)
        val audioJob = viewModelScope.launch { audioManager.playClipLive(clip) }
        val startMs = System.currentTimeMillis()
        while (viewModelScope.isActive) {
            val el = (System.currentTimeMillis() - startMs) / 1000f
            val bmp = renderIOBitmap(SlideRenderer.IOState(text, anim, themeIdx, dur, el))
            _uiState.update { it.copy(previewBitmap = bmp) }
            if (!audioJob.isActive && el > dur + 0.8f) break
            delay(33)
        }
        audioJob.cancel()
    }

    private suspend fun playQuestionPhase(q: QuizQuestion, idx: Int) {
        val st = _uiState.value.settings
        val lang = QuizCatalog.langByKey(st.lang)
        val qStr = "${q.q}. ${lang.optLabel} A, ${q.a}. ${lang.optLabel} B, ${q.b}. ${lang.optLabel} C, ${q.c}. ${lang.optLabel} D, ${q.d}."
        val clip = withContext(Dispatchers.IO) {
            audioManager.getSpeechClip(qStr, st.lang, st.voice, st.rate)
        }
        val optFracs = SlideRenderer.optFractions(q, lang.optLabel)
        val optTimes = FloatArray(4) { i -> (optFracs[i] * clip.durationSec - 0.1f).coerceAtLeast(0f) }

        val audioJob = viewModelScope.launch { audioManager.playClipLive(clip) }
        val startMs = System.currentTimeMillis()
        while (audioJob.isActive) {
            val el = (System.currentTimeMillis() - startMs) / 1000f
            refreshPreviewFrame(reveal = false, progress = null, animElapsed = el, optTimes = optTimes)
            delay(33)
        }
        refreshPreviewFrame(reveal = false, progress = null, animElapsed = 10f, optTimes = optTimes)
    }

    private suspend fun playWaitBarPhase(q: QuizQuestion, idx: Int, delaySec: Int) {
        val startMs = System.currentTimeMillis()
        var lastTick = -1
        while (viewModelScope.isActive) {
            val el = (System.currentTimeMillis() - startMs) / 1000f
            val prog = (el / delaySec).coerceIn(0f, 1f)
            val tickIdx = (el * 2f).toInt()
            if (tickIdx != lastTick) {
                lastTick = tickIdx
                audioManager.playTickAsync(viewModelScope, tickIdx % 2 == 0)
            }
            refreshPreviewFrame(reveal = false, progress = prog, animElapsed = 10f, globalTime = el)
            if (el >= delaySec) break
            delay(40)
        }
    }

    private suspend fun playAnswerPhase(q: QuizQuestion, idx: Int) {
        val st = _uiState.value.settings
        val lang = QuizCatalog.langByKey(st.lang)
        val aStr = "${lang.ansLabel} ${q.normalizedAns()}, ${q.correctOptionText()}."
        val clip = withContext(Dispatchers.IO) {
            audioManager.getSpeechClip(aStr, st.lang, st.voice, st.rate)
        }
        val audioJob = viewModelScope.launch { audioManager.playClipLive(clip) }
        val startMs = System.currentTimeMillis()
        while (audioJob.isActive) {
            val el = (System.currentTimeMillis() - startMs) / 1000f
            refreshPreviewFrame(reveal = true, progress = null, animElapsed = 10f, revealElapsed = el)
            delay(33)
        }
        refreshPreviewFrame(reveal = true, progress = null, animElapsed = 10f, revealElapsed = 3f)
    }

    fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        audioManager.stopAll()
        _uiState.update { it.copy(isPlaying = false, isMusicPreviewPlaying = false) }
    }

    /* ---------- Video Export ---------- */

    fun startVideoExport() {
        val state = _uiState.value
        val qs = state.questions
        if (qs.isEmpty()) {
            showToast("Please add questions first.")
            return
        }
        val st = state.settings
        val fromIdx = ((st.fromQ.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        val toIdx = (st.toQ.toIntOrNull() ?: qs.size).coerceAtMost(qs.size)
        if (fromIdx >= toIdx) {
            showToast("Invalid Q# range.")
            return
        }
        if (st.musicOn && st.musicId.isBlank()) {
            showToast("Background music is ON, but no track is selected.")
            return
        }
        val musicFile = if (st.musicOn) {
            state.musicTracks.find { it.id == st.musicId }?.let { File(storage.musicDir, it.fileName) }
        } else null

        stopPlayback()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    exportStatus = "Preparing video export...",
                    exportedVideoFile = null
                )
            }

            try {
                val outFile = File(storage.exportsDir, "ksb-quiz-video-${System.currentTimeMillis()}.mp4")
                val (file, dur) = exporter.exportVideo(
                    questions = qs,
                    fromIdx = fromIdx,
                    toIdx = toIdx,
                    settings = st,
                    customBgPath = state.customBgPath,
                    musicFile = musicFile,
                    outputFile = outFile
                ) { txt, prog ->
                    _uiState.update { it.copy(exportStatus = txt, exportProgress = prog) }
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        exportStatus = "Video ready! Length: ${formatDur(dur)}",
                        exportedVideoFile = file,
                        exportedDurationSec = dur
                    )
                }
                showToast("MP4 video ready!")
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportStatus = "Cancelled.") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 0f,
                        exportStatus = "Export failed: ${e.message}"
                    )
                }
                showToast("Video export failed: ${e.message}")
            }
        }
    }

    fun cancelVideoExport() {
        exportJob?.cancel()
        exportJob = null
    }

    private fun formatDur(sec: Float): String {
        val s = sec.toInt().coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /* ---------- Toast ---------- */

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        playJob?.cancel()
        exportJob?.cancel()
        audioManager.release()
    }
}
