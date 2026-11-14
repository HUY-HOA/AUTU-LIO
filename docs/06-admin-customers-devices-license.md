# AUTU-LIO — Thiết kế màn hình 06: Khách hàng, Thiết bị và Giấy phép

**Trạng thái:** Đặc tả giao diện để duyệt.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.

## 1. Khách hàng

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

## 2. Thiết bị

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

Mã cài đặt có thể đổi sau khi gỡ/cài lại ứng dụng hoặc xóa dữ liệu. Không dùng IMEI, số sê-ri phần cứng hoặc MAC để nhận diện.

## 3. Giấy phép

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

## 4. Thống kê
- Tổng tài khoản.
- Tổng thiết bị đã đăng ký.
- Thiết bị hoạt động gần đây.
- Phân bố theo hãng/model nếu dữ liệu có.
- Giấy phép còn hạn, sắp hết hạn và hết hạn.
- Thời điểm cập nhật dữ liệu.

Không suy diễn trạng thái online chỉ từ lần đăng nhập cũ; phải định nghĩa cửa sổ hoạt động và hiển thị rõ.

## 5. Quyền riêng tư
- Thông báo cho người dùng về dữ liệu tài khoản và thiết bị được thu thập.
- Thu thập tối thiểu để xác thực, giới hạn thiết bị, cấp phép, bảo mật và hỗ trợ.
- IP chỉ xử lý/lưu khi có mục đích bảo mật hợp lệ, có thời hạn lưu và thông báo phù hợp.
- Không thu thập IMEI, số sê-ri, MAC, GPS, danh bạ, SMS hoặc nội dung màn hình cho chức năng quản lý thông thường.
- Admin chỉ xem được thông tin đúng với nhiệm vụ quản trị; truy cập quan trọng phải ghi log.

## 6. Trạng thái
- Chưa có khách hàng/thiết bị/giấy phép.
- Đang tải.
- Không tìm thấy.
- Mất kết nối với máy chủ.
- Dữ liệu đã cũ, hiển thị thời điểm đồng bộ cuối.
- Thao tác thành công/thất bại.
- Cần xác nhận hành động nhạy cảm.

## 7. Phạm vi chưa triển khai
- Chưa có backend hay dữ liệu thật.
- Chưa tích hợp OAuth hoặc Cloudflare.
- Chưa có thanh toán.
- Chưa viết mã Android.
