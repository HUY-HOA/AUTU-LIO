# AUTU-LIO — Ngôn ngữ lập trình tự động hóa AUTU Script 1.16

**Phiên bản cú pháp:** `1.16`  
**Quy tắc bắt buộc:** từ khóa, lệnh, hàm, kiểu và tên biến do người viết đặt đều phải viết HOA. Từ khóa tiếng Việt phải có đúng dấu. Không chấp nhận bí danh lệnh tiếng Anh hoặc cách viết thường.  
**Bộ biên dịch:** `ScriptCompiler.kt`  
**Bộ thực thi:** `ScriptExecutionEngine.kt`

AUTU Script là ngôn ngữ kịch bản chuyên cho tự động hóa Android trong AUTU-LIO. Mã được phân tích thành cấu trúc lệnh an toàn, kiểm tra ngân sách rồi mới được runtime thực thi. Không có `eval`, shell, reflection hay thực thi Kotlin/Java tùy ý.

## 1. Quy tắc viết mã

- Lệnh và từ khóa phải viết HOA, đúng dấu: `NẾU`, `LẶP`, `CHẠM`, `NHẤN_GIỮ`.
- Các tên lệnh điều hướng Android dùng tên mô tả đúng thao tác; tên cũ vẫn được chấp nhận như bí danh tương thích.
- Tên biến, tham số và hàm tự định nghĩa cũng phải viết HOA: `SO_LAN`, `SỐ_LẦN`, `TINH_TONG`.
- Tên có thể gồm chữ Unicode, chữ số và dấu gạch dưới `_`; tên phải bắt đầu bằng chữ hoặc `_`.
- Mã phân biệt chính xác chữ hoa/thường và dấu tiếng Việt. `chạm()`, `CHAM()`, `tap()` đều không hợp lệ.
- Mỗi câu lệnh nằm trên một dòng; có thể ngăn cách nhiều lệnh bằng dấu `;`.
- Chú thích một dòng bắt đầu bằng `//`.
- Chuỗi đặt trong dấu nháy kép. Ký tự thoát: `\n`, `\r`, `\t`, `\\`, `\"`.

## 2. Kiểu dữ liệu và biến

Kiểu được hỗ trợ:

| Kiểu | Dữ liệu |
|---|---|
| `SỐ` | Số nguyên hoặc thập phân |
| `CHUỖI` | Văn bản Unicode |
| `LUẬN_LÝ` | `ĐÚNG` hoặc `SAI` |
| `DANH_SÁCH` | Danh sách tối đa 64 phần tử số, chuỗi hoặc luận lý |
| `BẢN_ĐỒ` | Bản đồ tối đa 64 cặp khóa–giá trị; khóa là chuỗi không rỗng, giá trị là số, chuỗi hoặc luận lý |
| `BẤT_KỲ` | Giá trị thuộc các kiểu được runtime hỗ trợ |

Khai báo biến có kiểu:

```text
ĐẶT SỐ_LẦN: SỐ = 3
ĐẶT TÊN_GÓI: CHUỖI = "com.example.game"
ĐẶT ĐÃ_SẴN_SÀNG: LUẬN_LÝ = SAI
```

Có thể khai báo không ghi kiểu; khi đó biến không bị khóa vào một kiểu cụ thể:

```text
ĐẶT KẾT_QUẢ = 10
KẾT_QUẢ = KẾT_QUẢ + 5
```

Biến đã khai báo kiểu sẽ kiểm tra mọi lần gán tiếp theo. Gán sai kiểu làm runtime dừng với lỗi `variable_type_mismatch`.

### Lưu tệp và JSON

```text
TẠO_THƯ_MỤC("settings")
GHI_TỆP("settings/note.txt", "AUTU-LIO")
ĐẶT NỘI_DUNG: CHUỖI = ĐỌC_TỆP("settings/note.txt")
GHI_JSON_TỆP("settings/config.json", TẠO_BẢN_ĐỒ("TÊN", "AUTU-LIO", "BẬT", ĐÚNG))
ĐẶT CẤU_HÌNH: BẢN_ĐỒ = ĐỌC_JSON_TỆP("settings/config.json")
ĐẶT TỆP: DANH_SÁCH = LIỆT_KÊ_TỆP("settings")
```

Các tệp nằm trong thư mục riêng của AUTU-LIO. Đường dẫn phải tương đối, cấm "..", "." và đường dẫn tuyệt đối; không truy cập tùy ý bộ nhớ dùng chung hoặc dữ liệu ứng dụng khác. Kích thước mỗi tệp tối đa 1 MiB. LIỆT_KÊ_TỆP chỉ trả về tệp trực tiếp trong thư mục, tối đa 128 tên.

