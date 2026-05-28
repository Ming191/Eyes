# Repo-Wide Clean Architecture Refactor Plan

## Goal

Refactor whole repo toward Clean Architecture while preserving behavior.

Primary oversized class remains:

```text
app/src/main/java/com/example/eyes/ui/camera/CameraViewModel.kt
```

But scope is repo-wide: ViewModels, feature logic, repositories, device adapters, DI, localization, voice flow, settings, camera subfeatures, and tests.

Target rule:

```text
ui -> application/use cases -> domain ports/models
infrastructure/device/data -> implements domain ports
di -> wires everything
domain -> no Android/framework dependencies
```

## Repo-Wide Current Package Map

| Package | Current role | Refactor concern |
|---|---|---|
| `ui.*` | Compose screens, ViewModels, navigation, theme, blind helpers | ViewModels orchestrate business/device/data logic directly |
| `ui.camera` | Camera UI plus OCR, scene, object detection, currency, speech, haptics, audio, bitmap lifecycle | Largest mixed layer; split into use cases and ports |
| `ui.voice` | Voice UI, STT state, command parsing, persistence, navigation decisions | Split command handling from UI state |
| `ui.home` | Home UI, action catalog, greeting speech | Move action/greeting rules to application layer |
| `ui.settings` | Settings UI plus direct settings writes and TTS speed side effect | Move writes/effects to settings use cases |
| `ui.navigation` | Nav graph, onboarding, requested camera mode, announcements | Separate navigation UI from app-level state/use cases |
| `domain.voice` | `VoiceCommand`, `CommandParser` | Good domain island; expand pattern |
| `ocr` | OCR ports, models, MLKit, GPT, HTTP parsing, translation, guidance | Split domain/application/device/data |
| `objectdetection` | Detection models, port, YOLO pre/postprocess, ExecuTorch adapter, localized text | Split domain port/models from device implementation |
| `camera` | CameraX manager, image conversion, currency analyzer, frame throttle | Split camera device adapters from currency ML |
| `data` | DataStore settings and voice command persistence | Hide behind repository ports |
| `data.remote` | Scene repository/engine, network check, localized errors | Return domain results/errors, not UI text |
| `system` | TTS/STT/haptics interfaces and Android services mixed | Move ports inward, implementations outward |
| `voiceguide` | Announcement coordination, app scope, accessibility state | Move policy/use case inward, Android state outward |
| `i18n` | App language and localized resource provider | Keep as core adapter; avoid data/domain string access |
| `di` | One large Koin module | Split by core/feature/layer |

## Repo-Wide Layer Violations

1. UI depends on concrete data/device classes.
   - `CameraViewModel` depends on `DataStoreManager`, `SceneRepository`, `MlKitOcrGuidanceAnalyzer`, `YoloExecutorchDetector`, `AudioManager`, `TtsService`, `HapticService`.
   - `HomeViewModel`, `SettingsViewModel`, `AppNavViewModel`, `VoiceCommandViewModel` depend directly on `DataStoreManager` and device-ish services.

2. Domain ports live in infrastructure-like packages.
   - `OcrEngine` in `ocr`.
   - `ObjectDetector` in `objectdetection`.
   - `SpeechOutput` in `system`.

3. Ports expose Android types.
   - `OcrEngine` accepts `ImageProxy`/`Bitmap`.
   - `ObjectDetector` accepts `Bitmap`.
   - This makes app/domain tests Android-bound.

4. Data/repository layer returns localized UI messages.
   - `SceneRepository` uses `LocalizedTextProvider` and `R.string`.
   - Better: return typed domain errors; UI maps to strings.

5. Feature boundaries are blurred.
   - Camera feature contains OCR, scene, object detection, currency.
   - Voice command persistence is used as command bus.
   - Navigation imports UI feature models like `CameraMode` and `OcrMode`.

6. DI is flat.
   - `AppModule.kt` wires platform, data, engines, services, and ViewModels together.
   - Layer boundaries are not visible.

7. Localization is spread across layers.
   - ViewModels, repositories, object detection, and camera text logic do string mapping.

## Target Feature Boundaries

