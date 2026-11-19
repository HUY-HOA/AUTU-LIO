# AUTU-LIO — Một ứng dụng, hai vai trò

## Kiến trúc

AUTU-LIO phát hành một ứng dụng Android và một APK (`com.autulio.app`). Google Credential Manager xác thực; Cloudflare Worker xác minh ID token/session; D1 lưu tài khoản, vai trò, giấy phép, thiết bị, nhật ký và dữ liệu package.

## Vai trò được hỗ trợ

| Vai trò | Phạm vi |
|---|---|
| OWNER | Quản trị khách hàng, giấy phép, thiết bị, gói AUTU và nhật ký |
| CUSTOMER | Dùng AutoClick và package đã được cấp phép; không truy cập chức năng quản trị |

Worker chỉ cấp OWNER cho Google OpenID Connect `sub` khớp chính xác secret `OWNER_GOOGLE_SUB`. Mọi Google account khác nhận CUSTOMER. Android không tự quyết định quyền bằng email hoặc bộ chọn Debug.

## Luồng xác thực

1. Android yêu cầu nonce dùng một lần từ Auth API.
2. Credential Manager lấy Google ID token có nonce.
3. Worker xác minh chữ ký, issuer, audience, expiry, email đã xác minh và nonce.
4. Worker tra cứu tài khoản, xác định OWNER/CUSTOMER và tạo session.
5. Android lưu session được bảo vệ bằng Android Keystore và xác minh lại với `/api/auth/me`.

Không đưa Google client secret, Cloudflare API token, khóa riêng hoặc khóa ký vào APK/Git.

## Quyền package và thiết bị

CUSTOMER chỉ được đăng ký thiết bị khi có license hợp lệ. Worker kiểm tra session, license và thiết bị đã đăng ký trước khi cho tải package. Package được mã hóa AES-GCM; khóa dữ liệu được bọc cho khóa công khai RSA của thiết bị; khóa riêng ở Android Keystore.

OWNER là vai trò duy nhất được quản lý khách hàng, license, thiết bị và phát hành package. Mọi endpoint quản trị phải kiểm tra vai trò tại Worker.

## Những chức năng đã loại khỏi phạm vi hiện tại

Đã xóa khỏi Android và Worker; migration D1 `0006` đã áp dụng lên database và xóa các bảng dữ liệu liên quan:
- Vai trò Developer, Designer và Reviewer.
- Lời mời, chấp nhận/hủy lời mời và quản lý cộng tác viên.
- Permission override theo từng cộng tác viên.
- Dự án dùng chung, thành viên dự án, bản nháp script/UI, submission và quy trình review.

Không có màn hình ẩn hoặc API dự phòng cho các chức năng này. Khi có nhu cầu trong tương lai sẽ thiết kế lại từ đầu.

## ML Kit và AI

Google ML Kit được đóng gói trong Android để OCR, QR/mã vạch, nhãn ảnh và phát hiện vật thể trên thiết bị. Đây là xử lý ML cục bộ; Gemini và AI tạo sinh không được tích hợp. Đọc ảnh màn hình cần Android Accessibility screenshot (Android 11/API 30 trở lên) và có thể bị chặn bởi cửa sổ bảo mật.

## Kiểm chứng trước phát hành

Cần giữ Worker tests, kiểm tra mọi migration D1, Kotlin compilation, unit tests, lint và nghiệm thu trên thiết bị Android thật. Không tạo APK Release nếu chưa nhận đúng lệnh `BUILD APK`.
