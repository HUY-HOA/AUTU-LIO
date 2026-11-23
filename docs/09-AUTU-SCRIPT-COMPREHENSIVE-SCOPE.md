# PHẠM VI TOÀN DIỆN AUTU SCRIPT

**Mục tiêu:** xây dựng AUTU Script thành nền tảng tự động hóa Android đa năng, có ngôn ngữ rõ ràng, runtime an toàn, thư viện thiết bị và công cụ phát triển hoàn chỉnh.

Tài liệu này phân biệt rõ **đã triển khai**, **đang triển khai**, **dự kiến** và **không thể bảo đảm do giới hạn hệ điều hành**. Không được công bố một lệnh là sẵn sàng nếu chưa có parser, runtime, quyền Android, kiểm thử và tài liệu tương ứng.

## Nguyên tắc thiết kế

1. Tên lệnh, từ khóa, hàm dựng sẵn và kiểu dữ liệu viết HOA, tiếng Việt đủ dấu; dùng tên thể hiện đúng vai trò như BIẾN để khai báo biến và HÀM để định nghĩa hàm.
2. Lệnh phải có triển khai thực tế; không tạo lệnh chỉ để đủ danh sách.
3. Tác vụ có rủi ro phải có quyền phù hợp, thông báo minh bạch và cơ chế dừng.
4. Ưu tiên xử lý tại thiết bị; không gửi ảnh màn hình, văn bản hoặc dữ liệu riêng tư lên máy chủ nếu người dùng không chủ động cấu hình.
5. Mỗi tính năng phải có giới hạn tài nguyên, lỗi xác định, test unit hoặc test tích hợp và tài liệu.
6. Tính năng phải kiểm tra phiên bản Android, quyền, trạng thái dịch vụ và khả năng của thiết bị trước khi chạy.

## On-device ML Kit screen vision

AUTU-LIO includes Google ML Kit on-device vision APIs. The app supports OCR, structured OCR, region OCR, image labels, object detection and bounding boxes, QR scanning and common barcode formats. ML Kit runs locally on the device; there is no Gemini or generative-AI integration. Screen capture depends on Android Accessibility screenshot support (Android 11/API 30 or newer), and secure windows may block capture.

## Ma trận tính năng

**Trạng thái mã nguồn hiện tại:** AUTU Script 1.22. Bảng dưới đây được cập nhật theo tài liệu cú pháp 1.22; các khả năng vẫn cần kiểm thử thiết bị thật được ghi riêng, không coi CI là xác nhận trên mọi thiết bị.

| Phân hệ | Phạm vi mục tiêu | Trạng thái |
|---|---|---|
| Ngôn ngữ lõi | Biến, kiểu, biểu thức, điều kiện, vòng lặp, hàm, danh sách, bản đồ | Đã có nền tảng |
| Điều khiển luồng | THỬ/BẮT_LỖI/LUÔN_THỰC_HIỆN, hủy, thời hạn, luồng tác vụ | Đã có try/catch, hủy và timeout; chưa có đa luồng script |
| Chuỗi và dữ liệu | Unicode, JSON lồng nhau, biểu thức chính quy, ngày giờ, mã hóa dữ liệu | Đã có Unicode, JSON lồng nhau và ngày giờ ISO 8601; regex/mã hóa còn thiếu |
| Tệp | Đọc/ghi tệp riêng, thư mục, JSON, CSV, chọn URI, multipart upload/download | Đã có SAF nhập/xuất từ Cài đặt và CHỌN_TỆP/LƯU_TỆP trực tiếp trong runtime; giới hạn 1 MiB và 120 giây |
| Mạng | HTTPS GET/POST/PUT/PATCH/DELETE/HEAD, header, timeout, giới hạn payload và phản hồi | Đã có HTTPS client giới hạn, multipart upload và download qua HTTPS; redirect bị chặn |
| Cơ sở dữ liệu | SQLite, giao dịch, truy vấn tham số, di trú schema, toàn vẹn, backup/restore | Đã có SQLite nội bộ, SQL allow-list/bind tham số, transaction ghi, di trú tuần tự checksum SHA-256, integrity/foreign-key check và backup/restore ZIP qua SAF có checksum/rollback; còn thiếu nghiệm thu thiết bị thật |
| Trợ năng | Đọc cây giao diện, tìm text/resource ID, click/scroll/set text | Đã có các thao tác text/ID/scroll; chưa có lọc theo mọi thuộc tính node |
| Văn bản giao diện | Đọc cây Trợ năng, tìm nhãn và trả tọa độ | Kết hợp cây Accessibility và OCR ML Kit; nhận diện nhãn ảnh/vật thể, QR/mã vạch trên thiết bị |

