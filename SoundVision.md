# 🦯 Blind Assistant App Vietnam — Dev Plan (5 Tuần)

> **Platform:** Android Native · Kotlin · Jetpack Compose · Koin 4.1
> **Min SDK:** 26 (Android 8.0+) · **Target SDK:** 35
> **Architecture:** MVVM + Clean Architecture · Hybrid On-device/Backend AI

---

## Quyết định kiến trúc: On-Device vs Backend Server

| Tiêu chí | On-Device | Backend Server |
|----------|-----------|----------------|
| **Latency** | < 120ms (không qua mạng) | 500–2000ms round-trip |
| **Offline** | ✅ Hoàn toàn | ❌ Không dùng được |
| **Privacy** | ✅ Ảnh không rời thiết bị | ⚠️ Ảnh upload lên server |
| **Chi phí** | $0/tháng | $7–150/tháng tùy GPU |
| **Model quality** | Giới hạn bởi RAM/chip | Không giới hạn, GPT-4o |
| **Update model** | Phải release app mới | Deploy server là xong |

**→ Kết luận: Hybrid**

```
Realtime (< 200ms — bắt buộc on-device)
  Object detection · OCR · Face recognition · Currency

On-Demand (user chủ động nhấn → 2–5s chấp nhận được)
  Scene description (GPT-4o Vision qua backend)
  TTS chất lượng cao (Google Cloud TTS WaveNet)

Offline fallback
  Template từ YOLO: "Phía trước có xe máy. Chú ý người bên trái."
```

---

## Tech Stack

### Core Android
| Thư viện | Version | Mục đích |
|---------|---------|---------|
| Kotlin | 2.1.20 | Ngôn ngữ chính |
| Jetpack Compose BOM | 2025.06.00 | UI toàn bộ app |
| Compose Navigation | 2.8.5 | Điều hướng màn hình |
| CameraX | 1.4.1 | Camera stream + ImageAnalysis |
| **Koin BOM** | **4.1.1** | Dependency injection |
| Coroutines + Flow | 1.8.1 | Async, state management |
| DataStore Preferences | 1.1.1 | Lưu settings người dùng |
| Retrofit + OkHttp | 2.11.0 / 4.12.0 | Gọi backend API |
| Timber | 5.0.1 | Logging |

### Koin 4.1.1 — Setup

> **Tại sao Koin thay vì Hilt?**
> Koin 4.1 không cần annotation processor (kapt/ksp) → build nhanh hơn đáng kể.
> DSL đơn giản, dễ debug cho team nhỏ. Compile-safe với Koin Compiler Plugin.
> Koin 4.1 tự động xử lý `KoinAndroidContext` trong Compose — không cần manual context passing.

```toml
# libs.versions.toml
[versions]
koin-bom = "4.1.1"

[libraries]
koin-bom              = { module = "io.insert-koin:koin-bom",              version.ref = "koin-bom" }
koin-android          = { module = "io.insert-koin:koin-android" }
koin-androidx-compose = { module = "io.insert-koin:koin-androidx-compose" }
koin-compose-viewmodel= { module = "io.insert-koin:koin-compose-viewmodel" }
```

```kotlin
// build.gradle.kts (app)
implementation(platform(libs.koin.bom))
implementation(libs.koin.android)
implementation(libs.koin.androidx.compose)
implementation(libs.koin.compose.viewmodel)
```

```kotlin
// App.kt
class BlindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BlindApp)
            androidLogger(Level.DEBUG)
            modules(appModule, aiModule, speechModule, navigationModule)
        }
    }
}
```

### On-Device AI
| Thư viện | Version | Dùng cho |
|---------|---------|---------|
| TFLite Java API | 2.16.1 | YOLOv8n · FaceNet · VNĐ classifier |
| TFLite Support | 0.4.4 | ImageProcessor, TensorBuffer |
| ML Kit Text Recognition | 19.0.1 (GMS) | OCR tiếng Việt on-device |
| ML Kit Face Detection | 16.1.7 | Detect face bounding box |

> **Không dùng NNAPI delegate** — unreliable trên Samsung/Xiaomi VN (silent fallback CPU).
> Dùng `numThreads = 4` (CPU multithread) làm baseline an toàn.

### Speech
| | Engine | Ghi chú |
|--|--------|---------|
| **TTS** | `android.speech.tts.TextToSpeech` (vi-VN) | On-device, miễn phí, không cần mạng |
| **TTS backup** | Google Cloud TTS WaveNet qua backend | Chất lượng cao hơn, $4/1M ký tự |
| **STT** | `android.speech.SpeechRecognizer` (vi-VN) | ⚠️ Cần internet thực tế — đừng quảng cáo offline |

