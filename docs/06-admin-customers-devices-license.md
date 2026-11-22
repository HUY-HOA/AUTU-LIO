# AUTU-LIO — Thiết kế màn hình 06: Khách hàng, Thiết bị và Giấy phép

**Trạng thái:** Thiết kế đã triển khai một trang quản lý truy cập thống nhất.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.

## 1. Trang quản lý truy cập thống nhất

Gộp khách hàng, giấy phép và thiết bị vào một màn hình duy nhất với ba bộ lọc nội bộ, ô tìm kiếm và lọc trạng thái. Mỗi hồ sơ có các thao tác nhanh; tải dữ liệu từ API quản trị và làm mới đồng bộ.

- Khóa/mở khóa khách hàng: chấm dứt phiên hiện tại; không tự thu hồi giấy phép hoặc thiết bị khi chỉ tạm khóa.
- Thu hồi tài khoản: chấm dứt phiên, thu hồi giấy phép và thiết bị đang hoạt động. Đây là thu hồi mềm để giữ dấu vết đối soát; không xóa vật lý hồ sơ hoặc nhật ký.
- Giấy phép: cấp mới, gia hạn, điều chỉnh giới hạn thiết bị, tạm dừng/mở lại và thu hồi. Thu hồi hoặc tạm dừng giấy phép không xóa đăng ký thiết bị; thiết bị được giữ trạng thái liên kết nhưng không thể chạy AUTU khi không có giấy phép hợp lệ.
- Thiết bị: xem thông tin đã đăng ký, lý do thu hồi, trạng thái tài khoản/giấy phép và số khe thiết bị đang dùng; chủ ứng dụng có thể thu hồi hoặc mở lại thiết bị.
- Mở lại thiết bị yêu cầu tài khoản ACTIVE, giấy phép còn hiệu lực và số thiết bị đang hoạt động nhỏ hơn giới hạn giấy phép.
- Nếu khách hàng tự gỡ thiết bị, họ có thể đăng ký lại bằng khóa Android Keystore cũ sau khi có giấy phép hợp lệ. Thiết bị bị chủ ứng dụng hoặc tài khoản thu hồi không được tự mở lại; chủ ứng dụng phải thực hiện.
- Các thao tác thu hồi, khóa hoặc mở lại đều cần hộp thoại xác nhận giải thích hậu quả.

## 2. Khách hàng

### Danh sách
Mỗi hồ sơ hiển thị:
- Tên hiển thị và email tài khoản.
- Trạng thái tài khoản.
- Số thiết bị đã liên kết.
- Loại giấy phép và ngày hết hạn.
- Hoạt động gần nhất.
- Tìm kiếm theo tên/email và lọc theo trạng thái.

### Chi tiết khách hàng
- Thông tin tài khoản do khách hàng cung cấp hoặc cho phép ứng dụng lấy.
- Ngày tạo tài khoản và lần đăng nhập gần nhất.
- Danh sách thiết bị được liên kết.
- Lịch sử giấy phép và giao dịch nếu có.
- Nhật ký các thay đổi quản trị liên quan tới tài khoản.
- Nút khóa/mở khóa, thu hồi phiên đăng nhập và mở hồ sơ giấy phép; các hành động ảnh hưởng tài khoản phải xác nhận.

## 3. Thiết bị

### Danh sách thiết bị
Hiển thị:
- Tên hãng và model.
- Phiên bản Android.
- Phiên bản AUTU-LIO.
- Tên/email chủ tài khoản.
- Ngày đăng ký và lần hoạt động gần nhất.
- Trạng thái: hoạt động, không hoạt động gần đây, bị chặn hoặc cần kiểm tra.

### Chi tiết thiết bị
- Mã cài đặt do ứng dụng tự tạo.
- Model, hãng, phiên bản Android và phiên bản app.
- Trạng thái đăng ký với tài khoản.
- Lịch sử liên kết và gỡ liên kết.
- Trạng thái giấy phép áp dụng trên máy.
- Nút gỡ liên kết hoặc chặn thiết bị, có xác nhận.
- Nút mở lại thiết bị đã thu hồi; máy chủ kiểm tra tình trạng khách hàng, giấy phép và số khe trước khi cho phép.

Mã cài đặt có thể đổi sau khi gỡ/cài lại ứng dụng hoặc xóa dữ liệu. Không dùng IMEI, số sê-ri phần cứng hoặc MAC để nhận diện.

## 4. Giấy phép

### Danh sách giấy phép
- Khách hàng sở hữu.
- Gói/loại giấy phép.
- Ngày bắt đầu và ngày hết hạn.
- Giới hạn số thiết bị.
- Trạng thái: còn hạn, sắp hết hạn, hết hạn, tạm khóa hoặc thu hồi.

### Chi tiết và thao tác
- Cấp mới, gia hạn, đổi gói.
- Điều chỉnh giới hạn thiết bị.
- Tạm khóa / mở khóa.
- Thu hồi.
- Lịch sử thao tác và ghi chú quản trị.

Mọi thay đổi cần ghi nhật ký; hiển thị cảnh báo trước khi thu hồi hoặc khóa giấy phép đang được sử dụng.

## 5. Thống kê
- Tổng tài khoản.
- Tổng thiết bị đã đăng ký.
- Thiết bị hoạt động gần đây.
- Phân bố theo hãng/model nếu dữ liệu có.
- Giấy phép còn hạn, sắp hết hạn và hết hạn.
- Thời điểm cập nhật dữ liệu.

Không suy diễn trạng thái online chỉ từ lần đăng nhập cũ; phải định nghĩa cửa sổ hoạt động và hiển thị rõ.

## 6. Quyền riêng tư
- Thông báo cho người dùng về dữ liệu tài khoản và thiết bị được thu thập.
- Thu thập tối thiểu để xác thực, giới hạn thiết bị, cấp phép, bảo mật và hỗ trợ.
- IP chỉ xử lý/lưu khi có mục đích bảo mật hợp lệ, có thời hạn lưu và thông báo phù hợp.
- Không thu thập IMEI, số sê-ri, MAC, GPS, danh bạ, SMS hoặc nội dung màn hình cho chức năng quản lý thông thường.
- Admin chỉ xem được thông tin đúng với nhiệm vụ quản trị; truy cập quan trọng phải ghi log.

## 7. Trạng thái
- Chưa có khách hàng/thiết bị/giấy phép.
- Đang tải.
- Không tìm thấy.
- Mất kết nối với máy chủ.
- Dữ liệu đã cũ, hiển thị thời điểm đồng bộ cuối.
- Thao tác thành công/thất bại.
- Cần xác nhận hành động nhạy cảm.

## 8. Phạm vi còn cần nghiệm thu
- Cần kiểm thử thực tế các luồng khóa/mở, thu hồi tài khoản, gia hạn và giới hạn thiết bị trên máy Android đã đăng nhập OWNER.
- Thu hồi tài khoản hiện là thu hồi mềm để bảo toàn lịch sử quản trị; không phải xóa vật lý dữ liệu.
- Không có luồng thanh toán trong ứng dụng ở giai đoạn hiện tại.
