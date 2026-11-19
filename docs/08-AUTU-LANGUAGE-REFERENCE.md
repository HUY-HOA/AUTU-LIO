# NGÔN NGỮ AUTU-LIO — CÚ PHÁP CHUẨN 1.17

**Ngôn ngữ:** AUTU-LIO Script 1.17  
**Quy tắc:** lệnh, từ khóa, hàm, kiểu và tên biến do người viết đặt đều phải VIẾT HOA. Từ khóa tiếng Việt phải có đầy đủ dấu.

Tài liệu tổng hợp cú pháp đã có trong parser và runtime. Không xem tính năng chưa triển khai là lệnh hợp lệ. Tên lệnh Android đã được chuẩn hóa ở phiên bản 1.11; các tên cũ được giữ làm bí danh tương thích.

## 1. Kiểu dữ liệu

| Kiểu | Giá trị |
|---|---|
| `SỐ` | Số nguyên, số thập phân hữu hạn |
| `CHUỖI` | Văn bản Unicode |
| `LUẬN_LÝ` | `ĐÚNG` hoặc `SAI` |
| `DANH_SÁCH` | Tối đa 64 phần tử số, chuỗi hoặc luận lý |
| `BẢN_ĐỒ` | Tối đa 64 cặp khóa–giá trị |
| `BẤT_KỲ` | Kiểu động được runtime hỗ trợ |

Ví dụ:

```text
ĐẶT SO_LAN: SỐ = 3
ĐẶT TEN_GOI: CHUỖI = "com.example.game"
ĐẶT DA_SAN_SANG: LUẬN_LÝ = ĐÚNG
ĐẶT CAC_GIA_TRI: DANH_SÁCH = TẠO_DANH_SÁCH(10, 20, 30)
```

## 2. Từ khóa điều khiển

`ĐẶT`, `NẾU`, `NGƯỢC_LẠI_NẾU`, `NGƯỢC_LẠI`, `LẶP`, `LẶP_KHI`, `LẶP_ĐẾN_KHI`, `LẶP_MỖI`, `TRONG`, `CHỜ_ĐẾN_KHI`, `THOÁT_LẶP`, `BỎ_QUA_LƯỢT`, `ĐỊNH_NGHĨA_HÀM`, `TRẢ_VỀ`, `ĐÚNG`, `SAI`, `VÀ`, `HOẶC`, `KHÔNG`.

## 3. Toán tử

- Số học: `+`, `-`, `*`, `/`, `%`, `^`.
- So sánh: `==`, `!=`, `>`, `>=`, `<`, `<=`.
- Logic: `VÀ`, `HOẶC`, `KHÔNG`.
- Dùng `(...)` để nhóm biểu thức. Lũy thừa kết hợp từ phải sang trái.

## 4. Điều kiện và vòng lặp

```text
NẾU (PIN() > 20 VÀ CÓ_MẠNG()) {
    CHẠM(200, 400)
} NGƯỢC_LẠI {
    CHỜ(500)
}

LẶP(3) {
    CHẠM(200, 400)
}

ĐẶT DEM = 0
LẶP_KHI (DEM < 5) {
    DEM = DEM + 1
}

LẶP_ĐẾN_KHI(DEM >= 10) {
    DEM = DEM + 1
}

ĐẶT DANH_SÁCH: DANH_SÁCH = TẠO_DANH_SÁCH(100, 200, 300)
LẶP_MỖI (TOA_DO_X TRONG DANH_SÁCH) {
    CHẠM(TOA_DO_X, 400)
    CHỜ(250)
}
```

`THOÁT_LẶP` thoát vòng lặp gần nhất. `BỎ_QUA_LƯỢT` bỏ phần còn lại của lượt hiện tại. `LẶP_MỖI` duyệt bản sao an toàn của danh sách, biến vòng lặp được khôi phục khi thoát.

## 5. Hàm tự định nghĩa

```text
ĐỊNH_NGHĨA_HÀM TINH_TONG(A: SỐ, B: SỐ): SỐ {
    TRẢ_VỀ A + B
}

ĐẶT KET_QUA: SỐ = TINH_TONG(10, 20)
```

Hàm phải khai báo ở cấp cao nhất, tối đa 16 tham số và 16 cấp gọi lồng nhau. Kiểu tham số và kiểu trả về có thể bỏ trống; khi khai báo thì runtime kiểm tra kiểu.