### Backend (minimal — FastAPI + Railway)
```
POST /describe  →  JPEG → GPT-4o Vision → text tiếng Việt
GET  /health    →  health check

Chi phí: ~$7/tháng Railway + ~$0.005/request GPT-4o Vision
```

### Navigation & Sensors
| Thứ | Thư viện |
|-----|---------|
| Maps | Google Maps SDK for Android `19.0.0` |
| GPS | `FusedLocationProviderClient` (Jetpack) |
| Compass | `SensorManager` TYPE_MAGNETIC_FIELD + ACCELEROMETER → tự tính heading |
| Haptic | `VibrationEffect` API (Android 8+) |

---

## Cấu trúc Project

```
app/src/main/
├── java/vn/blindapp/
│   ├── App.kt                          ← startKoin()
│   │
│   ├── di/
│   │   ├── AppModule.kt                ← TtsService, HapticService, DataStore
│   │   ├── AiModule.kt                 ← YoloDetector, OcrEngine, FaceRecognizer
│   │   ├── NetworkModule.kt            ← Retrofit, SceneApi
│   │   └── NavigationModule.kt         ← LocationService, MapsRepository
│   │
│   ├── ui/
│   │   ├── theme/                      ← MaterialTheme, Typography
│   │   ├── navigation/AppNavGraph.kt   ← NavHost, destinations
│   │   ├── home/
│   │   │   ├── HomeScreen.kt           ← Compose UI
│   │   │   └── HomeViewModel.kt        ← koinViewModel()
│   │   ├── camera/
│   │   │   ├── CameraScreen.kt
│   │   │   └── CameraViewModel.kt
│   │   ├── ocr/OcrScreen.kt + OcrViewModel.kt
│   │   ├── navigation/NavScreen.kt + NavViewModel.kt
│   │   └── settings/SettingsScreen.kt + SettingsViewModel.kt
│   │
│   ├── domain/usecase/
│   │   ├── DetectObjectsUseCase.kt
│   │   ├── ReadTextUseCase.kt
│   │   ├── DescribeSceneUseCase.kt
│   │   └── NavigateUseCase.kt
│   │
│   └── data/
│       ├── ondevice/
│       │   ├── YoloDetector.kt
│       │   ├── MlKitOcrEngine.kt
│       │   ├── FaceNetRecognizer.kt
│       │   └── CurrencyClassifier.kt
│       ├── remote/
│       │   ├── SceneApi.kt             ← Retrofit interface
│       │   └── SceneRepository.kt
│       └── system/
│           ├── TtsService.kt
│           ├── HapticService.kt
│           └── SttService.kt
│
└── assets/models/
    ├── yolov8n_int8.tflite             ← ~3MB
    ├── labels_vi.txt                   ← 80 COCO labels tiếng Việt
    ├── facenet.tflite                  ← ~3MB
    └── vnd_classifier.tflite           ← ~2MB (fine-tune tuần 5)
```

---

## Tuần 1 — Project Setup + CameraX + TTS/Haptic Foundation

**Mục tiêu:** Khung app native Compose chạy ổn, CameraX stream hoạt động, TalkBack pass.

### 1.1 Project Setup (Ngày 1)
- [ ] Tạo Android project: **Empty Activity (Compose)**, Kotlin, minSdk 26, compileSdk 35
- [ ] `libs.versions.toml` — version catalog cho toàn bộ dependency
- [ ] `build.gradle.kts` (app):
  ```kotlin
  android {
      compileSdk = 35
      defaultConfig { minSdk = 26; targetSdk = 35 }
      buildFeatures { compose = true }
  }
  ```
- [ ] Koin 4.1.1 với BOM — setup như mẫu ở trên
- [ ] `AppModule.kt` cơ bản:
  ```kotlin
  val appModule = module {
      single { TtsService(androidContext()) }
      single { HapticService(androidContext()) }
      single { DataStoreManager(androidContext()) }
  }
  ```
- [ ] `AndroidManifest.xml`: `CAMERA · RECORD_AUDIO · ACCESS_FINE_LOCATION · VIBRATE · INTERNET`
- [ ] GitHub Actions CI: `./gradlew assembleDebug` → upload APK artifact

