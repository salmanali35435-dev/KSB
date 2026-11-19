package com.example.model

import androidx.annotation.FontRes
import com.example.R
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class QuizQuestion(
    val id: String = UUID.randomUUID().toString(),
    val q: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val ans: String = "A",
    val font: String = "",
    val unsure: Boolean = false
) {
    fun normalizedAns(): String = if (ans.uppercase() in listOf("A", "B", "C", "D")) ans.uppercase() else "A"
    fun optionByKey(key: String): String = when (key.uppercase()) {
        "A" -> a
        "B" -> b
        "C" -> c
        "D" -> d
        else -> a
    }
    fun correctOptionText(): String = optionByKey(normalizedAns())
}

@Serializable
data class StudioSettings(
    val lang: String = "en",
    val voice: String = "g:f1",
    val bg: String = "blue",
    val design: String = "circles",
    val auto: String = "off",
    val font: String = "default",
    val uiTheme: String = "system", // system | light | dark
    val smooth: Boolean = true,
    val musicOn: Boolean = false,
    val musicId: String = "",
    val musicVol: Int = 20,
    val musicDuck: Boolean = true,
    val duckLvl: Int = 25,
    val musicLen: String = "0",
    val musicSecs: Int = 20,
    val musicFrom: Int = 0,
    val musicLoop: Boolean = true,
    val introOn: Boolean = false,
    val outroOn: Boolean = false,
    val introText: String = "",
    val outroText: String = "",
    val introAnim: String = "type",
    val outroAnim: String = "fade",
    val qAnimOn: Boolean = false,
    val qAnim: String = "slide",
    val bar: String = "#2979ff",
    val style: String = "smooth",
    val delay: Int = 6,
    val rate: Float = 1.0f,
    val wm: String = "",
    val wmOp: Float = 0.25f,
    val wmSize: Float = 2.4f,
    val wmPos: String = "br",
    val fromQ: String = "",
    val toQ: String = ""
)

@Serializable
data class MusicTrack(
    val id: String,
    val name: String,
    val durationSec: Double,
    val sizeBytes: Long,
    val fileName: String
)

data class LangConfig(
    val key: String,
    val name: String,
    val localeCode: String,
    val ttsCode: String,
    val optLabel: String,
    val ansLabel: String
)

data class VoiceCharacter(
    val id: String,
    val gender: String,
    val name: String,
    val description: String,
    val rateMultiplier: Float
)

data class SlideFont(
    val key: String,
    val label: String,
    @FontRes val fontRes: Int?
)

data class SlideTheme(
    val key: String,
    val bgHex: String,
    val accentHex: String,
    val label: String
)

data class SlideDesignOption(
    val key: String,
    val label: String
)

data class BarColorOption(
    val hex: String,
    val label: String
)

data class BarStyleOption(
    val key: String,
    val label: String
)

data class AnimOption(
    val key: String,
    val label: String
)

object QuizCatalog {
    val sampleQuestions = listOf(
        QuizQuestion(
            q = "What is the capital of the United States?",
            a = "New York",
            b = "Washington, D.C.",
            c = "Los Angeles",
            d = "Chicago",
            ans = "B",
            font = ""
        )
    )