### JSON và cấu trúc dữ liệu lồng nhau

```text
ĐẶT CẤU_HÌNH: BẢN_ĐỒ = PHÂN_TÍCH_JSON("{\"TÊN\":\"AUTU\",\"ITEMS\":[1,{\"X\":120},null]}")
ĐẶT ITEMS: DANH_SÁCH = LẤY_GIÁ_TRỊ(CẤU_HÌNH, "ITEMS")
ĐẶT ITEM: BẢN_ĐỒ = LẤY_PHẦN_TỬ(ITEMS, 1)
ĐẶT X: SỐ = LẤY_GIÁ_TRỊ(ITEM, "X")
ĐẶT JSON_KẾT_QUẢ: CHUỖI = CHUYỂN_JSON(CẤU_HÌNH)
ĐẶT KHÔNG_CÓ_GIÁ_TRỊ: BẤT_KỲ = RỖNG
```

JSON hỗ trợ object/array lồng nhau, chuỗi Unicode, số hữu hạn, boolean và null. Bản đồ/danh sách cũng có thể lồng nhau. Giới hạn: 65.536 ký tự JSON, 64 phần tử hoặc khóa trong mỗi container, 8 cấp lồng nhau, 512 giá trị và 4.096 ký tự mỗi chuỗi. JSON có khóa lặp, sai cú pháp hoặc vượt giới hạn sẽ báo lỗi và có thể được bắt bằng BẮT_LỖI.

### Bản đồ khóa–giá trị

```text
ĐẶT CAU_HINH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ("TOA_DO_X", 120, "TOA_DO_Y", 400)
ĐẶT X: SỐ = LẤY_GIÁ_TRỊ(CAU_HINH, "TOA_DO_X")
ĐẶT_GIÁ_TRỊ(CAU_HINH, "TOA_DO_X", 180)
ĐẶT KHOA: DANH_SÁCH = DANH_SÁCH_KHÓA(CAU_HINH)

LẶP_MỖI (TEN_KHOA TRONG KHOA) {
    CHẠM(LẤY_GIÁ_TRỊ(CAU_HINH, TEN_KHOA), 500)
}
```

Khóa phải là chuỗi không rỗng và không trùng nhau. Giá trị chỉ được là số hữu hạn, chuỗi hoặc ĐÚNG/SAI. Mỗi bản đồ tối đa 64 cặp. `CÓ_KHÓA` kiểm tra khóa; `XÓA_KHÓA` xóa và trả về giá trị cũ; `ĐỘ_DÀI` trả về số cặp khóa–giá trị.

## 3. Toán tử và biểu thức

| Nhóm | Toán tử | Ý nghĩa |
|---|---|---|
| Số học | `+`, `-`, `*`, `/`, `%`, `^` | Cộng, trừ, nhân, chia, chia dư, lũy thừa |
| So sánh | `==`, `!=`, `>`, `>=`, `<`, `<=` | So sánh số hoặc chuỗi cùng kiểu |
| Logic | `VÀ`, `HOẶC`, `KHÔNG` | AND, OR, NOT |
| Nhóm | `(...)` | Thay đổi thứ tự ưu tiên |

Thứ tự ưu tiên từ thấp đến cao: `HOẶC` → `VÀ` → so sánh bằng/khác → so sánh lớn/nhỏ → cộng/trừ → nhân/chia/chia dư → lũy thừa → dấu một ngôi. Lũy thừa kết hợp từ phải sang trái: `2 ^ 3 ^ 2` được hiểu là `2 ^ (3 ^ 2)`.

Phép cộng với ít nhất một toán hạng là chuỗi sẽ nối chuỗi. Phép chia cho 0, phép chia dư cho 0 hoặc kết quả số không hữu hạn sẽ phát sinh lỗi.

## 4. Điều khiển luồng

### Điều kiện

```text
NẾU (PIN() > 20 VÀ CÓ_MẠNG()) {
    CHẠM(200, 400)
} NGƯỢC_LẠI_NẾU (PIN() > 10) {
    CHỜ(500)
} NGƯỢC_LẠI {
    QUAY_LẠI()
}
```

### Vòng lặp theo số lần

```text
ĐẶT SỐ_LẦN = 3
LẶP(SỐ_LẦN) {
    CHẠM(200, 400)
    CHỜ(500)
}
```

### Lặp theo điều kiện

```text
ĐẶT BỘ_ĐẾM = 0
LẶP_KHI (BỘ_ĐẾM < 5) {
    CHẠM(200, 400)
    BỘ_ĐẾM = BỘ_ĐẾM + 1
}
```

### Lặp đến khi điều kiện đúng