### 1.2 Compose Navigation + Home Screen (Ngày 2)
- [ ] `AppNavGraph.kt` với `NavHost`, 4 destinations: `home · camera · map · settings`
- [ ] `HomeScreen.kt` — 3 nút lớn dùng `koinViewModel()`:
  ```kotlin
  @Composable
  fun HomeScreen(
      navController: NavController,
      viewModel: HomeViewModel = koinViewModel()
  ) {
      Column(Modifier.fillMaxSize()) {
          BigActionButton(
              label = "Xem xung quanh",
              modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()   // 48dp min — Material3
                  .semantics {
                      contentDescription =
                          "Xem xung quanh. Nhấn để mở camera nhận dạng vật cản và đọc văn bản"
                  },
              onClick = { navController.navigate("camera") }
          )
          // Đọc · Đi
      }
  }
  ```
  > Touch target: dùng `Modifier.minimumInteractiveComponentSize()` của Material3 — tự enforce 48dp, nhưng nút chính cần 88dp trở lên
- [ ] `HomeViewModel`:
  ```kotlin
  class HomeViewModel(private val tts: TtsService) : ViewModel() {
      fun greet() = tts.speak("Chào mừng. Nhấn Xem, Đọc hoặc Đi để bắt đầu.")
  }
  // DI module:
  val appModule = module {
      viewModel { HomeViewModel(get()) }
  }
  ```

### 1.3 CameraX Pipeline (Ngày 2–3)
- [ ] `CameraManager.kt` (singleton qua Koin):
  ```kotlin
  class CameraManager(private val context: Context) {
      private var analysisUseCase: ImageAnalysis? = null

      fun bindToLifecycle(
          lifecycleOwner: LifecycleOwner,
          previewView: PreviewView,
          onFrame: (ImageProxy) -> Unit
      ) {
          val provider = ProcessCameraProvider.getInstance(context).get()
          val preview = Preview.Builder().build()
              .also { it.setSurfaceProvider(previewView.surfaceProvider) }
          analysisUseCase = ImageAnalysis.Builder()
              .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), onFrame) }
          provider.bindToLifecycle(lifecycleOwner,
              CameraSelector.DEFAULT_BACK_CAMERA, preview, analysisUseCase)
      }
  }
  // Module:
  single { CameraManager(androidContext()) }
  ```
- [ ] Frame throttle: `System.currentTimeMillis()` → chỉ pass frame xuống AI mỗi 200ms (5fps)
- [ ] `ImageProxy → Bitmap` với đúng rotation degrees
- [ ] `CameraScreen.kt` embed `PreviewView` vào Compose:
  ```kotlin
  val cameraManager: CameraManager = koinInject()
  AndroidView(
      factory = { ctx ->
          PreviewView(ctx).also { preview ->
              cameraManager.bindToLifecycle(lifecycleOwner, preview) { imageProxy ->
                  viewModel.processFrame(imageProxy)
              }
          }
      },
      modifier = Modifier.fillMaxSize()
  )
  ```

### 1.4 TTS + Haptic Services (Ngày 3–4)
- [ ] `TtsService.kt`:
  ```kotlin
  class TtsService(context: Context) {
      enum class Priority { URGENT, HIGH, NORMAL }

      private val tts = TextToSpeech(context) { /* check status */ }

      init {
          tts.language = Locale("vi", "VN")
          tts.setSpeechRate(1.2f)
      }

      fun speak(text: String, priority: Priority = Priority.NORMAL) {
          val mode = if (priority == Priority.URGENT) QUEUE_FLUSH else QUEUE_ADD
          tts.speak(preprocessText(text), mode, null, UUID.randomUUID().toString())
      }

      fun stop() = tts.stop()

      private fun preprocessText(raw: String): String =
          raw.replace(Regex("https?://\\S+"), "link")
             .replace(Regex("[|■▪►]"), "")
  }
  ```
  - Kiểm tra `Locale("vi","VN")` availability khi init, TTS thông báo nếu thiếu
- [ ] `HapticService.kt` với `VibrationEffect` (Android 8+):
  ```kotlin
  class HapticService(context: Context) {
      private val vibrator = context.getSystemService(Vibrator::class.java)

      fun obstacleLeft()   = vibrate(longArrayOf(0, 100))
      fun obstacleCenter() = vibrate(longArrayOf(0, 100, 60, 100))
      fun obstacleRight()  = vibrate(longArrayOf(0, 100, 60, 100, 60, 100))
      fun confirm()        = vibrate(longArrayOf(0, 50))
      fun loading()        = vibrate(longArrayOf(0, 30, 30, 30, 30, 30))

      private fun vibrate(pattern: LongArray) =
          vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
  }
  ```

### 1.5 Accessibility + Permission (Ngày 4–5)
- [ ] **Mọi Composable** có `Modifier.semantics { contentDescription = "..." }`
- [ ] TalkBack enable qua ADB để test:
  ```bash
  adb shell settings put secure enabled_accessibility_services \
    com.google.android.marvin.talkback/.TalkBackService
  ```