### Tệp và thư mục riêng của AUTU

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_TỆP | ĐỌC_TỆP("settings/config.txt") | Đọc văn bản UTF-8 |
| GHI_TỆP | GHI_TỆP("settings/config.txt", NỘI_DUNG) | Ghi/thay thế tệp UTF-8 |
| TỆP_TỒN_TẠI | TỆP_TỒN_TẠI("settings/config.txt") | Kiểm tra tệp |
| XÓA_TỆP | XÓA_TỆP("settings/old.txt") | Xóa một tệp |
| TẠO_THƯ_MỤC | TẠO_THƯ_MỤC("settings/backup") | Tạo thư mục |
| LIỆT_KÊ_TỆP | LIỆT_KÊ_TỆP("settings") | Danh sách tệp trực tiếp trong thư mục |
| ĐỌC_JSON_TỆP | ĐỌC_JSON_TỆP("settings/config.json") | Đọc và phân tích JSON |
| GHI_JSON_TỆP | GHI_JSON_TỆP("settings/config.json", GIÁ_TRỊ) | Chuyển giá trị thành JSON rồi ghi |

Tệp chỉ được lưu trong vùng dữ liệu riêng của AUTU-LIO, không truy cập tùy ý bộ nhớ chung hoặc thư mục của ứng dụng khác. Đường dẫn phải tương đối; không chấp nhận đường dẫn tuyệt đối, phân đoạn "..", "." hoặc symlink thoát khỏi vùng lưu trữ. Mỗi tệp tối đa 1 MiB. GHI_TỆP thay thế tệp theo cách an toàn; LIỆT_KÊ_TỆP chỉ liệt kê tệp trực tiếp, tối đa 128 mục.

Ví dụ:

```text
TẠO_THƯ_MỤC("settings")
GHI_JSON_TỆP("settings/config.json", TẠO_BẢN_ĐỒ("TÊN", "AUTU-LIO", "BẬT", ĐÚNG))
ĐẶT CẤU_HÌNH: BẢN_ĐỒ = ĐỌC_JSON_TỆP("settings/config.json")
ĐẶT TÊN: CHUỖI = LẤY_GIÁ_TRỊ(CẤU_HÌNH, "TÊN")
```

### JSON và dữ liệu lồng nhau

| Hàm / giá trị | Cú pháp | Kết quả |
|---|---|---|
| RỖNG | RỖNG | Giá trị null của JSON |
| PHÂN_TÍCH_JSON | PHÂN_TÍCH_JSON(CHUỖI_JSON) | Chuyển JSON thành giá trị AUTU |
| CHUYỂN_JSON | CHUYỂN_JSON(GIÁ_TRỊ) | Chuyển giá trị AUTU thành chuỗi JSON |

Danh sách và bản đồ có thể chứa nhau nhiều cấp. JSON hỗ trợ object, array, chuỗi Unicode, số hữu hạn, true/false và null. Giới hạn hiện tại: 65.536 ký tự JSON, 64 phần tử/khóa trong mỗi container, độ sâu 8 cấp, tổng 512 giá trị và chuỗi đơn tối đa 4.096 ký tự. Khóa object phải là chuỗi; khóa trùng bị từ chối. Số NaN/vô hạn, JSON sai cú pháp và dữ liệu vượt giới hạn sẽ báo lỗi. Lỗi phân tích JSON có thể xử lý bằng THỬ / BẮT_LỖI.

Ví dụ:

```text
ĐẶT CẤU_HÌNH: BẢN_ĐỒ = PHÂN_TÍCH_JSON("{\"TÊN\":\"AUTU\",\"VẬT_THỂ\":[{\"X\":120,\"Y\":240},null]}")
ĐẶT DANH_SÁCH: DANH_SÁCH = LẤY_GIÁ_TRỊ(CẤU_HÌNH, "VẬT_THỂ")
ĐẶT VẬT_THỂ: BẢN_ĐỒ = LẤY_PHẦN_TỬ(DANH_SÁCH, 0)
ĐẶT JSON_KẾT_QUẢ: CHUỖI = CHUYỂN_JSON(CẤU_HÌNH)
```

## 6. Hàm xử lý dữ liệu, danh sách và bản đồ

