# 🧪 SoundVision — Unit Test Plan Chi Tiết (Accessibility First)

> **Stack test:** JUnit 5 (Jupiter) · MockK · Turbine · Koin Test · kotlinx-coroutines-test · Robolectric (Semantics)  
> **Convention:** mỗi test case ghi rõ `// GIVEN / WHEN / THEN`  
> **CI:** chạy `./gradlew testDebugUnitTest` trước mỗi merge

---

## Dependencies cần thêm vào `build.gradle.kts`

```kotlin
// Core Testing & Mocking
testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
testImplementation("io.mockk:mockk:1.13.10")
testImplementation("com.google.truth:truth:1.4.2")

// Coroutines & Flow
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
testImplementation("app.cash.turbine:turbine:1.1.0")

// Koin DI
testImplementation("io.insert-koin:koin-test:4.1.1")
testImplementation("io.insert-koin:koin-test-junit5:4.1.1")

// Compose UI Semantics Testing (Robolectric)
testImplementation("org.robolectric:robolectric:4.12.1")
testImplementation("androidx.compose.ui:ui-test-junit4")
testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2") // Chạy mix JUnit 4 & 5
```

---

## Utility: MainDispatcherExtension (Dành cho JUnit 5)

Vì chúng ta sử dụng JUnit 5, không còn `@Rule` như JUnit 4, cần tạo Extension sau để giả lập Main thread cho ViewModel.

```kotlin
// test/util/MainDispatcherExtension.kt
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
```

---

## TUẦN 1 — Project Setup + CameraX + Semantics + TTS Foundation

---

### 1.1 Project Setup — DI Graph

**File:** `test/di/KoinModulesTest.kt`

```kotlin
class KoinModulesTest : KoinTest {

    @Test
    fun `1_1_1 - Koin graph không có lỗi khi khởi tạo toàn bộ module`() {
        val modules = listOf(appModule, aiModule, networkModule, navigationModule)
        checkKoinModules(modules)
    }

    @Test
    fun `1_1_2 - TtsService và HapticService được resolve từ Koin graph`() {
        startKoin { modules(appModule) }
        val tts: TtsService by inject()
        val haptic: HapticService by inject()
        assertNotNull(tts)
        assertNotNull(haptic)
        stopKoin()
    }
}
```

---

### 1.2 Compose UI Semantics — Hỗ trợ TalkBack

**File:** `test/ui/home/HomeScreenSemanticsTest.kt`

**Mục đích:** Đảm bảo mọi UI Component đều có `contentDescription` để TalkBack có thể đọc cho người khiếm thị. *(Chạy bằng Robolectric).*

```kotlin
@RunWith(RobolectricTestRunner::class)
class HomeScreenSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `1_2_1 - Nút 'Xem xung quanh' có contentDescription đầy đủ và bấm được`() {
        // GIVEN
        composeTestRule.setContent {
            HomeScreen(navController = mockk(relaxed = true))
        }

        // WHEN + THEN
        composeTestRule.onNodeWithText("Xem xung quanh")
            .assertExists()
            .assertContentDescriptionContains("mở camera nhận dạng vật cản")
            .assertHasClickAction()
    }
}
```

---

### 1.3 Frame Throttle Test

**File:** `test/camera/FrameThrottleTest.kt`

```kotlin
class FrameThrottleTest {
    private val throttle = FrameThrottle(intervalMs = 200)

    @Test
    fun `1_3_1 - Frame đầu tiên luôn pass qua throttle`() {
        assertThat(throttle.shouldProcess(currentTimeMs = 0)).isTrue()
    }

    @Test
    fun `1_3_2 - Frame thứ 2 trong cùng 200ms bị drop`() {
        throttle.shouldProcess(currentTimeMs = 0)
        assertThat(throttle.shouldProcess(currentTimeMs = 150)).isFalse()
    }
}
```

---

### 1.4 TtsService — Xử lý Text & Lỗi thực tế

**File:** `test/system/TtsServiceTest.kt`