    val languages = listOf(
        LangConfig("en", "English", "en-US", "en", "Option", "The correct answer is"),
        LangConfig("ur", "اردو (Urdu)", "ur-PK", "ur", "آپشن", "صحیح جواب ہے"),
        LangConfig("hi", "हिन्दी (Hindi)", "hi-IN", "hi", "विकल्प", "सही जवाब है"),
        LangConfig("ar", "العربية (Arabic)", "ar-SA", "ar", "الخيار", "الإجابة الصحيحة هي"),
        LangConfig("bn", "বাংলা (Bengali)", "bn-IN", "bn", "বিকল্প", "সঠিক উত্তর হলো"),
        LangConfig("ta", "தமிழ் (Tamil)", "ta-IN", "ta", "விருப்பம்", "சரியான பதில்"),
        LangConfig("te", "తెలుగు (Telugu)", "te-IN", "te", "ఎంపిక", "సరైన సమాధానం"),
        LangConfig("mr", "मराठी (Marathi)", "mr-IN", "mr", "पर्याय", "बरोबर उत्तर आहे"),
        LangConfig("gu", "ગુજરાતી (Gujarati)", "gu-IN", "gu", "વિકલ્પ", "સાચો જવાબ છે"),
        LangConfig("kn", "ಕನ್ನಡ (Kannada)", "kn-IN", "kn", "ಆಯ್ಕೆ", "ಸರಿಯಾದ ಉತ್ತರ"),
        LangConfig("ml", "മലയാളം (Malayalam)", "ml-IN", "ml", "ഓപ്ഷൻ", "ശരിയായ ഉത്തരം"),
        LangConfig("ne", "नेपाली (Nepali)", "ne-NP", "ne", "विकल्प", "सही उत्तर हो"),
        LangConfig("es", "Español (Spanish)", "es-ES", "es", "Opción", "La respuesta correcta es"),
        LangConfig("fr", "Français (French)", "fr-FR", "fr", "Option", "La bonne réponse est"),
        LangConfig("de", "Deutsch (German)", "de-DE", "de", "Option", "Die richtige Antwort ist"),
        LangConfig("it", "Italiano (Italian)", "it-IT", "it", "Opzione", "La risposta corretta è"),
        LangConfig("pt", "Português (Portuguese)", "pt-BR", "pt", "Opção", "A resposta correta é"),
        LangConfig("ru", "Русский (Russian)", "ru-RU", "ru", "Вариант", "Правильный ответ —"),
        LangConfig("tr", "Türkçe (Turkish)", "tr-TR", "tr", "Seçenek", "Doğru cevap"),
        LangConfig("ja", "日本語 (Japanese)", "ja-JP", "ja", "選択肢", "正解は"),
        LangConfig("ko", "한국어 (Korean)", "ko-KR", "ko", "보기", "정답은"),
        LangConfig("zh", "中文 (Chinese)", "zh-CN", "zh-CN", "选项", "正确答案是"),
        LangConfig("id", "Bahasa Indonesia", "id-ID", "id", "Opsi", "Jawaban yang benar adalah"),
        LangConfig("th", "ไทย (Thai)", "th-TH", "th", "ตัวเลือก", "คำตอบที่ถูกต้องคือ"),
        LangConfig("vi", "Tiếng Việt (Vietnamese)", "vi-VN", "vi", "Lựa chọn", "Đáp án đúng là"),
        LangConfig("nl", "Nederlands (Dutch)", "nl-NL", "nl", "Optie", "Het juiste antwoord is"),
        LangConfig("pl", "Polski (Polish)", "pl-PL", "pl", "Opcja", "Prawidłowa odpowiedź to")
    )

    fun langByKey(key: String): LangConfig = languages.find { it.key == key } ?: languages.first()

    val voiceCharacters = listOf(
        VoiceCharacter("f1", "F", "Sara", "Natural", 1.00f),
        VoiceCharacter("f2", "F", "Noor", "Soft & warm", 1.08f),
        VoiceCharacter("f3", "F", "Maya", "Bright / young", 1.16f),
        VoiceCharacter("m1", "M", "Ali", "Natural", 0.90f),
        VoiceCharacter("m2", "M", "Hamza", "Deep & calm", 0.82f)
    )

    fun charOf(voiceId: String): VoiceCharacter {
        val id = voiceId.removePrefix("g:")
        return voiceCharacters.find { it.id == id } ?: voiceCharacters.first()
    }

    val fonts = listOf(
        SlideFont("default", "System Sans (Default)", null),
        SlideFont("poppins", "Poppins – Bold Modern", R.font.poppins),
        SlideFont("montserrat", "Montserrat – Clean Bold", R.font.montserrat),
        SlideFont("oswald", "Oswald – Strong Condensed", R.font.oswald),
        SlideFont("playfair", "Playfair – Elegant Serif", R.font.playfair_display),
        SlideFont("bebas", "Bebas Neue – Tall Headline", R.font.bebas_neue),
        SlideFont("baloo", "Baloo 2 – Friendly Rounded", R.font.baloo_2),
        SlideFont("naskh", "Naskh – Urdu/Arabic Clean", R.font.noto_naskh_arabic),
        SlideFont("nastaliq", "Nastaliq – Urdu Calligraphy", R.font.noto_nastaliq_urdu),
        SlideFont("amiri", "Amiri – Classic Urdu/Arabic", R.font.amiri),
        SlideFont("devanagari", "Devanagari – Hindi Bold", R.font.noto_sans_devanagari)
    )