| Hàm | Cú pháp | Ý nghĩa |
|---|---|---|
| `SỐ` | `SỐ(GIA_TRI)` | Chuyển thành số |
| `CHUỖI` | `CHUỖI(GIA_TRI)` | Chuyển thành chuỗi |
| `LÀM_TRÒN` | `LÀM_TRÒN(SO)` | Làm tròn |
| `TUYỆT_ĐỐI` | `TUYỆT_ĐỐI(SO)` | Giá trị tuyệt đối |
| `ĐỘ_DÀI` | `ĐỘ_DÀI(CHUOI_HOAC_DANH_SACH)` | Số ký tự hoặc số phần tử |
| `NHỎ_NHẤT` | `NHỎ_NHẤT(A, B)` | Giá trị nhỏ hơn |
| `LỚN_NHẤT` | `LỚN_NHẤT(A, B)` | Giá trị lớn hơn |
| `CHỮ_HOA` | `CHỮ_HOA(CHUỖI)` | Chuyển thành chữ HOA Unicode |
| `CHỮ_THƯỜNG` | `CHỮ_THƯỜNG(CHUỖI)` | Chuyển thành chữ thường Unicode |
| `CẮT_KHOẢNG_TRẮNG` | `CẮT_KHOẢNG_TRẮNG(CHUỖI)` | Cắt khoảng trắng ở hai đầu |
| `CHỨA_CHUỖI` | `CHỨA_CHUỖI(A, B)` | Kiểm tra chuỗi con |
| `BẮT_ĐẦU_BẰNG` | `BẮT_ĐẦU_BẰNG(A, B)` | Kiểm tra tiền tố |
| `KẾT_THÚC_BẰNG` | `KẾT_THÚC_BẰNG(A, B)` | Kiểm tra hậu tố |
| `THAY_THẾ` | `THAY_THẾ(A, CŨ, MỚI)` | Thay chuỗi con |
| `TÁCH_CHUỖI` | `TÁCH_CHUỖI(CHUỖI, PHÂN_CÁCH)` | Tách thành danh sách, tối đa 64 |
| `NỐI_CHUỖI` | `NỐI_CHUỖI(DANH_SÁCH, PHÂN_CÁCH)` | Nối danh sách chuỗi |
| `KÝ_TỰ_TẠI` | `KÝ_TỰ_TẠI(CHUỖI, VỊ_TRÍ)` | Lấy code point, vị trí từ 0 |
| `CẮT_CHUỖI` | `CẮT_CHUỖI(CHUỖI, VỊ_TRÍ, ĐỘ_DÀI)` | Cắt chuỗi theo code point |
| `TẠO_DANH_SÁCH` | `TẠO_DANH_SÁCH(A, B, ...)` | Tạo danh sách, 0–64 phần tử |
| `LẤY_PHẦN_TỬ` | `LẤY_PHẦN_TỬ(DS, CHI_SO)` | Lấy phần tử |
| `THÊM_PHẦN_TỬ` | `THÊM_PHẦN_TỬ(DS, GIA_TRI)` | Thêm phần tử |
| `ĐẶT_PHẦN_TỬ` | `ĐẶT_PHẦN_TỬ(DS, CHI_SO, GIA_TRI)` | Thay phần tử |
| `XÓA_PHẦN_TỬ` | `XÓA_PHẦN_TỬ(DS, CHI_SO)` | Xóa và trả về phần tử |
| `CÓ_PHẦN_TỬ` | `CÓ_PHẦN_TỬ(DS, GIA_TRI)` | Kiểm tra phần tử |
| `TẠO_BẢN_ĐỒ` | `TẠO_BẢN_ĐỒ(KHOA, GIA_TRI, ...)` | Tạo bản đồ, tối đa 64 cặp, số tham số chẵn |
| `LẤY_GIÁ_TRỊ` | `LẤY_GIÁ_TRỊ(BD, KHOA)` | Lấy giá trị theo khóa |
| `ĐẶT_GIÁ_TRỊ` | `ĐẶT_GIÁ_TRỊ(BD, KHOA, GIA_TRI)` | Thêm hoặc cập nhật giá trị |
| `XÓA_KHÓA` | `XÓA_KHÓA(BD, KHOA)` | Xóa và trả về giá trị cũ |
| `CÓ_KHÓA` | `CÓ_KHÓA(BD, KHOA)` | Kiểm tra khóa |
| `DANH_SÁCH_KHÓA` | `DANH_SÁCH_KHÓA(BD)` | Lấy danh sách khóa |

Chỉ số danh sách và vị trí chuỗi bắt đầu từ 0. KÝ_TỰ_TẠI/CẮT_CHUỖI dùng code point Unicode. TÁCH_CHUỖI từ chối dấu phân cách rỗng và không trả quá 64 phần tử. Phần tử chỉ nhận số hữu hạn, chuỗi hoặc ĐÚNG/SAI. Truy cập ngoài phạm vi hoặc truyền sai kiểu sẽ báo lỗi runtime.

