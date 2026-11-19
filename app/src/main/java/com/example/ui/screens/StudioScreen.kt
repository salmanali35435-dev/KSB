package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.QuizCatalog
import com.example.ui.components.KsbDropdown
import com.example.ui.components.KsbSectionCard
import com.example.ui.components.KsbSwitchRow
import com.example.ui.theme.KsbColors
import com.example.viewmodel.KsbUiState
import com.example.viewmodel.KsbViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    state: KsbUiState,
    vm: KsbViewModel,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    var pendingMusicUri by remember { mutableStateOf<Uri?>(null) }
    var musicNameInput by remember { mutableStateOf("") }
    var showResetFontsConfirm by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.setCustomBackground(uri)
    }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingMusicUri = uri
            musicNameInput = ""
        }
    }

    val saveVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val file = state.exportedVideoFile
        if (uri != null && file != null && file.exists()) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { inp -> inp.copyTo(out) }
                }
                true
            }.getOrDefault(false)
            vm.showToast(if (ok) "Video saved to device" else "Could not save video")
        }
    }

    pendingMusicUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingMusicUri = null },
            title = { Text("Music Track Name") },
            text = {
                OutlinedTextField(
                    value = musicNameInput,
                    onValueChange = { musicNameInput = it },
                    label = { Text("Name in list (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = uri
                    val title = musicNameInput
                    pendingMusicUri = null
                    vm.importMusicFile(chosen, title)
                }) { Text("Add Music") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMusicUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showResetFontsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetFontsConfirm = false },
            title = { Text("Reset per-question fonts?") },
            text = { Text("All questions will use the global font.") },
            confirmButton = {
                Button(onClick = {
                    showResetFontsConfirm = false
                    vm.resetPerQuestionFonts()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetFontsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StagePreviewCard(state, vm)
                VideoExportCard(
                    state = state,
                    vm = vm,
                    onSaveVideo = { saveVideoLauncher.launch("ksb-quiz-video.mp4") },
                    onShareVideo = { file -> shareVideoFile(context, file) }
                )
            }
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioSettingsCards(
                    state = state,
                    vm = vm,
                    onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                    onPickBgImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAskResetFonts = { showResetFontsConfirm = true }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StagePreviewCard(state, vm) }
            item {
                VideoExportCard(
                    state = state,
                    vm = vm,
                    onSaveVideo = { saveVideoLauncher.launch("ksb-quiz-video.mp4") },
                    onShareVideo = { file -> shareVideoFile(context, file) }
                )
            }
            item {
                StudioSettingsCards(
                    state = state,
                    vm = vm,
                    onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                    onPickBgImage = {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAskResetFonts = { showResetFontsConfirm = true }
                )
            }
        }
    }
}

private fun shareVideoFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Quiz Video"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagePreviewCard(state: KsbUiState, vm: KsbViewModel) {
    val curQ = state.questions.getOrNull(state.curIndex)
    val jumpOptions = state.questions.mapIndexed { i, q ->
        i to "${i + 1}. ${q.q.take(28)}"
    }.ifEmpty { listOf(0 to "No questions") }
    val selectedJump = jumpOptions.find { it.first == state.curIndex }?.second ?: jumpOptions.first().second
    val qFontKey = curQ?.font.orEmpty()
    val qFontLabel = if (qFontKey.isBlank()) "Same as global font" else QuizCatalog.fontByKey(qFontKey).label

    KsbSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KsbDropdown(
                label = "Go to question",
                selectedText = selectedJump,
                options = jumpOptions,
                onSelect = { vm.jumpToQuestion(it) },
                modifier = Modifier.weight(1f),
                testTag = "studio_jump_select"
            )
            KsbDropdown(
                label = "This question's font",
                selectedText = qFontLabel,
                options = listOf("" to "Same as global font") + QuizCatalog.fonts.map { it.key to it.label },
                onSelect = { f ->
                    if (curQ != null) {
                        vm.updateQuestion(state.curIndex) { it.copy(font = f) }
                    }
                },
                modifier = Modifier.weight(1f),
                testTag = "studio_qfont_select"
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A3FB5))
                .testTag("studio_slide_preview"),
            contentAlignment = Alignment.Center
        ) {
            state.previewBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Quiz slide preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { vm.prevQuestion() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_prev_button")
            ) {
                Icon(Icons.Filled.SkipPrevious, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Prev")
            }
            OutlinedButton(
                onClick = { vm.readCurrentQuestion() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_readq_button")
            ) {
                Icon(Icons.Filled.VolumeUp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Read Question")
            }
            OutlinedButton(
                onClick = { vm.showAndReadAnswer() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_showa_button")
            ) {
                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp), tint = KsbColors.OkGreen)
                Spacer(Modifier.width(4.dp))
                Text("Show + Read Answer")
            }
            OutlinedButton(
                onClick = { vm.nextQuestion() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_next_button")
            ) {
                Text("Next")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(18.dp))
            }
            Button(
                onClick = { vm.startAutoPlay() },
                colors = ButtonDefaults.buttonColors(containerColor = KsbColors.OkGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_autoplay_button")
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Auto Play")
            }
            Button(
                onClick = { vm.stopPlayback() },
                colors = ButtonDefaults.buttonColors(containerColor = KsbColors.DangerRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("studio_stop_button")
            ) {
                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun VideoExportCard(
    state: KsbUiState,
    vm: KsbViewModel,
    onSaveVideo: () -> Unit,
    onShareVideo: (File) -> Unit
) {
    KsbSectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { vm.startVideoExport() },
                    enabled = !state.isExporting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(KsbColors.VideoGradStart, KsbColors.VideoGradEnd)))
                        .testTag("studio_create_video_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VideoFile, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Create & Download Video (with voice)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
                if (state.isExporting) {
                    Button(
                        onClick = { vm.cancelVideoExport() },
                        colors = ButtonDefaults.buttonColors(containerColor = KsbColors.DangerRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 52.dp).testTag("studio_cancel_export_button")
                    ) {
                        Icon(Icons.Filled.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            }

            if (state.isExporting || state.exportStatus.isNotBlank()) {
                Text(
                    text = state.exportStatus,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    progress = { state.exportProgress.coerceIn(0f, 1f) },
                    color = KsbColors.OkGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            state.exportedVideoFile?.takeIf { it.exists() }?.let { file ->
                ExportedVideoPlayer(file = file)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onSaveVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = KsbColors.OkGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_save_mp4_button")
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save MP4")
                    }
                    OutlinedButton(
                        onClick = { onShareVideo(file) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("studio_share_mp4_button")
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Video")
                    }
                }
            }

            Text(
                text = "One click renders 1280×720 H.264 MP4 with voice, tick countdown, loading bar, animations, and background music.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExportedVideoPlayer(file: File) {
    val context = LocalContext.current
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    LaunchedEffect(file.absolutePath) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }
    DisposableEffect(file.absolutePath) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    )
}

@Composable
private fun StudioSettingsCards(
    state: KsbUiState,
    vm: KsbViewModel,
    onPickMusic: () -> Unit,
    onPickBgImage: () -> Unit,
    onAskResetFonts: () -> Unit
) {
    val s = state.settings
    val langObj = QuizCatalog.langByKey(s.lang)
    val voiceOptions = buildList {
        QuizCatalog.voiceCharacters.forEach { c ->
            val icon = if (c.gender == "F") "👩" else "👨"
            val gLabel = if (c.gender == "F") "Female" else "Male"
            add("g:${c.id}" to "$icon ${c.name} — $gLabel · ${c.description}")
        }
        state.systemVoices.forEach { (id, label) ->
            add(id to "🔈 $label")
        }
    }
    val selectedVoiceLabel = voiceOptions.find { it.first == s.voice }?.second ?: voiceOptions.first().second

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Voice
        KsbSectionCard(title = "🎙 Voice") {
            KsbDropdown(
                label = "Language (27)",
                selectedText = langObj.name,
                options = QuizCatalog.languages.map { it.key to it.name },
                onSelect = { k -> vm.updateSettings { it.copy(lang = k) } },
                testTag = "setting_lang"
            )
            Spacer(Modifier.height(10.dp))
            KsbDropdown(
                label = "Voice character",
                selectedText = selectedVoiceLabel,
                options = voiceOptions,
                onSelect = { v -> vm.updateSettings { it.copy(voice = v) } },
                testTag = "setting_voice"
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Speed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.1f".format(s.rate)}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = s.rate,
                onValueChange = { r -> vm.updateSettings { it.copy(rate = (r * 10f).roundToInt() / 10f) } },
                valueRange = 0.6f..1.4f,
                steps = 7
            )
            OutlinedTextField(
                value = s.delay.toString(),
                onValueChange = { txt ->
                    val v = txt.toIntOrNull()
                    if (v != null) vm.updateSettings { it.copy(delay = v.coerceIn(2, 60)) }
                    else if (txt.isEmpty()) vm.updateSettings { it.copy(delay = 2) }
                },
                label = { Text("Loading time (2–60 sec)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Intro & Outro
        KsbSectionCard(title = "🎞 Intro & Outro") {
            KsbSwitchRow(
                label = "Add Intro (at video start)",
                checked = s.introOn,
                onCheckedChange = { on -> vm.updateSettings { it.copy(introOn = on) } }
            )
            KsbDropdown(
                label = "Intro animation",
                selectedText = QuizCatalog.ioAnimations.find { it.key == s.introAnim }?.label ?: "Typewriter",
                options = QuizCatalog.ioAnimations.map { it.key to it.label },
                onSelect = { a -> vm.updateSettings { it.copy(introAnim = a) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.introText,
                onValueChange = { t -> vm.updateSettings { it.copy(introText = t) } },
                label = { Text("Intro text (any language)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.previewIO("intro") },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Text("▶ Preview Intro")
            }

            Spacer(Modifier.height(12.dp))
            KsbSwitchRow(
                label = "Add Outro (at video end)",
                checked = s.outroOn,
                onCheckedChange = { on -> vm.updateSettings { it.copy(outroOn = on) } }
            )
            KsbDropdown(
                label = "Outro animation",
                selectedText = QuizCatalog.ioAnimations.find { it.key == s.outroAnim }?.label ?: "Fade Up",
                options = QuizCatalog.ioAnimations.map { it.key to it.label },
                onSelect = { a -> vm.updateSettings { it.copy(outroAnim = a) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.outroText,
                onValueChange = { t -> vm.updateSettings { it.copy(outroText = t) } },
                label = { Text("Outro text") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.previewIO("outro") },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Text("▶ Preview Outro")
            }
        }

        // 3. Background Music
        KsbSectionCard(title = "🎵 Background Music") {
            KsbSwitchRow(
                label = "Add background music (Intro + Quiz + Outro)",
                checked = s.musicOn,
                onCheckedChange = { on -> vm.updateSettings { it.copy(musicOn = on) } }
            )
            val trackOptions = state.musicTracks.map { t ->
                val mm = t.durationSec.toInt() / 60
                val ss = t.durationSec.toInt() % 60
                val mb = "%.1f MB".format(t.sizeBytes / 1_048_576.0)
                t.id to "${t.name} ($mm:${"%02d".format(ss)} · $mb)"
            }.ifEmpty { listOf("" to "— no music added —") }
            val selectedTrackLabel = trackOptions.find { it.first == s.musicId }?.second ?: trackOptions.first().second

            KsbDropdown(
                label = "Music (from your list)",
                selectedText = selectedTrackLabel,
                options = trackOptions,
                onSelect = { id -> vm.updateSettings { it.copy(musicId = id) } }
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPickMusic,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add File", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { vm.toggleMusicPreview() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (state.isMusicPreviewPlaying) "■ Stop" else "▶ Preview", fontSize = 12.sp)
                }
                Button(
                    onClick = { vm.deleteSelectedMusic() },
                    colors = ButtonDefaults.buttonColors(containerColor = KsbColors.DangerRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete music", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            VolumeControlRow(
                label = "Music volume (%)",
                value = s.musicVol,
                onChange = { v -> vm.updateSettings { it.copy(musicVol = v.coerceIn(1, 100)) } }
            )
            KsbSwitchRow(
                label = "Auto-lower when voice speaks",
                checked = s.musicDuck,
                onCheckedChange = { d -> vm.updateSettings { it.copy(musicDuck = d) } }
            )
            VolumeControlRow(
                label = "Auto-lower level (%)",
                value = s.duckLvl,
                onChange = { v -> vm.updateSettings { it.copy(duckLvl = v.coerceIn(1, 100)) } }
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KsbDropdown(
                    label = "Music duration",
                    selectedText = QuizCatalog.musicLengths.find { it.key == s.musicLen }?.label ?: "Full video",
                    options = QuizCatalog.musicLengths.map { it.key to it.label },
                    onSelect = { k -> vm.updateSettings { it.copy(musicLen = k) } },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = s.musicFrom.toString(),
                    onValueChange = { txt ->
                        val v = txt.toIntOrNull() ?: 0
                        vm.updateSettings { it.copy(musicFrom = v.coerceAtLeast(0)) }
                    },
                    label = { Text("Start at (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }
            if (s.musicLen == "custom") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = s.musicSecs.toString(),
                    onValueChange = { txt ->
                        val v = txt.toIntOrNull() ?: 20
                        vm.updateSettings { it.copy(musicSecs = v.coerceIn(1, 3600)) }
                    },
                    label = { Text("Custom duration (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            KsbSwitchRow(
                label = "Loop track if shorter than video",
                checked = s.musicLoop,
                onCheckedChange = { l -> vm.updateSettings { it.copy(musicLoop = l) } }
            )
        }

        // 4. Question animations
        KsbSectionCard(title = "✨ Question Animations") {
            KsbSwitchRow(
                label = "Animate questions & options",
                checked = s.qAnimOn,
                onCheckedChange = { on -> vm.updateSettings { it.copy(qAnimOn = on) } }
            )
            KsbDropdown(
                label = "Entrance style",
                selectedText = QuizCatalog.qAnimations.find { it.key == s.qAnim }?.label ?: "Slide",
                options = QuizCatalog.qAnimations.map { it.key to it.label },
                onSelect = { a -> vm.updateSettings { it.copy(qAnim = a) } }
            )
        }

        // 5. Appearance
        KsbSectionCard(title = "🎨 Appearance") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KsbDropdown(
                    label = "Background colour",
                    selectedText = QuizCatalog.themes.find { it.key == s.bg }?.label ?: "Blue",
                    options = QuizCatalog.themes.map { it.key to it.label },
                    onSelect = { bg ->
                        vm.resetCustomBackground()
                        vm.updateSettings { it.copy(bg = bg) }
                    },
                    modifier = Modifier.weight(1f)
                )
                KsbDropdown(
                    label = "Background design",
                    selectedText = QuizCatalog.designs.find { it.key == s.design }?.label ?: "Circles",
                    options = QuizCatalog.designs.map { it.key to it.label },
                    onSelect = { d ->
                        vm.resetCustomBackground()
                        vm.updateSettings { it.copy(design = d) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            KsbDropdown(
                label = "Auto-change background",
                selectedText = when (s.auto) {
                    "colors" -> "Change colour every question"
                    "both" -> "Change colour + design every question"
                    else -> "Off (fixed)"
                },
                options = listOf(
                    "off" to "Off (fixed)",
                    "colors" to "Change colour every question",
                    "both" to "Change colour + design every question"
                ),
                onSelect = { a -> vm.updateSettings { it.copy(auto = a) } }
            )
            Spacer(Modifier.height(8.dp))
            KsbDropdown(
                label = "Font (all questions)",
                selectedText = QuizCatalog.fontByKey(s.font).label,
                options = QuizCatalog.fonts.map { it.key to it.label },
                onSelect = { f -> vm.updateSettings { it.copy(font = f) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAskResetFonts,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) { Text("Reset per-question fonts") }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickBgImage,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Custom image")
                }
                OutlinedButton(
                    onClick = { vm.resetCustomBackground() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Reset") }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KsbDropdown(
                    label = "Bar colour",
                    selectedText = QuizCatalog.barColors.find { it.hex == s.bar }?.label ?: "Blue",
                    options = QuizCatalog.barColors.map { it.hex to it.label },
                    onSelect = { c -> vm.updateSettings { it.copy(bar = c) } },
                    modifier = Modifier.weight(1f)
                )
                KsbDropdown(
                    label = "Bar style",
                    selectedText = QuizCatalog.barStyles.find { it.key == s.style }?.label ?: "1. Smooth",
                    options = QuizCatalog.barStyles.map { it.key to it.label },
                    onSelect = { st -> vm.updateSettings { it.copy(style = st) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6. Watermark & Range
        KsbSectionCard(title = "🏷 Watermark & Range") {
            OutlinedTextField(
                value = s.wm,
                onValueChange = { w -> vm.updateSettings { it.copy(wm = w) } },
                label = { Text("Channel watermark") },
                placeholder = { Text("Channel name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Opacity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(s.wmOp * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = s.wmOp,
                onValueChange = { op -> vm.updateSettings { it.copy(wmOp = op) } },
                valueRange = 0.03f..1f
            )
            Text("Size (${"%.1f".format(s.wmSize)})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = s.wmSize,
                onValueChange = { sz -> vm.updateSettings { it.copy(wmSize = sz) } },
                valueRange = 1f..8f
            )
            KsbDropdown(
                label = "Position",
                selectedText = QuizCatalog.watermarkPositions.find { it.key == s.wmPos }?.label ?: "Bottom right",
                options = QuizCatalog.watermarkPositions.map { it.key to it.label },
                onSelect = { p -> vm.updateSettings { it.copy(wmPos = p) } }
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.fromQ,
                    onValueChange = { f -> vm.updateSettings { it.copy(fromQ = f) } },
                    label = { Text("Record from Q#") },
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = s.toQ,
                    onValueChange = { t -> vm.updateSettings { it.copy(toQ = t) } },
                    label = { Text("to Q#") },
                    placeholder = { Text("last") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VolumeControlRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onChange((value - 5).coerceIn(1, 100)) },
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(1, 100)) },
                valueRange = 1f..100f,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onChange((value + 5).coerceIn(1, 100)) },
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}