- [ ] `PermissionScreen.kt` dùng `rememberLauncherForActivityResult`
- [ ] Onboarding `HorizontalPager` 3 trang: xin quyền → gesture → bắt đầu
- [ ] Settings screen với DataStore-backed preferences (tốc độ TTS, độ nhạy alert)

### Deliverable tuần 1
> App native Compose chạy trên thiết bị thật. Camera preview ổn định. TalkBack điều hướng 3 màn hình bằng swipe. TTS đọc tiếng Việt. Haptic rung đúng pattern.

---

## Tuần 2 — OCR tiếng Việt + Document Reader

**Mục tiêu:** Hướng camera → đọc văn bản tiếng Việt < 2 giây.

### 2.1 ML Kit OCR + Koin (Ngày 1–2)
- [ ] Dependency GMS unbundled (nhỏ hơn bundled ~20MB, tự update qua Play):
  ```kotlin
  implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
  ```
- [ ] `MlKitOcrEngine.kt`:
  ```kotlin
  class MlKitOcrEngine {
      private val recognizer = TextRecognition.getClient(
          TextRecognizerOptions.DEFAULT_OPTIONS  // Latin script — đủ cho tiếng Việt
      )

      suspend fun recognize(imageProxy: ImageProxy): OcrResult =
          suspendCancellableCoroutine { cont ->
              val img = InputImage.fromMediaImage(
                  imageProxy.image!!, imageProxy.imageInfo.rotationDegrees
              )
              recognizer.process(img)
                  .addOnSuccessListener { visionText ->
                      cont.resume(visionText.toOcrResult())
                  }
                  .addOnFailureListener { cont.resumeWithException(it) }
                  .addOnCompleteListener { imageProxy.close() }
          }
  }

  // Module:
  val aiModule = module {
      single { MlKitOcrEngine() }
  }
  ```
- [ ] Post-processing: lọc block < 3 ký tự, sort spatial (top→bottom, left→right), confidence ≥ 0.6
- [ ] Dictionary correction: 50 pattern dấu thanh thường nhầm (`ắ→á`, `ề→e` v.v.)
- [ ] Unit test: 20 ảnh (biển hiệu, hoá đơn, trang sách, menu) — target > 90% WER

### 2.2 Scene Text Mode — Realtime (Ngày 2–3)
- [ ] `OcrViewModel.kt`:
  ```kotlin
  class OcrViewModel(
      private val ocr: MlKitOcrEngine,
      private val tts: TtsService,
      private val haptic: HapticService
  ) : ViewModel() {
      private var lastText = ""

      fun processFrame(imageProxy: ImageProxy) {
          viewModelScope.launch(Dispatchers.Default) {
              val result = ocr.recognize(imageProxy)
              if (result.text.levenshteinDistanceTo(lastText) > 0.3f) {
                  lastText = result.text
                  withContext(Dispatchers.Main) {
                      haptic.confirm()
                      tts.speak(result.text, TtsService.Priority.NORMAL)
                  }
              }
          }
      }
  }
  // Module:
  viewModel { OcrViewModel(get(), get(), get()) }
  ```
- [ ] Rate: 1 frame OCR / 2 giây từ CameraX stream

### 2.3 Document Reader Mode — Chụp ảnh (Ngày 3–4)
- [ ] Trigger: double tap `CameraScreen` → `ImageCapture.takePicture()` full-res
- [ ] Layout-aware: sort `TextBlock` theo `boundingBox.top` rồi `.left`
- [ ] Navigation state:
  ```kotlin
  data class DocumentState(val sentences: List<String>, val currentIndex: Int = 0)
  ```
- [ ] Gesture trong Compose:
  ```kotlin
  Modifier.pointerInput(Unit) {
      detectHorizontalDragGestures { _, drag ->
          if (drag > 50f) viewModel.nextSentence()
          if (drag < -50f) viewModel.prevSentence()
      }
  }
  ```

### 2.4 TTS Polish (Ngày 4–5)
- [ ] SSML prosody (Android 9+): `.` → 400ms ngắt, `,` → 150ms
- [ ] Số điện thoại: đọc từng chữ số theo nhóm 3-3-4
- [ ] URL/email → "link" / "địa chỉ email"
- [ ] TTS priority queue: URGENT (obstacle) > HIGH (command) > NORMAL (ocr)

### Deliverable tuần 2
> Hướng camera vào biển hiệu → đọc < 2 giây. Chụp trang sách → swipe điều hướng từng câu.

---

## Tuần 3 — Object Detection + Obstacle Alert

**Mục tiêu:** Cảnh báo vật cản realtime bằng haptic + TTS. Long press → mô tả cảnh từ backend.