```kotlin
class TtsServiceTest {

    private fun preprocess(raw: String): String = TtsTextPreprocessor.process(raw)

    @Test
    fun `1_4_1 - URL được thay bằng chữ 'link'`() {
        assertThat(preprocess("Tải về https://file.apk")).isEqualTo("Tải về link")
    }

    @Test
    fun `1_4_2 - Ký tự đặc biệt '■▪►' bị xoá`() {
        assertThat(preprocess("■ Mục 1 ► Mục 2")).doesNotContainMatch(Regex("[■►]"))
    }

    @Test
    fun `1_4_3 - Priority URGENT dùng QUEUE_FLUSH`() {
        val mockTts = mockk<TextToSpeech>(relaxed = true)
        val service = TtsService(mockTts, mockk(relaxed = true))
        service.speak("Khẩn", TtsService.Priority.URGENT)
        verify { mockTts.speak(any(), TextToSpeech.QUEUE_FLUSH, any(), any()) }
    }

    @Test
    fun `1_4_4 - Fallback haptic khi TTS lỗi (Người mù cần biết app còn sống)`() {
        val mockTts = mockk<TextToSpeech>(relaxed = true)
        val mockHaptic = mockk<HapticService>(relaxed = true)
        val service = TtsService(mockTts, mockHaptic)
        
        // Giả lập TTS trả về ERROR
        every { mockTts.speak(any(), any(), any(), any()) } returns TextToSpeech.ERROR
        
        service.speak("Chào bạn")
        // App phải rung lỗi để báo hiệu
        verify { mockHaptic.error() }
    }
}
```

---

### 1.5 HomeViewModel

**File:** `test/ui/home/HomeViewModelTest.kt`

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class HomeViewModelTest {

    private val mockTts = mockk<TtsService>(relaxed = true)
    private lateinit var vm: HomeViewModel

    @BeforeEach
    fun setup() {
        vm = HomeViewModel(mockTts)
    }

    @Test
    fun `1_5_1 - greet() đề cập đến 3 nút chính`() {
        val slot = slot<String>()
        every { mockTts.speak(capture(slot), any()) } just runs
        vm.greet()
        assertThat(slot.captured).containsMatch(Regex("Xem|Đọc|Đi"))
    }
}
```

---

## TUẦN 2 — OCR tiếng Việt + Document Reader

*(Giữ nguyên logic của OcrPostProcessorTest, DiacriticCorrectorTest, DocumentStateTest, TtsTextFormatterTest)*

### 2.2 OcrViewModel — Change Detection

**File:** `test/ui/ocr/OcrViewModelTest.kt`

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class OcrViewModelTest {

    private val mockOcr = mockk<MlKitOcrEngine>()
    private val mockTts = mockk<TtsService>(relaxed = true)
    private val mockHaptic = mockk<HapticService>(relaxed = true)
    private lateinit var vm: OcrViewModel

    @BeforeEach
    fun setup() {
        vm = OcrViewModel(mockOcr, mockTts, mockHaptic)
    }

    @Test
    fun `2_2_1 - Text thay đổi > 30% -> haptic confirm được trigger và đọc TTS`() = runTest {
        coEvery { mockOcr.recognize(any()) } returnsMany listOf(
            OcrResult("Cà phê Trung Nguyên"),
            OcrResult("Bún bò Huế ngon")
        )
        vm.processFrame(mockk(relaxed = true))
        advanceUntilIdle()
        vm.processFrame(mockk(relaxed = true))
        advanceUntilIdle()
        verify(atLeast = 1) { mockHaptic.confirm() }
        verify(exactly = 2) { mockTts.speak(any(), any()) }
    }
}
```

---

## TUẦN 3 — Object Detection + Obstacle Alert

### 3.2 Obstacle Spam Filter — Logic an toàn sinh mạng

**File:** `test/data/ondevice/SpamFilterTest.kt`