## 7. HTTP/HTTPS và API

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| GỬI_YÊU_CẦU_HTTP | GỬI_YÊU_CẦU_HTTP(PHƯƠNG_THỨC, URL, THÂN, TIÊU_ĐỀ, TIMEOUT_MS) | BẢN_ĐỒ phản hồi HTTP |

Ví dụ GET:

```text
ĐẶT PHẢN_HỒI: BẢN_ĐỒ = GỬI_YÊU_CẦU_HTTP(
    "GET",
    "https://api.example.com/status",
    RỖNG,
    TẠO_BẢN_ĐỒ("Accept", "application/json"),
    10000
)
ĐẶT MÃ_TRẠNG_THÁI: SỐ = LẤY_GIÁ_TRỊ(PHẢN_HỒI, "TRẠNG_THÁI")
ĐẶT NỘI_DUNG: CHUỖI = LẤY_GIÁ_TRỊ(PHẢN_HỒI, "NỘI_DUNG")
NẾU (LẤY_GIÁ_TRỊ(PHẢN_HỒI, "THÀNH_CÔNG")) {
    ĐẶT DỮ_LIỆU: BẢN_ĐỒ = PHÂN_TÍCH_JSON(NỘI_DUNG)
}
```

Ví dụ POST JSON:

```text
ĐẶT THÂN: CHUỖI = CHUYỂN_JSON(TẠO_BẢN_ĐỒ("TÊN", "AUTU-LIO"))
ĐẶT PHẢN_HỒI: BẢN_ĐỒ = GỬI_YÊU_CẦU_HTTP(
    "POST",
    "https://api.example.com/items",
    THÂN,
    TẠO_BẢN_ĐỒ("Authorization", "Bearer TOKEN", "Content-Type", "application/json"),
    15000
)
```

Kết quả trả về gồm `TRẠNG_THÁI`, `THÀNH_CÔNG`, `NỘI_DUNG`, `TIÊU_ĐỀ` và `URL`. Tiêu đề yêu cầu là BẢN_ĐỒ; giá trị tiêu đề chỉ được là chuỗi, số hoặc luận lý. Thân yêu cầu là chuỗi hoặc RỖNG. Chỉ hỗ trợ GET, POST, PUT, DELETE và HEAD; thân yêu cầu chỉ dùng với POST/PUT/DELETE.

Chỉ chấp nhận URL HTTPS, không nhúng user/password và không theo chuyển hướng tự động. Tối đa 32 tiêu đề và 16.384 ký tự tiêu đề; dữ liệu gửi và phản hồi tối đa 1 MiB mỗi chiều; timeout từ 100 đến 30.000 ms. Không đặt các tiêu đề hệ thống như Host, Content-Length hoặc Connection. Không ghi token/khóa truy cập vào nhật ký hoặc mã chia sẻ. Lỗi mạng, timeout, URL, header và giới hạn dữ liệu làm phát sinh lỗi runtime; có thể xử lý bằng THỬ / BẮT_LỖI.

## 8. Hàm truy vấn Android

`PIN()`, `PHẦN_TRĂM_PIN()`, `ĐANG_SẠC()`, `CÓ_MẠNG()`, `MÀN_HÌNH_SÁNG()`, `ỨNG_DỤNG_ĐÃ_CÀI("TÊN_GÓI")`, `ỨNG_DỤNG_ĐANG_MỞ("TÊN_GÓI")`, `CHIỀU_RỘNG_MÀN_HÌNH()`, `CHIỀU_CAO_MÀN_HÌNH()`, `THỜI_GIAN_MS()`.

## 9. Đọc văn bản và OCR màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_VĂN_BẢN_MÀN_HÌNH() | Trả về văn bản tìm được trên màn hình |
| ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH() | Danh sách khối OCR và từng dòng, gồm văn bản cùng tọa độ X/Y, rộng, cao |
| MÀN_HÌNH_CHỨA_CHỮ | MÀN_HÌNH_CHỨA_CHỮ("chuỗi") | ĐÚNG nếu màn hình có chuỗi cần tìm |
| TÌM_VỊ_TRÍ_CHỮ | TÌM_VỊ_TRÍ_CHỮ("chuỗi") | Danh sách [X, Y] là tâm vị trí tìm thấy; danh sách rỗng nếu không có |

AUTU-LIO đọc cây giao diện trợ năng và dùng OCR trên ảnh màn hình khi Android hỗ trợ. OCR được gọi theo yêu cầu của kịch bản, kết quả chỉ lưu tạm trong bộ nhớ. Từ Android 11/API 30, ảnh màn hình được lấy qua AccessibilityService; Android cũ hơn chỉ có thể đọc nội dung mà cây giao diện trợ năng cung cấp. Nội dung trong màn hình bảo mật, ứng dụng chặn chụp hoặc thành phần đồ họa không được Android công khai có thể không đọc được. Kết quả OCR có thể sai; cần kiểm tra trước khi thực hiện thao tác quan trọng.

