# PHẠM VI TOÀN DIỆN AUTU SCRIPT

**Mục tiêu:** xây dựng AUTU Script thành nền tảng tự động hóa Android đa năng, có ngôn ngữ rõ ràng, runtime an toàn, thư viện thiết bị và công cụ phát triển hoàn chỉnh.

Tài liệu này phân biệt rõ **đã triển khai**, **đang triển khai**, **dự kiến** và **không thể bảo đảm do giới hạn hệ điều hành**. Không được công bố một lệnh là sẵn sàng nếu chưa có parser, runtime, quyền Android, kiểm thử và tài liệu tương ứng.

## Nguyên tắc thiết kế

1. Tên lệnh, từ khóa, hàm dựng sẵn và kiểu dữ liệu viết HOA, tiếng Việt đủ dấu.
2. Lệnh phải có triển khai thực tế; không tạo lệnh chỉ để đủ danh sách.
3. Tác vụ có rủi ro phải có quyền phù hợp, thông báo minh bạch và cơ chế dừng.
4. Ưu tiên xử lý tại thiết bị; không gửi ảnh màn hình, văn bản hoặc dữ liệu riêng tư lên máy chủ nếu người dùng không chủ động cấu hình.
5. Mỗi tính năng phải có giới hạn tài nguyên, lỗi xác định, test unit hoặc test tích hợp và tài liệu.
6. Tính năng phải kiểm tra phiên bản Android, quyền, trạng thái dịch vụ và khả năng của thiết bị trước khi chạy.

## Ma trận tính năng

**Trạng thái mã nguồn hiện tại:** AUTU Script 1.10. Bảng dưới đây được cập nhật theo tài liệu cú pháp 1.10; các khả năng vẫn cần kiểm thử thiết bị thật được ghi riêng, không coi CI là xác nhận trên mọi thiết bị.

| Phân hệ | Phạm vi mục tiêu | Trạng thái |
|---|---|---|
| Ngôn ngữ lõi | Biến, kiểu, biểu thức, điều kiện, vòng lặp, hàm, danh sách, bản đồ | Đã có nền tảng |
| Điều khiển luồng | THỬ/BẮT_LỖI/LUÔN_THỰC_HIỆN, hủy, thời hạn, luồng tác vụ | Đã có try/catch, hủy và timeout; chưa có đa luồng script |
| Chuỗi và dữ liệu | Unicode, JSON lồng nhau, biểu thức chính quy, ngày giờ, mã hóa dữ liệu | Đã có Unicode và JSON lồng nhau; regex/ngày giờ/mã hóa còn thiếu |
| Tệp | Đọc/ghi tệp riêng, thư mục, JSON, CSV, chọn URI người dùng | Đã có kho tệp riêng và JSON; CSV/chọn URI còn thiếu |
| Mạng | HTTP(S), GET/POST/PUT/PATCH/DELETE, header, timeout, JSON, tải lên/tải xuống | Còn thiếu |
| Cơ sở dữ liệu | SQLite, giao dịch, truy vấn tham số, di trú schema | Còn thiếu |
| Trợ năng | Đọc cây giao diện, tìm text/resource ID, click/scroll/set text | Đã có các thao tác text/ID/scroll; chưa có lọc theo mọi thuộc tính node |
| OCR | Đọc màn hình/vùng, tìm chữ, trả tọa độ | Đã có OCR màn hình và vùng; OCR từ tệp ảnh và chờ chữ chuyên dụng còn thiếu |
| Thị giác máy tính | Nhãn ảnh, vật thể, hộp giới hạn, QR/mã vạch, màu, mẫu ảnh | Đã có nhãn ảnh, vật thể cơ bản, QR/mã vạch; màu và so khớp ảnh mẫu còn thiếu |
| Ảnh và khung hình | Chụp màn hình, crop vùng để OCR/nhận diện | Đã có chụp màn hình và crop nội bộ; xử lý ảnh/tệp và so sánh khung hình còn thiếu |
| Tương tác | Chạm, nhấn giữ, vuốt; kéo-thả/đa điểm khi Android cho phép | Đã có chạm/giữ/vuốt; kéo-thả đường cong và đa điểm còn thiếu |
| Tọa độ | Pixel, DP, tỷ lệ màn hình, vùng an toàn, xoay màn hình | Pixel đã có; API tỷ lệ/DP và hiệu chỉnh vùng an toàn còn thiếu |
| Sự kiện | Thay đổi cửa sổ, nội dung, thông báo do người dùng cấp quyền, trạng thái ứng dụng | Còn thiếu |
| Lịch và nền | Lịch chạy, hàng đợi, retry, điều kiện pin/mạng, tác vụ định kỳ và thông báo trạng thái | Còn thiếu |
| Thiết bị | Pin, sạc, mạng, màn hình, ứng dụng foreground và thông số màn hình | Đã có truy vấn cơ bản; âm lượng/rung/clipboard còn thiếu |
| Giao diện kịch bản | Form, nút, ô nhập, danh sách, bảng điều khiển và biến tương tác | Còn thiếu |
| Thư viện | Module, import/export, phiên bản thư viện, tái sử dụng hàm | Còn thiếu |
| Trình soạn thảo | Tô màu, gợi ý, tự hoàn thành, kiểm tra kiểu, mẫu mã, định dạng | Một phần |
| Gỡ lỗi | Breakpoint, chạy từng bước, xem biến, call stack, nhật ký, xuất báo cáo lỗi | Một phần |
| Quản lý chạy | Chạy/tạm dừng/tiếp tục/dừng và cửa sổ nổi | Đã có; hàng đợi nhiều kịch bản và lịch sử chi tiết còn thiếu |
| Kiểm thử | Test parser/runtime, test thiết bị, test quyền, test tương thích Android | Một phần |