```kotlin
class SpamFilterTest {

    private val spamFilter = ObstacleSpamFilter(cooldownMs = 3000)

    @Test
    fun `3_2_S1 - Object mới không bị đánh dấu spam`() {
        assertThat(spamFilter.isSpam("xe máy", distance = 5f, currentTimeMs = 0)).isFalse()
    }

    @Test
    fun `3_2_S2 - Object cùng label và khoảng cách ổn định trong 3s -> là Spam`() {
        spamFilter.markAnnounced("xe máy", distance = 5f, currentTimeMs = 0)
        val isSpam = spamFilter.isSpam("xe máy", distance = 4.8f, currentTimeMs = 2000)
        assertThat(isSpam).isTrue()
    }

    @Test
    fun `3_2_S3 - VẬT DI CHUYỂN NHANH: Tiến lại gần đột ngột -> Xóa Spam, phải cảnh báo ngay`() {
        // GIVEN: Báo xe máy cách 5m ở giây 0
        spamFilter.markAnnounced("xe máy", distance = 5.0f, currentTimeMs = 0)
        
        // WHEN: Ở giây thứ 1 (vẫn trong 3s cooldown), xe máy lao tới còn 1.5m
        val isSpam = spamFilter.isSpam("xe máy", distance = 1.5f, currentTimeMs = 1000)
        
        // THEN: Phải ngắt cooldown, cảnh báo nguy hiểm
        assertThat(isSpam).isFalse()
    }
}
```

---

### 3.3 ObstacleViewModel — Phân luồng Cảnh báo

**File:** `test/ui/camera/ObstacleViewModelTest.kt`

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class ObstacleViewModelTest {

    private val mockDetector = mockk<YoloDetector>()
    private val mockTts = mockk<TtsService>(relaxed = true)
    private val mockHaptic = mockk<HapticService>(relaxed = true)
    private lateinit var vm: ObstacleViewModel

    @BeforeEach
    fun setup() {
        vm = ObstacleViewModel(mockDetector, mockTts, mockHaptic)
    }

    @Test
    fun `3_3_V1 - Object CENTER -> haptic.obstacleCenter() và TTS URGENT`() = runTest {
        every { mockDetector.detect(any()) } returns listOf(
            Detection(labelVi="người", zone=Zone.CENTER, distance=1.5f, isNearby=true)
        )
        vm.processFrame(mockk())
        advanceUntilIdle()
        verify { mockHaptic.obstacleCenter() }
        verify { mockTts.speak(match { it.contains("người") }, TtsService.Priority.URGENT) }
    }
}
```

---

## TUẦN 4 — STT Voice Command + Navigation + Emergency

### 4.1 CommandParser — Chống nhiễu giọng nói (Filler words)

**File:** `test/domain/CommandParserTest.kt`

```kotlin
class CommandParserTest {

    private val parser = CommandParser()

    @Test fun `4_1_1 - Lệnh thuần: "đi đến bệnh viện" -> Navigate`() {
        val cmd = parser.parse("đi đến bệnh viện Bạch Mai")
        assertThat(cmd).isInstanceOf(Command.Navigate::class.java)
    }

    @Test fun `4_1_2 - Chống nhiễu từ đệm (filler words) - Người mù ngập ngừng`() {
        // Có chứa "ờ", "cho tôi", "nhé"
        val cmd = parser.parse("ờ cho tôi đi đến bệnh viện Bạch Mai nhé")
        assertThat(cmd).isInstanceOf(Command.Navigate::class.java)
        assertThat((cmd as Command.Navigate).destination).isEqualTo("bệnh viện Bạch Mai")
    }

    @Test fun `4_1_3 - Lệnh "gọi taxi" -> TAXI`() =
        assertThat(parser.parse("gọi taxi đi")).isEqualTo(Command.TAXI)
}
```

---

### 4.3 NavViewModel — State Flow

**File:** `test/ui/navigation/NavViewModelTest.kt`

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class NavViewModelTest {

    private val mockMaps = mockk<MapsRepository>(relaxed = true)
    private lateinit var vm: NavViewModel

    @BeforeEach
    fun setup() {
        vm = NavViewModel(mockk(relaxed = true), mockMaps, mockk(relaxed = true))
    }

    @Test
    fun `4_3_1 - startNav() chuyển state sang NAVIGATING`() = runTest {
        coEvery { mockMaps.getDirections(any()) } returns listOf("Rẽ trái")

        vm.navState.test {
            assertThat(awaitItem()).isEqualTo(NavState.IDLE)
            vm.startNav("Hồ Gươm")
            assertThat(awaitItem()).isEqualTo(NavState.NAVIGATING)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

### 4.4 Emergency — Chống chịu Mất Mạng

**File:** `test/domain/EmergencyHandlerTest.kt`

```kotlin
class EmergencyHandlerTest {

