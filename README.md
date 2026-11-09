# AutoShorts for Android

AutoShorts is a native Android app for turning long videos and audio recordings into ranked short-form clip ideas. It uses Android's document picker, Room storage, Media3 playback and portrait rendering, Deepgram transcription, and configurable AI moment analysis. The UI is built with Kotlin and Jetpack Compose; it is not a WebView wrapper.

## Features

- Import one or multiple device video and audio files through Android's system file picker. An optional validated YouTube video URL can be attached to a single selected file as a reference; editing always uses the authorized local file, and the app does not download YouTube streams.
- Transcribe source audio with Deepgram Nova-2 and detect/rank clip moments with DeepSeek, Claude, Gemini, OpenAI, or an OpenAI-compatible endpoint.
- Review, select, preview, and trim candidates with persistent per-project history.
- Create and export manually timed clips without AI credentials; exports show a copyright review warning.
- Export selected clips as 1080 x 1920 MP4 files to `Movies/AutoShorts` and share them with Android apps.
- Store API keys in Android EncryptedSharedPreferences.
- Run analysis as a unique WorkManager job per project and track pipeline progress and export status on device.

Analysis sends extracted audio to Deepgram and transcript text to the selected AI provider. Configure keys in the app's Settings tab before running analysis. Manual clip creation and export work without those keys, but transcription and automatic moment ranking are not yet API-free. WorkManager jobs restart analysis from the beginning after interruption; audio is still extracted as one full file, so this is not yet a reliable 10-hour pipeline. Supported input formats depend on Android's media decoders and the selected file provider. Local Ollama/Whisper execution and desktop-only YouTube download/license checks are not part of this Android build.

## Build In GitHub Codespaces

1. Open this repository in a GitHub Codespace. No PC-side Android Studio or emulator is required.
2. In the Codespaces terminal, use JDK 17-21. If the image's default Java is newer, select its installed JDK 21:

	```bash
	export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.13.0+2-ms
	export PATH="$JAVA_HOME/bin:$PATH"
	```

	On another Codespaces image, use `sdk list java` and select an installed JDK from 17 through 21.

3. Build the installable debug APK:

	```bash
	./gradlew assembleDebug
	```

	The APK is `app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed and can be installed directly on an Android phone.

4. To get the APK onto a phone, use the Codespaces Explorer to download the APK, or push your branch and run the **Android APK** GitHub Actions workflow; its `autoshorts-debug-apk` artifact can be downloaded from the workflow run page on mobile.
5. On the phone, open the downloaded APK and allow installation for the browser or file manager when Android prompts. Launch AutoShorts, open **Settings**, add a Deepgram key and an AI provider key, then import media from **Studio**.

The first Gradle build downloads Android SDK components and dependencies. A Codespaces container does not provide an Android emulator; verify playback and export on the phone after installing the artifact.

## Build Locally

Requirements: JDK 17-21 and Android SDK Platform 35. Then run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The installable APK is written to `app/build/outputs/apk/debug/app-debug.apk`.