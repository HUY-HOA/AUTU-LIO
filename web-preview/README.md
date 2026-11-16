# AUTU-LIO Web Preview

Bản xem trước trên trình duyệt điện thoại dùng để đối chiếu các vai trò và màn hình với mã Android trên nhánh `main`.

## Mở và đối chiếu

- Màn hình đầu mô phỏng điểm vào Release: Google Sign-In và khôi phục phiên.
- Nút chọn 5 vai trò là chế độ Debug Preview, chỉ để xem bố cục; không cấp quyền và không xác thực tài khoản.
- Sau khi chọn vai trò, dùng nút **Đối chiếu Android** trên thanh đầu trang để xem danh mục màn hình, tên tệp Kotlin và trạng thái triển khai.
- Trang đối chiếu là danh mục kiểm kê; nó không phải màn hình trong APK.

## Các vai trò được phản chiếu

- **Chủ sở hữu:** Tổng quan, AUTU/kịch bản, trình soạn thảo, Khách hàng, Thiết bị, Cộng tác viên, Dự án cộng tác, Giấy phép, Thư viện mẫu, Gói AUTU, Phát hành, Nhật ký và Cài đặt.
- **Nhà phát triển:** Không gian riêng, bản nháp, trình soạn thảo riêng và dự án được giao.
- **Nhà thiết kế:** Không gian thiết kế, chỉnh màu và xem trước UI.
- **Người kiểm duyệt:** Hàng đợi kiểm duyệt và dự án được giao.
- **Người dùng:** Trang chủ, kho AUTU phát hành, thiết bị/giấy phép và cài đặt/mã lời mời.

## Phân biệt trạng thái

- Bản Web chỉ dùng trạng thái trong phiên trình duyệt; không đọc/ghi bản nháp Android.
- Các thao tác Chạy/Tạm dừng/Dừng trong Web chỉ mô phỏng trạng thái; không gọi interpreter Kotlin, AccessibilityService hoặc thao tác ứng dụng khác.
- Android có parser/compiler, interpreter, AccessibilityService và cửa sổ chạy nổi trong mã nguồn. Việc chạy thật cần quyền Android và kiểm thử trên điện thoại.
- Các màn hình cần tài khoản, giấy phép, thiết bị hoặc dữ liệu cộng tác phải được kiểm tra với Worker/API thật; không tạo dữ liệu mẫu giả để thay thế dữ liệu máy chủ.
- Vai trò Debug Preview không phải quyền Release. Quyền thực tế phải do máy chủ xác minh.

## Cập nhật

Mỗi lần thay đổi màn hình hoặc điều hướng Android, phải rà soát `ANDROID-SCREEN-MAP.md`, cập nhật Web tương ứng, chạy kiểm tra cú pháp và triển khai Worker preview. GitHub–Cloudflare Git integration hiện chưa tự động deploy; việc push code không tự cập nhật trang cho đến khi Worker được triển khai lại.

## Kiểm tra

Workflow `.github/workflows/web-preview-check.yml` kiểm tra cú pháp JavaScript và sự tồn tại của HTML/CSS/JS. Đây không phải kiểm thử Android và không tạo APK.
