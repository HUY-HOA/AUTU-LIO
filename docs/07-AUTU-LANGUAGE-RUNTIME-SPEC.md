# AUTU-LIO — Đặc tả ngôn ngữ và bộ máy tự động hóa

**Trạng thái:** Đặc tả mục tiêu; không đồng nghĩa các chức năng đã được triển khai.  
**Nguyên tắc:** Mỗi lệnh xuất hiện trong trình soạn thảo phải có parser, kiểm tra kiểu/tham số, runtime handler, kiểm thử và thông báo quyền cần thiết. Không đưa lệnh giả chỉ để làm giao diện trông đầy đủ.

## 1. Mục tiêu

AUTU-LIO cần một ngôn ngữ kịch bản riêng, dùng từ khóa tiếng Việt có dấu và viết hoa. Ngôn ngữ phải đủ tổng quát để viết quy trình tự động hóa, không giới hạn ở chạm và chờ. Cùng một bộ máy thực thi phải dùng được cho AUTU do chủ sở hữu tạo và AUTU đã phát hành cho người dùng.

Không dùng `eval`, thực thi mã Kotlin/Java tùy ý, shell tùy ý, reflection hoặc tải mã thực thi không tin cậy. Parser tạo AST/IR có kiểu; runtime chỉ gọi các khả năng đã đăng ký trong thư viện AUTU.

## 2. Cấu trúc ngôn ngữ

### 2.1 Dữ liệu và biến

Các kiểu dữ liệu mục tiêu:
- Số nguyên, số thập phân và số đo thời gian.
- Chuỗi Unicode, giá trị ĐÚNG/SAI và giá trị rỗng.
- Danh sách, bản đồ khóa–giá trị và đối tượng kết quả có cấu trúc.
- Biến cục bộ, tham số hàm, biến toàn cục của một lần chạy và kho dữ liệu tồn tại lâu dài có phạm vi/quyền rõ ràng.

Cần hỗ trợ khai báo, gán, tăng/giảm, phép tính, nối chuỗi, ép kiểu có kiểm tra, kiểm tra kiểu và giá trị mặc định. Không tự chuyển kiểu mơ hồ.

### 2.2 Toán tử và biểu thức

Toán tử số học: cộng, trừ, nhân, chia, chia lấy dư, lũy thừa và dấu ngoặc.  
So sánh: bằng, khác, lớn hơn, lớn hơn hoặc bằng, nhỏ hơn, nhỏ hơn hoặc bằng.  
Logic: VÀ, HOẶC, KHÔNG; có thứ tự ưu tiên xác định và đánh giá ngắn mạch.  
Biểu thức có thể gọi hàm thư viện đã đăng ký và trả về số, chuỗi, đúng/sai hoặc kết quả có kiểu.

### 2.3 Điều khiển luồng

Bắt buộc có:
- NẾU / NGƯỢC_LẠI_NẾU / NGƯỢC_LẠI.
- LẶP theo số lần; LẶP_KHI; LẶP_ĐẾN_KHI; duyệt danh sách.
- LẶP_VÔ_HẠN có điểm dừng, nút dừng của người dùng và watchdog runtime.
- THOÁT_LẶP, TIẾP_TỤC, TRẢ_VỀ và DỪNG_KỊCH_BẢN.
- Khối lệnh lồng nhau, phạm vi biến và giới hạn độ sâu.

### 2.4 Hàm và xử lý lỗi

- ĐỊNH_NGHĨA_HÀM, tham số có kiểu, giá trị mặc định, kết quả trả về và gọi hàm.
- Hàm thư viện dùng chung, phiên bản thư viện và kiểm tra tương thích trước khi phát hành.
- THỬ / BẮT_LỖI / LUÔN_THỰC_HIỆN, phát sinh lỗi có mã và thông điệp.
- THỬ_LẠI với số lần tối đa, khoảng nghỉ và điều kiện dừng.
- Ghi nhật ký theo mức độ, dòng lệnh, thời điểm, mã lỗi; không ghi nội dung nhạy cảm.

## 3. Thư viện thao tác

### 3.1 Tương tác màn hình

- CHẠM, NHẤN_GIỮ, NHẤN_ĐÚP, VUỐT, KÉO_THẢ và thao tác đa điểm khi Android API cho phép.
- CHỜ, CHỜ_ĐẾN_KHI, CHỜ_TỐI_ĐA và giới hạn thời gian.
- Tọa độ pixel, tọa độ theo phần trăm màn hình, kích thước màn hình, vùng thao tác, hướng màn hình và chuyển đổi tọa độ.
- Kiểm tra tọa độ, giới hạn thời lượng gesture, hủy thao tác và báo lỗi khi AccessibilityService từ chối.

