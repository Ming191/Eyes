# 🗺️ Map Compose Demo — Kế hoạch triển khai theo PR

> **Mục tiêu:** dựng bản demo Map riêng cho SoundVision bằng Google Maps Compose, đủ để người dùng xem vị trí hiện tại, nhập điểm đến và xem trước tuyến đi bộ.  
> **Phạm vi:** chỉ phần Map. Chưa gộp voice command, SOS, reverse geocoding, autocomplete hay turn-by-turn.

---

## 1. Mục tiêu v1

Phiên bản đầu tiên của Map cần cho phép người dùng:

- Xem vị trí hiện tại trên bản đồ
- Nhập điểm đến bằng địa chỉ dạng text
- Xem tuyến đi bộ thật từ vị trí hiện tại đến điểm đến
- Xem đường đi dưới dạng polyline
- Nghe/đọc được phần tóm tắt tuyến: tổng quãng đường, thời gian ước tính
- Theo dõi danh sách từng bước bằng tiếng Việt, phù hợp với TalkBack

Đây là **demo client-side**, chưa phải kiến trúc production. Mục tiêu của giai đoạn này là chứng minh trọn luồng trải nghiệm Map trước khi đầu tư vào routing backend, voice flow hoặc điều hướng thực địa.

---

## 2. Interface và type chính

Các abstraction nên xuất hiện sớm để UI, location và routing không dính chặt vào nhau:

```kotlin
sealed interface MapUiState {
    data object Idle : MapUiState
    data object Locating : MapUiState
    data class Ready(val location: UserLocation) : MapUiState
    data class RouteLoading(val location: UserLocation, val destination: String) : MapUiState
    data class RouteReady(
        val location: UserLocation,
        val destination: String,
        val route: RoutePreview
    ) : MapUiState
    data class Error(val message: String) : MapUiState
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

data class RoutePreview(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<RouteStep>
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int
)

interface LocationProvider {
    suspend fun currentLocation(): Result<UserLocation>
}

interface RouteRepository {
    suspend fun previewWalkingRoute(
        origin: UserLocation,
        destination: String
    ): Result<RoutePreview>
}
```

`MapViewModel` tối thiểu cần có:

```kotlin
fun onMapOpened()
fun onDestinationChanged(text: String)
fun previewRoute()
fun retryLocation()
```

---

## 3. Chuỗi PR đề xuất

### PR 1 — Dựng nền móng Map demo

**Mục tiêu:** thêm hạ tầng đủ sạch để các PR sau tập trung vào hành vi thay vì wiring.

**Commits**

1. `build: add google maps compose and location dependencies`
2. `build: add demo map and routes api key config slots`
3. `feat(map): introduce map domain models and repository contracts`
4. `test(di): register map dependencies in koin smoke tests`

**Nội dung chính**

- Thêm dependency cho Maps Compose và Google Play services location
- Thêm chỗ cấu hình API key cho Maps SDK và Routes API trong demo build
- Khai báo model/domain contract cho location và route preview
- Đăng ký dependency mới vào Koin
- Mở rộng smoke test DI để phát hiện thiếu wiring sớm

---

### PR 2 — Thay placeholder bằng màn hình Map thật

**Mục tiêu:** người dùng mở tab Map và thấy bản đồ thật cùng trạng thái vị trí hiện tại.

**Commits**

1. `fix(permission): request coarse and fine location together`
2. `feat(map): add fused location provider implementation`
3. `feat(map): add map viewmodel and current location state flow`
4. `feat(map-ui): replace placeholder with accessible map screen`
5. `test(map): cover permission denied, locating, and ready states`

**Nội dung chính**

- Cập nhật luồng permission để xử lý `ACCESS_COARSE_LOCATION` và `ACCESS_FINE_LOCATION` cùng lúc
- Dùng `FusedLocationProviderClient` để lấy vị trí hiện tại
- Thêm `MapViewModel` và state flow cho các trạng thái:
  - chưa có quyền
  - đang lấy vị trí
  - lấy vị trí thành công
  - lỗi vị trí
- Thay `MapScreen` placeholder bằng map screen thật
- Bảo đảm semantics tiếng Việt, heading rõ, CTA có touch target đúng chuẩn

---

### PR 3 — Nhập điểm đến và lấy route preview

**Mục tiêu:** người dùng nhập địa chỉ và nhận được tuyến đi bộ thật.

**Commits**