```text
feature/home
feature/settings
feature/navigation
feature/onboarding
feature/voice
feature/camera-shell
feature/ocr
feature/scene
feature/objectdetection
feature/currency
feature/emergency

core/accessibility
core/i18n
core/audio
core/camera
core/common
```

Camera shell should route mode and render UI. OCR, scene, object detection, and currency should be separate feature/application units.

## Current Responsibilities in `CameraViewModel`

| Responsibility | Current location | Target owner |
|---|---|---|
| UI state and events | `CameraViewModel` | `CameraViewModel` |
| Camera frame routing | `processFrame()` | `CameraViewModel` initially; later `CameraFrameRouter` if needed |
| OCR guidance analysis | `processOcrGuidanceImageProxy()`, guidance helpers | `AnalyzeOcrGuidanceUseCase` / `OcrGuidanceTracker` |
| OCR recognition | `processCapturedOcrImage()`, `recognizeByMode()` | `RecognizeOcrDocumentUseCase` |
| OCR translation | `maybeTranslateForSpeech()`, `translateToVietnameseOrFallback()` | `TranslateOcrDocumentUseCase` |
| Object detection warmup | `warmUpObjectDetectionModel()` | `WarmUpObjectDetectionUseCase` |
| Object detection frame processing | `processObjectDetectionImageProxy()`, `processObjectDetection()` | `DetectObjectsUseCase` |
| Scene description | `processCapturedSceneImage()`, `describeCapturedScene()` | `DescribeSceneUseCase` |
| Currency recognition | `CurrencyAnalyzer` lifecycle and callbacks | `RecognizeCurrencyUseCase` / currency scanner port |
| Speech output | direct `TtsService` calls | speech/announcement port or use case |
| Haptic feedback | direct `HapticService` calls | haptic port or use case |
| Audio route/headset checks | `AudioManager`, `AudioDeviceInfo`, `Build` | `AudioRouteProvider` |
| Camera text/localized copy | nested `CameraText` | `CameraText.kt` + `CameraTextProvider.kt` |
| Bitmap lifecycle | `latestFrame`, recycle helpers | keep in ViewModel first; revisit after feature extraction |

## Target Package Shape

```text
domain/
  accessibility/
  camera/
  currency/
  objectdetection/
  ocr/
  scene/
  settings/
  voice/

application/
  accessibility/
  currency/
  objectdetection/
  ocr/
  scene/

data/
  settings/
  remote/
    openai/
    ocr/
    scene/

device/
  audio/
  camera/
  haptics/
  ml/
    currency/
    objectdetection/
    ocr/
  speech/

ui/
  camera/
  home/
  navigation/
  settings/
  voice/

di/
```

This can be reached incrementally. Do not big-bang move packages first.

## Repo-Wide Refactor Sequence

### Phase 0: Behavior freeze and guardrails

Purpose: avoid architecture rewrite breaking app behavior.

Actions:

1. Add/expand tests around current behavior before moving code.
2. Keep package moves small and incremental.
3. Introduce interfaces/use cases beside current implementations first.
4. Migrate callers one by one.
5. Delete old paths only after tests and references confirm safe.

Validation each step:

```text
./gradlew testDebugUnitTest
```

### Phase 1: Define domain ports and models

Add inward-facing contracts with no Android imports where feasible:

```text
domain/settings/
  UserSettings
  SettingsRepository

domain/speech/
  SpeechOutput
  HapticFeedback
  AudioRouteProvider

domain/voice/
  VoiceCommand
  CommandParser
  VoiceCommandRepository or CameraCommandBus

domain/ocr/
  OcrMode
  OcrDocument
  OcrEnginePort
  OcrTranslatorPort
  OcrGuidanceResult

domain/objectdetection/
  Detection
  DetectionPosition
  ObjectDetectorPort
  ObjectDetectionModelInspector

domain/scene/
  SceneDescriptionPort
  SceneDescriptionResult
  SceneDescriptionError

domain/currency/
  CurrencyRecognizer
  CurrencyRecognitionResult
```

Short-term exception: adapters may still use `Bitmap`/`ImageProxy` while migrating. Long-term target: domain/application uses app-owned image model, device layer maps Android image types.

### Phase 2: Introduce application use cases by feature

Create use cases and migrate ViewModels to call them.