| Ảnh và màu màn hình | Lấy mẫu điểm ảnh để đọc màu khi người dùng gọi lệnh | Lấy mẫu RGB và xử lý ảnh ML Kit cục bộ; không dùng AI tạo sinh |
| Tương tác | Chạm, nhấn giữ, vuốt; kéo-thả/đa điểm khi Android cho phép | Đã có chạm/giữ/vuốt và chạm/vuốt đồng thời 2–5 ngón, tối đa 16 điểm mỗi đường; kéo-thả đường cong chuyên biệt còn thiếu |
| Tọa độ | Pixel, DP, tỷ lệ màn hình, vùng an toàn, xoay màn hình | Đã có quy đổi DP/pixel, tỷ lệ toàn màn hình/vùng an toàn và chuyển tọa độ giữa hướng ROTATION_0 với hướng logic hiện tại; lệnh thao tác nhận pixel nguyên; kéo-thả đường cong còn thiếu |
| Sự kiện | Thay đổi cửa sổ/nội dung và trạng thái ứng dụng; kích hoạt một kịch bản đã được người dùng bật theo đúng mã gói ứng dụng ở tiền cảnh | Đã có trigger theo ứng dụng tiền cảnh; sự kiện thông báo, điều kiện nội dung tùy chỉnh và nhiều trigger ghép còn thiếu |
| Lịch và nền | Lịch chạy, hàng đợi, retry, điều kiện pin/mạng, tác vụ định kỳ và thông báo trạng thái | Đã có hàng đợi tuần tự tối đa 10 tác vụ, lưu mã nguồn mã hóa AES-GCM bằng Android Keystore, khôi phục sau khi dịch vụ khởi động lại nhưng yêu cầu người dùng xác nhận; retry opt-in tối đa 2 lần với backoff; hẹn giờ một lần tối đa 24 giờ; trigger tự chạy theo đúng mã gói ứng dụng khi ứng dụng ở tiền cảnh và màn hình mở khóa. Bộ hẹn giờ/trigger chạy trong tiến trình Accessibility Service, không bảo đảm khi tiến trình bị dừng; lịch lặp, điều kiện pin/mạng và tác vụ nền định kỳ còn thiếu |
| Thiết bị | Pin, sạc, mạng, màn hình, ứng dụng foreground và thông số màn hình | Đã có pin/sạc/mạng, trạng thái màn hình, pixel/DP, density, hướng/xoay, package foreground, model/nhà sản xuất/phiên bản Android và trạng thái Keyguard; âm lượng/rung/clipboard còn thiếu |
| Giao diện kịch bản | Form, nút, ô nhập, danh sách, bảng điều khiển và biến tương tác | Còn thiếu |
| Thư viện | Module, import/export, phiên bản thư viện, tái sử dụng hàm | Còn thiếu |
| Trình soạn thảo | Tô màu, gợi ý, tự hoàn thành, kiểm tra kiểu, mẫu mã, định dạng | Một phần |
| Gỡ lỗi | Breakpoint, chạy từng bước, xem biến, call stack, nhật ký, xuất báo cáo lỗi | Một phần |
| Quản lý chạy | Chạy/tạm dừng/tiếp tục/dừng và cửa sổ nổi | Đã có; hàng đợi tuần tự có lưu/khôi phục mã hóa và tự chạy tiếp sau khi kịch bản hoàn tất; lịch sử chi tiết còn thiếu |
| Kiểm thử | Test parser/runtime, test thiết bị, test quyền, test tương thích Android | Một phần |

### Các API màn hình đã nối runtime ở 1.18–1.21