### OCR theo vùng màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_VÙNG | ĐỌC_VĂN_BẢN_VÙNG(X, Y, RỘNG, CAO) | Đọc chữ bên trong vùng pixel |
| MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG | MÀN_HÌNH_CHỨA_CHỮ_TRONG_VÙNG("OK", X, Y, RỘNG, CAO) | Kiểm tra chuỗi trong vùng |
| TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG | TÌM_VỊ_TRÍ_CHỮ_TRONG_VÙNG("OK", X, Y, RỘNG, CAO) | Trả về tâm chữ theo tọa độ pixel toàn màn hình |

Tọa độ X/Y bắt đầu từ góc trái trên. RỘNG/CAO phải là số nguyên dương; toàn bộ vùng phải nằm trong ảnh chụp màn hình thực tế. OCR vùng sử dụng crop của ảnh màn hình và model Text Recognition on-device.

### Nhận diện hình ảnh và mã

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH | NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH() | Danh sách nhãn ảnh theo độ tin cậy |
| MÀN_HÌNH_CHỨA_NHÃN_ẢNH | MÀN_HÌNH_CHỨA_NHÃN_ẢNH("Phone") | Kiểm tra nhãn có chứa từ khóa |
| QUÉT_MÃ_QR_MÀN_HÌNH | QUÉT_MÃ_QR_MÀN_HÌNH() | Danh sách dữ liệu QR đã giải mã |
| QUÉT_MÃ_VẠCH_MÀN_HÌNH | QUÉT_MÃ_VẠCH_MÀN_HÌNH() | Danh sách dữ liệu mã vạch đã giải mã |
| CHI_TIẾT_MÃ_QR_MÀN_HÌNH | CHI_TIẾT_MÃ_QR_MÀN_HÌNH() | Danh sách giá trị, định dạng, loại nội dung và hộp tọa độ của mã QR |
| CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH | CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH() | Danh sách chi tiết mã vạch của các định dạng được hỗ trợ |

Các hàm CHI_TIẾT_MÃ_QR_MÀN_HÌNH và CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH trả về GIÁ_TRỊ_GỐC, GIÁ_TRỊ_HIỂN_THỊ, ĐỊNH_DẠNG, LOẠI_NỘI_DUNG và X/Y/RỘNG/CAO nếu ML Kit xác định được hộp giới hạn. Dữ liệu quét chỉ được trả về cho kịch bản; ứng dụng không tự mở liên kết, gọi điện hoặc kết nối Wi‑Fi.

Mô hình gắn nhãn ảnh cơ bản nhận diện nhãn tổng quát, không phải mô hình phát hiện mọi vật thể hay xác định tọa độ từng vật. Quét mã cần đủ độ phân giải và độ nét; giá trị trả về là nội dung giải mã, không tự mở liên kết hoặc thực hiện hành động.

### Trợ năng: tìm và thao tác thành phần giao diện

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| TÌM_THÀNH_PHẦN_VĂN_BẢN | TÌM_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập") | Kiểm tra văn bản hoặc mô tả nội dung có trên cây trợ năng |
| NHẤN_THÀNH_PHẦN_VĂN_BẢN | NHẤN_THÀNH_PHẦN_VĂN_BẢN("Đăng nhập") | Thực hiện ACTION_CLICK trên node hoặc tổ tiên có thể nhấn |
| NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN | NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN("Tên", "LIO") | Gửi ACTION_SET_TEXT tới node có thể nhập |
| TÌM_TỌA_ĐỘ_THÀNH_PHẦN | TÌM_TỌA_ĐỘ_THÀNH_PHẦN("Đăng nhập") | Trả về [X, Y] tâm bounds theo pixel màn hình |
| TÌM_THÀNH_PHẦN_THEO_ID | TÌM_THÀNH_PHẦN_THEO_ID("login_button") | Tìm resource ID đầy đủ hoặc phần tên cuối |
| NHẤN_THÀNH_PHẦN_THEO_ID | NHẤN_THÀNH_PHẦN_THEO_ID("login_button") | Nhấn phần tử theo resource ID |
| NHẬP_VĂN_BẢN_THEO_ID | NHẬP_VĂN_BẢN_THEO_ID("username", "AUTU") | Nhập văn bản vào phần tử theo resource ID |
| CUỘN_XUỐNG / CUỘN_LÊN | CUỘN_XUỐNG() / CUỘN_LÊN() | Cuộn node đầu tiên có thể cuộn |