```text
ĐẶT BỘ_ĐẾM = 0
LẶP_ĐẾN_KHI(BỘ_ĐẾM >= 5) {
    BỘ_ĐẾM = BỘ_ĐẾM + 1
    CHỜ(250)
}
```

### Danh sách và duyệt từng phần tử

```text
ĐẶT DANH_SÁCH: DANH_SÁCH = TẠO_DANH_SÁCH(120, 240, 360)
LẶP_MỖI (TOA_DO_X TRONG DANH_SÁCH) {
    CHẠM(TOA_DO_X, 400)
    CHỜ(300)
}
```

`LẶP_MỖI` duyệt trên bản sao an toàn của danh sách tại thời điểm bắt đầu vòng lặp. Biến vòng lặp chỉ tồn tại trong phạm vi vòng lặp; nếu trùng tên biến đã có, giá trị bên ngoài được khôi phục khi thoát.

- `THOÁT_LẶP`: thoát vòng lặp gần nhất.
- `BỎ_QUA_LƯỢT`: bỏ phần còn lại của lượt hiện tại và chuyển sang lượt tiếp theo.
- `CHỜ_ĐẾN_KHI(ĐIỀU_KIỆN, THỜI_HẠN_MS, CHU_KỲ_MS)`: kiểm tra điều kiện theo chu kỳ; dừng bằng lỗi timeout nếu hết thời hạn.

## 5. Hàm tự định nghĩa

Cú pháp:

```text
ĐỊNH_NGHĨA_HÀM TÊN_HÀM(THAM_SỐ_1: KIỂU, THAM_SỐ_2: KIỂU): KIỂU_TRẢ_VỀ {
    // Các lệnh trong hàm
    TRẢ_VỀ BIỂU_THỨC
}
```

Kiểu tham số và kiểu trả về là tùy chọn. Mỗi hàm có tối đa 16 tham số. Hàm được khai báo ở cấp cao nhất, không được lồng trong NẾU hoặc vòng lặp.

Ví dụ hàm tính tổng:

```text
ĐỊNH_NGHĨA_HÀM TINH_TONG(A: SỐ, B: SỐ): SỐ {
    TRẢ_VỀ A + B
}

ĐẶT KẾT_QUẢ: SỐ = TINH_TONG(12, 30)
CHẠM(KẾT_QUẢ, 300)
```

Hàm có thể gọi hàm khác và tự gọi lại chính nó. Độ sâu gọi hàm tối đa 16 cấp. Runtime kiểm tra số tham số, kiểu tham số, kiểu trả về, lỗi thiếu TRẢ_VỀ và ngân sách lệnh. Mỗi lần gọi hàm có phạm vi biến cục bộ riêng; hàm đọc được giá trị biến của nơi gọi tại thời điểm gọi, nhưng phép gán bên trong hàm không ghi ngược thay đổi vào biến của nơi gọi.

### Tọa độ DP và tỷ lệ màn hình

```text
ĐẶT X: SỐ = TỌA_ĐỘ_X_THEO_TỶ_LỆ(0.5)
ĐẶT Y: SỐ = TỌA_ĐỘ_Y_THEO_TỶ_LỆ(0.75)
CHẠM(X, Y)
ĐẶT KHOẢNG_CÁCH: SỐ = DP_SANG_PIXEL(24)
```

Dùng `MẬT_ĐỘ_MÀN_HÌNH()`, `DP_SANG_PIXEL(DP)`, `PIXEL_SANG_DP(PIXEL)`, `TỌA_ĐỘ_X_THEO_TỶ_LỆ(TỶ_LỆ)`, `TỌA_ĐỘ_Y_THEO_TỶ_LỆ(TỶ_LỆ)`, `TỶ_LỆ_X_THEO_TỌA_ĐỘ(X)` và `TỶ_LỆ_Y_THEO_TỌA_ĐỘ(Y)`. Tỷ lệ 0–1 ánh xạ từ pixel đầu đến pixel cuối của màn hình. Chưa tự loại trừ status bar, navigation bar hoặc cutout.

## 6. Lệnh thao tác Android đã nối runtime

