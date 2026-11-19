# NGÔN NGỮ AUTU-LIO — CÚ PHÁP CHUẨN 1.19

**Ngôn ngữ:** AUTU-LIO Script 1.19  
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

## 9. Đọc văn bản giao diện qua Trợ năng

AUTU-LIO chỉ đọc văn bản, nhãn và tọa độ được ứng dụng đích công khai qua cây Android Accessibility. Không có OCR, mô hình nhận diện ảnh, quét QR/mã vạch hoặc phát hiện vật thể.

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_VĂN_BẢN_MÀN_HÌNH() | Văn bản và nhãn công khai qua cây Trợ năng |
| ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH() | Danh sách cấu trúc khối/dòng, kèm tọa độ bounds |
| MÀN_HÌNH_CHỨA_CHỮ | MÀN_HÌNH_CHỨA_CHỮ("chuỗi") | ĐÚNG nếu nhãn truy cập có chuỗi cần tìm |
| TÌM_VỊ_TRÍ_CHỮ | TÌM_VỊ_TRÍ_CHỮ("chuỗi") | Danh sách [X, Y] là tâm vị trí tìm thấy |

Các hàm chỉ đọc dữ liệu mà ứng dụng đích đã công khai qua Trợ năng. Chúng không phân tích ảnh, không chạy OCR và không gửi ảnh hoặc âm thanh ra ngoài thiết bị. Nội dung game canvas, video hoặc thành phần tùy chỉnh không có nhãn có thể không đọc được.

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