Các thao tác này chỉ tác động node mà ứng dụng đích công khai qua AccessibilityService. Nếu ứng dụng không cung cấp node, phần tử không bật, không thể nhấn/nhập hoặc nội dung nằm trong canvas/game, hàm sẽ trả về SAI/ danh sách rỗng. Không có cách bảo đảm mọi ứng dụng đều phơi bày cây giao diện.

### Nhận diện vật thể và hộp giới hạn

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH | PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH() | Danh sách tối đa 5 bản đồ vật thể |
| MÀN_HÌNH_CÓ_VẬT_THỂ | MÀN_HÌNH_CÓ_VẬT_THỂ("Food") | Kiểm tra nhãn phân loại có chứa từ khóa |
| TÌM_TỌA_ĐỘ_VẬT_THỂ | TÌM_TỌA_ĐỘ_VẬT_THỂ("Food") | Trả về [X,Y] tâm hộp giới hạn đầu tiên khớp nhãn |
| ĐẾM_VẬT_THỂ_MÀN_HÌNH | ĐẾM_VẬT_THỂ_MÀN_HÌNH() | Số vật thể phát hiện trong ảnh |

Mỗi bản đồ vật thể gồm X, Y, RỘNG, CAO theo pixel ảnh chụp màn hình và NHÃN là danh sách bản đồ có TÊN, CHỈ_SỐ, ĐỘ_TIN_CẬY. Bộ nhận diện ảnh tĩnh bật phân loại và phát hiện nhiều vật thể, tối đa 5 vật thể. Phân loại mặc định chỉ đưa ra nhóm tổng quát như thời trang, thực phẩm, đồ gia dụng, địa điểm hoặc cây cối; không phải mô hình nhận diện tên cụ thể mọi vật thể. Không có tracking ID trong chế độ ảnh tĩnh. Kết quả phụ thuộc kích thước/vị trí vật thể, độ nét và cảnh ảnh.


### Phân tích màu RGB trên màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| MÀU_TẠI | MÀU_TẠI(X, Y) | Bản đồ ĐỎ, XANH_LÁ, XANH_DƯƠNG, ĐỘ_TRONG_SUỐT và HEX |
| MÀN_HÌNH_CÓ_MÀU | MÀN_HÌNH_CÓ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ) | ĐÚNG nếu ảnh mẫu có pixel gần màu chỉ định |
| TÌM_VỊ_TRÍ_MÀU | TÌM_VỊ_TRÍ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ) | Tọa độ gần đúng [X, Y] của điểm khớp đầu tiên |
| TỶ_LỆ_MÀU_TRONG_VÙNG | TỶ_LỆ_MÀU_TRONG_VÙNG(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ, X, Y, RỘNG, CAO) | Phần trăm pixel mẫu gần màu chỉ định trong vùng |

Mỗi kênh RGB và SAI_SỐ là số nguyên từ 0 đến 255. SAI_SỐ áp dụng riêng cho từng kênh màu. Tọa độ là pixel tính từ góc trên bên trái. Phân tích màn hình dùng ảnh chụp tại thiết bị và lấy mẫu tối đa 262.144 điểm toàn màn hình hoặc 65.536 điểm trong vùng; tỷ lệ trả về là ước lượng, không phải phép đếm chính xác từng pixel. Không thể đọc màn hình bị Android hoặc ứng dụng đích chặn chụp.

Ví dụ:

```text
ĐẶT MAU_NUT: BẢN_ĐỒ = MÀU_TẠI(300, 500)
NẾU (MÀN_HÌNH_CÓ_MÀU(0, 180, 80, 12)) {
    ĐẶT TOA_DO: DANH_SÁCH = TÌM_VỊ_TRÍ_MÀU(0, 180, 80, 12)
    CHẠM(LẤY_PHẦN_TỬ(TOA_DO, 0), LẤY_PHẦN_TỬ(TOA_DO, 1))
}
ĐẶT TY_LE_XANH: SỐ = TỶ_LỆ_MÀU_TRONG_VÙNG(0, 180, 80, 12, 100, 200, 400, 300)
```

## 10. SQLite riêng của AUTU-LIO

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| SQL_THỰC_THI | SQL_THỰC_THI(CÂU_SQL, DANH_SÁCH_THAM_SỐ) | Bản đồ SỐ_DÒNG và ID_MỚI |
| SQL_TRUY_VẤN | SQL_TRUY_VẤN(CÂU_SELECT, DANH_SÁCH_THAM_SỐ) | Danh sách bản đồ, mỗi bản đồ là một dòng |