```text
application/settings/
  ObserveSettingsUseCase
  UpdateSettingsUseCase
  ApplySpeechRateUseCase

application/home/
  BuildHomeActionsUseCase
  AnnounceHomeGreetingUseCase

application/navigation/
  CompleteOnboardingUseCase
  AnnounceDestinationUseCase
  RequestCameraModeUseCase

application/voice/
  HandleVoiceCommandUseCase
  PersistVoiceCommandUseCase
  StartVoiceRecognitionUseCase

application/ocr/
  AnalyzeOcrGuidanceUseCase
  RecognizeOcrDocumentUseCase
  TranslateOcrDocumentUseCase

application/objectdetection/
  WarmUpObjectDetectionUseCase
  DetectObjectsUseCase

application/scene/
  DescribeSceneUseCase

application/currency/
  RecognizeCurrencyUseCase
```

ViewModels should become:

```text
collect state
call use cases
reduce UI state
emit UI/navigation events
```

### Phase 3: Move infrastructure behind ports

Target adapters:

```text
data/settings/DataStoreSettingsRepository
data/voice/DataStoreVoiceCommandRepository
data/remote/openai/OpenAiResponsesHttpClient
data/remote/ocr/Gpt4oOcrEngine
data/remote/scene/Gpt4oSceneDescriptionEngine

device/camera/CameraXCameraController
device/camera/ImageProxyMapper
device/ml/ocr/MlKitOcrEngine
device/ml/ocr/MlKitOcrGuidanceAnalyzer
device/ml/objectdetection/YoloExecutorchDetector
device/ml/objectdetection/YoloPreprocessor
device/ml/objectdetection/YoloPostprocessor
device/ml/objectdetection/ExecutorchModelAssetCopier
device/ml/currency/CurrencyAnalyzer
device/audio/AndroidAudioRouteProvider
device/speech/AndroidTtsService
device/speech/AndroidSttService
device/haptics/AndroidHapticFeedback
```

### Phase 4: Refactor ViewModels from smallest to largest

Order:

1. `SettingsViewModel`
2. `HomeViewModel`
3. `AppNavViewModel`
4. `VoiceCommandViewModel`
5. `CameraViewModel`

Reason: smaller ViewModels establish patterns before largest file.

### Phase 5: Split DI modules

Replace one large `AppModule.kt` with feature/layer modules:

```text
CoreModule.kt
I18nModule.kt
SettingsDataModule.kt
AudioDeviceModule.kt
AccessibilityModule.kt
OcrModule.kt
ObjectDetectionModule.kt
SceneModule.kt
CurrencyModule.kt
CameraDeviceModule.kt
VoiceModule.kt
PresentationModule.kt
```

Bind interfaces, not concretes:

```kotlin
single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
single<ObjectDetectorPort> { get<YoloExecutorchDetector>() }
single<ObjectDetectionModelInspector> { get<YoloExecutorchDetector>() }
single<SceneDescriptionPort> { Gpt4oSceneDescriptionEngine(get()) }
```

Use OCR qualifiers:

```kotlin
named("quick")
named("accuracy")
```

### Phase 6: Enforce architecture rules

Add architecture tests or static checks:

```text
domain must not import android.*, androidx.*, ui.*, data.*, device.*
application must not import ui.*, data.*, device.*, androidx.lifecycle.*
data/device must not import ui.*
ui should depend on application/domain, not concrete adapters
di may depend on all layers
```

## ViewModel Refactor Order

### 1. `SettingsViewModel`

Current problem:

```text
direct DataStoreManager writes
direct SpeechOutput side effect
```

Target:

```text
ObserveSettingsUseCase
UpdateSettingsUseCase
ApplySpeechRateUseCase
```

### 2. `HomeViewModel`

Current problem:

```text
resource mapping + greeting speech + settings flow in ViewModel
```

Target:

```text
BuildHomeActionsUseCase
AnnounceHomeGreetingUseCase
```

### 3. `AppNavViewModel`

Current problem:

```text
onboarding state + TTS speed sync + destination announcements + camera requests
```

Target:

```text
CompleteOnboardingUseCase
ObserveNavigationSettingsUseCase
AnnounceDestinationUseCase
CameraModeRequestStore
```

### 4. `VoiceCommandViewModel`

