# AUTU-LIO — Thiết kế màn hình 01: Tổng quan Admin Studio

**Trạng thái:** Bản thiết kế để duyệt, chưa phải giao diện Android đã lập trình.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.  
**Nền tảng mục tiêu:** Android, ưu tiên màn hình điện thoại.

## 1. Mục tiêu màn hình
Màn hình Tổng quan là trang đầu tiên sau khi chủ ứng dụng đăng nhập. Trong vài giây, chủ ứng dụng cần biết quy mô hệ thống, trạng thái phát hành, tình trạng dịch vụ và có thể mở nhanh các tác vụ thường dùng.

Màn hình này chỉ dành cho Admin. Không đưa trình soạn thảo, dữ liệu quản trị hoặc lối vào Admin vào bản AutoClick dành cho khách hàng.

## 2. Bố cục từ trên xuống

### A. Thanh tiêu đề
- Logo chữ **AUTU-LIO**.
- Nhãn nhỏ **ADMIN STUDIO**.
- Nút thông báo (chỉ hiển thị khi có thông báo).
- Ảnh đại diện hoặc chữ viết tắt tài khoản quản trị.
- Thanh tiêu đề gọn, không chiếm quá nhiều chiều cao.

### B. Lời chào
- Tiêu đề: **Tổng quan**.
- Dòng phụ: **Quản lý ứng dụng và hoạt động hệ thống**.
- Không tự hiển thị tên cá nhân nếu chưa có hồ sơ.

### C. Thẻ trạng thái hệ thống
Hiển thị trạng thái kết nối backend / dịch vụ phát hành:
- Khi chưa cấu hình: **Chưa kết nối máy chủ** và giải thích đây là môi trường thiết kế.
- Khi hoạt động: **Hệ thống hoạt động**.
- Khi có sự cố: **Cần kiểm tra**, kèm thời điểm và đường dẫn đến nhật ký.
Không dùng trạng thái màu xanh nếu hệ thống chưa được kiểm tra thực tế.

### D. Bốn thẻ thống kê
Xếp thành lưới 2 cột trên điện thoại:
1. **Khách hàng** — tổng số tài khoản.
2. **Thiết bị** — tổng số thiết bị đã đăng ký.
3. **Giấy phép còn hạn** — số giấy phép đang hoạt động.
4. **Kịch bản phát hành** — số kịch bản đang được phân phối.

Trong bản thiết kế hiện tại, các giá trị đều là dấu gạch ngang hoặc số 0 kèm nhãn **Dữ liệu mẫu / Chưa kết nối**. Không tạo số liệu giả làm báo cáo thật.

Mỗi thẻ có một biểu tượng đơn giản, tên rõ ràng, giá trị lớn, mô tả ngắn. Nhấn thẻ sẽ mở trang quản lý tương ứng khi chức năng được triển khai.

### E. Truy cập nhanh
Bốn hành động:
- **Tạo kịch bản**
- **Mở trình soạn thảo**
- **Phát hành phiên bản**
- **Quản lý khách hàng**

Các hành động chưa triển khai phải hiển thị trạng thái chưa khả dụng hoặc mở màn hình giới thiệu; không được giả vờ đã thực hiện.

### F. Hoạt động gần đây
Danh sách tối đa 5 sự kiện mới nhất, ví dụ:
- Kịch bản được phát hành.
- Khách hàng mới đăng ký.
- Thiết bị mới được liên kết.
- Giấy phép được gia hạn.
- Có báo cáo lỗi mới.

Khi chưa có dữ liệu, hiển thị trạng thái rỗng: **Chưa có hoạt động** và giải thích sự kiện sẽ xuất hiện tại đây sau khi hệ thống được kết nối.

### G. Thanh điều hướng dưới
Năm mục chính:
1. **Tổng quan**
2. **Kịch bản**
3. **Khách hàng**
4. **Thiết bị**
5. **Thêm**