- `CHIỀU_RỘNG_DP()`, `CHIỀU_CAO_DP()`, `TỶ_LỆ_KHUNG_HÌNH()` trả thông số kích thước theo density và pixel.
- `HƯỚNG_MÀN_HÌNH()`, `XOAY_MÀN_HÌNH()`, `GÓC_XOAY_MÀN_HÌNH()` trả hướng và rotation hiện tại.
- `GÓI_ỨNG_DỤNG_ĐANG_MỞ()` trả package gần nhất từ sự kiện AccessibilityService; có thể rỗng nếu chưa có sự kiện.
- `TỌA_ĐỘ_X_VÙNG_AN_TOÀN()`, `TỌA_ĐỘ_Y_VÙNG_AN_TOÀN()` đổi tỷ lệ 0–1 thành pixel trong vùng an toàn; hai hàm `TỶ_LỆ_*_VÙNG_AN_TOÀN()` thực hiện chiều ngược lại.
- `TỌA_ĐỘ_TỪ_HƯỚNG_GỐC(X,Y)` và `TỌA_ĐỘ_VỀ_HƯỚNG_GỐC(X,Y)` chuyển tọa độ giữa ROTATION_0 và hệ trục logic hiện tại. Công thức bao quát thiết bị hướng gốc ngang và cả bốn mã xoay Android.
- Chuyển đổi góc xoay chỉ đổi hệ tọa độ trả về; không đổi tự động các tham số CHẠM/VUỐT. Cần nghiệm thu thực tế trên thiết bị để xác nhận sự tương thích với từng OEM.

### Thông tin thiết bị đã nối runtime ở 1.21

Các hàm HÀNG_SẢN_XUẤT(), MẪU_THIẾT_BỊ(), PHIÊN_BẢN_ANDROID(), MỨC_API_ANDROID(), MÀN_HÌNH_BỊ_KHÓA() và THIẾT_BỊ_ĐƯỢC_BẢO_MẬT() đọc thuộc tính thiết bị/Keyguard cục bộ. Trạng thái bảo mật chỉ được truy vấn; không có hàm bỏ qua hoặc mở khóa bảo mật.

## Danh mục API dự kiến

### Văn bản giao diện qua Trợ năng

Các lệnh đọc văn bản kết hợp cây Android Accessibility với OCR ML Kit chạy cục bộ trên ảnh màn hình. Nhận diện nhãn ảnh, vật thể, QR và mã vạch cũng chạy tại thiết bị; ứng dụng không gửi ảnh/âm thanh lên Gemini hoặc dịch vụ AI tạo sinh.

Các lệnh đã được nối vào AUTU Script:
- `ĐỌC_VĂN_BẢN_VÙNG(X, Y, RỘNG, CAO)`
- `MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG(CHUỖI, X, Y, RỘNG, CAO)`
- `TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG(CHUỖI, X, Y, RỘNG, CAO)`
- `NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()`, `MÀN_HÌNH_CHỨA_NHÃN_ẢNH(CHUỖI)`
- `QUÉT_MÃ_QR_MÀN_HÌNH()`, `QUÉT_MÃ_VẠCH_MÀN_HÌNH()`
- `CHI_TIẾT_MÃ_QR_MÀN_HÌNH()`, `CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH()`
- `PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH()`, `MÀN_HÌNH_CÓ_VẬT_THỂ(CHUỖI)`, `TÌM_TỌA_ĐỘ_VẬT_THỂ(CHUỖI)`, `ĐẾM_VẬT_THỂ_MÀN_HÌNH()`

Ảnh chụp màn hình qua Accessibility yêu cầu Android 11/API 30 trở lên; ứng dụng đích hoặc cửa sổ bảo mật có thể chặn chụp.

### Trợ năng và điều khiển giao diện
- Tìm theo văn bản, mô tả nội dung, resource ID, loại thành phần và trạng thái.
- Đọc bounds, trạng thái bật/tắt, khả năng click/scroll.
- Click, long-click, nhập văn bản, cuộn đến phần tử.
- Không giả định ứng dụng bên ngoài cho phép đọc hoặc thao tác mọi thành phần.

