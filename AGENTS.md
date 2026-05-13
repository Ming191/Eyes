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

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **Eyes** (880 symbols, 1714 relationships, 43 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/Eyes/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/Eyes/context` | Codebase overview, check index freshness |
| `gitnexus://repo/Eyes/clusters` | All functional areas |
| `gitnexus://repo/Eyes/processes` | All execution flows |
| `gitnexus://repo/Eyes/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## Keeping the Index Fresh

After committing code changes, the GitNexus index becomes stale. Re-run analyze to update it:

```bash
npx gitnexus analyze
```

If the index previously included embeddings, preserve them by adding `--embeddings`:

```bash
npx gitnexus analyze --embeddings
```

To check whether embeddings exist, inspect `.gitnexus/meta.json` — the `stats.embeddings` field shows the count (0 means no embeddings). **Running analyze without `--embeddings` will delete any previously generated embeddings.**

> Claude Code users: A PostToolUse hook handles this automatically after `git commit` and `git merge`.

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
