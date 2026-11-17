# AUTU-LIO — Trạng thái phát triển ứng dụng Android

**Cập nhật:** 17/11/2026  
**Repository:** `HUY-HOA-1/AUTU-LIO`  
**Nhánh tích hợp:** `feature/floating-runner-secure-20261117` (PR #33)

## 1. Mô hình sản phẩm bắt buộc

- Một ứng dụng Android native, một APK: `com.autulio.app`.
- Đúng hai loại tài khoản: OWNER và CUSTOMER.
- OWNER duy nhất: `phunghuyhoahaikba@gmail.com`.
- Mọi tài khoản Google khác luôn được Worker xác định là CUSTOMER.
- Không có cộng tác viên, lời mời, vai trò Developer/Designer/Reviewer, workspace/dự án dùng chung hoặc bộ chọn vai trò xem thử.
- CUSTOMER chỉ có một màn hình thư viện script được cấp phép để tải và chạy.
- Không có website, web dashboard hay web preview. `backend/` là API cho APK Android, không được xóa.

## 2. Những thay đổi đã có trên nhánh tích hợp

### Phân quyền và máy chủ
- Worker chỉ phát hành role OWNER hoặc CUSTOMER; role được tính từ `OWNER_EMAIL`, không tin role client gửi.
- `OWNER_EMAIL` đã được đặt trong `backend/wrangler.jsonc` là `phunghuyhoahaikba@gmail.com`.
- API collaborator, invitation, permission override và shared workspace đã bị gỡ khỏi route.
- Code Android của màn hình cộng tác, màn hình dự án dùng chung và client API cộng tác đã được xóa.
- Migration `0006_single_owner_customer_only.sql` hạ các tài khoản còn lại xuống CUSTOMER, xóa invitation/permission override và xóa các bảng dự án, thành viên, bản nháp workspace và submission.
- Worker test xác nhận các endpoint cộng tác/lời mời/workspace trả 404.

### Giao diện Android
- AuthenticatedAppHost chỉ điều hướng OWNER tới AdminApp; mọi tài khoản khác chỉ vào CustomerApp.
- CustomerApp chỉ còn màn hình thư viện script; đăng ký thiết bị và giấy phép mở trong luồng ngay trên màn hình này, không có tab quản trị riêng.
- OWNER giữ các chức năng quản trị khách hàng, thiết bị, giấy phép, thư viện, phát hành, nhật ký và trình soạn thảo script.
- Đã xóa màn hình cộng tác, dự án dùng chung, client API cộng tác và lời mời trong cài đặt.
- Đã xóa đường mở editor Debug ngoại tuyến; editor phải xác minh session, OWNER và quyền EDIT_SCRIPTS từ máy chủ.

### Trợ năng và cửa sổ nổi
- Khi mở ứng dụng, AuthenticatedAppHost kiểm tra AUTU-LIO đã được bật trong Android Accessibility Settings chưa. Nếu chưa, hiển thị hướng dẫn và mở đúng trang Cài đặt.
- Trước khi mở editor, ứng dụng kiểm tra lại quyền; editor chỉ mở cho OWNER đã xác minh.
- Khi tạo/mở script, AccessibilityService mở cửa sổ điều khiển nổi trong lúc chỉnh sửa.
- Cửa sổ dùng `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`, cho phép hiển thị trên ứng dụng khác mà không cần cấp `SYSTEM_ALERT_WINDOW` riêng.
- Trước khi chuẩn bị script và khi nhấn Chạy/Tiếp tục, quyền được kiểm tra lại. Nếu quyền bị tắt, thao tác bị chặn và Android Settings được mở để cấp lại.
- Editor mã nguồn vẫn là màn hình bảo mật bên trong AUTU-LIO; cửa sổ điều khiển nổi hiển thị trên cùng trong lúc chỉnh sửa và khi chuyển sang ứng dụng mục tiêu.

## 3. Kiểm thử

- GitHub Actions Worker checks và Android source checks đều thành công trên commit `4447cbb8a187d59f04b56b4c7af90572718bbc39` (run `43988019892` và `43988019877`).
- Sau commit đó đã có thêm chỉnh sửa lifecycle quyền Trợ năng, dọn kho nháp cũ, nhật ký và logic mở khóa nút Chạy. Các commit mới nhất chưa có workflow run tương ứng; cần chạy lại source checks trước khi xác nhận nhánh cuối cùng đã biên dịch thành công.
- Chưa có build APK mới cho các thay đổi OWNER/CUSTOMER và permission gate.
- Cần kiểm thử trực tiếp trên Android thật: bật/tắt Accessibility, tạo/sửa script, chuyển sang ứng dụng khác, chạy/tạm dừng/tiếp tục/dừng, thu hồi quyền trong lúc app đang mở và xác nhận cửa sổ nổi trên Xiaomi/HyperOS.

## 4. Điều kiện còn thiếu trước khi dùng thực tế

1. Deploy Worker `autu-lio-auth-api` từ `backend/` và cấu hình Google OAuth secrets.
2. Áp dụng các D1 migrations còn thiếu, bao gồm `0006_single_owner_customer_only.sql`; xác nhận email OWNER duy nhất và các bảng cộng tác đã bị xóa.
3. Cấu hình URL Auth API và Google Server Client ID cho Android; thử đăng nhập Owner và một tài khoản Customer thật.
4. Kiểm tra license, đăng ký thiết bị RSA, tải gói script đã mã hóa và kiểm soát trạng thái license.
5. Chạy Android source checks, Worker tests và kiểm thử cửa sổ nổi/gesture trên thiết bị thật.
6. Chỉ tạo APK phát hành mới khi chủ ứng dụng gửi đúng cụm `BUILD APK`.

## 5. Bản Release cũ

GitHub Actions run [43972068339](https://github.com/HUY-HOA-1/AUTU-LIO/actions/runs/43972068339) đã tạo `AUTU-LIO-v0.1.10.apk`, Release đã ký và chữ ký v2 hợp lệ. APK đó được build **trước** các thay đổi một OWNER/CUSTOMER-only và permission gate hiện tại; không dùng nó để xác nhận những thay đổi mới đã có trong APK.

## 6. Quy tắc

- Không báo chức năng đã hoạt động khi mới có mã nguồn nhưng chưa deploy/test thực tế.
- Không cấp quyền OWNER bằng màn hình chọn vai trò hoặc cờ local/debug.
- Không xóa Cloudflare API/D1 cần thiết cho Android; không tạo lại website.
- Không tuyên bố APK mới đã có nếu chưa có workflow thành công và artifact tải được.