### 3.1 YOLOv8n TFLite INT8 (Ngày 1–2)
- [ ] Export model (chạy trên máy tính):
  ```bash
  pip install ultralytics
  yolo export model=yolov8n.pt format=tflite imgsz=320 int8=True
  # INT8: ~3MB, nhanh ~2x so với FP16
  ```
- [ ] `YoloDetector.kt`:
  ```kotlin
  class YoloDetector(context: Context) {
      private val interpreter = Interpreter(
          FileUtil.loadMappedFile(context, "models/yolov8n_int8.tflite"),
          Interpreter.Options().apply { numThreads = 4 }
      )
      private val labels = FileUtil.loadLabels(context, "models/labels_vi.txt")

      fun detect(bitmap: Bitmap): List<Detection> {
          val input  = preprocessBitmap(bitmap, 320, 320)   // ByteBuffer
          val output = Array(1) { Array(84) { FloatArray(8400) } }
          interpreter.run(input, output)
          return postprocess(output[0], labels)              // NMS: iou=0.45, conf=0.4
      }
  }
  // Module:
  single { YoloDetector(androidContext()) }
  ```
- [ ] Benchmark: target < 120ms/frame trên Samsung A15, Xiaomi Redmi Note 12

### 3.2 Obstacle Logic + ObstacleViewModel (Ngày 2–3)
- [ ] Zone: LEFT (0–33%) · CENTER (33–67%) · RIGHT (67–100%)
- [ ] Distance estimation từ bbox height (focal length method)
- [ ] Spam map: `HashMap<String, Long>` — không announce lại trong 3s
- [ ] Priority whitelist: `người · xe máy · xe đạp · ô tô · bậc thang · biển dừng`
- [ ] `ObstacleViewModel.kt`:
  ```kotlin
  class ObstacleViewModel(
      private val detector: YoloDetector,
      private val tts: TtsService,
      private val haptic: HapticService
  ) : ViewModel() {

      fun processFrame(bitmap: Bitmap) {
          viewModelScope.launch(Dispatchers.Default) {
              detector.detect(bitmap)
                  .filter { it.isNearby() && it.isPriority() && !it.isSpam() }
                  .forEach { d ->
                      withContext(Dispatchers.Main) {
                          when (d.zone) {
                              Zone.LEFT   -> haptic.obstacleLeft()
                              Zone.CENTER -> haptic.obstacleCenter()
                              Zone.RIGHT  -> haptic.obstacleRight()
                          }
                          tts.speak("Chú ý! ${d.labelVi} phía ${d.zoneVi}", URGENT)
                      }
                  }
          }
      }
  }
  viewModel { ObstacleViewModel(get(), get(), get()) }
  ```

### 3.3 Backend Scene Description (Ngày 3–4)
- [ ] Backend FastAPI (Railway free tier ~$7/tháng):
  ```python
  @app.post("/describe")
  async def describe(file: UploadFile):
      b64 = base64.b64encode(await file.read()).decode()
      res = openai.chat.completions.create(
          model="gpt-4o",
          messages=[{"role":"user","content":[
              {"type":"image_url","image_url":{"url":f"data:image/jpeg;base64,{b64}"}},
              {"type":"text","text":
                "Mô tả ngắn 2 câu bằng tiếng Việt cho người mù đang đứng ở đây."}
          ]}]
      )
      return {"text": res.choices[0].message.content}
  ```
- [ ] `SceneApi.kt` (Retrofit):
  ```kotlin
  interface SceneApi {
      @Multipart @POST("describe")
      suspend fun describe(@Part image: MultipartBody.Part): SceneResponse
  }
  // Module:
  val networkModule = module {
      single { Retrofit.Builder().baseUrl(BuildConfig.BACKEND_URL)
          .addConverterFactory(GsonConverterFactory.create()).build()
          .create(SceneApi::class.java) }
      single { SceneRepository(get()) }
  }
  ```
- [ ] Compress ảnh 512px trước upload, timeout 8s
- [ ] Fallback offline: template từ YOLO khi không có mạng
- [ ] Long press `CameraScreen` → `describeScene()` → haptic loading → TTS kết quả

### 3.4 Obstacle Foreground Service (Ngày 4–5)
- [ ] `ObstacleDetectionService : Service` — giữ camera + detection khi di chuyển
- [ ] Notification channel bắt buộc Android 8+: "Chế độ di chuyển đang bật"
- [ ] Toggle: Volume Down × 2
- [ ] Headphone detect → chỉ haptic, tắt TTS (tránh làm phiền)

### Deliverable tuần 3
> Realtime haptic cảnh báo đúng hướng. Long press → mô tả cảnh tiếng Việt < 5s.

---

## Tuần 4 — STT Voice Command + Navigation + Emergency

