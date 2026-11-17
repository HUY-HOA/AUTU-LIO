# AUTU-LIO — Bảo mật ứng dụng, quyền Android và ký APK

## 1. Tài khoản và quyền truy cập

- Chỉ `phunghuyhoahaikba@gmail.com` là OWNER.
- Tất cả tài khoản Google khác là CUSTOMER, chỉ dùng thư viện script đã cấp phép.
- Worker xác định role từ email Google đã xác minh và biến `OWNER_EMAIL`; không tin role do APK gửi lên.
- Không có cộng tác viên, invitation, role phụ, local role chooser hoặc đường mở editor ngoại tuyến.
- Mọi thao tác sửa script phải xác minh session OWNER và quyền EDIT_SCRIPTS từ máy chủ.

## 2. Quyền Trợ năng và cửa sổ nổi

AUTU-LIO dùng Android AccessibilityService để dispatch gesture và dựng cửa sổ điều khiển nổi bằng `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.

- Khi vào app, kiểm tra service AUTU-LIO đã được bật trong Android Settings → Accessibility.
- Nếu chưa, ứng dụng yêu cầu người dùng mở Cài đặt và tự bật quyền.
- Trước khi mở trình soạn thảo, quyền được kiểm tra lại.
- Khi tạo/chỉnh sửa script, AccessibilityService hiển thị cửa sổ điều khiển nổi trong lúc editor mở.
- Trước khi chuẩn bị script, khi nhấn Chạy/Tiếp tục và ngay trong service trước khi dispatch, quyền được kiểm tra lại.
- Nếu quyền bị thu hồi, script bị chặn/dừng và người dùng được yêu cầu bật lại quyền.
- `TYPE_ACCESSIBILITY_OVERLAY` là cửa sổ do AccessibilityService sở hữu; thiết kế này không cần cấp `SYSTEM_ALERT_WINDOW` riêng.

Mã nguồn được chỉnh trong Activity editor bảo mật của AUTU-LIO; bảng điều khiển nổi vẫn hiển thị trên cùng khi chuyển sang ứng dụng mục tiêu. Người dùng chỉ nên bật Trợ năng cho AUTU-LIO khi hiểu rằng quyền này cho phép ứng dụng thực hiện thao tác chạm/vuốt theo script do họ chủ động chạy.

## 3. Bảo vệ mã nguồn và dữ liệu

Không thể bảo đảm mã Kotlin/Dex hoặc script đã giải mã trên Android luôn bí mật hoặc không thể dịch ngược. APK phải chứa mã thực thi; thiết bị đã root hoặc bị hook vẫn có thể quan sát dữ liệu trong bộ nhớ.

Các lớp bảo vệ:
1. GitHub repository ở chế độ private và giới hạn quyền truy cập.
2. Release bật R8 để shrink/optimize/obfuscate mã và shrink resources.
3. Không nhúng signing key, mật khẩu hoặc Worker secret vào APK.
4. Editor dùng `FLAG_SECURE` để hạn chế screenshot và hiển thị trong Recent Apps.
5. Bản nháp script cục bộ được mã hóa AES-256-GCM; khóa nằm trong Android Keystore.
6. Package script phát hành được mã hóa AES-256-GCM từng phiên bản; khóa dữ liệu được Worker bọc cho RSA-3072 public key của thiết bị đăng ký.

R8 là làm rối và thu gọn mã, không phải mã hóa tuyệt đối. Khi script được giải mã để chạy, nội dung vẫn xuất hiện trong bộ nhớ tiến trình.

## 4. Phiên đăng nhập gắn với thiết bị
- Khi đăng nhập, Android ký nonce một lần bằng khóa RSA-3072 trong Android Keystore; Worker lưu khóa công khai theo phiên.
- Mọi API có Bearer session phải kèm chữ ký trên method, path/query, thời gian, nonce và SHA-256 body.
- Worker chỉ chấp nhận thời gian lệch tối đa 120 giây và nonce chưa dùng; yêu cầu phát lại bị từ chối.
- Phải áp dụng migration `0007_device_bound_signed_sessions.sql` trước khi deploy. Phiên cũ phải đăng nhập lại.
- Cơ chế này chống sao chép token và phát lại yêu cầu; không chứng minh APK nguyên bản tuyệt đối và không thay thế kiểm thử chống hook.

## 5. Release signing

GitHub Actions cần đủ bốn Repository secrets:

- `AUTU_LIO_RELEASE_KEYSTORE_BASE64`
- `AUTU_LIO_RELEASE_STORE_PASSWORD`
- `AUTU_LIO_RELEASE_KEY_ALIAS`
- `AUTU_LIO_RELEASE_KEY_PASSWORD`

Keystore phải do chủ sở hữu tạo và sao lưu an toàn. Không commit keystore, mật khẩu hoặc private key lên Git. Mất keystore có thể khiến không thể cập nhật đè APK Release đã cài.

Khi đủ secrets, workflow giải mã keystore tạm trên runner, chạy lint Release, assemble Release, xác minh chữ ký bằng apksigner, xóa keystore tạm rồi upload artifact `AUTU-LIO-Release`. Nhánh Release thiếu bất kỳ secret nào phải thất bại; không được hạ xuống Debug.

## 6. Backend và điều kiện phát hành

- Worker AUTU-LIO chưa deploy; Google OAuth và các cấu hình cần thiết chưa hoàn tất.
- Migration `0006_single_owner_customer_only.sql` và `0007_device_bound_signed_sessions.sql` phải được áp dụng vào D1 trước khi deploy Worker.
- D1 sau migration phải chỉ có một OWNER theo email cấu hình; tất cả người khác là CUSTOMER; không còn invitation, permission override hoặc bảng shared-workspace.
- Phải kiểm thử đăng nhập đúng OWNER, đăng nhập CUSTOMER, API phân quyền, license, đăng ký thiết bị, tải package mã hóa và revoke thiết bị.
- Phải kiểm thử trên thiết bị Android thật việc bật/tắt Trợ năng, cửa sổ nổi, gesture, chuyển ứng dụng, dừng khẩn cấp và hành vi Xiaomi/HyperOS.
- Không dùng APK cũ để khẳng định thay đổi single-owner/permission gate đã có trong bản phát hành mới.