### Tệp, dữ liệu và mạng
- Đã có kho tệp UTF-8 riêng, JSON lồng nhau, CSV, HTTP multipart upload/download và SQLite nội bộ.
- HTTP hỗ trợ HTTPS GET/POST/PUT/PATCH/DELETE/HEAD, header map, timeout, giới hạn payload/phản hồi và không tự theo redirect. Multipart upload dùng POST/PUT/PATCH; download dùng GET và chỉ lưu tệp khi HTTP 2xx.
- Cài đặt có SAF nhập/xuất tệp tổng quát; runtime có CHỌN_TỆP(MIME) và LƯU_TỆP(ĐƯỜNG_DẪN, TÊN_GỢI_Ý), chờ kết quả người dùng tối đa 120 giây, hỗ trợ dừng kịch bản, không lưu quyền URI lâu dài. Tệp được sao chép qua kho riêng và giới hạn 1 MiB; backup/restore SQLite qua SAF có luồng riêng.
- API mạng phải có timeout, giới hạn kích thước, HTTPS mặc định, kiểm tra phản hồi và không ghi log bí mật.

### Tác vụ nền và sự kiện
- Trigger hiện tại chỉ hoạt động sau khi người dùng chủ động bật và nhập đúng mã gói ứng dụng; AUTU chỉ chạy khi ứng dụng đó ở tiền cảnh, màn hình đã mở khóa và phiên/quyền còn hợp lệ.
- Trigger và hẹn giờ phụ thuộc tiến trình Accessibility Service; không bảo đảm khi Android/OEM dừng tiến trình, khi máy khóa hoặc khi ứng dụng đích không ở tiền cảnh.
- Lịch lặp, trigger theo thông báo/nội dung, điều kiện pin/mạng và tác vụ nền định kỳ cần thiết kế riêng theo giới hạn Android.
- Tác vụ có thể hủy; retry có giới hạn và backoff, mặc định tắt vì có thể lặp lại tác động đã thực hiện một phần.
- Không tự động khởi chạy hoặc thao tác ứng dụng nhạy cảm ngoài cấu hình rõ ràng của người dùng.

## Quy tắc tương thích và giới hạn

- Ảnh chụp màn hình qua AccessibilityService yêu cầu Android 11/API 30 trở lên và khai báo capability tương ứng. Màn hình bảo mật hoặc ứng dụng chặn chụp có thể không đọc được.
- Cây giao diện trợ năng chỉ cung cấp dữ liệu mà ứng dụng đích phơi bày; canvas/game/custom view có thể không có text node.
- Cây Trợ năng chỉ cung cấp dữ liệu mà ứng dụng đích công khai; không thể đọc mọi canvas, video hoặc thành phần tùy chỉnh.
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

1. Hoàn thiện hiệu chỉnh vùng an toàn, tọa độ theo xoay màn hình, kéo-thả và đánh giá đa điểm theo khả năng Android.
2. HTTP/API có timeout, HTTPS, giới hạn payload và chính sách dữ liệu.
3. Backup/restore SQLite đã có mã nguồn/CI; kiểm thử trên thiết bị thật với WAL, SAF và lỗi giữa chừng còn thiếu.
4. Sự kiện, lịch, hàng đợi và tác vụ nền có thể hủy.
5. Module/thư viện dùng chung; regex an toàn vẫn còn thiếu. Ngày giờ ISO 8601 đã có mã nguồn và unit tests.
6. Trình soạn thảo: tô màu, autocomplete, định dạng và debugger từng bước.
7. Kiểm thử thiết bị thật: Xiaomi/HyperOS, Android nhiều API, quyền, vòng đời service, pin và độ phân giải.
8. Rà soát bảo mật, hiệu năng, khả năng tiếp cận và tương thích trước phát hành.

**Ghi chú:** Không có danh sách hữu hạn nào bao quát mọi API OEM, mọi ứng dụng và mọi phiên bản Android. Mục tiêu là bao phủ toàn diện các khả năng công khai, được cấp quyền và có thể kiểm thử trên Android, đồng thời công bố trung thực các giới hạn.