| Lệnh | Tham số | Ý nghĩa |
|---|---|---|
| `CHẠM(X, Y)` | 2 tọa độ | Chạm một lần |
| `NHẤN_GIỮ(X, Y, MS)` | tọa độ và thời lượng | Nhấn giữ |
| `VUỐT(X1, Y1, X2, Y2, MS)` | điểm đầu, điểm cuối, thời lượng | Vuốt theo đường thẳng |
| `CHỜ(MS)` | thời lượng | Chờ |
| `MỞ_ỨNG_DỤNG("tên.gói")` | tên gói Android | Mở Activity khởi chạy hợp lệ |
| `QUAY_LẠI()` | không | Hành động Back |
| `VỀ_MÀN_HÌNH_CHÍNH()` | không | Hành động Home |
| `MỞ_ỨNG_DỤNG_GẦN_ĐÂY()` | không | Mở danh sách ứng dụng gần đây |
| `MỞ_THANH_THÔNG_BÁO()` | không | Mở ngăn thông báo |
| `MỞ_BẢNG_CÀI_ĐẶT_NHANH()` | không | Mở bảng cài đặt nhanh |
| `MỞ_HỘP_THOẠI_NGUỒN()` | không | Hiện menu nguồn |
| `KHÓA_MÀN_HÌNH()` | không | Khóa màn hình nếu Android hỗ trợ |
| `BẬT_TẮT_CHIA_MÀN_HÌNH()` | không | Chuyển chế độ chia màn hình nếu hệ thống hỗ trợ |

Tọa độ là pixel tuyệt đối. Dispatcher kiểm tra lại tọa độ theo kích thước màn hình thực tế và chỉ báo thành công khi Android chấp nhận và hoàn tất thao tác. Hành động hệ thống có thể bị từ chối tùy phiên bản Android, thiết bị hoặc trạng thái Trợ năng.

## 7. Hàm dựng sẵn

### Toán học và chuỗi

| Hàm | Tham số | Trả về |
|---|---|---|
| `SỐ(GIÁ_TRỊ)` | 1 | Chuyển số hoặc chuỗi số thành số |
| `CHUỖI(GIÁ_TRỊ)` | 1 | Chuyển thành chuỗi |
| `LÀM_TRÒN(SỐ)` | 1 | Làm tròn |
| `TUYỆT_ĐỐI(SỐ)` | 1 | Giá trị tuyệt đối |
| `ĐỘ_DÀI(CHUỖI)` | 1 | Độ dài chuỗi |
| `NHỎ_NHẤT(A, B)` | 2 | Số nhỏ hơn |
| `LỚN_NHẤT(A, B)` | 2 | Số lớn hơn |
| `CHỮ_HOA(CHUỖI)` | 1 | Chuyển thành chữ HOA theo Unicode |
| `CHỮ_THƯỜNG(CHUỖI)` | 1 | Chuyển thành chữ thường theo Unicode |
| `CẮT_KHOẢNG_TRẮNG(CHUỖI)` | 1 | Xóa khoảng trắng ở hai đầu |
| `CHỨA_CHUỖI(A, B)` | 2 | Kiểm tra A có chứa B |
| `BẮT_ĐẦU_BẰNG(A, B)` | 2 | Kiểm tra tiền tố |
| `KẾT_THÚC_BẰNG(A, B)` | 2 | Kiểm tra hậu tố |
| `THAY_THẾ(A, CŨ, MỚI)` | 3 | Thay tất cả chuỗi con khớp chính xác |
| `TÁCH_CHUỖI(CHUỖI, PHÂN_CÁCH)` | 2 | Tách thành danh sách, tối đa 64 phần tử |
| `NỐI_CHUỖI(DANH_SÁCH, PHÂN_CÁCH)` | 2 | Nối danh sách chuỗi |
| `KÝ_TỰ_TẠI(CHUỖI, VỊ_TRÍ)` | 2 | Lấy ký tự theo vị trí bắt đầu từ 0 |
| `CẮT_CHUỖI(CHUỖI, VỊ_TRÍ, ĐỘ_DÀI)` | 3 | Cắt theo vị trí và số ký tự |
| `TẠO_DANH_SÁCH(...)` | 0–64 | Tạo danh sách mới |
| `ĐỘ_DÀI(DANH_SÁCH)` | 1 | Số phần tử trong danh sách |
| `LẤY_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ)` | 2 | Lấy phần tử theo chỉ số bắt đầu từ 0 |
| `THÊM_PHẦN_TỬ(DANH_SÁCH, GIÁ_TRỊ)` | 2 | Thêm phần tử, trả về số lượng mới |
| `ĐẶT_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ, GIÁ_TRỊ)` | 3 | Thay phần tử theo chỉ số |
| `XÓA_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ)` | 2 | Xóa và trả về phần tử đã xóa |
| `CÓ_PHẦN_TỬ(DANH_SÁCH, GIÁ_TRỊ)` | 2 | Kiểm tra phần tử có trong danh sách |
| `TẠO_BẢN_ĐỒ(KHÓA, GIÁ_TRỊ, ...)` | 0–64 cặp | Tạo bản đồ mới, số tham số phải chẵn |
| `LẤY_GIÁ_TRỊ(BẢN_ĐỒ, KHÓA)` | 2 | Lấy giá trị theo khóa |
| `ĐẶT_GIÁ_TRỊ(BẢN_ĐỒ, KHÓA, GIÁ_TRỊ)` | 3 | Thêm hoặc cập nhật giá trị |
| `XÓA_KHÓA(BẢN_ĐỒ, KHÓA)` | 2 | Xóa và trả về giá trị cũ |
| `CÓ_KHÓA(BẢN_ĐỒ, KHÓA)` | 2 | Kiểm tra khóa có tồn tại |
| `DANH_SÁCH_KHÓA(BẢN_ĐỒ)` | 1 | Lấy danh sách khóa theo thứ tự thêm |