Current problem:

```text
STT orchestration + parser + persistence + speech/haptic + navigation
```

Target:

```text
HandleVoiceCommandUseCase
VoiceRecognitionController
VoiceCommandRepository or CameraCommandBus
```

### 5. `CameraViewModel`

Largest final ViewModel; details below.

## Camera Refactor Sequence

### Step 1: Extract camera localization text

Purpose: shrink file with low behavior risk.

Create:

```text
app/src/main/java/com/example/eyes/ui/camera/CameraText.kt
app/src/main/java/com/example/eyes/ui/camera/CameraTextProvider.kt
```

Move nested `CameraText` and resource-loading logic out of `CameraViewModel`.

Keep same API first:

```kotlin
CameraText.from(localizedTextProvider, language)
```

Then optionally introduce:

```kotlin
class CameraTextProvider(
    private val localizedTextProvider: LocalizedTextProvider
) {
    fun forLanguage(language: AppLanguage): CameraText =
        CameraText.from(localizedTextProvider, language)
}
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 2: Depend on `ObjectDetector` interface, not YOLO concrete class

Purpose: enforce dependency inversion for object detection.

Current problem:

```kotlin
private val objectDetector: YoloExecutorchDetector
```

Target:

```kotlin
private val objectDetector: ObjectDetector
```

Ensure `YoloExecutorchDetector` implements `ObjectDetector`.

DI should bind:

```kotlin
single<ObjectDetector> { get<YoloExecutorchDetector>() }
```

If `inspectOutputShape()` exists only on `YoloExecutorchDetector`, either:

1. add warmup/inspection method to `ObjectDetector`, or
2. create `ObjectDetectionModelInspector` port.

Preferred short-term:

```kotlin
interface ObjectDetector {
    suspend fun detect(bitmap: Bitmap): List<Detection>
    suspend fun inspectOutputShape(): List<ModelOutputInfo>
}
```

Preferred longer-term:

```text
WarmUpObjectDetectionUseCase -> ObjectDetectionModelInspector
DetectObjectsUseCase -> ObjectDetector
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 3: Extract OCR pure helpers

Purpose: reduce ViewModel logic before use-case split.

Create:

```text
app/src/main/java/com/example/eyes/application/ocr/OcrRecognitionPolicy.kt
```

Move pure functions:

```text
looksEnglish
shouldAutoTranslateToVietnamese
looksLikeGptRefusal
buildFallbackReason
```

Keep inputs/outputs primitive/domain-only. No Android imports.

Add tests:

```text
app/src/test/java/com/example/eyes/application/ocr/OcrRecognitionPolicyTest.kt
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 4: Extract OCR guidance tracker

Purpose: move stateful guidance stabilization out of ViewModel.

Create:

```text
app/src/main/java/com/example/eyes/application/ocr/OcrGuidanceTracker.kt
```

Responsibilities:

```text
stable frame counting
last guidance bounds comparison
announcement throttling decision
ready-to-capture decision
```

Keep MLKit-specific analyzer outside tracker. Tracker should consume OCR guidance result/status/bounds only.

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 5: Extract `RecognizeOcrDocumentUseCase`

Purpose: move OCR recognition orchestration out of ViewModel.

Create:

```text
app/src/main/java/com/example/eyes/application/ocr/RecognizeOcrDocumentUseCase.kt
```

Inputs:

```kotlin
data class RecognizeOcrDocumentRequest(
    val imageProxy: ImageProxy,
    val mode: OcrMode,
)
```

Short-term note: `ImageProxy` is Android-specific. Accept this temporarily if minimizing change. Longer-term, use a device mapper to convert `ImageProxy` into domain image input before use case.

Output:

```kotlin
data class RecognizeOcrDocumentResult(
    val result: OcrResult,
    val usedFallbackFromAccuracy: Boolean,
    val fallbackReason: String?,
)
```

Move:

```text
recognizeByMode()
accuracy fallback handling
fallback reason construction
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 6: Extract `TranslateOcrDocumentUseCase`

Purpose: isolate translation behavior and make it testable.

Create:

```text
app/src/main/java/com/example/eyes/application/ocr/TranslateOcrDocumentUseCase.kt
```

Move:

