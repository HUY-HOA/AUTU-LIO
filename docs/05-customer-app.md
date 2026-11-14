# AUTU-LIO — Thiết kế ứng dụng khách hàng AutoClick

**Trạng thái:** Đặc tả giao diện để duyệt.  
**Đối tượng:** Khách hàng sử dụng ứng dụng.  
**Quyền:** Chỉ sử dụng chức năng và kịch bản do chủ ứng dụng phát hành.

## 1. Nguyên tắc phân tách
- Bản khách hàng không có Admin Studio, trình soạn thảo, trình biên dịch hoặc màn hình quản trị.
- Người dùng không được xem/sửa mã nguồn kịch bản.
- Chỉ hiển thị danh sách tính năng và kịch bản đã được cấp quyền.
- Giao diện cần rõ ràng với người không có kiến thức lập trình.

## 2. Danh sách màn hình

### A. Đăng nhập / Kích hoạt
- Logo AUTU-LIO.
- Giới thiệu ngắn về ứng dụng.
- Nút đăng nhập Google khi OAuth được triển khai.
- Khu vực nhập mã kích hoạt nếu chính sách cấp phép sử dụng phương thức này.
- Liên kết Điều khoản và Chính sách quyền riêng tư.
- Trạng thái đang đăng nhập, lỗi, chưa có mạng và tài khoản chưa được cấp phép.
- Không yêu cầu quyền Android không cần thiết trên màn hình đăng nhập.

### B. Trang chủ
- Lời chào.
- Trạng thái giấy phép: đang hoạt động / sắp hết hạn / hết hạn / bị khóa.
- Trạng thái dịch vụ cần thiết cho tự động hóa.
- Kịch bản nổi bật hoặc dùng gần đây.
- Nút mở danh sách kịch bản.
- Thông báo cập nhật mới nếu có.
- Không hiển thị số liệu giả khi chưa kết nối.

### C. Kịch bản
Mỗi thẻ hiển thị:
- Tên kịch bản.
- Mô tả do Admin cung cấp.
- Biểu tượng hoặc ảnh đại diện.
- Phiên bản.
- Trạng thái tương thích và quyền sử dụng.
- Nút xem chi tiết.

Không hiển thị code, hàm nội bộ, khóa hoặc thông tin kỹ thuật không dành cho khách hàng.

### D. Chi tiết kịch bản
- Tên, mô tả, phiên bản.
- Hướng dẫn sử dụng và lưu ý.
- Những quyền Android cần thiết cho kịch bản.
- Nút **Chạy** khi đã đủ điều kiện.
- Nút **Cập nhật** nếu có phiên bản mới.
- Thông báo tương thích nếu thiết bị không đáp ứng.
- Không tự cấp quyền hệ thống; hướng dẫn người dùng đi đến màn hình Android phù hợp.

### E. Bảng điều khiển chạy
- Tên kịch bản đang chạy.
- Trạng thái: sẵn sàng / đang chạy / tạm dừng / đã dừng / lỗi.
- Nút **Chạy**, **Tạm dừng**, **Tiếp tục**, **Dừng**.
- Hiển thị tiến trình nếu kịch bản cung cấp tiến trình.
- Nhật ký hiển thị cho người dùng ở mức dễ hiểu, không chứa dữ liệu riêng tư hoặc nội dung mã nguồn.
- Có đường dẫn trợ giúp khi xảy ra lỗi.

### F. Tải xuống và cập nhật
- Danh sách gói đã cài.
- Phiên bản hiện tại và phiên bản mới.
- Dung lượng tải nếu có.
- Trạng thái tải / xác minh / cài đặt.
- Hiển thị lỗi và nút thử lại.
- Không thực thi package khi chữ ký hoặc tính toàn vẹn không hợp lệ.

### G. Thiết bị và Giấy phép
- Email/tài khoản đang đăng nhập.
- Gói giấy phép.
- Ngày bắt đầu và hết hạn.
- Số thiết bị đã liên kết và giới hạn cho phép.
- Thông tin thiết bị hiện tại ở mức cần thiết.
- Nút yêu cầu hỗ trợ hoặc quản lý thiết bị nếu chính sách cho phép.
- Giải thích rõ dữ liệu thiết bị nào được thu thập.

### H. Hỗ trợ và Cài đặt
- Ngôn ngữ: tiếng Việt nếu thiết bị đặt tiếng Việt; tiếng Anh cho tiếng Anh hoặc ngôn ngữ khác.
- Giao diện sáng/tối/theo hệ thống.
- Hướng dẫn sử dụng.
- Báo lỗi có sự đồng ý của người dùng.
- Chính sách quyền riêng tư và điều khoản.
- Thông tin phiên bản ứng dụng.
- Đăng xuất.

## 3. Điều hướng chính
Thanh điều hướng dưới gồm bốn mục:
1. **Trang chủ**
2. **Kịch bản**
3. **Thiết bị**
4. **Cài đặt**

Mục đang chọn phải có nhãn và dấu hiệu trực quan rõ ràng. Không có tab Admin hoặc Editor.

## 4. Xin quyền Android
- Chỉ xin quyền ngay trước khi cần dùng chức năng tương ứng hoặc khi người dùng bắt đầu thiết lập tính năng.
- Giải thích mục đích bằng tiếng Việt/tiếng Anh.
- Nếu người dùng từ chối, giải thích tính năng nào bị ảnh hưởng và cách bật lại trong cài đặt Android.
- Không tự động vượt qua hộp thoại hệ thống hoặc giả định mọi quyền đều đã được cấp.
- Quyền Accessibility phải có màn hình giải thích minh bạch và chỉ dùng cho thao tác tự động hóa được người dùng chủ động cấu hình.

## 5. Quyền riêng tư
- Thu thập dữ liệu tối thiểu để đăng nhập, quản lý thiết bị, giấy phép, đồng bộ và hỗ trợ.
- Không thu thập IMEI, số sê-ri, MAC, GPS, danh bạ, SMS hoặc nội dung màn hình để quản lý thông thường.
- Báo lỗi và nhật ký kỹ thuật phải có thông báo và cơ chế đồng ý phù hợp.
- Cho người dùng xem thông tin tài khoản/thiết bị và yêu cầu hỗ trợ xóa dữ liệu theo chính sách.

## 6. Trạng thái giao diện
- Chưa đăng nhập.
- Đang xác thực.
- Chưa có giấy phép.
- Giấy phép hết hạn / bị khóa.
- Chưa có kịch bản được cấp.
- Đang tải kịch bản.
- Chưa cấp quyền cần thiết.
- Sẵn sàng chạy.
- Đang chạy / tạm dừng / đã dừng.
- Có bản cập nhật.
- Lỗi mạng / lỗi xác minh package.

## 7. Tiêu chí duyệt
- Người dùng có thể tìm và chạy kịch bản mà không cần hiểu code.
- Trạng thái giấy phép và quyền Android luôn rõ ràng.
- Không có đường vào Admin Studio.
- Không ẩn các quyền và hoạt động thu thập dữ liệu.
- Dùng được trên màn hình nhỏ, thao tác một tay và hỗ trợ sáng/tối.
- Có trạng thái lỗi và hướng dẫn khắc phục cho từng bước quan trọng.

## 8. Phạm vi chưa triển khai
- Chưa tích hợp Google OAuth.
- Chưa kết nối API, Cloudflare hoặc cơ sở dữ liệu.
- Chưa xây runtime hoặc engine auto click.
- Chưa viết Kotlin / Jetpack Compose.
- Chưa build APK.
