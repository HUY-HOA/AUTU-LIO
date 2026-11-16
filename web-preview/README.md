# AUTU-LIO Web Preview

Bản xem trước này được dựng để đối chiếu giao diện với mã Android trên nhánh `main`. Danh mục màn hình được ghi lại tại `ANDROID-SCREEN-MAP.md`.

## Luồng mở ứng dụng

- Màn hình đầu tiên mô phỏng `AuthScreen` của bản Android Release: đăng nhập Google để máy chủ xác minh vai trò.
- Nút đăng nhập hiện bị khóa vì Auth API URL và Google Server Client ID chưa được cấu hình trong môi trường hiện tại.
- Bộ chọn 5 vai trò chỉ là Debug Preview. Nó giúp xem các giao diện Owner, Developer, Designer, Reviewer và Customer; không phải cách cấp quyền hay đăng nhập thật trong bản Release.

## Giao diện Android được phản chiếu

- **Chủ sở hữu:** Tổng quan, AUTU/kịch bản, Khách hàng, Thiết bị, Thêm; trong Thêm có Cộng tác viên, Dự án cộng tác, Giấy phép, Thư viện mẫu AUTU, Gói AUTU, Phát hành, Nhật ký và Cài đặt.
- **Người dùng:** Trang chủ, Kịch bản, chi tiết kịch bản mẫu, Thiết bị và giấy phép, Cài đặt và nhập mã lời mời.
- **Nhà phát triển:** Không gian nhà phát triển, danh sách bản nháp riêng, mở trình soạn thảo riêng, quyền sửa/kiểm thử/gửi duyệt và dự án cộng tác.
- **Nhà thiết kế:** Không gian thiết kế, xem trước giao diện, chọn màu nhấn, quyền thiết kế/xem trước/gửi duyệt và dự án cộng tác.
- **Người kiểm duyệt:** Hàng đợi gửi duyệt, quyền kiểm thử/duyệt và dự án cộng tác.

## Trạng thái cần phân biệt

- Trên Android, bản nháp Chủ sở hữu và Nhà phát triển được lưu cục bộ riêng; bản nháp Nhà phát triển được mã hóa bằng Android Keystore.
- Web preview chỉ dùng trạng thái trong phiên trình duyệt. Nó không đọc hoặc ghi kho bản nháp Android.
- Android đã có `ScriptCompiler`, `ScriptExecutionEngine`, `AutoClickAccessibilityService` và cửa sổ chạy nổi. Gói kịch bản mẫu của Customer vẫn chỉ là mẫu giao diện, không có lệnh tự động hóa thật.
- Các màn hình quản lý khách hàng, thiết bị, giấy phép, package và dự án cần phiên đăng nhập hợp lệ cùng API/Worker được cấu hình. Web preview không dùng dữ liệu tài khoản thật.

## Giới hạn

Đây không phải APK và không phải bản Web vận hành backend. Web không đăng nhập Google, không gọi Cloudflare Worker API, không chạy ScriptCompiler Android, không khởi chạy AccessibilityService và không thực hiện chạm/vuốt trên ứng dụng khác. Các nút mô phỏng chỉ thay đổi giao diện hoặc hiện thông báo.

Các màn hình Web được cập nhật thủ công sau khi đối chiếu mã Kotlin; không tự động sinh từ Compose. Vì vậy, mỗi thay đổi điều hướng hoặc UI Android phải được rà soát và đồng bộ lại tại đây.

## Kiểm tra và triển khai

GitHub Actions `.github/workflows/web-preview-check.yml` kiểm tra cú pháp JavaScript và sự tồn tại của HTML/CSS/JS. Sau khi cập nhật nhánh `preview/web-live`, bản Worker preview cần được triển khai lại thủ công; GitHub push chưa tự động deploy vì Git integration của Cloudflare Pages chưa hoạt động.

## Nguồn Android tham chiếu

- `MainActivity.kt` và `AuthenticatedAppHost.kt`
- `AccessRole.kt` và `CollaboratorWorkspace.kt`
- `CollaboratorsScreen.kt` và `CollaboratorsServerScreen.kt`
- `LiveManagementScreens.kt` và `SharedProjectScreens.kt`
- `ScriptEditorActivity.kt`, `ScriptCompiler.kt`, `ScriptExecutionEngine.kt`
- `AutoClickAccessibilityService.kt`
