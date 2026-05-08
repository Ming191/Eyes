# Tài liệu kỹ thuật thay đổi module Camera/Obstacle

## Phạm vi so sánh

- **Commit gốc (baseline):** `318b2d7`
- **Trạng thái hiện tại:** working tree local (chưa commit)
- **Module trọng tâm:**
  - `app/src/main/java/com/example/eyes/ui/camera/*`
  - `app/src/main/java/com/example/eyes/service/ObstacleDetectionService.kt`
  - `app/src/main/java/com/example/eyes/ai/*` (các thành phần fusion/depth mới)
  - `app/src/main/java/com/example/eyes/camera/FrameThrottle.kt`

---

## 1) Mục tiêu thay đổi

Các thay đổi tập trung vào 4 mục tiêu:

1. Giảm false positive từ MiDaS (cảnh báo ảo dù không nguy hiểm).
2. Tăng khả năng bắt vật cản gần theo thời gian thực (đặc biệt trường hợp áp sát tường).
3. Giảm hiện tượng dao động trạng thái (`cảnh báo -> an toàn -> cảnh báo...`).
4. Tăng khả năng debug tại hiện trường bằng cách hiển thị depth map trực tiếp trên UI.

---

## 2) Thay đổi kiến trúc logic cảnh báo

### 2.1. Thêm Depth hazard detector chuyên biệt

**File mới:** `app/src/main/java/com/example/eyes/ai/DepthHazardDetector.kt`

Nội dung chính:

- Phân vùng ảnh theo **Zone** (`LEFT/CENTER/RIGHT`) và **VerticalBand** (`GROUND/TORSO/HEAD`).
- Đánh giá độ gần bằng:
  - percentile (`p80`)
  - `mediumRatio` / `highRatio` theo từng vùng
- Chính sách ngưỡng chặt hơn:
  - `nearThresholdMedium = 0.80`
  - `nearThresholdHigh = 0.88`
- `HEAD` bị loại khỏi depth-only candidate để giảm nhiễu cảnh báo va chạm đi lại.
- `persistenceFrames` mặc định đang là `1` (bắt nhanh hơn khi đã tăng cadence refresh).

### 2.2. Thêm Fusion engine YOLO + Depth

**File mới:** `app/src/main/java/com/example/eyes/ai/HazardFusionEngine.kt`

Nội dung chính:

- `YOLO` là nguồn primary nếu có object hợp lệ.
- Depth có thể trở thành secondary haptic khi khác zone và đủ mạnh.
- Khi không có YOLO, depth-only vẫn có thể tạo alert để không bỏ lỡ va chạm gần.

### 2.3. Thêm Speech rate limiter

**File mới:** `app/src/main/java/com/example/eyes/ai/SpeechRateLimiter.kt`

- Cooldown mặc định: `2500ms`
- Mục tiêu: chống lặp TTS quá dày trong bối cảnh frame rate tăng.

---

## 3) Thay đổi cadence xử lý (latency/performance)

### 3.1. Tăng tần suất frame pipeline

**File:** `app/src/main/java/com/example/eyes/camera/FrameThrottle.kt`

- `intervalMs: 200ms -> 100ms`
- Tương đương: ~`5fps -> 10fps` cho pipeline xử lý.

### 3.2. Tăng tần suất refresh MiDaS

**Files:**

- `CameraViewModel.kt`
- `ObstacleDetectionService.kt`

Thay đổi:

- `DEPTH_FRAME_INTERVAL: 30 -> 3`

Tác động thực tế (với pipeline ~10fps):

- Refresh depth khoảng mỗi `0.3s` thay vì nhiều giây/lần.

---

## 4) Giảm dao động cảnh báo (flicker)

### 4.1. Nguyên nhân gốc đã xử lý

1. Spam filter từng được đặt ở bước chọn candidate -> làm mất hazard presence giữa các frame.
2. Depth candidate có khoảng trống giữa các lần refresh.
3. UI đổi sang "an toàn" quá nhanh chỉ sau 1 frame null.

### 4.2. Cơ chế ổn định mới

#### Trong `CameraViewModel.kt`

- Đọc depth candidate qua hàm **freshness TTL**:
  - `DEPTH_HAZARD_TTL_MS = 1200ms`