**Mục tiêu:** Điều khiển toàn bộ bằng giọng nói. Dẫn đường turn-by-turn.

### 4.1 STT + CommandParser (Ngày 1–2)
- [ ] `SttService.kt`:
  ```kotlin
  class SttService(private val context: Context) {
      private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

      fun startListening(onResult: (String) -> Unit) {
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
              putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
              putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          }
          recognizer.setRecognitionListener(/* ... */)
          recognizer.startListening(intent)
      }
  }
  single { SttService(androidContext()) }
  ```
  > ⚠️ Cần internet trên hầu hết thiết bị — đừng quảng cáo offline
- [ ] Wake trigger: **Volume Down × 2** (không cần nhìn màn hình)
- [ ] `CommandParser.kt`:
  ```kotlin
  fun parse(text: String): Command = when {
      text.contains(Regex("đọc|xem văn bản"))   -> Command.OCR
      text.contains(Regex("đây là gì|mô tả"))   -> Command.DESCRIBE
      text.contains(Regex("đi đến|dẫn đường"))  -> Command.Navigate(extractDest(text))
      text.contains(Regex("tiền bao nhiêu"))     -> Command.CURRENCY
      text.contains(Regex("ai đây"))             -> Command.FACE
      text.contains(Regex("tôi đang ở đâu"))     -> Command.LOCATION
      text.contains(Regex("gọi taxi"))           -> Command.TAXI
      text.contains(Regex("dừng lại"))           -> Command.STOP
      text.contains(Regex("trợ giúp"))           -> Command.HELP
      else -> Command.UNKNOWN
  }
  ```
- [ ] Confirmation: TTS đọc lại lệnh trước khi thực hiện

### 4.2 Bảng lệnh giọng nói

| Lệnh ví dụ | Hành động |
|-----------|-----------|
| "Đọc cái này" | Trigger chụp + OCR |
| "Đây là gì?" / "Mô tả" | Gọi backend scene description |
| "Đi đến bệnh viện Bạch Mai" | Mở navigation |
| "Tiền bao nhiêu?" | Currency detection |
| "Ai đây?" | Face recognition |
| "Tôi đang ở đâu?" | Reverse geocoding → đọc địa chỉ |
| "Gọi taxi" | Deeplink Grab / Be |
| "Dừng lại" | Cancel action |
| "Trợ giúp" | Đọc danh sách lệnh |

### 4.3 Navigation Module (Ngày 2–3)
- [ ] `NavScreen.kt` embed Google Maps:
  ```kotlin
  AndroidView(
      factory = { ctx -> MapView(ctx).also { it.onCreate(null); it.getMapAsync { /* init */ } } },
      modifier = Modifier.fillMaxSize()
  )
  ```
- [ ] Turn-by-turn: Directions API → parse steps → TTS từng bước
- [ ] Announce turns sớm **20m** trước khi rẽ
- [ ] Heading từ `SensorManager` (TYPE_MAGNETIC_FIELD + TYPE_ACCELEROMETER):
  ```kotlin
  SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
  SensorManager.getOrientation(rotationMatrix, orientation)
  val headingDegrees = Math.toDegrees(orientation[0].toDouble())
  ```
- [ ] `NavViewModel`:
  ```kotlin
  class NavViewModel(
      private val location: LocationService,
      private val maps: MapsRepository,
      private val tts: TtsService
  ) : ViewModel() {
      val navState: StateFlow<NavState>
      fun startNav(destination: String)
      fun stopNav()
  }
  viewModel { NavViewModel(get(), get(), get()) }
  ```

### 4.4 Emergency (Ngày 3–4)
- [ ] SOS: nhấn giữ volume down 3s → SMS vị trí GPS đến số khẩn cấp (lưu DataStore)
- [ ] "Tôi đang ở đâu?" → `Geocoder.getFromLocation()` → TTS địa chỉ
- [ ] "Gọi [tên]" → `Intent(Intent.ACTION_CALL)` + ContactsContract
- [ ] "Gọi taxi" → `Intent(Intent.ACTION_VIEW, Uri.parse("grab://..."))` deeplink

### Deliverable tuần 4
> Nói "Đi đến [địa điểm]" → dẫn đường tiếng Việt. Volume down × 2 → kích hoạt giọng nói. SOS gửi SMS vị trí.

---

## Tuần 5 — Currency + Face + Polish + Release

**Mục tiêu:** 2 tính năng đặc thù VN, test người dùng thật, publish.

