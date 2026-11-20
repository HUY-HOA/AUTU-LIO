# AUTU-LIO — Ngôn ngữ lập trình tự động hóa AUTU Script 1.20

**Phiên bản cú pháp:** `1.20`  
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

### Cử chỉ đa điểm

```text
CHẠM_ĐA_ĐIỂM(
    TẠO_DANH_SÁCH(TẠO_DANH_SÁCH(100, 200), TẠO_DANH_SÁCH(300, 200)),
    100
)
VUỐT_ĐA_ĐIỂM(
    TẠO_DANH_SÁCH(
        TẠO_DANH_SÁCH(200, 400, 150, 350),
        TẠO_DANH_SÁCH(400, 400, 450, 350)
    ),
    300
)
```

`CHẠM_ĐA_ĐIỂM` chạm đồng thời 2–5 điểm. `VUỐT_ĐA_ĐIỂM` nhận danh sách 2–5 đường; mỗi đường là dãy tọa độ X/Y phẳng, có 2–16 điểm. Các đường chạy đồng thời cùng thời lượng. Ứng dụng đích hoặc Android có thể từ chối thao tác đa chạm.

### Tọa độ DP và tỷ lệ màn hình

```text
ĐẶT X: SỐ = TỌA_ĐỘ_X_THEO_TỶ_LỆ(0.5)
ĐẶT Y: SỐ = TỌA_ĐỘ_Y_THEO_TỶ_LỆ(0.75)
CHẠM(X, Y)
ĐẶT KHOẢNG_CÁCH: SỐ = DP_SANG_PIXEL(24)
```

Dùng `MẬT_ĐỘ_MÀN_HÌNH()`, `DP_SANG_PIXEL(DP)`, `PIXEL_SANG_DP(PIXEL)`, `TỌA_ĐỘ_X_THEO_TỶ_LỆ(TỶ_LỆ)`, `TỌA_ĐỘ_Y_THEO_TỶ_LỆ(TỶ_LỆ)`, `TỶ_LỆ_X_THEO_TỌA_ĐỘ(X)` và `TỶ_LỆ_Y_THEO_TỌA_ĐỘ(Y)`. Tỷ lệ 0–1 ánh xạ từ pixel đầu đến pixel cuối của màn hình. Từ phiên bản 1.20 có thêm `TỌA_ĐỘ_X_VÙNG_AN_TOÀN(TỶ_LỆ)`, `TỌA_ĐỘ_Y_VÙNG_AN_TOÀN(TỶ_LỆ)`, `TỶ_LỆ_X_VÙNG_AN_TOÀN(X)` và `TỶ_LỆ_Y_VÙNG_AN_TOÀN(Y)` để quy đổi trong vùng an toàn theo system bars/cutout mà Android xác định được. `TỌA_ĐỘ_TỪ_HƯỚNG_GỐC(X,Y)` và `TỌA_ĐỘ_VỀ_HƯỚNG_GỐC(X,Y)` chuyển tọa độ giữa hướng tự nhiên ROTATION_0 và hệ tọa độ logic hiện tại, bao gồm thiết bị có hướng gốc ngang. Tọa độ đầu vào và đầu ra của hai hàm này là pixel nguyên; chuyển đổi không tự thay tham số CHẠM/VUỐT.

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
| `CHIỀU_RỘNG_DP()` / `CHIỀU_CAO_DP()` | 0 | Kích thước màn hình theo DP hiện tại |
| `TỶ_LỆ_KHUNG_HÌNH()` | 0 | Tỷ lệ chiều rộng pixel / chiều cao pixel |
| `HƯỚNG_MÀN_HÌNH()` | 0 | `NGANG`, `DỌC` hoặc `VUÔNG` |
| `XOAY_MÀN_HÌNH()` | 0 | Chỉ số rotation Android 0–3 |
| `GÓC_XOAY_MÀN_HÌNH()` | 0 | Góc xoay 0°, 90°, 180° hoặc 270° |
| `GÓI_ỨNG_DỤNG_ĐANG_MỞ()` | 0 | Package foreground gần nhất được gửi từ sự kiện Trợ năng |
| `HÀNG_SẢN_XUẤT()` / `MẪU_THIẾT_BỊ()` | 0 | Nhà sản xuất và model thiết bị |
| `PHIÊN_BẢN_ANDROID()` / `MỨC_API_ANDROID()` | 0 | Chuỗi phiên bản và mức API Android |
| `MÀN_HÌNH_BỊ_KHÓA()` | 0 | ĐÚNG khi Keyguard đang khóa màn hình |
| `THIẾT_BỊ_ĐƯỢC_BẢO_MẬT()` | 0 | ĐÚNG nếu thiết bị có khóa bảo mật được cấu hình |
| `THỜI_GIAN_MS()` | 0 | Unix time theo mili giây |

Thông tin hãng/model, phiên bản Android và trạng thái Keyguard được đọc cục bộ tại thiết bị, không gửi lên máy chủ. Các hàm Keyguard chỉ đọc trạng thái, không mở khóa hoặc thay đổi thiết lập bảo mật. Các hàm DP dùng density hiện tại; tỷ lệ khung hình và hướng được suy ra từ kích thước pixel thực. Chỉ số xoay 0–3 là rotation Android, góc xoay là 0°/90°/180°/270°. Package foreground chỉ có khi AccessibilityService nhận được sự kiện cửa sổ, nên có thể rỗng khi dịch vụ chưa bật hoặc chưa nhận sự kiện.
 
Các hàm thiết bị trả về thông tin Android hiện được dispatcher cung cấp. AUTU Script 1.20 bổ sung dữ liệu hiển thị theo DP, hướng/xoay màn hình và package foreground.

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

## 9. Văn bản giao diện qua Trợ năng và màu màn hình

AUTU-LIO không tích hợp OCR, nhận diện ảnh, quét QR/mã vạch hoặc phát hiện vật thể. Văn bản chỉ được đọc cục bộ từ cây Android Accessibility mà ứng dụng đang mở chủ động công khai.

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_VĂN_BẢN_MÀN_HÌNH() | Văn bản và nhãn có thể truy cập qua cây Trợ năng |
| MÀN_HÌNH_CHỨA_CHỮ | MÀN_HÌNH_CHỨA_CHỮ("Đăng nhập") | ĐÚNG nếu một nhãn được công khai có chứa chuỗi |
| TÌM_VỊ_TRÍ_CHỮ | TÌM_VỊ_TRÍ_CHỮ("Đăng nhập") | Danh sách [X, Y] là tâm bounds của nhãn tìm thấy |
| ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH() | Cấu trúc khối/dòng và tọa độ từ cây Trợ năng |

Cây Trợ năng không đảm bảo hiển thị nội dung game canvas, video, nội dung vẽ trực tiếp hoặc thành phần tùy chỉnh không cung cấp nhãn. Ảnh màn hình chỉ được xử lý cục bộ cho các lệnh màu RGB, không được gửi tới dịch vụ AI.

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