    fun fontByKey(key: String): SlideFont = fonts.find { it.key == key } ?: fonts.first()

    val themes = listOf(
        SlideTheme("blue", "#0A3FB5", "#FFC916", "Blue"),
        SlideTheme("red", "#B71C1C", "#FFD54F", "Red"),
        SlideTheme("green", "#1B5E20", "#FFEE58", "Green"),
        SlideTheme("purple", "#4A148C", "#FFCA28", "Purple"),
        SlideTheme("teal", "#00695C", "#FFD54F", "Teal"),
        SlideTheme("orange", "#D84315", "#FFFFFF", "Orange"),
        SlideTheme("pink", "#AD1457", "#FFE082", "Pink"),
        SlideTheme("navy", "#0D1B3E", "#4FC3F7", "Navy"),
        SlideTheme("black", "#111827", "#FBBF24", "Black")
    )

    val designs = listOf(
        SlideDesignOption("circles", "Circles"),
        SlideDesignOption("rings", "Rings"),
        SlideDesignOption("dots", "Dots"),
        SlideDesignOption("stripes", "Stripes"),
        SlideDesignOption("grid", "Grid"),
        SlideDesignOption("waves", "Waves"),
        SlideDesignOption("bokeh", "Bokeh"),
        SlideDesignOption("spot", "Spotlight"),
        SlideDesignOption("plain", "Plain")
    )

    fun effectiveLook(settings: StudioSettings, questionIndex: Int): Pair<SlideTheme, String> {
        val baseColorIdx = themes.indexOfFirst { it.key == settings.bg }.coerceAtLeast(0)
        val baseDesignIdx = designs.indexOfFirst { it.key == settings.design }.coerceAtLeast(0)
        val theme = if (settings.auto != "off") {
            themes[(baseColorIdx + questionIndex.coerceAtLeast(0)) % themes.size]
        } else {
            themes[baseColorIdx]
        }
        val design = if (settings.auto == "both") {
            designs[(baseDesignIdx + questionIndex.coerceAtLeast(0)) % designs.size].key
        } else {
            designs[baseDesignIdx].key
        }
        return theme to design
    }

    val barColors = listOf(
        BarColorOption("#2979ff", "Blue"),
        BarColorOption("#e53935", "Red"),
        BarColorOption("#43a047", "Green"),
        BarColorOption("#ffc916", "Yellow"),
        BarColorOption("#ff9800", "Orange"),
        BarColorOption("#ec407a", "Pink"),
        BarColorOption("#ab47bc", "Purple"),
        BarColorOption("#26c6da", "Cyan"),
        BarColorOption("#ffffff", "White")
    )

    val barStyles = listOf(
        BarStyleOption("smooth", "1. Smooth"),
        BarStyleOption("round", "2. Rounded"),
        BarStyleOption("seg", "3. Segments"),
        BarStyleOption("stripes", "4. Moving Stripes"),
        BarStyleOption("gradient", "5. Gradient"),
        BarStyleOption("glow", "6. Glow"),
        BarStyleOption("dots", "7. Dots"),
        BarStyleOption("thin", "8. Thin Line"),
        BarStyleOption("neon", "9. Neon")
    )

    val ioAnimations = listOf(
        AnimOption("type", "Typewriter"),
        AnimOption("fade", "Fade Up (line by line)"),
        AnimOption("zoom", "Zoom / Pop (word by word)"),
        AnimOption("highlight", "Word Highlight (spoken)"),
        AnimOption("glow", "Glow & Bounce"),
        AnimOption("none", "No animation")
    )

    val qAnimations = listOf(
        AnimOption("fade", "Fade"),
        AnimOption("slide", "Slide"),
        AnimOption("zoom", "Zoom"),
        AnimOption("bounce", "Bounce")
    )

    val watermarkPositions = listOf(
        AnimOption("br", "Bottom right"),
        AnimOption("bl", "Bottom left"),
        AnimOption("tr", "Top right"),
        AnimOption("tl", "Top left"),
        AnimOption("c", "Center")
    )

    val musicLengths = listOf(
        AnimOption("0", "Full video"),
        AnimOption("5", "5 seconds"),
        AnimOption("10", "10 seconds"),
        AnimOption("20", "20 seconds"),
        AnimOption("30", "30 seconds"),
        AnimOption("custom", "Custom seconds...")
    )
}

enum class MainTab {
    EDITOR,
    STUDIO
}
