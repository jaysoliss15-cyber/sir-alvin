# ToonForge Tagalog

On-device 1080×1920 (9:16 vertical, 30fps) 2D/2.5D animated Tagalog storytelling video studio built in 100% pure Kotlin and Jetpack Compose—no PC, external video editor, FFmpeg, or NDK/C++ required.

## Highlights
- **6 Animation Styles**: `2.5D Paper-Cut Diorama`, `Stickman Whiteboard Explainer` (100% offline), `Modern Flat Vector`, `Dark Motion Comic`, `Chibi Anime Storytime`, and `Viral HUD & Motion Explainer`.
- **Master Prompt Template Manager & `.txt` Importer**: Pre-loaded with 3 Tagalog storytelling templates (`Viral Pinoy Explainer`, `Tagalog Horror & Urban Legend`, `Money & Life Parable`), SAF `.txt` import, inline editor, and locked JSON schema composition.
- **Open-Source LLM Router + `JsonSanitizer`**: Custom OpenAI-compatible → Groq → OpenRouter → free keyless AI Horde (`0000000000`), stripping `<think>` blocks and validating scene JSON.
- **Magic-Byte Validated Art + Procedural Diorama Fallback**: AI Horde `FLUX.1-schnell` / `AlbedoBase XL` with strict PNG/JPEG/WebP header checks and Canvas diorama fallback.
- **ElevenLabs TTS + Sample-Accurate PCM Stitching**: Tagalog phonetic normalizer, multi-key quota rotation, `MediaExtractor` + `MediaCodec` 44.1kHz mono PCM stitching, and zero-drift lip sync.
- **Deterministic `EGL14` + `MediaCodec` + `MediaMuxer` Export**: Saves H.264/AAC `.mp4` directly to `Movies/ToonForgeTagalog` via `MediaStore` inside a `dataSync` `ForegroundService` with partial `WakeLock`.

## Building on GitHub Actions
Pushing to `main` or `master` automatically triggers `.github/workflows/android-build.yml`, which:
1. Sets up JDK 21 and Gradle 9.3.1
2. Restores `debug.keystore` from `debug.keystore.base64` (or generates a fresh `debug.keystore` via `keytool` if absent)
3. Runs `ToonForgeCoreTest` unit tests and `:app:assembleDebug`
4. Uploads `ToonForge-Tagalog-debug-apk` under the Actions tab for direct APK download.

## Building Locally (Android Studio / CLI)
1. Clone this repository.
2. Restore or generate the debug keystore (only needed once):
   ```bash
   if [ -f debug.keystore.base64 ]; then base64 -d debug.keystore.base64 > debug.keystore; else keytool -genkeypair -noprompt -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"; fi
   ```
3. Open in Android Studio (JDK 21) or run:
   ```bash
   ./gradlew :app:assembleDebug
   ```
