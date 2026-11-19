# AUTU-LIO — Trình soạn thảo và ngôn ngữ kịch bản

**Ngôn ngữ chuẩn:** AUTU-LIO Script 0.3  
**Trạng thái:** Parser/validator và runtime core Kotlin đã hợp nhất. Các thao tác Android được gửi qua AccessibilityService. Kiểm thử CI không thay thế kiểm thử thiết bị thật.

## 1. Mục tiêu và quy tắc
Trình soạn thảo dành cho vai trò được cấp quyền tạo/kiểm thử kịch bản. Người dùng AUTU chỉ nhận và chạy package được phát hành. Ngôn ngữ chuẩn dùng từ khóa tiếng Việt VIẾT HOA, ĐẦY ĐỦ DẤU. Tên biến do người viết tự đặt. Bí danh tiếng Anh cũ chỉ để tương thích.

## 2. Quy trình
1. Viết và lưu bản nháp.
2. Kiểm tra cú pháp, tham số và ngân sách.
3. Chuẩn bị chạy; ứng dụng yêu cầu dịch vụ Trợ năng.
4. Người dùng tự chuyển sang ứng dụng mục tiêu và nhấn Chạy trên cửa sổ nổi.
5. Có thể Tạm dừng, Tiếp tục hoặc Dừng.
6. Kiểm tra lại và phát hành package theo quyền được cấp.

## 3. Lệnh hiện có
`ĐẶT`, `NẾU`, `NGƯỢC_LẠI_NẾU`, `NGƯỢC_LẠI`, `LẶP`, `LẶP_KHI`, `LẶP_ĐẾN_KHI`, `CHỜ_ĐẾN_KHI`, `THOÁT_LẶP`, `BỎ_QUA_LƯỢT`; các giá trị `ĐÚNG`, `SAI`; toán tử `VÀ`, `HOẶC`, `KHÔNG`.

Lệnh Android: `CHẠM`, `NHẤN_GIỮ`, `VUỐT`, `CHỜ`, `MỞ_ỨNG_DỤNG`, `QUAY_LẠI`, `VỀ_MÀN_HÌNH_CHÍNH`, `ỨNG_DỤNG_GẦN_ĐÂY`, `MỞ_THÔNG_BÁO`, `MỞ_CÀI_ĐẶT_NHANH`, `HIỆN_MENU_NGUỒN`, `KHÓA_MÀN_HÌNH`, `CHIA_MÀN_HÌNH`.

Hàm dữ liệu và thiết bị được liệt kê trong tài liệu tham chiếu ngôn ngữ.

## 4. Giới hạn an toàn
- Mã tối đa 32.768 ký tự; tối đa 1.000 câu lệnh; lồng nhau tối đa 8 cấp.
- LẶP tối đa 100 lượt; LẶP_KHI tối đa 900 lượt.
- Tối đa 5.000 lệnh sau khi mở rộng vòng lặp; thời lượng tĩnh tối đa 10 phút.
- Runtime kiểm tra lại AST và metadata trước khi chạy.
- Tọa độ phải nằm trong màn hình thật; gesture bị từ chối hoặc dịch vụ mất kết nối phải báo lỗi.
- Không tự cấp quyền Android, không đọc nội dung cửa sổ, không thực thi mã tùy ý.

## 5. Chưa hoàn tất
Hàm do người dùng định nghĩa, lớp/đối tượng, mảng/bản đồ, OCR, nhận diện ảnh, tọa độ tương đối, sự kiện nền, đa điểm, debugger từng bước, gợi ý mã và thư viện dùng chung chưa được triển khai đầy đủ. Không hiển thị các tính năng này như đã sẵn sàng.

## 6. Tài liệu chính
Xem [NGÔN NGỮ AUTU-LIO — CÚ PHÁP CHUẨN](08-AUTU-LANGUAGE-REFERENCE.md) để biết kiểu dữ liệu, toán tử, từng lệnh, hàm, giới hạn và ví dụ. Mọi lệnh mới phải có parser, runtime, kiểm tra quyền, test và xác minh thiết bị trước khi công bố.