### 3.2 Ứng dụng

- KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI(packageName).
- KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ(packageName).
- MỞ_ỨNG_DỤNG(packageName) bằng Activity khởi chạy hợp lệ của ứng dụng.
- CHỜ_ỨNG_DỤNG_MỞ, CHỜ_ỨNG_DỤNG_ĐÓNG và kiểm tra ứng dụng mục tiêu đang ở foreground.
- Lấy thông tin gói tối thiểu cần thiết; tuân thủ giới hạn package visibility của Android.

Không tự ý force-stop ứng dụng khác, đọc dữ liệu nội bộ ứng dụng, lấy mật khẩu hoặc điều khiển màn hình đăng nhập/bảo mật.

### 3.3 Hệ thống và thiết bị

Bổ sung bộ điều kiện/hàm truy vấn có kiểu cho:
- Màn hình đang sáng/tắt và trạng thái thiết bị tương tác được.
- Phần trăm pin, pin yếu, trạng thái đang sạc/rút sạc.
- Có kết nối mạng; loại kết nối chỉ khi Android cung cấp và quyền cho phép.
- Hướng màn hình, kích thước màn hình và mật độ hiển thị.
- Ngày, giờ, phút, thứ, múi giờ của thiết bị.
- Trợ năng đã bật, quyền hiển thị trên ứng dụng khác, quyền thông báo, quyền chụp màn hình phiên hiện tại và các quyền cần thiết khác.
- Ứng dụng có thể khởi chạy và khả năng của thiết bị/API đang dùng.

Mọi phép kiểm tra quyền phải trả về trạng thái rõ ràng: ĐÃ_CẤP, CHƯA_CẤP, KHÔNG_HỖ_TRỢ hoặc CẦN_XÁC_NHẬN. Không giả định quyền tồn tại chỉ vì đã khai báo trong Manifest.

### 3.4 Hành động hệ thống

- BACK, HOME và các hành động toàn cục Android được AccessibilityService chính thức hỗ trợ.
- Mở trang Cài đặt phù hợp để người dùng tự xem/cấp quyền.
- Mở ứng dụng bằng Intent hợp lệ.
- Hành động cần quyền hoặc có ảnh hưởng đáng kể phải khai báo quyền trước khi chạy và hiển thị lý do.

Không tự bật/tắt quyền hệ thống, tự cấp quyền, tự cài/gỡ APK, vượt khóa màn hình, tắt cơ chế bảo vệ hoặc thực hiện thao tác Android bị hệ điều hành chặn.

## 4. Nhận diện và điều kiện dựa trên nội dung

Mục tiêu dài hạn:
- QUÉT_ẢNH, tìm ảnh trong vùng xác định, ngưỡng tương đồng, số lần quét, khoảng nghỉ và thời hạn quét.
- ĐỌC_CHỮ/OCR, lọc văn bản, so khớp nội dung và xử lý kết quả.
- Phân tích kết quả tìm thấy/không tìm thấy, điểm tin cậy, tọa độ và vùng kết quả.

Các khả năng đọc hình ảnh hoặc nội dung màn hình chỉ được bật trong phiên mà người dùng chủ động cấp quyền chụp màn hình Android (MediaProjection) và nhìn thấy thông báo phiên đang hoạt động. Không âm thầm chụp màn hình, ghi nội dung ứng dụng hoặc lưu ảnh/văn bản ngoài phạm vi đã đồng ý. Nếu chưa có quyền hoặc bộ nhận diện chưa được tích hợp, lệnh phải báo CHƯA_HỖ_TRỢ/CẦN_CẤP_QUYỀN, không giả vờ đã quét.

AccessibilityService mặc định không đọc cây giao diện, nội dung cửa sổ, trường nhập liệu hoặc văn bản ứng dụng. Chỉ dùng metadata tối thiểu cần thiết như package foreground cho điều kiện ứng dụng, theo cấu hình sự kiện được duyệt.

## 5. Sự kiện và điều kiện theo thời gian

- Sự kiện ứng dụng mục tiêu vào/ra foreground.
- Màn hình tương tác, sạc/rút sạc, mạng kết nối/ngắt kết nối.
- Bộ hẹn giờ, khoảng thời gian, lịch chạy và điều kiện thời gian.
- Chạy khi sự kiện xảy ra, lọc sự kiện, chống kích hoạt trùng và thời gian chờ.
- Chạy nền phải tuân thủ Android foreground-service, notification, Doze và giới hạn nền. Khi cần, yêu cầu người dùng bật/tắt theo hướng dẫn của Android; không lén chạy nền.

