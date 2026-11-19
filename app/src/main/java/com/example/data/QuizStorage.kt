package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.MusicTrack
import com.example.model.QuizCatalog
import com.example.model.QuizQuestion
import com.example.model.StudioSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val Context.ksbDataStore by preferencesDataStore(name = "ksb_studio_prefs")

class QuizStorage(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val QUESTIONS = stringPreferencesKey("questions_json")
        val SETTINGS = stringPreferencesKey("settings_json")
        val MUSIC_TRACKS = stringPreferencesKey("music_tracks_json")
        val CUSTOM_BG_PATH = stringPreferencesKey("custom_bg_path")
    }

    val musicDir: File by lazy {
        File(context.filesDir, "music").apply { mkdirs() }
    }

    val exportsDir: File by lazy {
        File(context.filesDir, "exports").apply { mkdirs() }
    }

    val ttsCacheDir: File by lazy {
        File(context.cacheDir, "tts_mp3").apply { mkdirs() }
    }

    val customBgFile: File by lazy {
        File(context.filesDir, "custom_slide_bg.jpg")
    }

    suspend fun loadQuestions(): List<QuizQuestion> {
        val raw = context.ksbDataStore.data.first()[Keys.QUESTIONS]
        if (raw.isNullOrBlank()) return QuizCatalog.sampleQuestions
        return runCatching {
            val parsed = json.decodeFromString<List<QuizQuestion>>(raw)
            if (parsed.isEmpty()) QuizCatalog.sampleQuestions else parsed.map {
                it.copy(ans = it.normalizedAns())
            }
        }.getOrElse { QuizCatalog.sampleQuestions }
    }

    suspend fun saveQuestions(questions: List<QuizQuestion>) {
        val clean = questions.map { it.copy(unsure = false) }
        val encoded = json.encodeToString(clean)
        context.ksbDataStore.edit { it[Keys.QUESTIONS] = encoded }
    }

    suspend fun loadSettings(): StudioSettings {
        val raw = context.ksbDataStore.data.first()[Keys.SETTINGS] ?: return StudioSettings()
        return runCatching { json.decodeFromString<StudioSettings>(raw) }
            .getOrElse { StudioSettings() }
    }

    suspend fun saveSettings(settings: StudioSettings) {
        val encoded = json.encodeToString(settings)
        context.ksbDataStore.edit { it[Keys.SETTINGS] = encoded }
    }

    suspend fun loadMusicTracks(): List<MusicTrack> {
        val raw = context.ksbDataStore.data.first()[Keys.MUSIC_TRACKS] ?: return emptyList()
        return runCatching { json.decodeFromString<List<MusicTrack>>(raw) }
            .getOrElse { emptyList() }
            .filter { File(musicDir, it.fileName).exists() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun saveMusicTracks(tracks: List<MusicTrack>) {
        val encoded = json.encodeToString(tracks)
        context.ksbDataStore.edit { it[Keys.MUSIC_TRACKS] = encoded }
    }

    suspend fun loadCustomBgPath(): String? {
        val path = context.ksbDataStore.data.first()[Keys.CUSTOM_BG_PATH]
        return if (!path.isNullOrBlank() && File(path).exists()) path else null
    }

    suspend fun saveCustomBgPath(path: String?) {
        context.ksbDataStore.edit {
            if (path.isNullOrBlank()) it.remove(Keys.CUSTOM_BG_PATH)
            else it[Keys.CUSTOM_BG_PATH] = path
        }
    }
}
