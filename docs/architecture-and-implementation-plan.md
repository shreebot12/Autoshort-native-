# AutoShorts Architecture and Implementation Plan

## Current Architecture

AutoShorts is a single-module native Android application. It uses Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Jetpack Compose and Material 3, with a minimum Android API level of 26 and Java 17 bytecode. There is no backend module or web client.

The current runtime is organized around these ownership boundaries:

| Area | Current owner | Responsibility |
| --- | --- | --- |
| Activity and app shell | `MainActivity.kt`, `ui/AutoShortsApp.kt` | Compose entry point, Studio/History/Settings navigation, system media picker, project list, project editor, preview, moment cards, share actions |
| Visual identity | `ui/AutoShortsTheme.kt` | Paper background, ink text, leaf accent, coral error/accent, Material 3 light color scheme |
| UI state | `AppViewModel.kt` | Selected project, observed Room flows, analyze/export state, progress and transient messages |
| Persistence | `data/AutoShortsDatabase.kt`, `data/Entities.kt` | Room projects, transcript JSON, ranked candidates, cascading project deletion |
| Repository and workflow | `data/AutoShortsRepository.kt` | Media import, analysis orchestration, project stage updates, caption and candidate persistence |
| External AI | `data/AiApi.kt`, `data/AppSettings.kt` | Deepgram transcription and configurable hosted LLM requests; provider keys use EncryptedSharedPreferences |
| Android media | `media/AndroidMediaEngine.kt` | MediaExtractor audio remuxing, Media3 portrait clip rendering, MediaStore MP4 publishing and SRT output |
| Tests | `app/src/test/.../CaptionFormatterTest.kt` | Unit coverage for SRT clipping, offsetting, and empty ranges |

### Existing User Experience to Preserve

- Bottom navigation for Studio, History, and Settings.
- Project list, import action, recent-work rows, and empty state.
- Project editor with the portrait media preview, moment finder, progress display, ranked moment cards, trim range, selection, export, and share controls.
- Settings form, current typography and spacing, 8-12 dp rounded shapes, and the Ink/Leaf/Paper/Coral palette.

These are reusable presentation surfaces. New workflows should be introduced within this shell and its existing visual language; replace behavior behind the surfaces before considering any layout changes.

## Current Behavior and Constraints

- Imports one or more Android document URIs and requires readable media duration. An optional supported YouTube video URL can be attached to a single selected local file; it is validated and stored as a reference only.
- Extracts the full source audio to cache before uploading it. This is not a bounded-memory, resumable pipeline for very long media.
- Analysis currently requires a Deepgram key and an LLM key; extracted audio and transcript text leave the device.
- Analysis runs as a unique WorkManager job per project with persisted progress and cancellation. Export still runs from `viewModelScope`; analysis restarts from the beginning after interruption because there are no per-chunk checkpoints yet.
- Exports a single selected candidate as a 1080 x 1920 MP4. Captions are exported as SRT; captions are not burned into the video.
- Users can create manually timed, titled clips and export them without AI credentials. These candidates are labeled as manual and have no AI engagement estimate. Export opens a copyright review warning.
- No permitted YouTube metadata retrieval, queue, pause/resume, duplicate detection, local transcription, local moment analysis, calendar, brand kit, thumbnail editor, or performance tracker exists.
- Supported containers/codecs and successful export depend on Android platform decoders/encoders and available device storage. There is no FFmpeg integration today.

## API-Free Target Architecture

Keep the Android/Kotlin/Compose stack and add capabilities behind clear module boundaries inside `app` first. Do not introduce a hosted backend or a paid-service dependency. Any model downloads should be optional, user-visible, open-weight assets with license, storage-size, and hardware requirements shown before download; processing must have a useful no-credential path.

| Target boundary | Responsibility |
| --- | --- |
| `data` | Versioned Room schema for source records, jobs, transcript segments, moments, renders, presets, brand, calendar, and manual performance entries |
| `media` | Platform-compatible probing, bounded segment reads, audio extraction, scene/energy signals, render planning, Media3/FFmpeg adapter behind a stable interface |
| `processing` | Chunk planner, resumable pipeline stages, local inference adapters, merge/offset of timestamps, cancellation and temporary-file cleanup |
| `work` | Unique WorkManager jobs, persisted progress, retry policy, constraints, and recovery after process closure |
| `export` | Validated render settings, storage checks, MediaStore publishing, SRT/VTT output, cancellation and retry state |
| `ui` | Preserve the current shell; add guided source, queue, editing, export, planning, and history surfaces as they become functional |

YouTube URLs are references, not media sources. Validate supported URL forms and show only legitimately available metadata. Never retrieve audiovisual streams. Explain that an authorized local source file is required for analysis and editing. Without credentials, publishing remains a local planner and manual upload workflow; performance data remains user-entered.

## Incremental Implementation Plan

1. **Baseline and data contracts:** retain a backup Git ref; document existing ownership; add focused tests and schema migration strategy before changing persisted entities.
2. **Local source/project workflow:** add URL reference plus authorized local file association, multiple selection, media validation, source history, copyright notice, and clear unsupported-format errors. Keep the existing import and project surfaces.
3. **Durable processing foundation:** add WorkManager-backed unique jobs and persisted stage/progress/error state; plan bounded chunks with absolute timestamps, resume checkpoints, cancellation, disk estimates, and cleanup. Prevent duplicate active jobs for the same source/configuration.
4. **Offline media intelligence:** integrate a maintainable open-source local speech-recognition/runtime option only after device/ABI/license/storage evaluation; process audio in bounded chunks; merge transcript offsets; add local energy/silence/scene signals. Avoid assuming a 10-hour job fits device memory or storage.
5. **Moments and captions:** build deterministic candidate ranking from available transcript/audio/scene signals, explain scores as estimates, allow manual trims, and add editable SRT/VTT/caption rendering. Keep heuristic analysis useful without downloaded models.
6. **Shorts and editing:** add verified aspect-ratio/duration controls, previews, batch jobs, and a render adapter. Add tracking, segmentation, typography, split-screen, enhancement, and sound effects only when their actual local implementation and device limits are tested.
7. **Creator tools:** implement local title/description suggestions where supported, thumbnail editing, presets/brand kit, duplicate warnings, manual performance tracking, and the local publishing calendar.
8. **Reliability and release:** test short and long inputs, audio-only formats, multiple jobs, interruptions/retries, unsupported codecs, and low storage on representative Android devices; document model licensing/downloads, setup, APK build, and limitations.

Each increment must preserve the existing shell, remain buildable, include behavior-scoped tests, and be reported as implemented only after its checks pass. The requested feature list is much larger than the current app and should not be represented as complete by placeholder controls.

## Baseline Verification

The repository's test command is `./gradlew testDebugUnitTest`; CI also runs `./gradlew testDebugUnitTest assembleDebug`. In this Codespaces environment the default Java 25 runtime is incompatible with the Kotlin build-script parser. JDK 21 is installed and gets past that issue, but the baseline test attempt then failed while resolving `kotlin-compiler-embeddable:2.0.21` because the redirected Maven Central/GitHub download returned HTTP 504. No source-level baseline result is established yet.