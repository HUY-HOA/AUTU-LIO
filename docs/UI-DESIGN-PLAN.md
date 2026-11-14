# AUTU-LIO — Định hướng thiết kế giao diện

## 1. Phạm vi
- Sản phẩm chỉ phát triển cho Android.
- Prototype giao diện Android bằng Kotlin/Jetpack Compose đã được triển khai để kiểm tra bố cục và điều hướng; Google OAuth, Cloudflare, thanh toán và backend chưa được tích hợp.
- Chưa build APK trong giai đoạn duyệt thiết kế.
- Ngôn ngữ mặc định: tiếng Việt. Nếu ngôn ngữ thiết bị là tiếng Anh thì dùng tiếng Anh; ngôn ngữ khác dùng tiếng Anh.

## 2. Cấu trúc ứng dụng đề xuất
Dùng chung một APK, bộ nhận diện và thành phần giao diện. Các khu vực Admin và AutoClick được hiển thị theo vai trò tài khoản đã được máy chủ xác minh; bản Debug chỉ có bộ chọn preview để kiểm tra bố cục.
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

## 6. Thứ tự triển khai
1. Đã dựng prototype trang Tổng quan Admin và thanh điều hướng.
2. Đã dựng prototype quản lý kịch bản và trình soạn thảo.
3. Đã dựng các trang quản trị Khách hàng, Thiết bị, Giấy phép, Thư viện, Phát hành, Nhật ký và Cài đặt.
4. Đã dựng luồng xem trước khách hàng gồm Trang chủ, danh sách/chi tiết kịch bản mẫu, Thiết bị/Giấy phép và Cài đặt.
5. Tiếp theo: kiểm thử build APK debug, sửa lỗi giao diện theo phản hồi trên thiết bị thật.
6. Sau khi prototype ổn định, triển khai dữ liệu cục bộ, backend, xác thực, giấy phép và runtime theo từng giai đoạn.

## 7. Nguyên tắc bảo mật và quyền riêng tư
- Giao diện khách hàng không cung cấp đường vào Admin Studio hoặc trình soạn thảo.
- Chỉ hiển thị dữ liệu quản lý cần thiết và đã thông báo cho người dùng.
- Không thiết kế chức năng thu thập IMEI, số sê-ri, MAC, GPS, nội dung màn hình hoặc dữ liệu riêng tư không cần thiết.
- Các thao tác khóa tài khoản, thu hồi giấy phép và phát hành phiên bản phải có xác nhận phù hợp.

## 8. Trạng thái hiện tại
Prototype giao diện Android đã được viết bằng Kotlin/Jetpack Compose. Một số trang hiện mới có bố cục và trạng thái rỗng, chưa đọc/ghi dữ liệu thật. Các thao tác Chạy thử trên màn hình khách hàng chỉ là mô phỏng giao diện; chưa có engine Auto Click. Chi tiết tiến độ và phần chưa triển khai được ghi tại docs/PROJECT-STATUS.md.
