# AUTU-LIO — Thiết kế giao diện AutoClick dành cho khách hàng

**Trạng thái:** Đặc tả thiết kế để duyệt.  
**Đối tượng:** Người dùng cuối.  
**Nguyên tắc:** Không có IDE, trình soạn thảo, mã nguồn, công cụ biên dịch hoặc quyền Admin.

## 1. Mục tiêu trải nghiệm
Khách hàng đăng nhập/kích hoạt, xem các kịch bản đã được chủ ứng dụng phát hành, đọc hướng dẫn, cấp quyền Android cần thiết và chạy/tạm dừng/dừng thao tác. Giao diện đơn giản, không để lộ cơ chế lập trình nội bộ.

## 2. Điều hướng chính
Thanh điều hướng dưới gồm:
1. **Trang chủ**
2. **Kịch bản**
3. **Đang chạy**
4. **Tài khoản**
5. **Cài đặt**

## 3. Màn hình đăng nhập / kích hoạt
- Logo và tên AUTU-LIO.
- Nút đăng nhập Google (chỉ tích hợp ở giai đoạn backend).
- Tùy chọn nhập mã kích hoạt nếu chính sách cấp phép được duyệt.
- Liên kết điều khoản, chính sách quyền riêng tư và hỗ trợ.
- Trạng thái đang xác thực, lỗi mạng, tài khoản bị khóa, giấy phép hết hạn.
- Không yêu cầu người dùng gửi mật khẩu Google cho ứng dụng.

## 4. Trang chủ
- Lời chào và trạng thái tài khoản.
- Thẻ kịch bản gần đây.
- Kịch bản được chủ ứng dụng đề xuất hoặc ghim.
- Trạng thái dịch vụ trợ năng và quyền cần thiết.
- Thông báo phiên bản mới nếu có.
- Không hiển thị số liệu khách hàng khác hoặc thông tin quản trị.

## 5. Danh sách kịch bản
- Tìm kiếm theo tên.
- Danh mục và bộ lọc.
- Thẻ kịch bản có tên, mô tả, phiên bản và hướng dẫn ngắn.
- Chỉ hiển thị kịch bản đã được phát hành và được cấp quyền.
- Không có nút sửa mã, xem source hoặc tạo kịch bản mới.
- Trạng thái tải, chưa có nội dung, cập nhật và lỗi tải rõ ràng.

## 6. Chi tiết kịch bản
- Tên, mô tả, phiên bản, ngày cập nhật.
- Hướng dẫn sử dụng và cảnh báo quyền cần thiết.
- Nút tải/cập nhật nếu package chưa có trên thiết bị.
- Nút bắt đầu khi đủ điều kiện.
- Không tiết lộ source code hoặc cấu trúc package nội bộ.

## 7. Màn hình đang chạy
- Tên kịch bản đang chạy.
- Trạng thái: sẵn sàng, đang chạy, tạm dừng, đã dừng, lỗi.
- Nút **Chạy**, **Tạm dừng/Tiếp tục**, **Dừng** với vùng chạm lớn.
- Hiển thị tiến trình nếu kịch bản có cung cấp tiến trình.
- Nhật ký ngắn, chỉ gồm thông tin kỹ thuật cần thiết.
- Cơ chế dừng khẩn cấp và xử lý khi Android thu hồi quyền.

## 8. Tài khoản và thiết bị
- Tên/email tài khoản.
- Gói giấy phép, thời hạn và trạng thái.
- Thiết bị hiện tại và danh sách thiết bị đã liên kết nếu chính sách cho phép.
- Phiên bản app.
- Đăng xuất và yêu cầu xóa tài khoản/dữ liệu.
- Không thu thập IMEI, serial, MAC hoặc GPS để nhận diện thiết bị.

## 9. Cài đặt và hỗ trợ
- Ngôn ngữ: tiếng Việt nếu thiết bị dùng tiếng Việt; ngôn ngữ khác mặc định tiếng Anh.
- Giao diện sáng/tối/theo hệ thống.
- Thông báo và tùy chọn chẩn đoán có đồng ý.
- Hướng dẫn cấp quyền Android.
- Báo lỗi với bản xem trước dữ liệu sẽ gửi và tùy chọn gửi.
- Liên hệ hỗ trợ và điều khoản.

## 10. Trạng thái và quyền riêng tư
- Chỉ xin quyền Android đúng thời điểm và giải thích mục đích.
- Không tự động cấp quyền; Android yêu cầu người dùng xác nhận quyền đặc biệt.
- Không gửi log, thông tin thiết bị hoặc IP ngoài mục đích đã thông báo.
- Cho phép xem/xóa dữ liệu tài khoản theo chính sách.
- Khi offline, hiển thị trạng thái kết nối và chính sách grace period nếu sau này được triển khai.

## 11. Tiêu chí duyệt
- Khách hàng có thể tìm, tải và chạy kịch bản mà không cần kiến thức lập trình.
- Ba thao tác chạy/tạm dừng/dừng luôn dễ thấy.
- Quyền Android được giải thích rõ.
- Không xuất hiện chức năng Admin.
- Không có dữ liệu giả làm trạng thái thực.

## 12. Phạm vi chưa triển khai
- Chưa tích hợp Google OAuth, API hoặc Cloudflare.
- Chưa có engine thực thi kịch bản.
- Chưa có dữ liệu giấy phép thực.
- Chưa build APK.
