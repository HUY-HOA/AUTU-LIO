# AUTU-LIO — Định hướng thiết kế giao diện

## 1. Phạm vi
- Sản phẩm chỉ phát triển cho Android.
- Giai đoạn hiện tại ưu tiên thiết kế giao diện và trải nghiệm; chưa tích hợp Google OAuth, Cloudflare, thanh toán hoặc backend.
- Chưa build APK trong giai đoạn duyệt thiết kế.
- Ngôn ngữ mặc định: tiếng Việt. Nếu ngôn ngữ thiết bị là tiếng Anh thì dùng tiếng Anh; ngôn ngữ khác dùng tiếng Anh.

## 2. Cấu trúc ứng dụng đề xuất
Dùng chung bộ nhận diện và thành phần giao diện, nhưng tách quyền và màn hình thành hai biến thể:
- **AUTU-LIO Admin Studio:** dành cho chủ ứng dụng/quản trị viên; có quản lý, trình soạn thảo và phát hành kịch bản.
- **AUTU-LIO AutoClick:** dành cho khách hàng; chỉ chạy các kịch bản và tính năng đã được chủ ứng dụng phát hành. Không có trình lập trình, không xem hoặc sửa mã nguồn.

## 3. Phong cách giao diện
- Hiện đại, gọn, rõ ràng, tối ưu cho màn hình điện thoại.
- Thiết kế dark mode trước; bố cục và màu sắc phải hỗ trợ light mode.
- Thẻ thông tin, nút chính/phụ và trạng thái có phân cấp thị giác rõ.
- Chữ dễ đọc, vùng chạm đủ lớn, không nhồi quá nhiều thông tin vào một màn hình.
- Có trạng thái loading, empty, error, success và disabled cho các thao tác.
- Không dùng hiệu ứng hoặc màu RGB gây rối mắt; hiệu ứng chỉ dùng khi giúp nhận biết trạng thái.

## 4. Danh sách màn hình Admin Studio
1. Tổng quan: số khách hàng, thiết bị, giấy phép, kịch bản và trạng thái hệ thống.
2. Kịch bản: danh sách, tìm kiếm, phân loại, tạo mới và chỉnh sửa.
3. Trình soạn thảo: khu vực viết mã, danh sách hàm/thư viện, kiểm tra và chạy thử.
4. Thư viện: quản lý hàm, thành phần và tài nguyên dùng chung.
5. Phát hành: phiên bản, ghi chú cập nhật, phạm vi phát hành và quay lại phiên bản trước.
6. Khách hàng: hồ sơ, trạng thái và lịch sử giấy phép.
7. Thiết bị: model máy, phiên bản Android/app, số máy trên tài khoản và lần hoạt động gần nhất.
8. Giấy phép: gói, thời hạn, giới hạn thiết bị, gia hạn và thu hồi.
9. Nhật ký: sự kiện, lỗi và báo cáo kỹ thuật.
10. Cài đặt: ngôn ngữ, giao diện, bảo mật và thông tin ứng dụng.

## 5. Danh sách màn hình AutoClick
1. Đăng nhập / kích hoạt.
2. Trang chủ và trạng thái dịch vụ trợ năng.
3. Kịch bản đã phát hành.
4. Chi tiết kịch bản và hướng dẫn sử dụng.
5. Bảng điều khiển chạy, tạm dừng và dừng.
6. Kịch bản đã tải và cập nhật.
7. Thiết bị và giấy phép.
8. Hỗ trợ, nhật ký lỗi được phép gửi và cài đặt.

## 6. Thứ tự thiết kế
1. Chốt bố cục và phong cách trang Tổng quan Admin.
2. Thiết kế thanh điều hướng Admin và các màn hình quản lý chính.
3. Thiết kế Trình soạn thảo kịch bản.
4. Thiết kế trang chủ và luồng chạy kịch bản của khách hàng.
5. Rà soát kích thước màn hình, trạng thái và khả năng tiếp cận.
6. Sau khi duyệt thiết kế mới triển khai giao diện bằng Kotlin và Jetpack Compose.

## 7. Nguyên tắc bảo mật và quyền riêng tư
- Giao diện khách hàng không cung cấp đường vào Admin Studio hoặc trình soạn thảo.
- Chỉ hiển thị dữ liệu quản lý cần thiết và đã thông báo cho người dùng.
- Không thiết kế chức năng thu thập IMEI, số sê-ri, MAC, GPS, nội dung màn hình hoặc dữ liệu riêng tư không cần thiết.
- Các thao tác khóa tài khoản, thu hồi giấy phép và phát hành phiên bản phải có xác nhận phù hợp.

## 8. Trạng thái hiện tại
Đây là bản định hướng giao diện ban đầu để duyệt. Các màn hình, tên mục và chi tiết tương tác sẽ được hoàn thiện từng bước trước khi viết mã giao diện.