Hàm chuỗi xử lý văn bản Unicode. Vị trí của `KÝ_TỰ_TẠI` và `CẮT_CHUỖI` tính theo mã Unicode (code point), bắt đầu từ 0. `TÁCH_CHUỖI` từ chối dấu phân cách rỗng và không trả quá 64 phần tử. Danh sách chỉ nhận số hữu hạn, chuỗi hoặc ĐÚNG/SAI; chỉ số bắt đầu từ 0. Bản đồ chỉ nhận khóa chuỗi không rỗng và giá trị số, chuỗi hoặc ĐÚNG/SAI. Các thao tác truy cập/sửa/xóa ngoài phạm vi hoặc khóa không tồn tại sẽ báo lỗi runtime.

### Thiết bị và ứng dụng

| Hàm | Tham số | Trả về |
|---|---|---|
| `PIN()` / `PHẦN_TRĂM_PIN()` | 0 | Phần trăm pin |
| `ĐANG_SẠC()` | 0 | ĐÚNG nếu đang sạc hoặc pin đầy |
| `CÓ_MẠNG()` | 0 | ĐÚNG nếu kết nối Internet đã được xác thực |
| `MÀN_HÌNH_SÁNG()` | 0 | Trạng thái tương tác của màn hình |
| `ỨNG_DỤNG_ĐÃ_CÀI("tên.gói")` | 1 | Kiểm tra gói ứng dụng đã cài |
| `ỨNG_DỤNG_ĐANG_MỞ("tên.gói")` | 1 | Kiểm tra gói đang ở foreground |
| `CHIỀU_RỘNG_MÀN_HÌNH()` | 0 | Chiều rộng pixel |
| `CHIỀU_CAO_MÀN_HÌNH()` | 0 | Chiều cao pixel |
| `THỜI_GIAN_MS()` | 0 | Unix time theo mili giây |

Các hàm thiết bị trả về thông tin Android hiện được dispatcher cung cấp. AUTU Script 1.16 bổ sung đọc cây giao diện trợ năng và OCR màn hình theo yêu cầu.

## 8. Kết nối API qua HTTPS

AUTU Script cung cấp `GỬI_YÊU_CẦU_HTTP(PHƯƠNG_THỨC, URL, THÂN, TIÊU_ĐỀ, TIMEOUT_MS)`. Phương thức được hỗ trợ: GET, POST, PUT, DELETE và HEAD. Thân phải là chuỗi hoặc RỖNG; tiêu đề là BẢN_ĐỒ; timeout từ 100 đến 30.000 ms.

Kết quả là bản đồ gồm `TRẠNG_THÁI`, `THÀNH_CÔNG`, `NỘI_DUNG`, `TIÊU_ĐỀ` và `URL`. Dùng `PHÂN_TÍCH_JSON(LẤY_GIÁ_TRỊ(PHẢN_HỒI, "NỘI_DUNG"))` để đọc JSON server trả về.

Client chỉ chấp nhận HTTPS, không tự theo redirect, không cho phép URL nhúng mật khẩu, giới hạn thân gửi/nhận 1 MiB, tối đa 32 tiêu đề và không cho đặt Host/Content-Length/Connection. Các header nhạy cảm như Set-Cookie không được trả lại cho kịch bản. Lỗi HTTP transport, timeout, dữ liệu quá lớn hoặc header không hợp lệ sẽ phát sinh lỗi runtime.

Ví dụ:

```text
ĐẶT PHẢN_HỒI: BẢN_ĐỒ = GỬI_YÊU_CẦU_HTTP(
    "GET",
    "https://api.example.com/status",
    RỖNG,
    TẠO_BẢN_ĐỒ("Accept", "application/json"),
    10000
)
ĐẶT TRẠNG_THÁI: SỐ = LẤY_GIÁ_TRỊ(PHẢN_HỒI, "TRẠNG_THÁI")
```

### SQLite nội bộ

