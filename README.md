# KSB LABS – Video Studio (Android)

Native Android (Kotlin + Jetpack Compose) rewrite of **KSB LABS Video Studio**.

## Core Features
- **Direct Launch (No Login)**: Opens directly into the Question Table Editor & Quiz Video Studio with zero authentication gates or quota limits.
- **Question Table Editor**: Add 1 or 20 blank rows, inline edit questions/options/answers/per-row fonts, green highlight on correct answers, yellow highlight on unrecognized bulk-import answers, smart Bulk Import parser (Excel/Google Sheets TSV, CSV, Markdown, Semicolon, Pipe, and flat pipe lines across English, Hindi, and Urdu answer formats), and export to TXT, CSV, or JSON via Storage Access Framework.
- **Quiz Studio & 1280×720 Renderer**:
  - 27 languages with localized option/answer speech phrases.
  - 5 voice characters (`Sara`, `Noor`, `Maya`, `Ali`, `Hamza`) via Google TTS with local caching + Android `TextToSpeech` system voices fallback.
  - 11 bundled Google Fonts (`Inter`, `Poppins`, `Montserrat`, `Oswald`, `Playfair Display`, `Bebas Neue`, `Baloo 2`, `Noto Naskh Arabic`, `Noto Nastaliq Urdu`, `Amiri`, `Noto Sans Devanagari`) with RTL/LTR support.
  - 9 color themes, 9 decorative background designs, auto-change background mode, Photo Picker custom background image, 9 loading bar styles & colors, Intro/Outro slides with 6 text animations, Question entrance animations, Channel Watermark, and Q# range selection.
  - Background Music library with local storage, track preview, volume & auto-lower voice ducking, duration limit, start offset, and looping.
- **One-Click MP4 Video Export**: Hardware/software `MediaCodec` (H.264 + AAC) + `MediaMuxer` 1280×720@25fps exporter with synchronized voice, countdown ticks, loading bar, animations, ducked background music, built-in Media3 ExoPlayer preview, and Save/Share actions.
