# NGÔN NGỮ AUTU-LIO — CÚ PHÁP CHUẨN 1.5

**Ngôn ngữ:** AUTU-LIO Script 1.5  
**Quy tắc:** lệnh, từ khóa, hàm, kiểu và tên biến do người viết đặt đều phải VIẾT HOA. Từ khóa tiếng Việt phải có đầy đủ dấu.

Tài liệu tổng hợp cú pháp đã có trong parser và runtime. Không xem tính năng chưa triển khai là lệnh hợp lệ. Tên lệnh Android đã được chuẩn hóa ở phiên bản 1.5; các tên cũ được giữ làm bí danh tương thích.

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

## 7. Hàm truy vấn Android

`PIN()`, `PHẦN_TRĂM_PIN()`, `ĐANG_SẠC()`, `CÓ_MẠNG()`, `MÀN_HÌNH_SÁNG()`, `ỨNG_DỤNG_ĐÃ_CÀI("TÊN_GÓI")`, `ỨNG_DỤNG_ĐANG_MỞ("TÊN_GÓI")`, `CHIỀU_RỘNG_MÀN_HÌNH()`, `CHIỀU_CAO_MÀN_HÌNH()`, `THỜI_GIAN_MS()`.

## 8. Đọc văn bản và OCR màn hình

| Hàm | Cú pháp | Kết quả |
|---|---|---|
| ĐỌC_VĂN_BẢN_MÀN_HÌNH | ĐỌC_VĂN_BẢN_MÀN_HÌNH() | Trả về văn bản tìm được trên màn hình |
| MÀN_HÌNH_CHỨA_CHỮ | MÀN_HÌNH_CHỨA_CHỮ("chuỗi") | ĐÚNG nếu màn hình có chuỗi cần tìm |
| TÌM_VỊ_TRÍ_CHỮ | TÌM_VỊ_TRÍ_CHỮ("chuỗi") | Danh sách [X, Y] là tâm vị trí tìm thấy; danh sách rỗng nếu không có |

AUTU-LIO đọc cây giao diện trợ năng và dùng OCR trên ảnh màn hình khi Android hỗ trợ. OCR được gọi theo yêu cầu của kịch bản, kết quả chỉ lưu tạm trong bộ nhớ. Từ Android 11/API 30, ảnh màn hình được lấy qua AccessibilityService; Android cũ hơn chỉ có thể đọc nội dung mà cây giao diện trợ năng cung cấp. Nội dung trong màn hình bảo mật, ứng dụng chặn chụp hoặc thành phần đồ họa không được Android công khai có thể không đọc được. Kết quả OCR có thể sai; cần kiểm tra trước khi thực hiện thao tác quan trọng.

## 9. Lệnh Android

`CHẠM(X,Y)`, `NHẤN_GIỮ(X,Y,MS)`, `VUỐT(X1,Y1,X2,Y2,MS)`, `CHỜ(MS)`, `MỞ_ỨNG_DỤNG("TÊN_GÓI")`, `QUAY_LẠI()`, `VỀ_MÀN_HÌNH_CHÍNH()`, `MỞ_ỨNG_DỤNG_GẦN_ĐÂY()`, `MỞ_THANH_THÔNG_BÁO()`, `MỞ_BẢNG_CÀI_ĐẶT_NHANH()`, `MỞ_HỘP_THOẠI_NGUỒN()`, `KHÓA_MÀN_HÌNH()`, `BẬT_TẮT_CHIA_MÀN_HÌNH()`.

Các thao tác cần AccessibilityService hoạt động và chịu giới hạn Android. Tọa độ phải nằm trong màn hình thực tế; hành động bị Android từ chối sẽ làm runtime báo lỗi.

## 9. Giới hạn thực thi

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

## 10. Chưa hỗ trợ

Bản đồ khóa–giá trị, xử lý lỗi TRY/CATCH do người viết định nghĩa, lịch/sự kiện nền, OCR, nhận diện ảnh, đọc nội dung ứng dụng, tọa độ tương đối, đa điểm và thư viện hàm dùng chung chưa được tích hợp đầy đủ. Không dùng chúng như lệnh hợp lệ.

Tài liệu chi tiết và quy trình chạy nằm trong [docs/03-script-editor.md](03-script-editor.md).