```text
SQL_THỰC_THI("CREATE TABLE IF NOT EXISTS LICH_SU (ID INTEGER PRIMARY KEY, NOI_DUNG TEXT)", TẠO_DANH_SÁCH())
SQL_THỰC_THI("INSERT INTO LICH_SU (NOI_DUNG) VALUES (?)", TẠO_DANH_SÁCH("Đã chạy"))
ĐẶT CAC_DONG: DANH_SÁCH = SQL_TRUY_VẤN("SELECT ID, NOI_DUNG FROM LICH_SU", TẠO_DANH_SÁCH())
```

AUTU-LIO dùng một database SQLite nội bộ trong vùng riêng ứng dụng. SQL chỉ được thực thi qua allow-list và tham số dấu hỏi `?`; truy vấn chỉ cho SELECT, còn câu ghi chỉ cho CREATE/ALTER/DROP giới hạn, INSERT, UPDATE, DELETE và REPLACE. Không hỗ trợ tự chọn đường dẫn database, ATTACH, PRAGMA, nhiều câu SQL hoặc chú thích. Giới hạn kết quả: 256 dòng, 64 cột và 1 MiB văn bản.

## 9. OCR và văn bản trên màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_VĂN_BẢN_MÀN_HÌNH() | Trả về văn bản tìm được trên màn hình |
| MÀN_HÌNH_CHỨA_CHỮ | MÀN_HÌNH_CHỨA_CHỮ("Đăng nhập") | ĐÚNG nếu nội dung màn hình có chuỗi |
| TÌM_VỊ_TRÍ_CHỮ | TÌM_VỊ_TRÍ_CHỮ("Đăng nhập") | Danh sách [X, Y] là tâm vị trí; rỗng nếu không tìm thấy |

Các hàm chỉ phân tích màn hình khi kịch bản gọi chúng. AUTU-LIO kết hợp cây giao diện trợ năng với OCR on-device. Ảnh màn hình không được tải lên máy chủ. Trên Android 11/API 30 trở lên, OCR có thể dùng ảnh màn hình của AccessibilityService; Android cũ hơn chỉ đọc được nội dung mà cây trợ năng cung cấp. Màn hình bảo mật, ứng dụng chặn chụp và vùng đồ họa không phơi bày nội dung có thể không đọc được. OCR có thể nhận sai chữ hoặc dấu; phải kiểm tra kết quả trước thao tác quan trọng.

### OCR có cấu trúc

```text
ĐẶT CÁC_KHỐI: DANH_SÁCH = ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()
ĐẶT KHỐI_ĐẦU: BẢN_ĐỒ = LẤY_PHẦN_TỬ(CÁC_KHỐI, 0)
ĐẶT CÁC_DÒNG: DANH_SÁCH = LẤY_GIÁ_TRỊ(KHỐI_ĐẦU, "CÁC_DÒNG")
```

Mỗi khối trả về VĂN_BẢN, X, Y, RỘNG, CAO và danh sách CÁC_DÒNG. Mỗi dòng có VĂN_BẢN cùng bounds riêng. Kết quả được giới hạn tối đa 64 khối và 256 dòng để tránh tạo cấu trúc dữ liệu quá lớn. Tọa độ là pixel của ảnh chụp màn hình.

### OCR theo vùng và nhận diện ảnh

```text
ĐẶT VAN_BAN: CHUỖI = ĐỌC_VĂN_BẢN_VÙNG(100, 200, 500, 300)
NẾU (MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG("Đăng nhập", 100, 200, 500, 300)) {
    ĐẶT VI_TRI: DANH_SÁCH = TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG("Đăng nhập", 100, 200, 500, 300)
    CHẠM(LẤY_PHẦN_TỬ(VI_TRI, 0), LẤY_PHẦN_TỬ(VI_TRI, 1))
}

ĐẶT NHAN: DANH_SÁCH = NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()
NẾU (MÀN_HÌNH_CHỨA_NHÃN_ẢNH("Phone")) {
    ĐẶT QR: DANH_SÁCH = QUÉT_MÃ_QR_MÀN_HÌNH()
    ĐẶT MA_VACH: DANH_SÁCH = QUÉT_MÃ_VẠCH_MÀN_HÌNH()
}
```

Tọa độ vùng là pixel toàn màn hình, gốc ở góc trái trên. Vùng phải nằm trọn trong ảnh chụp. Gắn nhãn ảnh trả về nhãn tổng quát của toàn khung hình, không trả hộp giới hạn từng vật thể. Quét QR và mã vạch trả về nội dung giải mã; không tự mở URL. Ảnh phải đủ nét và mã đủ lớn để giải mã.

### Nhận diện vật thể