```text
maybeTranslateForSpeech()
translateToVietnameseOrFallback()
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 7: Extract object detection use cases

Create:

```text
app/src/main/java/com/example/eyes/application/objectdetection/WarmUpObjectDetectionUseCase.kt
app/src/main/java/com/example/eyes/application/objectdetection/DetectObjectsUseCase.kt
```

Move:

```text
warmUpObjectDetectionModel()
processObjectDetection()
detection result mapping except final UI overlay mapping
```

Keep `DetectionOverlayItem` in UI. Domain/app layer returns detections plus source dimensions/aspect info, UI maps to overlay item.

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 8: Extract scene description use case

Create:

```text
app/src/main/java/com/example/eyes/application/scene/DescribeSceneUseCase.kt
```

Move:

```text
describeCapturedScene()
scene result/error normalization
```

ViewModel remains responsible for capture event and UI state update.

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 9: Extract currency scanner/use case

Currency is highest-risk because current ViewModel owns lazy analyzer construction and callback handling.

Create port:

```kotlin
interface CurrencyRecognizer {
    fun analyzePreview(bitmap: Bitmap)
    suspend fun recognize(bitmap: Bitmap): CurrencyRecognitionResult
    fun close()
}
```

Implementation wraps existing `CurrencyAnalyzer`.

Create:

```text
app/src/main/java/com/example/eyes/application/currency/RecognizeCurrencyUseCase.kt
app/src/main/java/com/example/eyes/device/ml/currency/CurrencyAnalyzerRecognizer.kt
```

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 10: Split `CameraUiState` into feature substates

Target:

```kotlin
data class CameraUiState(
    val activeMode: CameraMode,
    val title: String,
    val summary: String,
    val statusMessage: String,
    val lastAnnouncement: String,
    val debugMetrics: String,
    val isStatusCardVisible: Boolean,
    val ocr: OcrUiState,
    val scene: SceneUiState,
    val objectDetection: ObjectDetectionUiState,
    val currency: CurrencyUiState,
)
```

Add mappers or compatibility properties if UI change too large.

Validation:

```text
./gradlew testDebugUnitTest
```

### Step 11: Split DI modules

Create:

```text
app/src/main/java/com/example/eyes/di/CoreModule.kt
app/src/main/java/com/example/eyes/di/SystemModule.kt
app/src/main/java/com/example/eyes/di/OcrModule.kt
app/src/main/java/com/example/eyes/di/ObjectDetectionModule.kt
app/src/main/java/com/example/eyes/di/SceneModule.kt
app/src/main/java/com/example/eyes/di/CurrencyModule.kt
app/src/main/java/com/example/eyes/di/ViewModelModule.kt
```

Keep `AppModule.kt` as aggregator if useful.

Validation:

```text
./gradlew testDebugUnitTest
```

## Dead Code Handling

Dead code is out of scope for this refactor plan unless explicitly requested.

Previously suspected items:

```text
HomeActionType.ReadTextAccuracy
HomeActionType.Settings
processScenePreviewImageProxy()
FrameThrottle
```

User will handle these decisions separately.

## Commit Strategy

One commit per safe step:

```text
refactor: extract camera text resources
refactor: depend on object detector interface
refactor: extract OCR recognition policy
refactor: extract OCR guidance tracker
refactor: extract OCR recognition use case
refactor: extract OCR translation use case
refactor: extract object detection use cases
refactor: extract scene description use case
refactor: extract currency recognition use case
refactor: split camera UI state
refactor: split dependency modules
```

Before each commit:

```text
git status --short
git diff -- <intended files>
./gradlew testDebugUnitTest
```

Because repo already has unrelated modified/untracked files, stage only intended files:

```text
git add <specific files>
```

Do not use:

```text
git add .
```

## Risk Notes

High-risk areas:

1. `ImageProxy` lifecycle and close paths.
2. `Bitmap` recycling and `latestFrame` ownership.
3. Currency analyzer callback threading/lifecycle.
4. TTS announcement throttling and duplicate speech prevention.
5. Object detection warmup errors currently surfaced through `debugMetrics`.
6. OCR accuracy fallback behavior and localized fallback reasons.

Refactor rule: after every extraction, preserve public UI behavior first; architecture purity second.