## Danh mục API dự kiến

### OCR và văn bản trên màn hình
Đã có: ĐỌC_VĂN_BẢN_MÀN_HÌNH(), MÀN_HÌNH_CHỨA_CHỮ(CHUỖI), TÌM_VỊ_TRÍ_CHỮ(CHUỖI), ĐỌC_VĂN_BẢN_VÙNG(X,Y,RỘNG,CAO), MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG và TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG.

Còn thiếu: đọc ảnh từ URI/tệp, trả về cấu trúc dòng/khối có confidence công khai, chờ chữ xuất hiện/biến mất có timeout riêng.

### Nhận diện hình ảnh
Đã có: nhãn ảnh tổng quát, phát hiện vật thể cơ bản, hộp giới hạn và quét QR/mã vạch trên màn hình.

Còn thiếu: tìm màu/vùng màu, so khớp ảnh mẫu do người dùng cung cấp, so sánh khung hình và API ảnh đầu vào từ tệp/URI. Không tuyên bố nhãn tổng quát là nhận diện chính xác mọi vật thể.

### Trợ năng và điều khiển giao diện
- Tìm theo văn bản, mô tả nội dung, resource ID, loại thành phần và trạng thái.
- Đọc bounds, trạng thái bật/tắt, khả năng click/scroll.
- Click, long-click, nhập văn bản, cuộn đến phần tử.
- Không giả định ứng dụng bên ngoài cho phép đọc hoặc thao tác mọi thành phần.

### Tệp, dữ liệu và mạng
- Chỉ đọc/ghi trong vùng app hoặc URI/thư mục người dùng đã cấp quyền.
- API mạng phải có timeout, giới hạn kích thước, HTTPS mặc định, kiểm tra phản hồi và không ghi log bí mật.
- JSON phải có object/array lồng nhau, kiểu null, boolean, số và chuỗi.
- SQLite dùng tham số truy vấn; không nối chuỗi đầu vào trực tiếp thành SQL.

### Tác vụ nền và sự kiện
- Tác vụ nền phải dùng cơ chế Android phù hợp, tuân thủ giới hạn chạy nền và có trạng thái hiển thị.
- Lịch định kỳ phải nêu rõ khoảng lặp thực tế có thể bị Android trì hoãn.
- Tác vụ có thể hủy; retry có giới hạn và backoff.
- Không tự động khởi chạy hoặc thao tác ứng dụng nhạy cảm ngoài cấu hình rõ ràng của người dùng.

## Quy tắc tương thích và giới hạn

- Ảnh chụp màn hình qua AccessibilityService yêu cầu Android 11/API 30 trở lên và khai báo capability tương ứng. Màn hình bảo mật hoặc ứng dụng chặn chụp có thể không đọc được.
- Cây giao diện trợ năng chỉ cung cấp dữ liệu mà ứng dụng đích phơi bày; canvas/game/custom view có thể không có text node.
- OCR và mô hình ảnh phụ thuộc độ phân giải, độ nét, ngôn ngữ và khả năng thiết bị; kết quả có thể sai và phải kiểm tra trước khi thao tác.
- Android có thể chặn thao tác hệ thống, đa điểm, nền, clipboard hoặc truy cập dữ liệu của ứng dụng khác.
- Không hỗ trợ vượt khóa bảo mật, né cơ chế bảo vệ, trích xuất thông tin xác thực, ghi phím bí mật hoặc giám sát ẩn.

## Tiêu chí hoàn thành một mô-đun

Một mô-đun chỉ được đánh dấu **Đã triển khai** khi có đủ:
1. Cú pháp tiếng Việt viết HOA và kiểm tra tham số.
2. Runtime thực thi thật, không dùng placeholder.
3. Xử lý quyền, API level, lỗi và hủy tác vụ.
4. Giới hạn hiệu năng/bộ nhớ/thời gian.
5. Unit test và/hoặc kiểm thử thiết bị cho luồng thành công, lỗi, thiếu quyền.
6. Cập nhật trợ giúp trong ứng dụng và tài liệu tham chiếu.
7. CI thành công.

## Lộ trình triển khai

1. Hoàn thiện OCR ảnh đầu vào, dòng/khối và chờ chữ.
2. Nhận diện ảnh: màu/vùng màu, ảnh mẫu và so sánh khung hình.
3. Tọa độ tỷ lệ/DP, vùng an toàn, kéo-thả và đánh giá đa điểm theo khả năng Android.
4. HTTP/API có timeout, HTTPS, giới hạn payload và chính sách dữ liệu.
5. SQLite với truy vấn tham số, giao dịch và di trú.
6. Sự kiện, lịch, hàng đợi và tác vụ nền có thể hủy.
7. Module/thư viện dùng chung và regex/ngày giờ.
8. Trình soạn thảo: tô màu, autocomplete, định dạng và debugger từng bước.
9. Kiểm thử thiết bị thật: Xiaomi/HyperOS, Android nhiều API, quyền, vòng đời service, pin và độ phân giải.
10. Rà soát bảo mật, hiệu năng, khả năng tiếp cận và tương thích trước phát hành.

**Ghi chú:** Không có danh sách hữu hạn nào bao quát mọi API OEM, mọi ứng dụng và mọi phiên bản Android. Mục tiêu là bao phủ toàn diện các khả năng công khai, được cấp quyền và có thể kiểm thử trên Android, đồng thời công bố trung thực các giới hạn.
