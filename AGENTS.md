# AGENTS.md — Eyes (SoundVision)

Blind-assistant Android app for Vietnamese users.
Kotlin + Jetpack Compose + Koin DI + CameraX.

## Build & Run

Always fetch lastest docs of Android Technology before building:
Description: The android docs command is a two-step process for accessing the Android Knowledge Base directly from the CLI. First, search for documentation related to your query using the search command. The search results will include special URLs starting with kb://, which you can then use with the fetch command to output the documentation commands to the terminal.

Usage:
```bash
    android docs search <query>
    android docs fetch <kb-url>
```


```bash
./gradlew assembleDebug          # Build debug APK (CI does this)
./gradlew testDebugUnitTest      # Run unit tests (Robolectric + JVM)
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
```

CI (GitHub Actions) runs `assembleDebug` only — no test step yet.
JDK 17 is used in CI.

## Architecture

- **Single module** (`:app`), package `com.example.eyes`
- **Single Activity** (`MainActivity`) → `EyesTheme { AppNavGraph() }`
- **Koin DI** (`EyesApp.kt` → `startKoin { modules(appModule) }`). All services and ViewModels registered in `AppModule.kt`. No Hilt/Dagger, no kapt/ksp.
- **Navigation**: 5 routes defined in `Routes` object inside `AppNavGraph.kt` — `onboarding`, `home`, `camera`, `map`, `settings`
- **State**: `DataStoreManager` stores `onboardingCompleted`, `ttsSpeed`, `alertSensitivity` via DataStore Preferences (no Room)

## Accessibility Rules (mandatory)

Every Composable **must** have `Modifier.semantics { contentDescription = "..." }` for TalkBack.
All user-facing strings are in Vietnamese (vi-VN locale).
Minimum touch target: 48dp; primary action buttons: 88dp+.

## Key Services

- **`TtsService`**: Thread-safe (`synchronized`), 3-tier priority queue (`URGENT`→QUEUE_FLUSH, `HIGH`/`NORMAL`→QUEUE_ADD), manages audio focus with `USAGE_ASSISTANCE_ACCESSIBILITY`. Falls back to engine default locale if vi-VN unavailable. Text preprocessing strips URLs and symbols before speaking.
- **`HapticService`**: API-level-aware vibrator access (`VibratorManager` on SDK 31+, fallback on older). Uses `VibrationAttributes.USAGE_ACCESSIBILITY` on SDK 33+. Patterns: `obstacleLeft/Center/Right`, `confirm`, `loading`, `error`.
- **`CameraManager`**: CameraX with `STRATEGY_KEEP_ONLY_LATEST` + `FrameThrottle` (200ms / 5fps). Output format: `YUV_420_888`. Runs analysis on dedicated single-thread executor. **AI inference must run on `Dispatchers.Default`** — never block Main.

## Testing Conventions

- **Robolectric** for all tests needing Android framework (Compose semantics, Koin resolution)
- **Pure JVM** tests for non-Android code (e.g. `FrameThrottleTest` — injects `currentTimeMs`)
- **Koin test pattern**: `stopKoin()` in `@Before`/`@After` with `runCatching`, start fresh per test
- **Compose UI tests**: use `createComposeRule()`, fake services via simple inner classes (e.g. `FakeSpeechOutput : SpeechOutput`)
- Comment style: `// GIVEN / WHEN / THEN`
- Test deps: JUnit 4, Robolectric 4.16.1, Koin Test, Compose UI Test JUnit4. **No MockK/Turbine/JUnit5** yet (proposed in test plan but not in build)

## Dev Plan vs Reality

`SoundVision.md` and `SoundVision_UnitTestPlan.md` describe a 5-week roadmap with aspirational versions and a `vn.blindapp` package. The actual codebase uses `com.example.eyes` and older dependency versions. **The TOML file and build.gradle.kts are the source of truth**, not the dev plan.

Current state: Week 1 scaffold — `CameraViewModel.processFrame()` is a stub, `MapScreen` is a placeholder.

## Gotchas

- Kotlin version is `2.0.21` (not `2.1.20` from the dev plan). Compose BOM is `2024.09.00` (not `2025.06.00`).
- No linter/formatter configured (no ktlint, detekt, or .editorconfig). Rely on Android Studio defaults.
- `local.properties` (SDK path) is gitignored — must exist locally before building.
- `robolectric.properties` in test resources is empty (uses defaults).
- Parallel Gradle builds are commented out in `gradle.properties`.
- `isMinifyEnabled = false` in release — no ProGuard/R8 setup yet.
- No Retrofit/OkHttp/Timber in actual deps yet (listed in dev plan only).