- Thêm cooldown rung:
  - `HAPTIC_COOLDOWN_MS = 800ms`
- Thêm hysteresis trạng thái an toàn:
  - `SAFE_STATUS_STREAK_FRAMES = 2`

Kết quả: giảm nhấp nháy `alert/safe` và giảm rung liên tục khi vật cản giữ nguyên.

#### Trong `ObstacleDetectionService.kt`

- Cùng cơ chế TTL depth (`1200ms`) + haptic cooldown (`800ms`) cho mode service.

---

## 5) Thêm công cụ debug trực quan depth

### 5.1. Hiển thị depth map trên màn hình camera

**Files:**

- `CameraViewModel.kt`
- `CameraScreen.kt`

Thay đổi:

- `CameraUiState` thêm `depthPreviewBitmap`.
- Mỗi lần refresh MiDaS, depth map được chuyển thành bitmap grayscale:
  - **Sáng = gần**
  - **Tối = xa**
- UI card hiển thị ảnh depth map để đối chiếu trực tiếp model output với thực cảnh.

---

## 6) Dọn code theo Compose + Accessibility checklist

**File:** `CameraScreen.kt`

- Gỡ semantics dư thừa gây TalkBack đọc lặp.
- Dùng `clearAndSetSemantics {}` cho debug text bên trong card đã `mergeDescendants`.
- Giữ `contentDescription` hợp lý cho depth image.
- Giữ touch target của nút toggle status card theo chuẩn Compose Material.

**File:** `CameraViewModel.kt`

- Cache trạng thái headset trong một lần xử lý frame để tránh gọi lặp.

---

## 7) Test đã thêm/cập nhật

**Files mới:**

- `app/src/test/java/com/example/eyes/ai/DepthHazardDetectorTest.kt`
- `app/src/test/java/com/example/eyes/ai/HazardFusionEngineTest.kt`
- `app/src/test/java/com/example/eyes/ai/SpeechRateLimiterTest.kt`

Bao phủ chính:

- Detector phát hiện hazard mạnh ngay frame depth mới.
- Bỏ qua `HEAD` band và bỏ qua patch nhiễu nhỏ.
- Fusion behavior cho YOLO/depth-only.
- Speech cooldown hoạt động đúng.

---

## 8) Danh sách file thay đổi (module scope)

### Modified

- `app/src/main/java/com/example/eyes/camera/FrameThrottle.kt`
- `app/src/main/java/com/example/eyes/service/ObstacleDetectionService.kt`
- `app/src/main/java/com/example/eyes/ui/camera/CameraScreen.kt`
- `app/src/main/java/com/example/eyes/ui/camera/CameraViewModel.kt`

### Added

- `app/src/main/java/com/example/eyes/ai/DepthHazardDetector.kt`
- `app/src/main/java/com/example/eyes/ai/HazardFusionEngine.kt`
- `app/src/main/java/com/example/eyes/ai/SpeechRateLimiter.kt`
- `app/src/test/java/com/example/eyes/ai/DepthHazardDetectorTest.kt`
- `app/src/test/java/com/example/eyes/ai/HazardFusionEngineTest.kt`
- `app/src/test/java/com/example/eyes/ai/SpeechRateLimiterTest.kt`

---

## 9) Kết quả verify gần nhất

Đã chạy thành công:

```bash
./gradlew --stop
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Lưu ý:

- Có gặp trạng thái lỗi daemon Kotlin tạm thời trong một lần chạy incremental; sau `--stop` thì pass ổn định.

---

## 10) Gợi ý theo dõi sau khi release nội bộ

1. Theo dõi tỷ lệ false positive/false negative theo bối cảnh (hành lang, cửa, ánh sáng yếu).
2. Nếu còn nhấp nháy, cân nhắc tăng `SAFE_STATUS_STREAK_FRAMES` lên `3`.
3. Nếu phản hồi rung còn dày, tăng `HAPTIC_COOLDOWN_MS` lên `1000~1200ms`.
4. Nếu bỏ lỡ vật cản nhanh, giảm `DEPTH_FRAME_INTERVAL` hoặc giảm nhẹ policy ratio ở `DepthHazardDetector`.