```text
ĐẶT VẬT_THỂ: DANH_SÁCH = PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH()
ĐẶT SO_LUONG: SỐ = ĐẾM_VẬT_THỂ_MÀN_HÌNH()
NẾU (MÀN_HÌNH_CÓ_VẬT_THỂ("Food")) {
    ĐẶT TỌA_ĐỘ: DANH_SÁCH = TÌM_TỌA_ĐỘ_VẬT_THỂ("Food")
    CHẠM(LẤY_PHẦN_TỬ(TỌA_ĐỘ, 0), LẤY_PHẦN_TỬ(TỌA_ĐỘ, 1))
}
```

Mỗi phần tử trong danh sách VẬT_THỂ là bản đồ có X, Y, RỘNG, CAO và NHÃN. Hộp giới hạn dùng pixel ảnh màn hình. NHÃN chứa các bản đồ có TÊN, CHỈ_SỐ và ĐỘ_TIN_CẬY. Bộ nhận diện mặc định trả tối đa 5 vật thể và chỉ dùng nhãn tổng quát; không phải nhận dạng mọi đồ vật với tên chính xác.

### Mã QR và mã vạch có cấu trúc

```text
ĐẶT CÁC_MÃ: DANH_SÁCH = CHI_TIẾT_MÃ_QR_MÀN_HÌNH()
LẶP_MỖI (MỘT_MÃ TRONG CÁC_MÃ) {
    ĐẶT GIÁ_TRỊ: CHUỖI = LẤY_GIÁ_TRỊ(MỘT_MÃ, "GIÁ_TRỊ_GỐC")
    ĐẶT ĐỊNH_DẠNG: CHUỖI = LẤY_GIÁ_TRỊ(MỘT_MÃ, "ĐỊNH_DẠNG")
}
ĐẶT MÃ_VẠCH: DANH_SÁCH = CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH()
```

Mỗi kết quả chứa GIÁ_TRỊ_GỐC, GIÁ_TRỊ_HIỂN_THỊ, ĐỊNH_DẠNG, LOẠI_NỘI_DUNG và bounds X/Y/RỘNG/CAO nếu có. Không phải mọi mã đều có giá trị văn bản UTF-8 hoặc bounds. Kịch bản tự quyết định xử lý tiếp; ứng dụng không tự mở URL hoặc kết nối tới dữ liệu được quét.

### Phân tích màu RGB trên màn hình

| Hàm | Cú pháp |
|---|---|
| MÀU_TẠI | `MÀU_TẠI(X, Y)` trả về các kênh RGB, alpha và HEX |
| MÀN_HÌNH_CÓ_MÀU | `MÀN_HÌNH_CÓ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)` |
| TÌM_VỊ_TRÍ_MÀU | `TÌM_VỊ_TRÍ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)` |
| TỶ_LỆ_MÀU_TRONG_VÙNG | `TỶ_LỆ_MÀU_TRONG_VÙNG(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ, X, Y, RỘNG, CAO)` |

Kênh RGB và sai số nằm trong khoảng 0–255. Phân tích màu dùng ảnh chụp tại thiết bị và lấy mẫu có giới hạn; tỷ lệ trong vùng là ước lượng. Màn hình bảo mật hoặc ứng dụng chặn chụp có thể không sử dụng được.

### Tìm và thao tác phần tử giao diện bằng Trợ năng

```text
NẾU (TÌM_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")) {
    NHẤN_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập")
}
NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN("Tên người dùng", "LIO")
ĐẶT TOA_DO: DANH_SÁCH = TÌM_TỌA_ĐỘ_THÀNH_PHẦN("Đăng nhập")
NẾU (TÌM_THÀNH_PHẦN_THEO_ID("login_button")) {
    NHẤN_THÀNH_PHẦN_THEO_ID("login_button")
}
NHẬP_VĂN_BẢN_THEO_ID("username", "AUTU")
CUỘN_XUỐNG()
CUỘN_LÊN()
```

Các API tìm kiếm chạy trên cây giao diện do Android và ứng dụng mục tiêu cung cấp. Tìm theo văn bản so khớp một phần không phân biệt hoa/thường với text hoặc contentDescription; tìm ID chấp nhận ID đầy đủ hoặc phần tên sau dấu /. Nhấn sử dụng ACTION_CLICK trên phần tử hoặc tổ tiên có thể nhấn; nhập văn bản yêu cầu node editable. Trả về SAI nếu không tìm thấy hoặc hành động bị từ chối.

### Xử lý lỗi

