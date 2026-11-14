# AUTU-LIO — Thiết kế màn hình 02: Quản lý kịch bản

**Trạng thái:** Đặc tả giao diện để duyệt.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.  
**Liên quan:** Admin Studio, chỉ có trong bản dành cho chủ ứng dụng.

## 1. Mục tiêu
Cho phép chủ ứng dụng xem, tìm, phân loại, tạo mới, chỉnh sửa, kiểm thử và chuẩn bị phát hành các kịch bản. Màn hình danh sách phải dễ dùng trên điện thoại nhưng vẫn hỗ trợ số lượng kịch bản lớn.

## 2. Bố cục

### A. Thanh đầu trang
- Tiêu đề: **Kịch bản**.
- Dòng mô tả: **Tạo, quản lý và phát hành kịch bản Auto Click**.
- Nút hành động chính **+ Tạo kịch bản**.
- Nút tìm kiếm hoặc ô tìm kiếm có thể mở rộng.

### B. Thống kê nhanh
Hiển thị dạng hàng cuộn ngang hoặc 3 ô nhỏ:
- **Tất cả**
- **Bản nháp**
- **Đã phát hành**
Số lượng phải lấy từ dữ liệu thật khi backend có; khi chưa kết nối thì dùng dấu gạch ngang và nhãn trạng thái thiết kế, không tự tạo số liệu.

### C. Tìm kiếm và bộ lọc
- Tìm theo tên kịch bản, mô tả hoặc mã định danh nội bộ.
- Bộ lọc trạng thái: Tất cả / Bản nháp / Đang kiểm thử / Đã phát hành / Đã lưu trữ.
- Bộ lọc danh mục do Admin đặt.
- Sắp xếp: cập nhật gần nhất, tên A–Z, ngày tạo.
- Có nút xóa bộ lọc.
- Bộ lọc phải dễ thao tác bằng một tay và không chiếm quá nhiều chiều cao.

### D. Danh sách kịch bản
Mỗi hàng/thẻ hiển thị:
- Tên kịch bản.
- Mô tả ngắn.
- Trạng thái bằng chữ và biểu tượng; màu chỉ là bổ trợ.
- Phiên bản hiện tại.
- Lần chỉnh sửa gần nhất.
- Nhãn danh mục.
- Nút menu thao tác.

Menu thao tác:
- Mở / chỉnh sửa.
- Nhân bản.
- Chạy thử.
- Xem lịch sử phiên bản.
- Chuẩn bị phát hành hoặc phát hành khi đủ điều kiện.
- Lưu trữ.

Không cho xóa vĩnh viễn nếu kịch bản đang được bản phát hành hoặc khách hàng sử dụng mà chưa xử lý ảnh hưởng. Các hành động phá hủy cần xác nhận.

### E. Trạng thái rỗng
Khi chưa có kịch bản:
- Biểu tượng minh họa trung tính.
- Tiêu đề: **Chưa có kịch bản nào**.
- Mô tả: **Tạo kịch bản đầu tiên để bắt đầu xây dựng thư viện Auto Click của bạn.**
- Nút: **Tạo kịch bản đầu tiên**.

### F. Trạng thái khác
- Đang tải: skeleton thẻ, giữ nguyên thanh tìm kiếm.
- Không tìm thấy: **Không tìm thấy kịch bản phù hợp**, kèm nút xóa bộ lọc.
- Mất kết nối: hiển thị dữ liệu lưu gần nhất cùng thời điểm cập nhật.
- Lỗi tải: mô tả ngắn, nút thử lại và không xóa trạng thái lọc người dùng.

## 3. Luồng tạo kịch bản
Chạm **Tạo kịch bản** sẽ mở biểu mẫu gọn:
1. Tên kịch bản (bắt buộc).
2. Mô tả (không bắt buộc).
3. Danh mục (có thể bỏ qua).
4. Tạo bản nháp.

Sau khi tạo bản nháp, mở Trình soạn thảo. Việc tạo bản nháp không có nghĩa là đã phát hành cho khách hàng.

## 4. Luồng phát hành
- Chỉ kịch bản đã qua kiểm tra hợp lệ mới được đưa vào quy trình phát hành.
- Trước khi phát hành, hiển thị tên, phiên bản, ghi chú cập nhật và phạm vi người dùng nhận được.
- Có bản xem trước và xác nhận cuối cùng.
- Lưu lịch sử phát hành và cho phép quay về phiên bản trước khi tương thích.
- Tách biệt rõ **Lưu bản nháp**, **Kiểm thử** và **Phát hành**.

## 5. Phong cách
- Dùng chung bộ màu AUTU-LIO Admin: nền xanh than, thẻ xanh đen, nhấn xanh lam và xanh ngọc.
- Danh sách ưu tiên mật độ thông tin vừa phải, không dùng thẻ quá cao.
- Trạng thái hiển thị bằng nhãn chữ rõ ràng; không chỉ dựa vào màu.
- Nút tạo mới luôn dễ tìm nhưng không che nội dung.
- Hỗ trợ dark/light mode, tiếng Việt/tiếng Anh và màn hình nhỏ.

## 6. Điều hướng
- Vào từ tab **Kịch bản** ở thanh điều hướng dưới.
- Mở một mục sẽ đi tới Trình soạn thảo hoặc trang chi tiết.
- Nút quay lại đưa người dùng về danh sách và giữ truy vấn, bộ lọc, vị trí cuộn khi có thể.
- Điều hướng không được hiển thị trong ứng dụng AutoClick dành cho khách hàng.

## 7. Tiêu chí duyệt
- Có thể tìm và lọc kịch bản nhanh.
- Nhìn rõ trạng thái bản nháp và bản đã phát hành.
- Không thể vô tình phát hành từ thao tác lưu.
- Không làm mất nội dung khi điều hướng hoặc tải lỗi.
- Mọi thao tác xóa/lưu trữ/phát hành có phản hồi và xác nhận phù hợp.
- Giao diện khách hàng không có màn hình hay thao tác quản trị kịch bản.

## 8. Phạm vi chưa làm
- Chưa viết Kotlin / Jetpack Compose.
- Chưa triển khai engine chạy kịch bản hoặc ngôn ngữ lập trình riêng.
- Chưa kết nối cơ sở dữ liệu, API hay Cloudflare.
- Chưa build APK.