## 6. Cú pháp minh họa

Đây là ví dụ định hướng cho ngôn ngữ mục tiêu, chưa phải cam kết rằng các lệnh mẫu đã có trong runtime hiện tại:

```text
ĐẶT soLanMo = 0

LẶP_KHI (soLanMo < 5) {
    NẾU (KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI("com.example.game")) {
        MỞ_ỨNG_DỤNG("com.example.game")
        CHỜ_ỨNG_DỤNG_MỞ("com.example.game", 10000)
        THOÁT_LẶP
    } NGƯỢC_LẠI {
        CHỜ(1000)
    }

    ĐẶT soLanMo = soLanMo + 1
}

NẾU (PHẦN_TRĂM_PIN() < 15 HOẶC KHÔNG CÓ_MẠNG()) {
    GHI_NHẬT_KÝ("Thiết bị cần kiểm tra")
}
```

Cú pháp cuối cùng phải thống nhất giữa parser, tài liệu trợ giúp, mẫu, bộ tô màu cú pháp và trình dựng khối lệnh. Tránh dùng hai cách viết khác nhau cho cùng một lệnh.

## 7. An toàn, giới hạn và độ tin cậy

Mỗi chương trình phải có:
- Kiểm tra cú pháp, tham số, kiểu dữ liệu, quyền và khả năng thiết bị trước khi chạy.
- Giới hạn mã nguồn, kích thước dữ liệu, độ sâu lồng nhau/đệ quy, số lần lặp, số lệnh, thời gian chạy và tốc độ gọi API.
- Điểm kiểm tra hủy ở mỗi vòng lặp/chờ/thao tác dài; nút Tạm dừng, Tiếp tục và Dừng luôn có hiệu lực.
- Timeout mặc định cho chờ, quét, mở ứng dụng và gọi dịch vụ.
- Kết quả từng hành động, mã lỗi ổn định, nhật ký có thể xem và chẩn đoán dòng lệnh.
- Cơ chế quyền theo nguyên tắc tối thiểu; AUTU phát hành chỉ được dùng các khả năng đã được chủ sở hữu cấp và người chạy chấp thuận.
- Không cho kịch bản tự thay đổi quyền, tự vượt giới hạn runtime hoặc tự tải mã thực thi không kiểm duyệt.

Không coi vòng lặp vô hạn là an toàn chỉ vì có nút Dừng; watchdog phải tự ngắt khi vượt ngân sách.

## 8. Yêu cầu phát triển và nghiệm thu

Một tính năng chỉ được đánh dấu hoàn thành khi có đủ:
1. Từ khóa/cú pháp tiếng Việt chuẩn và tài liệu trợ giúp.
2. Lexer/parser tạo AST/IR có kiểu.
3. Kiểm tra tham số, quyền, khả năng API và ngân sách.
4. Runtime handler thật, nối với Android API phù hợp.
5. Unit test parser, biểu thức, luồng điều khiển và runtime; test lỗi/giới hạn/hủy.
6. Kiểm tra CI; kiểm thử thiết bị thật cho hành động Android.
7. Phân biệt rõ ĐÃ_HỖ_TRỢ, CẦN_QUYỀN, CHƯA_HỖ_TRỢ và THẤT_BẠI.

### Các giai đoạn triển khai

- **Giai đoạn 1 — Ngôn ngữ lõi:** cú pháp tiếng Việt, biến, kiểu dữ liệu, biểu thức, điều kiện, vòng lặp, hàm, xử lý lỗi và ngân sách.
- **Giai đoạn 2 — Android cơ bản:** chạm/giữ/vuốt, tọa độ tương đối, kiểm tra quyền, mở ứng dụng, trạng thái foreground và điều kiện hệ thống.
- **Giai đoạn 3 — Sự kiện và nhận diện:** chờ theo sự kiện, bộ hẹn giờ, quét ảnh/OCR qua phiên MediaProjection có đồng ý.
- **Giai đoạn 4 — Thư viện và IDE:** hàm dùng chung, thư viện phiên bản, gợi ý cú pháp, debugger từng bước, biến quan sát, nhật ký và trình dựng khối.
- **Giai đoạn 5 — Phát hành an toàn:** đóng gói/phiên bản hóa, quyền theo từng AUTU, xác minh gói và kiểm thử tương thích.

Không được bỏ qua giai đoạn kiểm thử hoặc công bố một lệnh là chạy được khi chỉ mới có tài liệu hay nút giao diện.
