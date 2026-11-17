# AUTU-LIO

AUTU-LIO là **ứng dụng Android native**, phát hành dưới dạng một APK duy nhất với application ID `com.autulio.app`. Sản phẩm không có website, web dashboard, web app hoặc trang web xem trước. Không sử dụng Firebase cho xác thực hay dữ liệu ứng dụng.

## Hai loại tài khoản duy nhất

- **Chủ sở hữu:** chỉ Gmail `phunghuyhoahaikba@gmail.com` có quyền OWNER. Chủ sở hữu có toàn quyền quản trị, tạo và sửa script, kiểm tra, quản lý khách hàng, thiết bị, giấy phép, thư viện và phát hành.
- **Khách hàng:** mọi tài khoản Google khác đều là CUSTOMER. Khách hàng chỉ có một màn hình thư viện script được cấp phép để tải và chạy; không có IDE, màn hình quản trị, cộng tác viên, dự án, bộ chọn vai trò hoặc chế độ xem thử.

Worker xác định vai trò từ email Google đã xác minh và cấu hình OWNER_EMAIL; APK không tự chọn hoặc tự cấp vai trò.

## Tạo, chỉnh sửa và chạy script

- Khi vào ứng dụng, AUTU-LIO kiểm tra quyền Trợ năng. Nếu chưa bật, app yêu cầu mở Cài đặt Android để bật AUTU-LIO.
- Khi chủ sở hữu tạo/mở editor, quyền được kiểm tra lại và cửa sổ điều khiển nổi được mở.
- Cửa sổ điều khiển dùng `TYPE_ACCESSIBILITY_OVERLAY`, có thể hiển thị trên các ứng dụng khác và điều khiển Chạy/Tạm dừng/Tiếp tục/Dừng.
- Trước khi chuẩn bị script và mỗi lần nhấn Chạy/Tiếp tục, quyền được kiểm tra lại. Nếu bị thu hồi, thao tác bị chặn và app yêu cầu cấp lại.
- Mã nguồn được chỉnh trong trình soạn thảo bảo mật bên trong AUTU-LIO; bảng điều khiển nổi vẫn nằm trên cùng khi chuyển sang ứng dụng mục tiêu.

## Cấu trúc

- `app/`: ứng dụng Android Kotlin/Jetpack Compose, AUTU Script, runtime và Accessibility Service.
- `backend/`: Cloudflare Worker REST API và D1 migrations phục vụ đăng nhập, phân quyền, khách hàng, thiết bị, giấy phép và gói script. Đây không phải frontend website.
- `docs/`: tài liệu kiến trúc, vận hành, bảo mật và quy trình phát hành.

## Build và phát hành

- Chỉ APK Release được ký bằng keystore phát hành ổn định mới được phân phối cho người dùng.
- APK Debug chỉ dùng nội bộ phát triển, không phải bản phát hành và không dùng để cập nhật đè bản Release.
- Build APK chỉ được thực hiện khi chủ ứng dụng gửi đúng lệnh `BUILD APK`.

Xem [hướng dẫn bảo mật và ký APK](docs/SECURITY-AND-SIGNING.md), [quy trình GitHub](docs/GITHUB-WORKFLOW.md) và [trạng thái dự án](docs/PROJECT-STATUS.md).

## Trạng thái phát triển

Mã nguồn trên nhánh tích hợp đang được điều chỉnh theo mô hình một OWNER và CUSTOMER-only. Các thay đổi chưa được merge vào `main`; Worker AUTU-LIO chưa deploy, Google OAuth chưa cấu hình và migration xóa dữ liệu cộng tác chưa được áp dụng vào D1. Vì vậy chưa thể coi đăng nhập và dữ liệu máy chủ là hoạt động thực tế cho tới khi triển khai và kiểm tra hoàn tất.
