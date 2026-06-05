# 🎧 EarTranslator — Hướng dẫn cài đặt & chạy

Ứng dụng dịch thuật thời gian thực tích hợp tai nghe Android.
Sử dụng **MyMemory API** (hoàn toàn miễn phí, không cần đăng ký).

---

## 📋 Yêu cầu

| Thứ | Chi tiết |
|-----|---------|
| **Android Studio** | Phiên bản Hedgehog (2023.1) trở lên |
| **Điện thoại** | Android 10 (API 29) trở lên |
| **Kết nối internet** | Để dịch qua MyMemory API |
| **Tai nghe Bluetooth** | Bất kỳ loại nào |

---

## 🚀 Các bước cài đặt

### Bước 1 — Cài Android Studio
1. Tải tại: https://developer.android.com/studio
2. Cài đặt bình thường (Next → Next → Finish)
3. Mở Android Studio lần đầu, chờ tải SDK (~5 phút)

### Bước 2 — Mở Project
1. Mở Android Studio
2. Chọn **File → Open**
3. Chọn thư mục `EarTranslator` (thư mục này)
4. Chờ Gradle sync xong (~2-3 phút, thấy thanh progress ở dưới)

### Bước 3 — Bật chế độ Developer trên điện thoại
1. Vào **Cài đặt → Giới thiệu về điện thoại**
2. Nhấn **Số bản dựng** 7 lần liên tiếp
3. Quay lại **Cài đặt → Tùy chọn nhà phát triển**
4. Bật **Gỡ lỗi USB**

### Bước 4 — Kết nối điện thoại
1. Cắm cáp USB vào máy tính
2. Điện thoại hỏi "Cho phép gỡ lỗi USB?" → nhấn **Cho phép**
3. Android Studio sẽ nhận ra điện thoại (góc trên phải)

### Bước 5 — Chạy app
1. Nhấn nút **▶ Run** (màu xanh lá, góc trên phải)
2. Chọn điện thoại của bạn
3. Chờ build xong (~1 phút lần đầu)
4. App tự động mở trên điện thoại!

---

## 📱 Cách sử dụng

1. **Kết nối tai nghe Bluetooth** vào điện thoại
2. Mở **EarTranslator**
3. Chọn **ngôn ngữ nguồn** (ngôn ngữ trong video)
4. Nhấn **▶ Bắt đầu dịch**
5. Cho phép các quyền được yêu cầu
6. Mở video/phim muốn xem
7. Bản dịch tự động phát qua tai nghe! 🎧

---

## 🌐 Ngôn ngữ hỗ trợ (nguồn → Tiếng Việt)

- 🇺🇸 English
- 🇨🇳 中文 (Tiếng Trung)
- 🇯🇵 日本語 (Tiếng Nhật)
- 🇰🇷 한국어 (Tiếng Hàn)
- 🇫🇷 Français (Tiếng Pháp)
- 🇩🇪 Deutsch (Tiếng Đức)
- 🇪🇸 Español (Tiếng Tây Ban Nha)
- 🇹🇭 ภาษาไทย (Tiếng Thái)
- 🇷🇺 Русский (Tiếng Nga)

---

## ⚙️ Cấu trúc Project

```
EarTranslator/
├── app/src/main/
│   ├── java/com/eartranslator/
│   │   ├── api/
│   │   │   └── TranslatorApi.kt      ← Gọi MyMemory API dịch thuật
│   │   ├── service/
│   │   │   └── TranslatorService.kt  ← Service chạy nền (trái tim app)
│   │   ├── ui/
│   │   │   └── MainActivity.kt       ← Màn hình chính
│   │   └── utils/
│   │       ├── SpeechManager.kt      ← Nhận dạng giọng nói
│   │       └── TtsManager.kt         ← Đọc bản dịch
│   ├── res/
│   │   ├── layout/activity_main.xml  ← Giao diện
│   │   └── values/                   ← Màu sắc, theme
│   └── AndroidManifest.xml           ← Quyền & cấu hình
└── README.md                         ← File này
```

---

## 🔧 Nâng cấp sau này

Muốn chất lượng dịch tốt hơn? Thay MyMemory bằng:

**Claude API** (trả phí, chất lượng cao nhất):
```kotlin
// Trong TranslatorApi.kt, thay hàm translate():
val url = "https://api.anthropic.com/v1/messages"
// Thêm header: "x-api-key": "YOUR_KEY"
```

**Google Translate** ($300 credit miễn phí):
```kotlin
val url = "https://translation.googleapis.com/language/translate/v2?key=YOUR_KEY"
```

---

## ❓ Lỗi thường gặp

| Lỗi | Giải pháp |
|-----|----------|
| "Gradle sync failed" | File → Invalidate Caches → Restart |
| App không nhận giọng | Kiểm tra quyền Microphone trong Cài đặt |
| Không dịch được | Kiểm tra kết nối internet |
| Tai nghe không nghe | Đảm bảo tai nghe đang kết nối trước khi bật app |
| "Device not found" | Bật USB Debugging, thử cắm lại cáp |
