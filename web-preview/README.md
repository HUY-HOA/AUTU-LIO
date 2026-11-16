# AUTU-LIO Web Preview

Bản xem trước này được đối chiếu với mã nguồn Android hiện có trên nhánh `main`.

## Các giao diện được thể hiện

- Bộ chọn Debug Preview: Chủ sở hữu, Nhà phát triển, Nhà thiết kế, Người kiểm duyệt và Người dùng.
- Chủ sở hữu: Tổng quan, Kịch bản, Khách hàng, Thiết bị và Thêm; mục Thêm gồm Cộng tác viên, Dự án cộng tác, Giấy phép, Thư viện mẫu AUTU, Gói AUTU, Phát hành, Nhật ký và Cài đặt.
- Người dùng: Trang chủ, Kịch bản, Thiết bị và Cài đặt; có chi tiết kịch bản minh họa.
- Nhà phát triển: bản nháp cục bộ, mở trình soạn thảo riêng, quyền đã cấp và dự án cộng tác.
- Nhà thiết kế: bản xem trước UI, màu nhấn và quyền đã cấp.
- Người kiểm duyệt: hàng đợi gửi duyệt, quyền kiểm duyệt và dự án cộng tác.

## Giới hạn bắt buộc phải hiểu

Đây là Web preview để so sánh bố cục/điều hướng, không phải app Android và không phải backend đang hoạt động. Nó không đăng nhập Google, không có dữ liệu tài khoản thật, không gọi Cloudflare Worker API, không chạy ScriptCompiler Android, không khởi chạy AccessibilityService và không tạo thao tác chạm/vuốt trên ứng dụng khác. Những form/nút mô phỏng chỉ thay đổi giao diện hoặc hiển thị thông báo.

Số liệu hoặc trạng thái chưa có trong Android được giữ dưới dạng trạng thái trống/chưa kết nối; không bịa dữ liệu khách hàng, thiết bị, giấy phép hay package.

## Kiểm tra

GitHub Actions workflow `.github/workflows/web-preview-check.yml` kiểm tra cú pháp JavaScript và sự tồn tại của các tệp HTML/CSS/JS.

## Nguồn Android tham chiếu

- `MainActivity.kt`
- `AuthenticatedAppHost.kt`
- `CollaboratorWorkspace.kt`
- `LiveManagementScreens.kt`
- `SharedProjectScreens.kt`
- `ScriptExecutionEngine.kt`
- `ScriptCompiler.kt`

Bất cứ khi nào cấu trúc Android thay đổi, cần đối chiếu lại Web preview. Hiện tại việc xuất bản Web lên Cloudflare được thực hiện sau khi cập nhật GitHub branch; GitHub push chưa tự động deploy do Git integration của Pages chưa hoạt động.
