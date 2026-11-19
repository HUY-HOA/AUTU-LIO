# NGÔN NGỮ AUTU-LIO — CÚ PHÁP CHUẨN

**Tên:** AUTU-LIO Script · **Bộ biên dịch:** 0.3 · **Ngôn ngữ chuẩn:** Tiếng Việt VIẾT HOA, ĐẦY ĐỦ DẤU.

Tài liệu này ghi nhận các cú pháp đang có trong parser/runtime. Không xem chức năng trong kế hoạch là chức năng đã chạy.

## Quy tắc
- Viết từ khóa và lệnh theo dạng HOA, có dấu: `ĐẶT`, `NẾU`, `NGƯỢC_LẠI`, `LẶP`, `LẶP_KHI`, `LẶP_ĐẾN_KHI`, `CHỜ_ĐẾN_KHI`, `THOÁT_LẶP`, `BỎ_QUA_LƯỢT`, `ĐÚNG`, `SAI`, `VÀ`, `HOẶC`, `KHÔNG`.
- Tên biến tự đặt có thể dùng chữ hoa/thường, chữ số, dấu gạch dưới và chữ tiếng Việt; tên biến phân biệt hoa/thường.
- Chuỗi dùng dấu ngoặc kép; hỗ trợ escape newline, tab, carriage return, dấu gạch chéo ngược và ngoặc kép.
- Mỗi lệnh một dòng hoặc phân cách bằng `;`; chú thích bắt đầu bằng `//`; khối lệnh dùng `{}`.
- Dùng dấu chấm cho số thập phân; thời gian tính bằng mili giây.
- Parser còn nhận bí danh tiếng Anh cũ để tương thích; mã mới nên dùng tiếng Việt chuẩn.

## Kiểu và biểu thức
Hỗ trợ số hữu hạn, chuỗi Unicode, ĐÚNG/SAI và biến đã khai báo. Toán tử: `+`, `-`, `*`, `/`, `%`, `==`, `!=`, `>`, `>=`, `<`, `<=`, `VÀ`, `HOẶC`, `KHÔNG`. Có ngoặc nhóm, ưu tiên toán tử và đánh giá ngắn mạch. Không tự ép kiểu mơ hồ; chia cho 0 báo lỗi.

## Cú pháp lõi

```text
ĐẶT soLan = 3
soLan = soLan + 1

NẾU (soLan >= 3 VÀ ĐÚNG) {
    CHẠM(200, 400)
} NGƯỢC_LẠI_NẾU (soLan == 2) {
    CHỜ(300)
} NGƯỢC_LẠI {
    QUAY_LẠI()
}

LẶP(3) {
    CHẠM(200, 400)
    CHỜ(500)
}

LẶP_KHI (soLan < 10) {
    soLan = soLan + 1
}

LẶP_ĐẾN_KHI (soLan >= 10) {
    soLan = soLan + 1
}

CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ("com.example.game"), 15000, 250)
```

`THOÁT_LẶP` dừng vòng lặp gần nhất; `BỎ_QUA_LƯỢT` bỏ phần còn lại của lượt hiện tại.

## Lệnh Android có parser và runtime

| Lệnh | Tham số |
|---|---|
| `CHẠM(X, Y)` | 2 số nguyên |
| `NHẤN_GIỮ(X, Y, THỜI_LƯỢNG)` | 3 số nguyên |
| `VUỐT(X1, Y1, X2, Y2, THỜI_LƯỢNG)` | 5 số nguyên |
| `CHỜ(MILI_GIÂY)` | 1 số nguyên |
| `MỞ_ỨNG_DỤNG("TÊN_GÓI")` | 1 chuỗi |
| `QUAY_LẠI()` | Không |
| `VỀ_MÀN_HÌNH_CHÍNH()` | Không |
| `ỨNG_DỤNG_GẦN_ĐÂY()` | Không |
| `MỞ_THÔNG_BÁO()` | Không |
| `MỞ_CÀI_ĐẶT_NHANH()` | Không |
| `HIỆN_MENU_NGUỒN()` | Không |
| `KHÓA_MÀN_HÌNH()` | Không |
| `CHIA_MÀN_HÌNH()` | Không |

Giới hạn: tọa độ 0–10.000 (sau đó kiểm tra kích thước màn hình thật); giữ 100–10.000 ms; vuốt 1–10.000 ms; chờ 1–60.000 ms. Các lệnh thao tác cần AccessibilityService hoạt động; một số hành động phụ thuộc phiên bản Android.

## Hàm dữ liệu
- `SỐ(x)`, `CHUỖI(x)`, `LÀM_TRÒN(x)`, `TUYỆT_ĐỐI(x)`, `ĐỘ_DÀI(chuỗi)`, `NHỎ_NHẤT(a,b)`, `LỚN_NHẤT(a,b)`.

## Hàm thiết bị
- `PIN()`, `PHẦN_TRĂM_PIN()`, `ĐANG_SẠC()`, `CÓ_MẠNG()`, `MÀN_HÌNH_SÁNG()`.
- `ỨNG_DỤNG_ĐÃ_CÀI("TÊN_GÓI")`, `ỨNG_DỤNG_ĐANG_MỞ("TÊN_GÓI")`.
- `CHIỀU_RỘNG_MÀN_HÌNH()`, `CHIỀU_CAO_MÀN_HÌNH()`, `THỜI_GIAN_MS()`.

## Ví dụ hoàn chỉnh trong phạm vi hiện hỗ trợ

```text
ĐẶT tenUngDung = "com.example.game"
NẾU (ỨNG_DỤNG_ĐÃ_CÀI(tenUngDung)) {
    MỞ_ỨNG_DỤNG(tenUngDung)
    CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ(tenUngDung), 15000, 250)
    ĐẶT soLan = 0
    LẶP_KHI (soLan < 3) {
        CHẠM(200, 400)
        CHỜ(500)
        soLan = soLan + 1
    }
} NGƯỢC_LẠI {
    QUAY_LẠI()
}
```

## Ngân sách và an toàn
- Mã tối đa 32.768 ký tự; tối đa 1.000 câu lệnh; lồng nhau tối đa 8 cấp.
- `LẶP` tối đa 100 lượt; `LẶP_KHI` tối đa 900 lượt.
- Tối đa 5.000 lệnh sau khi mở rộng vòng lặp; thời lượng tĩnh tối đa 10 phút.
- Runtime chạy tuần tự; hỗ trợ Tạm dừng, Tiếp tục, Dừng.
- Không dùng eval, shell, reflection hoặc mã Kotlin/Java tùy ý.

## Chưa hỗ trợ
Hàm do người dùng tự định nghĩa, lớp/đối tượng, danh sách/bản đồ, vòng lặp for/foreach, xử lý ngoại lệ tùy biến, OCR, nhận diện ảnh, đọc nội dung ứng dụng, tọa độ tương đối, đa điểm, sự kiện nền và thư viện dùng chung chưa được hoàn tất. Không nhập các cú pháp đó như thể đã chạy được.

Một lệnh mới chỉ được công bố là ĐÃ_HỖ_TRỢ khi có parser, kiểm tra tham số, runtime Android thật, quyền phù hợp, xử lý lỗi, unit tests, kiểm thử thiết bị và tài liệu.