Ví dụ:

```text
SQL_THỰC_THI("CREATE TABLE IF NOT EXISTS GHI_CHU (ID INTEGER PRIMARY KEY, TEN TEXT)", TẠO_DANH_SÁCH())
SQL_THỰC_THI("INSERT INTO GHI_CHU (TEN) VALUES (?)", TẠO_DANH_SÁCH("Việc cần làm"))
ĐẶT CAC_DONG: DANH_SÁCH = SQL_TRUY_VẤN("SELECT ID, TEN FROM GHI_CHU WHERE ID > ?", TẠO_DANH_SÁCH(0))
```

Cơ sở dữ liệu được lưu trong vùng riêng của AUTU-LIO; script không chọn đường dẫn, mở cơ sở dữ liệu bên ngoài hoặc gắn thêm database. SQL_TRUY_VẤN chỉ nhận một câu SELECT. SQL_THỰC_THI chỉ nhận CREATE TABLE/INDEX, ALTER TABLE, DROP TABLE/INDEX, INSERT, UPDATE, DELETE hoặc REPLACE. Tham số phải dùng dấu hỏi không đánh số `?`; số dấu hỏi phải khớp danh sách tham số. Mỗi câu chỉ được có một lệnh; cấm dấu chấm phẩy, chú thích SQL, tham số có tên, ATTACH/DETACH, PRAGMA và các lệnh quản trị bị giới hạn. Mỗi lệnh thay đổi dữ liệu được chạy trong transaction nội bộ.

Giới hạn: câu SQL tối đa 8.192 ký tự; 64 tham số; kết quả tối đa 256 dòng, 64 cột và 1 MiB văn bản. Một ô văn bản hoặc tham số chuỗi tối đa 4.096 ký tự. SQLite không nhận tham số nhị phân, danh sách hoặc bản đồ. Đây là SQLite nội bộ của AUTU-LIO, không thay thế công cụ quản trị database tổng quát.

## 11. Tọa độ DP và tỷ lệ màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| MẬT_ĐỘ_MÀN_HÌNH | MẬT_ĐỘ_MÀN_HÌNH() | Hệ số density hiện tại của màn hình |
| DP_SANG_PIXEL | DP_SANG_PIXEL(DP) | Đổi DP sang pixel theo density hiện tại, làm tròn số nguyên |
| PIXEL_SANG_DP | PIXEL_SANG_DP(PIXEL) | Đổi pixel sang DP |
| TỌA_ĐỘ_X_THEO_TỶ_LỆ | TỌA_ĐỘ_X_THEO_TỶ_LỆ(TỶ_LỆ) | Tỷ lệ 0–1 thành tọa độ X theo chiều rộng hiện tại |
| TỌA_ĐỘ_Y_THEO_TỶ_LỆ | TỌA_ĐỘ_Y_THEO_TỶ_LỆ(TỶ_LỆ) | Tỷ lệ 0–1 thành tọa độ Y theo chiều cao hiện tại |
| TỶ_LỆ_X_THEO_TỌA_ĐỘ | TỶ_LỆ_X_THEO_TỌA_ĐỘ(X) | Đổi tọa độ X pixel sang tỷ lệ 0–1 |
| TỶ_LỆ_Y_THEO_TỌA_ĐỘ | TỶ_LỆ_Y_THEO_TỌA_ĐỘ(Y) | Đổi tọa độ Y pixel sang tỷ lệ 0–1 |

Ví dụ:

```text
ĐẶT X: SỐ = TỌA_ĐỘ_X_THEO_TỶ_LỆ(0.5)
ĐẶT Y: SỐ = TỌA_ĐỘ_Y_THEO_TỶ_LỆ(0.75)
CHẠM(X, Y)
ĐẶT X_DP: SỐ = PIXEL_SANG_DP(X)
```

Tỷ lệ 0 ứng với pixel đầu tiên, tỷ lệ 1 ứng với pixel cuối cùng trên trục tương ứng. Các hàm đọc kích thước màn hình tại thời điểm được gọi nên có thể dùng lại cùng một kịch bản trên nhiều độ phân giải. Chúng không tự hiệu chỉnh status bar, navigation bar, vùng camera cutout hoặc vùng bị ứng dụng che; kết quả là hệ tọa độ màn hình mà AccessibilityService đang sử dụng.

### Thao tác đa điểm và đa ngón