### 5.1 Nhận dạng tiền VNĐ (Ngày 1–2)
- [ ] Thu thập dataset: ~200 ảnh × 8 mệnh giá polymer (10k, 20k, 50k, 100k, 200k, 500k)
- [ ] Fine-tune MobileNetV2 trên Google Colab (~2h):
  ```python
  base = MobileNetV2(weights='imagenet', include_top=False, input_shape=(224,224,3))
  x = GlobalAveragePooling2D()(base.output)
  out = Dense(8, activation='softmax')(x)
  for layer in base.layers[:-5]: layer.trainable = False
  # Train 10 epochs, augmentation: rotate ±30°, brightness ±30%
  ```
- [ ] Export → TFLite INT8 (~2MB)
- [ ] `CurrencyClassifier.kt` cùng pattern YoloDetector, inject Koin
- [ ] UX: "Giơ tờ tiền lên, giữ thẳng" → haptic confirm → "Đây là tờ năm mươi nghìn đồng"

### 5.2 Face Recognition (Ngày 2–3)
- [ ] ML Kit Face Detection → crop face bitmap
- [ ] FaceNet TFLite → 128-dim embedding
- [ ] Room DB (encrypted) lưu profiles:
  ```kotlin
  @Entity data class FaceProfile(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val name: String,
      val embedding: FloatArray
  )
  ```
- [ ] Enroll: chụp 3 ảnh → average embedding → lưu
- [ ] Recognize: cosine similarity ≥ 0.6 → "Đây là [tên]"
- [ ] Privacy: 100% on-device, không upload

### 5.3 Màu sắc (Bonus nếu còn thời gian, Ngày 3)
- [ ] HSV color clustering trên center crop của frame
- [ ] Map HSV range → tên màu tiếng Việt (10 màu cơ bản)
- [ ] UX: "Áo màu xanh lam. Quần màu đen."

### 5.4 Performance Polish (Ngày 3–4)
- [ ] **Android Profiler**: CPU trace, Memory heap dump → tìm leak
- [ ] Coroutine Dispatcher audit: AI inference → `Dispatchers.Default`
- [ ] CameraX lifecycle: `unbindAll()` khi app về background
- [ ] Cold start: target < 2s → `adb shell am start -W vn.blindapp/.MainActivity`
- [ ] Test trên 3 thiết bị thật: **Samsung Galaxy A15** · **Xiaomi Redmi Note 12** · **OPPO A78**
- [ ] Koin graph verification:
  ```kotlin
  // test/
  @Test fun verifyKoinModules() = runTest {
      checkKoinModules(listOf(appModule, aiModule, networkModule, navigationModule))
  }
  ```

### 5.5 Testing + Release (Ngày 4–5)
- [ ] User testing: 3–5 người mù thật (Hội Người mù VN / trường đặc biệt HN/HCM)
- [ ] Fix top bugs từ session test
- [ ] Privacy Policy (bắt buộc Google Play) — ghi rõ face data xử lý on-device
- [ ] Signing: upload keystore lên GitHub Secrets
- [ ] Build release AAB: `./gradlew bundleRelease`
- [ ] Submit Google Play **Internal Testing** track

### Deliverable tuần 5
> MVP hoàn chỉnh, đã test với người dùng thật, AAB build thành công, submit Internal Testing.

---

## Timeline

```
Tuần 1  ████████  Project + CameraX + Compose Navigation + TTS/Haptic
Tuần 2  ████████  ML Kit OCR + Document Reader + TTS Polish
Tuần 3  ████████  YOLOv8 Obstacle Alert + Backend Scene Description
Tuần 4  ████████  STT Voice Command + Google Maps + Emergency
Tuần 5  ████████  Currency + Face + Polish + Google Play Release
```

| Tuần | KPI |
|------|-----|
| 1 | Camera preview < 500ms · TalkBack pass 3 màn hình |
| 2 | OCR accuracy > 90% · TTS latency < 2s |
| 3 | Object detection < 120ms/frame · Scene description < 5s |
| 4 | Voice command intent accuracy > 85% |
| 5 | User satisfaction ≥ 4/5 (3+ người dùng) · AAB build OK |

---

## `libs.versions.toml` đầy đủ