    private val mockSms = mockk<SmsManager>(relaxed = true)
    private val mockLocation = mockk<LocationService>()
    private val mockDataStore = mockk<DataStoreManager>()
    private val handler = EmergencyHandler(mockSms, mockLocation, mockDataStore)

    @Test
    fun `4_4_1 - SOS gửi SMS thành công khi có mạng`() = runTest {
        coEvery { mockLocation.getCurrentLocation() } returns LatLng(21.0, 105.8)
        coEvery { mockLocation.getAddressFromLocation(any()) } returns "123 P. Huế"
        coEvery { mockDataStore.getEmergencyNumber() } returns "0909123456"

        handler.sendSos()

        verify {
            mockSms.sendTextMessage(
                "0909123456", null, match { it.contains("123 P. Huế") }, any(), any()
            )
        }
    }

    @Test
    fun `4_4_2 - Khi mất mạng, không có địa chỉ, vẫn ép gửi tọa độ cứng kèm link Google Maps`() = runTest {
        coEvery { mockLocation.getCurrentLocation() } returns LatLng(21.0, 105.8)
        // Giả lập Geocoder ném lỗi do không có mạng
        coEvery { mockLocation.getAddressFromLocation(any()) } throws IOException("No network")
        coEvery { mockDataStore.getEmergencyNumber() } returns "0909123456"

        handler.sendSos()

        verify {
            mockSms.sendTextMessage(
                any(), any(),
                match { it.contains("21.0") && it.contains("105.8") && it.contains("maps.google.com") },
                any(), any()
            )
        }
    }
}
```

---

## TUẦN 5 — Currency + Face + Polish

*(Giữ nguyên logic của CurrencyClassifierTest, FaceNetRecognizerTest, ColorDetectorTest, DataStoreManagerTest vì các test này là model prediction thuần tuý không ảnh hưởng tới framework).*

---

## Tổng hợp: Coverage Target (Accessibility First)

| Module | File Test | Trọng tâm Test | Số Test Case |
|--------|-----------|----------------|-------------|
| UI Semantics | `HomeScreenSemanticsTest` | **TalkBack hỗ trợ đọc UI** | 3 |
| TTS / Haptic | `TtsServiceTest` | **Haptic báo lỗi thay vì im lặng** | 9 |
| Spam Filter | `SpamFilterTest` | **Vật thể lao tới nhanh ngắt spam** | 6 |
| Voice Command| `CommandParserTest` | **Bỏ qua filler word, nhận lệnh đúng** | 17 |
| Emergency | `EmergencyHandlerTest` | **Fallback gửi SMS tọa độ offline** | 4 |
| OCR Logic | `OcrPostProcessorTest` | Logic gom dòng văn bản | 6 |
| AI Pipeline | `YoloPostprocessorTest` | Xử lý Bounding box AI | 5 |
| DI / Graph | `KoinModulesTest` | Đảm bảo không crash lúc runtime | 5 |
| **TỔNG** | **~25 file** | **Bảo vệ rủi ro thực tế cho khiếm thị** | **~150 case** |

---

## CI Integration với Robolectric + JUnit 5

```yaml
# .github/workflows/test.yml
name: Unit Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      
      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run JUnit 5 and Robolectric Tests
        run: ./gradlew testDebugUnitTest --continue
      
      - name: Upload test report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-report
          path: app/build/reports/tests/
```

---
*Plan này đã được nâng cấp với JUnit 5 và bổ sung các Test Case "Cứu sinh" (Life-saving) để đảm bảo App hỗ trợ người khiếm thị một cách an toàn và tin cậy nhất trong môi trường thực tế.*
--- END OF FILE SoundVision_UnitTestPlan.md ---