| Lệnh | Cú pháp | Giới hạn |
|---|---|---|
| CHẠM_ĐA_ĐIỂM | CHẠM_ĐA_ĐIỂM(DANH_SÁCH_ĐIỂM, MS) | Chạm đồng thời từ 2 đến 5 điểm; mỗi điểm là [X, Y] |
| VUỐT_ĐA_ĐIỂM | VUỐT_ĐA_ĐIỂM(DANH_SÁCH_ĐƯỜNG, MS) | Vuốt đồng thời từ 2 đến 5 đường; mỗi đường chứa 2–16 cặp X/Y |

Ví dụ thu phóng hai ngón:

```text
VUỐT_ĐA_ĐIỂM(
    TẠO_DANH_SÁCH(
        TẠO_DANH_SÁCH(200, 400, 150, 350),
        TẠO_DANH_SÁCH(400, 400, 450, 350)
    ),
    300
)
```

Các đường bắt đầu cùng thời điểm và có cùng thời lượng. Tọa độ được kiểm tra theo giới hạn runtime rồi theo kích thước màn hình thực tế. Android/ứng dụng đích có thể hủy hoặc từ chối cử chỉ đa điểm; thiết bị và ứng dụng không hỗ trợ đa chạm sẽ không thực hiện được. Lệnh không mô phỏng phần cứng cảm ứng và không vượt qua giới hạn bảo mật Android.

## 12. Lệnh Android

`CHẠM(X,Y)`, `NHẤN_GIỮ(X,Y,MS)`, `VUỐT(X1,Y1,X2,Y2,MS)`, `CHỜ(MS)`, `MỞ_ỨNG_DỤNG("TÊN_GÓI")`, `QUAY_LẠI()`, `VỀ_MÀN_HÌNH_CHÍNH()`, `MỞ_ỨNG_DỤNG_GẦN_ĐÂY()`, `MỞ_THANH_THÔNG_BÁO()`, `MỞ_BẢNG_CÀI_ĐẶT_NHANH()`, `MỞ_HỘP_THOẠI_NGUỒN()`, `KHÓA_MÀN_HÌNH()`, `BẬT_TẮT_CHIA_MÀN_HÌNH()`.

Các thao tác cần AccessibilityService hoạt động và chịu giới hạn Android. Tọa độ phải nằm trong màn hình thực tế; hành động bị Android từ chối sẽ làm runtime báo lỗi.

## 14. Xử lý lỗi có cấu trúc

Cú pháp:

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

Biến trong BẮT_LỖI là BẢN_ĐỒ gồm khóa CHUỖI "MÃ" và "THÔNG_BÁO". BẮT_LỖI bắt lỗi thực thi của kịch bản; không bắt lệnh dừng, thoát vòng lặp, bỏ qua lượt hoặc trả về hàm. LUÔN_THỰC_HIỆN luôn chạy khi rời khối THỬ, kể cả khi lỗi không được xử lý. Nếu LUÔN_THỰC_HIỆN phát sinh lỗi mới, lỗi mới được truyền ra ngoài. Có thể dùng THỬ với LUÔN_THỰC_HIỆN mà không có BẮT_LỖI.

## 15. Giới hạn thực thi

| Hạng mục | Giới hạn |
|---|---:|
| Mã nguồn | 32.768 ký tự |
| Câu lệnh khai báo | 1.000 |
| Lặp LẶP | 100 lượt |
| Lặp LẶP_KHI | 900 lượt |
| Lồng khối | 8 cấp |
| Tham số hàm | 16 |
| Gọi hàm lồng nhau | 16 cấp |
| Danh sách | 64 phần tử |
| Lượt thực thi | 5.000 |
| Thời lượng tĩnh | 10 phút |

## 16. Chưa hỗ trợ

AUTU Script 1.13 hiện có kho tệp riêng, HTTP(S) giới hạn, SQLite nội bộ, OCR màn hình/vùng, nhận diện nhãn ảnh, vật thể, QR/mã vạch và phân tích màu RGB lấy mẫu. Phần còn thiếu gồm nhập ảnh qua bộ chọn tệp/URI để OCR và nhận diện, khớp ảnh mẫu, so sánh khung hình, CSV, PATCH/multipart/download, đoạn tọa độ DP/tương đối cơ bản đã có; hiệu chỉnh vùng an toàn, kéo-thả/đa điểm, sự kiện và lịch nền, module dùng chung, regex/ngày giờ và debugger từng bước. Các phần này chưa phải lệnh hợp lệ cho đến khi có parser, runtime, kiểm tra quyền, kiểm thử và tài liệu đầy đủ.

Tài liệu chi tiết và quy trình chạy nằm trong [docs/03-script-editor.md](03-script-editor.md).