1. `feat(map-data): add routes api client and dto mapping`
2. `feat(map): implement walking route preview use case`
3. `feat(map-ui): add destination input and preview action`
4. `test(map): cover route success, zero result, and network failure`

**Nội dung chính**

- Gọi Routes API trực tiếp từ app trong phạm vi demo
- Request mặc định:
  - mode: walking
  - language: `vi-VN`
  - origin: vị trí hiện tại
  - destination: text người dùng nhập
- Map response thành `RoutePreview` và `RouteStep`
- Thêm ô nhập điểm đến và nút xem tuyến
- Xử lý loading, không tìm thấy tuyến, lỗi mạng và retry

---

### PR 4 — Vẽ tuyến và hiển thị từng bước

**Mục tiêu:** biến route data thành trải nghiệm thật sự có ích cho người dùng khiếm thị.

**Commits**

1. `feat(map-ui): render route polyline and fit camera bounds`
2. `feat(map): normalize route steps for display`
3. `feat(map-ui): add route summary card and accessible step list`
4. `test(map-ui): verify route summary, step order, and talkback labels`

**Nội dung chính**

- Decode polyline và vẽ đường đi trên map
- Tự fit camera để thấy được toàn tuyến
- Hiển thị:
  - địa chỉ đích
  - tổng quãng đường
  - thời gian ước tính
  - danh sách bước theo thứ tự
- Làm sạch instruction trước khi render để không lộ HTML/raw text từ API
- Coi danh sách bước là output chính, không chỉ là phụ kiện bên dưới bản đồ

---

### PR 5 — Gia cố demo và chốt chất lượng

**Mục tiêu:** đưa demo từ trạng thái “chạy được” sang “đưa cho người khác dùng thử được”.

**Commits**

1. `feat(map): cancel stale route requests and clear obsolete previews`
2. `feat(map-ui): add empty, retry, and api error polish states`
3. `test(map): expand viewmodel and semantics regression coverage`
4. `docs(map): add demo setup and manual qa checklist`

**Nội dung chính**

- Hủy hoặc bỏ qua response cũ khi người dùng đổi địa chỉ liên tục
- Xóa route preview đã lỗi thời đúng lúc để tránh hiển thị sai
- Hoàn thiện empty/error/retry states
- Mở rộng test regression cho ViewModel và Compose semantics
- Ghi checklist QA thủ công cho bản demo

---

## 4. Test plan và acceptance criteria

### Unit tests

- `MapViewModel`
  - chuyển state đúng khi mở map
  - chuyển state đúng khi route loading / success / failure
  - không giữ route cũ sau khi destination thay đổi
- `RouteRepository`
  - map DTO thành domain model đúng
  - xử lý zero-result
  - xử lý response lỗi hoặc thiếu dữ liệu
- `LocationProvider`
  - trả vị trí thành công
  - trả lỗi khi không có quyền hoặc không lấy được fix

### Compose / Robolectric tests

- Màn hình có content description, heading và nhãn tiếng Việt
- Nút thao tác có semantics đúng
- Route summary hiển thị đúng dữ liệu
- Danh sách bước giữ đúng thứ tự
- Permission denied, loading, empty và error states hiển thị đúng

### Acceptance criteria

- Từ Home → Bản đồ, người dùng thấy map thật thay vì placeholder
- Khi có quyền vị trí, map center vào vị trí hiện tại
- Khi nhập địa chỉ hợp lệ và bấm xem tuyến, app hiển thị polyline + summary + steps
- Khi thiếu quyền, mất mạng hoặc không có tuyến, app không crash và luôn có thông báo hữu ích
- Build debug vẫn pass với stack hiện tại của repo

---

## 5. Assumptions và mặc định đã chốt

- Đây là bản **demo client-side**, chưa phải thiết kế production
- V1 chỉ hỗ trợ **đi bộ**
- V1 dùng **ô nhập địa chỉ dạng text**
- Giả định thiết bị **online** và có Google Play services
- Không triển khai trong đợt này:
  - voice command
  - SOS
  - reverse geocoding
  - Places Autocomplete
  - turn-by-turn navigation
  - recent destinations
- UI tiếp tục tuân thủ luật hiện tại của repo:
  - toàn bộ chuỗi hiển thị bằng tiếng Việt
  - composable có semantics cho TalkBack
  - touch target tối thiểu 48dp
  - nút chính ưu tiên 88dp trở lên
