# AUTU-LIO — Thiết kế màn hình 03: Trình soạn thảo ngôn ngữ riêng

**Trạng thái:** Đặc tả giao diện để duyệt.  
**Đối tượng:** Chủ ứng dụng / quản trị viên.  
**Nền tảng:** Admin Studio trên Android.

## 1. Mục tiêu
Tạo môi trường để chủ ứng dụng viết, tổ chức, kiểm tra và chạy thử kịch bản bằng ngôn ngữ lập trình riêng của AUTU-LIO. Đây là công cụ phát triển dành riêng cho Admin, không xuất hiện trong APK khách hàng.

Giao diện cần tối ưu cho màn hình điện thoại, hạn chế phải cuộn ngang và cho phép mở rộng không gian soạn thảo.

## 2. Bố cục chính
### A. Thanh công cụ trên
- Nút quay lại danh sách kịch bản.
- Tên kịch bản và trạng thái lưu: Đã lưu / Có thay đổi chưa lưu.
- Nút lưu bản nháp.
- Nút menu thao tác bổ sung.
- Không cho nút phát hành nằm sát nút lưu để tránh thao tác nhầm.

### B. Khu vực soạn thảo
- Ô nhập mã có font monospace, đánh số dòng và hỗ trợ thụt lề.
- Tô màu cú pháp theo token của ngôn ngữ AUTU-LIO.
- Hiển thị lỗi theo dòng và vị trí khi bộ phân tích cú pháp trả về.
- Tự động lưu bản nháp có kiểm soát; luôn có trạng thái lưu rõ ràng.
- Hỗ trợ hoàn tác, làm lại, chọn tất cả, sao chép và dán.
- Trên màn hình nhỏ, bảng hàm và biến có thể mở bằng bottom sheet để dành diện tích cho mã.
- Không tự chèn hoặc thay đổi mã người dùng mà không có thao tác xác nhận.

### C. Thanh công cụ soạn thảo
- Hoàn tác / Làm lại.
- Tìm kiếm trong mã.
- Chuyển đến dòng.
- Thụt lề / bỏ thụt lề.
- Chèn mẫu lệnh đã chọn.
- Phím tắt phù hợp với bàn phím vật lý nếu được kết nối.

### D. Bảng Hàm và Thư viện
- Nhóm hàm theo danh mục.
- Tìm kiếm tên hàm và mô tả.
- Mỗi hàm có tên, cú pháp, tham số, kiểu dữ liệu, giá trị trả về và ví dụ.
- Chạm một hàm mở chi tiết; nút chèn chỉ đưa mẫu vào vị trí con trỏ khi Admin xác nhận.
- Phân biệt hàm hệ thống, hàm thư viện và hàm tự định nghĩa.

### E. Bảng Biến
- Tên biến.
- Kiểu dữ liệu.
- Phạm vi.
- Giá trị hiện tại trong phiên chạy thử nếu runtime cung cấp.
- Không hiển thị giá trị nhạy cảm hoặc token bí mật.
- Khi chưa chạy thử, hiển thị trạng thái chưa có dữ liệu runtime.

### F. Khu vực Kiểm tra và Chạy thử
Có thể thu gọn/mở rộng ở cạnh dưới:
- Tab **Lỗi**: lỗi cú pháp, lỗi kiểu dữ liệu, lỗi tham số.
- Tab **Nhật ký**: thông tin chạy thử.
- Tab **Kết quả**: trạng thái hoàn thành/thất bại.
- Nút **Kiểm tra mã** chỉ phân tích, không thực thi.
- Nút **Chạy thử** thực thi trong môi trường thử nghiệm được giới hạn.
- Nút **Dừng** ngắt phiên chạy thử.
- Hiển thị rõ khi runtime chưa được tích hợp để không làm người dùng hiểu nhầm rằng mã đã chạy.

## 3. Luồng làm việc
1. Mở một kịch bản hoặc tạo bản nháp mới.
2. Nhập/chỉnh sửa mã.
3. Lưu bản nháp.
4. Chọn **Kiểm tra mã** để nhận kết quả phân tích.
5. Xem lỗi và chạm lỗi để chuyển đến dòng tương ứng.
6. Chạy thử trong môi trường được phép.
7. Lưu lại bản nháp sau khi sửa.
8. Chuyển sang màn hình phát hành riêng khi đã đạt điều kiện.

## 4. Ngôn ngữ lập trình riêng
- Tên hàm, cú pháp, kiểu dữ liệu và thông báo lỗi do AUTU-LIO định nghĩa.
- Hỗ trợ tiếng Việt có dấu trong định danh nếu ngôn ngữ và bộ mã hóa cho phép.
- Bộ gợi ý phải lấy từ danh mục hàm thực tế của runtime, không viết cứng danh sách khác với engine.
- Đặc tả cú pháp và ngữ nghĩa sẽ được chốt riêng trước khi triển khai trình biên dịch/runtime.
- Không thực thi mã tùy ý bằng JavaScript, reflection hoặc lệnh hệ thống không giới hạn.
- Runtime cần sandbox và danh sách quyền được phép cho từng API tự động hóa.

## 5. Trạng thái giao diện
- Đang tải kịch bản.
- Kịch bản trống.
- Đã lưu / chưa lưu.
- Đang phân tích.
- Phân tích thành công.
- Có lỗi theo dòng.
- Đang chạy thử.
- Chạy thử hoàn tất.
- Chạy thử thất bại.
- Runtime chưa sẵn sàng.
- Mất kết nối nhưng vẫn có thể chỉnh sửa bản nháp cục bộ nếu dữ liệu đã được lưu an toàn.

## 6. Bảo vệ dữ liệu và quyền
- Chỉ vai trò Admin được mở trình soạn thảo.
- APK khách hàng không chứa màn hình editor hoặc API sửa mã.
- Không đưa khóa ký, khóa mã hóa gốc hay secret vào nội dung editor.
- Có lưu phiên bản nháp và lịch sử thay đổi.
- Các thao tác xóa hoặc khôi phục phiên bản cần xác nhận.

## 7. Tiêu chí duyệt
- Vùng mã là phần lớn nhất của màn hình.
- Các bảng phụ thu gọn/mở rộng được.
- Dòng lỗi có thể chạm để định vị.
- Phân biệt rõ lưu, kiểm tra, chạy thử và phát hành.
- Giao diện dùng được ở chiều dọc trên điện thoại và có thể mở rộng ở màn hình lớn.
- Không hiển thị tính năng editor trong bản khách hàng.

## 8. Phạm vi chưa triển khai
- Chưa chốt grammar hoặc bộ hàm chính thức.
- Chưa xây compiler, interpreter hoặc runtime.
- Chưa tích hợp thực thi thao tác Android.
- Chưa có code Kotlin / Jetpack Compose.
- Chưa build APK.