```text
THỬ {
    MỞ_ỨNG_DỤNG("invalid")
} BẮT_LỖI (LỖI) {
    ĐẶT MÃ_LỖI: CHUỖI = LẤY_GIÁ_TRỊ(LỖI, "MÃ")
    ĐẶT THÔNG_BÁO: CHUỖI = LẤY_GIÁ_TRỊ(LỖI, "THÔNG_BÁO")
} LUÔN_THỰC_HIỆN {
    CHỜ(100)
}
```

Biến lỗi là BẢN_ĐỒ với hai khóa "MÃ" và "THÔNG_BÁO". LUÔN_THỰC_HIỆN luôn chạy khi rời khối, kể cả khi lỗi không được bắt. Lệnh dừng, thoát vòng lặp, bỏ qua lượt và trả về hàm vẫn giữ nguyên luồng điều khiển, không bị xem là lỗi có thể bắt.

## 10. Giới hạn an toàn và ngân sách

| Hạng mục | Giới hạn |
|---|---:|
| Mã nguồn | 32.768 ký tự |
| Số câu lệnh khai báo | 1.000 |
| Số lần LẶP | 1–100 |
| Số lượt LẶP_KHI tối đa | 900 |
| Độ sâu khối lệnh | 8 |
| Độ sâu gọi hàm | 16 |
| Tham số hàm | 16 |
| Phần tử trong một danh sách | 64 |
| Cặp khóa–giá trị trong một bản đồ | 64 |
| Số lượt thực thi thực tế | 5.000 |
| Tổng thời lượng chờ/thao tác tĩnh | 600.000 ms |
| Tọa độ pixel | 0–10.000 |
| CHỜ | 1–60.000 ms |
| NHẤN_GIỮ | 100–10.000 ms |
| VUỐT | 1–10.000 ms |
| Thời hạn CHỜ_ĐẾN_KHI | 1–60.000 ms |
| Chu kỳ CHỜ_ĐẾN_KHI | 50–5.000 ms |

Nút Dừng vẫn có thể hủy kịch bản khi chạy. Runtime có bộ đếm lệnh thực tế để chặn trường hợp hàm/vòng lặp lồng nhau vượt quá ngân sách tĩnh.

## 11. Ví dụ hoàn chỉnh

```text
// Khai báo dữ liệu và hàm
ĐẶT PIN_TỐI_THIỂU: SỐ = 20
ĐẶT TÊN_ỨNG_DỤNG: CHUỖI = "com.example.game"

ĐỊNH_NGHĨA_HÀM CO_THE_CHAY(): LUẬN_LÝ {
    TRẢ_VỀ PIN() >= PIN_TỐI_THIỂU VÀ CÓ_MẠNG()
}

// Mở ứng dụng nếu đủ điều kiện
NẾU (CO_THE_CHAY() VÀ ỨNG_DỤNG_ĐÃ_CÀI(TÊN_ỨNG_DỤNG)) {
    MỞ_ỨNG_DỤNG(TÊN_ỨNG_DỤNG)
    CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ(TÊN_ỨNG_DỤNG), 15000, 250)

    ĐẶT SỐ_LẦN = 3
    LẶP(SỐ_LẦN) {
        CHẠM(200, 400)
        CHỜ(500)
    }
} NGƯỢC_LẠI {
    QUAY_LẠI()
}
```

## 12. Những phần chưa được triển khai

Các mô-đun còn cần phát triển gồm OCR/nhận diện từ ảnh do người dùng chọn qua URI, khớp ảnh mẫu, so sánh khung hình, CSV, PATCH/multipart/download, hiệu chỉnh vùng an toàn cho tọa độ, kéo-thả/đa điểm, lịch và sự kiện nền, thư viện dùng chung, regex/ngày giờ, tô màu cú pháp, tự hoàn thành và debugger từng bước. Chỉ công bố từng lệnh sau khi có parser, runtime, kiểm tra quyền và unit tests tương ứng.

## 13. Quy trình chạy

1. Viết mã bằng cú pháp HOA và đúng dấu.
2. Nhấn **Kiểm tra mã**. Lỗi trả về mã lỗi, dòng và cột.
3. Nhấn **Chuẩn bị chạy** khi mã hợp lệ.
4. Tự bật AUTU-LIO trong Cài đặt Android > Trợ năng nếu chưa bật.
5. Chuyển sang ứng dụng mục tiêu rồi tự nhấn **Chạy** trên cửa sổ nổi.
6. Dùng Tạm dừng, Tiếp tục, Dừng hoặc đóng cửa sổ để điều khiển phiên chạy.

Bản nháp được mã hóa và lưu cục bộ. Gói phát hành được kiểm tra chữ ký/quyền, mã hóa phía Worker và chỉ được tải về thiết bị đã đăng ký có giấy phép hợp lệ.
