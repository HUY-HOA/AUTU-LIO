# AUTU-LIO — Ngôn ngữ lập trình tự động hóa AUTU Script 1.1

**Phiên bản cú pháp:** `1.1`  
**Quy tắc bắt buộc:** từ khóa, lệnh, hàm, kiểu và tên biến do người viết đặt đều phải viết HOA. Từ khóa tiếng Việt phải có đúng dấu. Không chấp nhận bí danh lệnh tiếng Anh hoặc cách viết thường.  
**Bộ biên dịch:** `ScriptCompiler.kt`  
**Bộ thực thi:** `ScriptExecutionEngine.kt`

AUTU Script là ngôn ngữ kịch bản chuyên cho tự động hóa Android trong AUTU-LIO. Mã được phân tích thành cấu trúc lệnh an toàn, kiểm tra ngân sách rồi mới được runtime thực thi. Không có `eval`, shell, reflection hay thực thi Kotlin/Java tùy ý.

## 1. Quy tắc viết mã

- Lệnh và từ khóa phải viết HOA, đúng dấu: `NẾU`, `LẶP`, `CHẠM`, `NHẤN_GIỮ`.
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
| `ỨNG_DỤNG_GẦN_ĐÂY()` | không | Mở danh sách ứng dụng gần đây |
| `MỞ_THÔNG_BÁO()` | không | Mở ngăn thông báo |
| `MỞ_CÀI_ĐẶT_NHANH()` | không | Mở bảng cài đặt nhanh |
| `HIỆN_MENU_NGUỒN()` | không | Hiện menu nguồn |
| `KHÓA_MÀN_HÌNH()` | không | Khóa màn hình nếu Android hỗ trợ |
| `CHIA_MÀN_HÌNH()` | không | Chuyển chế độ chia màn hình nếu hệ thống hỗ trợ |

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
| `TẠO_DANH_SÁCH(...)` | 0–64 | Tạo danh sách mới |
| `ĐỘ_DÀI(DANH_SÁCH)` | 1 | Số phần tử trong danh sách |
| `LẤY_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ)` | 2 | Lấy phần tử theo chỉ số bắt đầu từ 0 |
| `THÊM_PHẦN_TỬ(DANH_SÁCH, GIÁ_TRỊ)` | 2 | Thêm phần tử, trả về số lượng mới |
| `ĐẶT_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ, GIÁ_TRỊ)` | 3 | Thay phần tử theo chỉ số |
| `XÓA_PHẦN_TỬ(DANH_SÁCH, CHỈ_SỐ)` | 2 | Xóa và trả về phần tử đã xóa |
| `CÓ_PHẦN_TỬ(DANH_SÁCH, GIÁ_TRỊ)` | 2 | Kiểm tra phần tử có trong danh sách |

Danh sách chỉ nhận số hữu hạn, chuỗi hoặc ĐÚNG/SAI; chỉ số bắt đầu từ 0. Các thao tác truy cập/sửa/xóa ngoài phạm vi sẽ báo lỗi runtime.

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

Các hàm thiết bị chỉ trả về thông tin Android hiện được dispatcher cung cấp. Không có hàm đọc cây giao diện hoặc văn bản ứng dụng.

## 8. Giới hạn an toàn và ngân sách

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
| Số lượt thực thi thực tế | 5.000 |
| Tổng thời lượng chờ/thao tác tĩnh | 600.000 ms |
| Tọa độ pixel | 0–10.000 |
| CHỜ | 1–60.000 ms |
| NHẤN_GIỮ | 100–10.000 ms |
| VUỐT | 1–10.000 ms |
| Thời hạn CHỜ_ĐẾN_KHI | 1–60.000 ms |
| Chu kỳ CHỜ_ĐẾN_KHI | 50–5.000 ms |

Nút Dừng vẫn có thể hủy kịch bản khi chạy. Runtime có bộ đếm lệnh thực tế để chặn trường hợp hàm/vòng lặp lồng nhau vượt quá ngân sách tĩnh.

## 9. Ví dụ hoàn chỉnh

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

## 10. Những phần chưa thuộc AUTU Script 1.1

Phiên bản 1.1 **chưa** có bản đồ dữ liệu dạng khóa–giá trị, ném-bắt lỗi TRY/CATCH, lập lịch sự kiện, nhận diện ảnh, OCR, chụp màn hình hoặc thao tác đa điểm. Không dùng lệnh giả cho những tính năng này. Chỉ thêm chúng khi có AST, kiểm tra quyền, runtime Android và unit tests tương ứng.

## 11. Quy trình chạy

1. Viết mã bằng cú pháp HOA và đúng dấu.
2. Nhấn **Kiểm tra mã**. Lỗi trả về mã lỗi, dòng và cột.
3. Nhấn **Chuẩn bị chạy** khi mã hợp lệ.
4. Tự bật AUTU-LIO trong Cài đặt Android > Trợ năng nếu chưa bật.
5. Chuyển sang ứng dụng mục tiêu rồi tự nhấn **Chạy** trên cửa sổ nổi.
6. Dùng Tạm dừng, Tiếp tục, Dừng hoặc đóng cửa sổ để điều khiển phiên chạy.

Bản nháp được mã hóa và lưu cục bộ. Gói phát hành được kiểm tra chữ ký/quyền, mã hóa phía Worker và chỉ được tải về thiết bị đã đăng ký có giấy phép hợp lệ.
