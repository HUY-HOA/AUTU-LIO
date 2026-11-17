# AUTU-LIO — Một chủ sở hữu, khách hàng chỉ sử dụng script

## 1. Quy tắc tài khoản

AUTU-LIO phát hành một ứng dụng Android và một APK duy nhất với application ID `com.autulio.app`.

Chỉ có hai loại tài khoản:

| Tài khoản | Quyền |
|---|---|
| OWNER — `phunghuyhoahaikba@gmail.com` | Toàn quyền quản trị, tạo/sửa/kiểm tra script, quản lý khách hàng, thiết bị, giấy phép, thư viện và phát hành |
| CUSTOMER — mọi tài khoản Google khác | Chỉ thấy màn hình thư viện script đã được cấp phép; có thể tải và chạy script |

Không tạo thêm OWNER, Developer, Designer, Reviewer hay tài khoản cộng tác viên. Không có lời mời, phân quyền cộng tác viên, dự án dùng chung hoặc bộ chọn vai trò xem thử.

## 2. Phân quyền ở máy chủ

- Worker dùng biến `OWNER_EMAIL` được đặt bằng Gmail Owner.
- Khi đăng nhập và đọc session, Worker so sánh email Google đã xác minh với `OWNER_EMAIL`: trùng thì role OWNER; mọi trường hợp khác là CUSTOMER.
- Permission không đọc từ các bảng ghi đè quyền. OWNER và CUSTOMER nhận danh sách quyền cố định theo role.
- API cộng tác viên, invitation, permission override, shared project, draft workspace và review submission không còn được định tuyến.
- Migration `0006_single_owner_customer_only.sql` chuyển tài khoản khác về CUSTOMER và xóa bảng dữ liệu cộng tác cũ.

## 3. Giao diện

### OWNER
- Tổng quan và khu vực quản trị.
- Tạo, lưu, chỉnh sửa, kiểm tra và chuẩn bị chạy script.
- Quản lý khách hàng, thiết bị, giấy phép, thư viện, phát hành và nhật ký.
- Mã nguồn chỉ mở khi session máy chủ xác nhận đúng OWNER và quyền EDIT_SCRIPTS.

### CUSTOMER
- Chỉ một màn hình danh sách script được cấp phép để tải và chạy.
- Không có IDE, editor, màn hình quản trị, dự án, cài đặt vai trò hoặc thao tác phát hành.
- Đăng ký thiết bị/kích hoạt giấy phép nếu cần nằm trong luồng trên màn hình thư viện, không chuyển thành tab chức năng khác.

## 4. Quyền Trợ năng và cửa sổ nổi

1. Khi mở app, hệ thống kiểm tra AUTU-LIO đã được bật trong Android Settings → Accessibility.
2. Nếu chưa bật, ứng dụng dừng luồng vào app và hiển thị nút mở Cài đặt để người dùng chủ động cấp quyền.
3. Trước khi tạo/mở editor, ứng dụng kiểm tra quyền lại; editor cũng xác minh session OWNER và EDIT_SCRIPTS từ máy chủ.
4. Trong lúc tạo/chỉnh sửa script, AccessibilityService mở cửa sổ điều khiển nổi.
5. Cửa sổ dùng `TYPE_ACCESSIBILITY_OVERLAY`, hiển thị phía trên ứng dụng khác. Không cần quyền `SYSTEM_ALERT_WINDOW` riêng với kiểu cửa sổ này.
6. Trước khi chuẩn bị script và khi người dùng nhấn Chạy/Tiếp tục, quyền được kiểm tra lại. Nếu đã bị tắt, ứng dụng chặn thao tác và yêu cầu bật lại trong Accessibility Settings.
7. Nếu quyền Trợ năng bị thu hồi trong lúc script chạy, service dừng/không dispatch thao tác tiếp theo và yêu cầu cấp lại quyền.

Trình soạn thảo mã nguồn là màn hình bảo mật của AUTU-LIO; cửa sổ điều khiển nổi hiển thị trên cùng trong lúc chỉnh sửa và khi chuyển sang ứng dụng mục tiêu. Cửa sổ nổi là bảng điều khiển, không phải một cửa sổ nhập mã độc lập.

## 5. Không sử dụng Firebase

Xác thực, session, khách hàng, thiết bị, giấy phép và gói script dùng Cloudflare Worker + D1. Không sử dụng Firebase Authentication, Firestore, Realtime Database hoặc Firebase Storage.

**Tình trạng vận hành:** mã nguồn API đã có trong repository nhưng Worker AUTU-LIO chưa deploy và Google OAuth chưa cấu hình. Vì vậy chưa thể xác nhận đăng nhập và dữ liệu máy chủ hoạt động cho đến khi deploy/cấu hình và kiểm thử thật.
