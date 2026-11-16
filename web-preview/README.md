# AUTU-LIO Web Mirror

Bản Web này được xây dựng để đối chiếu giao diện AUTU-LIO Android theo mã nguồn hiện tại trên nhánh `main`.

## Luồng màn hình được phản chiếu
- Màn hình chọn 5 vai trò trong Debug preview: Chủ sở hữu, Nhà phát triển, Nhà thiết kế, Người kiểm duyệt và Người dùng.
- Chủ sở hữu: Tổng quan, Kịch bản, Khách hàng, Thiết bị và Thêm.
- Người dùng: Trang chủ, Kịch bản, Thiết bị và Cài đặt.
- Nhà phát triển, Nhà thiết kế và Người kiểm duyệt: workspace riêng theo `CollaboratorWorkspace.kt`.
- Trình soạn thảo AUTU, danh mục mẫu và hướng dẫn theo các màn hình trong mã Android.

## Nguyên tắc dữ liệu
- Không tự tạo khách hàng, thiết bị, giấy phép, gói AUTU hoặc dự án giả để làm đẹp bản xem trước.
- Khi không có phiên đăng nhập thật, màn hình sẽ thể hiện trạng thái chưa xác thực/chưa kết nối.
- Bản Web không đọc được bản nháp đã mã hóa trong bộ nhớ Android Keystore và không tự đồng bộ dữ liệu riêng trên điện thoại.
- Mẫu AUTU được lấy từ danh mục mẫu có trong mã nguồn Android.

## Giới hạn cần phân biệt
Đây là bản phản chiếu giao diện trình duyệt, không phải bản APK chạy bên trong Chrome/Cốc Cốc. Các chức năng phụ thuộc Android như Accessibility Service, cửa sổ nổi, thao tác chạm/vuốt trên ứng dụng khác, Android Keystore và bộ thực thi trên thiết bị chỉ có thể kiểm tra trong APK. Trang Web không tuyên bố các thao tác đó chạy thật.

## Cập nhật bản xem trước
Mã nguồn Web nằm trong nhánh `preview/web-live`. Sau khi sửa mã, cần triển khai lại Cloudflare Worker; sau đó tải lại trang Web để xem phiên bản mới.
