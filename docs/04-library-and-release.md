# AUTU-LIO — Thiết kế màn hình 04: Thư viện và Phát hành

**Trạng thái:** Đặc tả giao diện để duyệt.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.

## 1. Khu vực Thư viện

### Mục tiêu
Quản lý các hàm dùng chung, nhóm chức năng, mẫu kịch bản và tài nguyên mà Admin muốn tái sử dụng trong nhiều kịch bản.

### Danh sách thư viện
Mỗi mục hiển thị:
- Tên thư viện.
- Mô tả ngắn.
- Phiên bản.
- Trạng thái: bản nháp, đã phát hành, đã lưu trữ.
- Số kịch bản đang sử dụng.
- Lần cập nhật gần nhất.

### Chi tiết thư viện
- Thông tin và tài liệu sử dụng.
- Danh sách hàm / thành phần.
- Tham số và kiểu dữ liệu.
- Phiên bản tương thích runtime.
- Danh sách kịch bản phụ thuộc.
- Lịch sử phiên bản và ghi chú thay đổi.

### Thao tác
- Tạo thư viện.
- Chỉnh sửa bản nháp.
- Nhân bản.
- Kiểm tra tương thích.
- Phát hành phiên bản.
- Lưu trữ.
- Khôi phục phiên bản trước khi tương thích.

Trước khi cập nhật thư viện đang được kịch bản sử dụng, phải kiểm tra ảnh hưởng và cho Admin biết các kịch bản phụ thuộc. Không tự động thay đổi phiên bản của các kịch bản đang hoạt động.

## 2. Khu vực Phát hành

### Trang danh sách bản phát hành
Hiển thị:
- Tên gói/kịch bản.
- Phiên bản.
- Kênh phát hành: thử nghiệm / ổn định.
- Thời điểm phát hành.
- Số thiết bị nhận bản cập nhật nếu có dữ liệu.
- Trạng thái phát hành.

### Luồng tạo bản phát hành
1. Chọn kịch bản hoặc gói thư viện đủ điều kiện.
2. Kiểm tra kết quả phân tích và kiểm thử.
3. Chọn phiên bản.
4. Viết ghi chú cập nhật.
5. Chọn kênh phát hành.
6. Chọn phạm vi nhận cập nhật.
7. Xem trước thông tin gói.
8. Xác nhận phát hành.

### Trạng thái phát hành
- Bản nháp.
- Đang kiểm tra.
- Sẵn sàng phát hành.
- Đang phân phối.
- Đã phát hành.
- Tạm dừng.
- Thất bại.
- Đã thu hồi.

### An toàn
- Có màn hình xác nhận riêng trước khi phát hành.
- Hiển thị rõ gói, phiên bản và phạm vi người nhận.
- Có lịch sử phát hành và người thực hiện.
- Có khả năng tạm dừng phân phối.
- Có quy trình quay lại phiên bản tương thích trước đó.
- Không phát hành khi chữ ký gói hoặc kiểm tra toàn vẹn chưa hợp lệ.

## 3. Thiết kế điều hướng
Trong Admin Studio, mục **Thêm** mở:
- Thư viện.
- Phát hành.
- Giấy phép.
- Nhật ký.
- Cài đặt.

Có thể dùng tab con trong khu vực Thư viện và Phát hành, nhưng không tạo quá nhiều tầng điều hướng.

## 4. Trạng thái cần thiết
- Chưa có thư viện / chưa có bản phát hành.
- Đang tải.
- Không tìm thấy theo bộ lọc.
- Đang kiểm tra tương thích.
- Có cảnh báo phụ thuộc.
- Sẵn sàng phát hành.
- Phát hành thành công.
- Phát hành thất bại.
- Đang phân phối.
- Mất kết nối.

## 5. Tiêu chí duyệt
- Dễ phân biệt thư viện dùng chung với kịch bản độc lập.
- Dễ kiểm tra phụ thuộc và tương thích.
- Luồng phát hành có bước xem lại và xác nhận.
- Lịch sử phiên bản rõ ràng.
- Không thể nhầm thao tác lưu bản nháp với phát hành chính thức.
- Không xuất hiện công cụ quản trị này trong APK khách hàng.

## 6. Phạm vi chưa triển khai
- Chưa có hệ thống package, ký số hoặc mã hóa.
- Chưa tạo kho phân phối.
- Chưa tích hợp Cloudflare R2 / Workers / D1.
- Chưa viết Kotlin / Jetpack Compose.
- Chưa build APK.