Mục **Thêm** mở danh sách: Giấy phép, Thư viện, Phát hành, Nhật ký, Cài đặt. Mục đang chọn phải có nhãn và chỉ báo trực quan rõ ràng.

## 3. Phong cách hình ảnh

### Màu sắc
- Nền tối: xanh than rất đậm (#0B1220).
- Bề mặt thẻ: (#131E2E).
- Viền nhẹ: (#263449).
- Màu nhấn: xanh lam (#4F8CFF).
- Màu phụ: xanh ngọc (#35C6C8).
- Chữ chính: (#F3F6FC).
- Chữ phụ: (#9AA9BD).
- Cảnh báo: vàng cam; lỗi: đỏ.
- Trạng thái không xác định: xám trung tính.

Không dùng hiệu ứng RGB nhấp nháy hoặc gradient quá mạnh. Màu nhấn chỉ dùng để chỉ hành động chính và mục đang chọn.

### Hình dạng và khoảng cách
- Bo góc thẻ: 14–18 dp.
- Khoảng cách giữa các khu vực: 16–20 dp.
- Lề ngang màn hình: 16 dp.
- Vùng chạm tối thiểu: 48 dp.
- Chiều cao thanh điều hướng dưới: khoảng 64–72 dp cộng vùng system inset.
- Chữ tiêu đề rõ, số liệu đủ lớn, phần giải thích không quá nhỏ.

### Chế độ sáng
Dark mode là bản thiết kế chuẩn đầu tiên. Light mode phải dùng cùng cấu trúc, tương phản đạt yêu cầu và không chỉ đảo màu máy móc.

## 4. Trạng thái giao diện phải có
- **Đang tải:** skeleton cho thẻ thống kê và danh sách hoạt động.
- **Chưa có dữ liệu:** thông báo rỗng hữu ích, có hành động tiếp theo.
- **Chưa kết nối:** trạng thái trung tính; không hiển thị số liệu như thật.
- **Lỗi tải dữ liệu:** thông báo ngắn, nút thử lại và đường dẫn đến nhật ký khi thích hợp.
- **Đã kết nối:** hiển thị số liệu cùng thời điểm cập nhật gần nhất.
- **Mất kết nối:** giữ thông tin cuối cùng đã tải và ghi rõ thời điểm dữ liệu, không giả làm dữ liệu trực tiếp.

## 5. Tương tác
- Chạm thẻ thống kê mở màn hình quản lý tương ứng.
- Chạm hành động nhanh mở đúng luồng chức năng.
- Chạm thông báo mở nội dung thông báo.
- Chạm avatar mở hồ sơ quản trị và đăng xuất.
- Chạm **Thêm** mở các tính năng quản trị còn lại.
- Điều hướng giữ trạng thái màn hình khi quay lại nếu phù hợp.

## 6. Tiêu chí duyệt
- Nhìn vào là phân biệt được thương hiệu, khu vực Admin và trang Tổng quan.
- Bốn số liệu quan trọng nằm trong phần đầu màn hình, không cần cuộn nhiều.
- Người dùng mới hiểu trạng thái hệ thống ngay cả khi chưa cấu hình backend.
- Điều hướng bằng một tay thuận tiện trên điện thoại.
- Không có dữ liệu mẫu gây hiểu nhầm là dữ liệu khách hàng thật.
- Không có quyền hoặc tính năng Admin xuất hiện trong ứng dụng khách hàng.

## 7. Phạm vi chưa làm ở bước này
- Chưa lập trình Kotlin / Jetpack Compose.
- Chưa tạo API, cơ sở dữ liệu hoặc đăng nhập Google.
- Chưa cấu hình Cloudflare.
- Chưa build APK.
- Chưa nối các nút với chức năng thật.

**Bước tiếp theo sau khi duyệt:** thiết kế màn hình Kịch bản và thanh điều hướng Admin; sau đó mới thiết kế trình soạn thảo.