```toml
[versions]
kotlin              = "2.1.20"
compose-bom         = "2025.06.00"
camerax             = "1.4.1"
koin-bom            = "4.1.1"
tflite              = "2.16.1"
coroutines          = "1.8.1"
retrofit            = "2.11.0"
okhttp              = "4.12.0"

[libraries]
# Compose
compose-bom             = { module = "androidx.compose:compose-bom",                version.ref = "compose-bom" }
compose-ui              = { module = "androidx.compose.ui:ui" }
compose-material3       = { module = "androidx.compose.material3:material3" }
compose-ui-tooling      = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-navigation      = { module = "androidx.navigation:navigation-compose",       version = "2.8.5" }
activity-compose        = { module = "androidx.activity:activity-compose",           version = "1.9.3" }

# CameraX
camerax-core            = { module = "androidx.camera:camera-core",                  version.ref = "camerax" }
camerax-camera2         = { module = "androidx.camera:camera-camera2",               version.ref = "camerax" }
camerax-lifecycle       = { module = "androidx.camera:camera-lifecycle",             version.ref = "camerax" }
camerax-view            = { module = "androidx.camera:camera-view",                  version.ref = "camerax" }

# Koin (dùng BOM — không cần version riêng)
koin-bom                = { module = "io.insert-koin:koin-bom",                      version.ref = "koin-bom" }
koin-android            = { module = "io.insert-koin:koin-android" }
koin-compose            = { module = "io.insert-koin:koin-androidx-compose" }
koin-viewmodel          = { module = "io.insert-koin:koin-compose-viewmodel" }
koin-test               = { module = "io.insert-koin:koin-test" }

# AI
tflite-core             = { module = "org.tensorflow:tensorflow-lite",               version.ref = "tflite" }
tflite-support          = { module = "org.tensorflow:tensorflow-lite-support",       version = "0.4.4" }
mlkit-ocr               = { module = "com.google.android.gms:play-services-mlkit-text-recognition", version = "19.0.1" }
mlkit-face              = { module = "com.google.mlkit:face-detection",              version = "16.1.7" }

# Network
retrofit                = { module = "com.squareup.retrofit2:retrofit",              version.ref = "retrofit" }
retrofit-gson           = { module = "com.squareup.retrofit2:converter-gson",        version.ref = "retrofit" }
okhttp-logging          = { module = "com.squareup.okhttp3:logging-interceptor",     version.ref = "okhttp" }

# Jetpack
datastore-prefs         = { module = "androidx.datastore:datastore-preferences",     version = "1.1.1" }
room-runtime            = { module = "androidx.room:room-runtime",                   version = "2.6.1" }
room-ktx                = { module = "androidx.room:room-ktx",                       version = "2.6.1" }
core-ktx                = { module = "androidx.core:core-ktx",                       version = "1.15.0" }
lifecycle-viewmodel     = { module = "androidx.lifecycle:lifecycle-viewmodel-compose",version = "2.8.7" }

# Maps & Location
maps-sdk                = { module = "com.google.android.gms:play-services-maps",    version = "19.0.0" }
location                = { module = "com.google.android.gms:play-services-location",version = "21.3.0" }

# Misc
coroutines-android      = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
timber                  = { module = "com.jakewharton.timber:timber",                version = "5.0.1" }
```

---

## Rủi ro & Phương án dự phòng

| Rủi ro | Xác suất | Phương án |
|--------|---------|-----------|
| GPT-4o backend > 5s | Cao | YOLO template fallback + TTS "đang xử lý..." |
| STT cần internet, mất mạng | Cao | Fallback gesture + button rõ ràng |
| YOLOv8 INT8 giảm accuracy | Trung bình | Thử FP16 nếu miss nhiều vật nguy hiểm |
| Koin conflict giữa module | Thấp | `checkKoinModules()` test ngay tuần 1 |
| Dataset tiền VNĐ không đủ | Thấp | Augmentation (rotate, brightness, flip) |
| Railway free tier bị rate-limit | Thấp | Upgrade $7/tháng hoặc dùng Render |
| User test không kịp tuần 5 | Thấp | Facebook "Người mù Việt Nam" + group zalo |

---

## Ghi chú cho Developer

1. **TalkBack test trước mọi merge** — không pass = không merge
   ```bash
   adb shell settings put secure enabled_accessibility_services \
     com.google.android.marvin.talkback/.TalkBackService
   ```
2. **Mọi ViewModel dùng `koinViewModel()`** trong Composable — không dùng `viewModel()` AndroidX
3. **AI inference trên `Dispatchers.Default`** — không bao giờ block Main thread
4. **Không dùng NNAPI delegate** — `numThreads = 4` là baseline an toàn cho thiết bị VN
5. **Test trên phần cứng thật** — emulator không đủ cho camera + AI
6. **Target thiết bị tầm trung VN**: Snapdragon 460/680, RAM 4GB, giá 3–5 triệu
7. **Mọi tính năng phải có offline fallback** — mạng VN không ổn định
8. **Response < 2s** cho mọi tương tác chính — người mù cần feedback ngay lập tức

---

*Stack xác nhận tháng 4/2026: Kotlin 2.1.20 · Compose BOM 2025.06 · Koin 4.1.1 · CameraX 1.4.1 · TFLite 2.16.1 · ML Kit OCR 19.0.1*
