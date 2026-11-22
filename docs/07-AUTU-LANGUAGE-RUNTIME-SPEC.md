# AUTU-LIO — Đặc tả ngôn ngữ và bộ máy tự động hóa

**Trạng thái triển khai:** AUTU Script 1.4 có parser/compiler Kotlin, runtime và kiểm thử nguồn; xem tài liệu tham chiếu để biết đầy đủ kiểu, danh sách, bản đồ và hàm chuỗi.  
**Nguyên tắc:** chỉ công bố lệnh/hàm có parser, kiểm tra tham số/kiểu, runtime handler và unit tests. Không đưa lệnh giả để làm giao diện trông đầy đủ.

## 1. Quy tắc ngôn ngữ

- Tất cả từ khóa, lệnh, hàm, kiểu, biến và tham số đều phải viết HOA.
- Từ khóa/lệnh tiếng Việt phải có đúng dấu. Không chấp nhận bí danh tiếng Anh.
- Chuỗi Unicode trong dấu nháy kép; chú thích một dòng dùng `//`.
- Mỗi câu lệnh trên một dòng hoặc ngăn cách bằng `;`.
- Không dùng `eval`, shell, reflection, script runtime bên ngoài hoặc thực thi mã Kotlin/Java tùy ý.

## 2. Ngữ pháp nền tảng đã triển khai

```ebnf
CHUONG_TRINH  = { KHAI_BAO_HAM | CAU_LENH } ;
KHAI_BAO_HAM  = "HÀM", TEN_HAM, "(", [DANH_SACH_THAM_SO], ")", [":", KIEU], KHOI ;
DANH_SACH_THAM_SO = THAM_SO, {",", THAM_SO} ;
THAM_SO       = TEN, [":", KIEU] ;
CAU_LENH      = KHAI_BAO_BIEN | GAN_BIEN | LENH_ANDROID | DIEU_KIEN | VONG_LAP | CHO_DIEU_KIEN | DIEU_KHIEN_VONG | TRA_VE ;
KHAI_BAO_BIEN = "BIẾN", TEN, [":", KIEU], "=", BIEU_THUC ;
GAN_BIEN      = TEN, "=", BIEU_THUC ;
DIEU_KIEN     = "NẾU", "(", BIEU_THUC, ")", KHOI, ["NGƯỢC_LẠI_NẾU", ...], ["NGƯỢC_LẠI", KHOI] ;
VONG_LAP      = "LẶP", "(", BIEU_THUC, ")", KHOI
              | "LẶP_KHI", "(", BIEU_THUC, ")", KHOI
              | "LẶP_ĐẾN_KHI", "(", BIEU_THUC, ")", KHOI ;
CHO_DIEU_KIEN = "CHỜ_ĐẾN_KHI", "(", BIEU_THUC, ",", BIEU_THUC, ",", BIEU_THUC, ")" ;
DIEU_KHIEN_VONG = "THOÁT_LẶP" | "BỎ_QUA_LƯỢT" ;
TRA_VE        = "TRẢ_VỀ", BIEU_THUC ;
KHOI          = "{", {CAU_LENH}, "}" ;
KIEU          = "SỐ" | "CHUỖI" | "LUẬN_LÝ" | "BẤT_KỲ" ;
```

`BIẾN` và `HÀM` là cú pháp chuẩn duy nhất cho khai báo biến và định nghĩa hàm. Không hỗ trợ từ khóa thay thế hoặc cú pháp tương thích cũ. Gán lại biến đã khai báo dùng trực tiếp cú pháp `TEN_BIEN = BIEU_THUC`. Ngữ pháp mô tả cấu trúc; parser vẫn là nguồn quyết định cuối cùng về cú pháp hợp lệ.

## 3. AST và runtime

AST hiện có:
- Literal số, chuỗi và luận lý.
- Biến, toán tử một ngôi/hai ngôi, lời gọi hàm.
- Lệnh Android và gán biến có/không có kiểu.
- NẾU, LẶP, LẶP_KHI, LẶP_ĐẾN_KHI, CHỜ_ĐẾN_KHI.
- HÀM, tham số có kiểu, kiểu trả về và TRẢ_VỀ.
- THOÁT_LẶP và BỎ_QUA_LƯỢT.

Runtime:
- Thực thi tuần tự trên worker riêng.
- Pause, resume, stop, cancel và error state.
- Kiểm tra kiểu ở thời điểm gán, tham số và giá trị trả về.
- Hàm có phạm vi cục bộ; đọc được giá trị môi trường gọi tại thời điểm gọi; gán trong hàm không ghi ngược ra biến bên ngoài.
- Độ sâu gọi hàm tối đa 16; ngân sách lệnh thực tế tối đa 5.000.
- Hàm và biểu thức chỉ được gọi từ danh sách chuẩn hoặc hàm đã định nghĩa trong chương trình.

## 4. Tên chuẩn của lệnh Android nối runtime

`NHẤN`, `NHẤN_GIỮ`, `NHẤN_ĐA_ĐIỂM`, `VUỐT`, `VUỐT_ĐA_ĐIỂM`, `CHỜ`, `MỞ_ỨNG_DỤNG`, `QUAY_LẠI`, `VỀ_MÀN_HÌNH_CHÍNH`, `MỞ_ỨNG_DỤNG_GẦN_ĐÂY`, `MỞ_THANH_THÔNG_BÁO`, `MỞ_BẢNG_CÀI_ĐẶT_NHANH`, `MỞ_HỘP_THOẠI_NGUỒN`, `KHÓA_MÀN_HÌNH`, `BẬT_TẮT_CHIA_MÀN_HÌNH`.

Lệnh cử chỉ chạy qua Android AccessibilityService. Lệnh hệ thống có thể thất bại do Android/API hoặc trạng thái quyền; runtime báo lỗi thay vì giả lập thành công.

## 5. Hàm dựng sẵn

Toán học/chuỗi: `SỐ`, `CHUỖI`, `LÀM_TRÒN`, `TUYỆT_ĐỐI`, `ĐỘ_DÀI`, `NHỎ_NHẤT`, `LỚN_NHẤT`.

Thiết bị: `LẤY_PHẦN_TRĂM_PIN`, `KIỂM_TRA_ĐANG_SẠC`, `KIỂM_TRA_KẾT_NỐI_MẠNG`, `KIỂM_TRA_MÀN_HÌNH_ĐANG_SÁNG`, `KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI`, `KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ`, `LẤY_ÂM_LƯỢNG`, `LẤY_ĐỘ_SÁNG`, `CHIỀU_RỘNG_MÀN_HÌNH`, `CHIỀU_CAO_MÀN_HÌNH`, `THỜI_GIAN_MS`.

## 6. Những phần chưa triển khai

- Danh sách và bản đồ đã được hỗ trợ; giá trị lồng nhau và truy cập chỉ mục bằng cú pháp ngoặc vuông chưa được hỗ trợ.
- TRY/CATCH/FINALLY và cơ chế phục hồi lỗi ở cấp ngôn ngữ.
- Ghi nhật ký có cấu trúc từ mã AUTU.
- Tọa độ theo phần trăm, vùng thao tác, đa điểm.
- Chờ sự kiện/lịch chạy và quản lý tác vụ nền.
- OCR, nhận diện ảnh, quét mã và các tính năng AI không thuộc phạm vi runtime. Đọc văn bản chỉ dùng nhãn mà ứng dụng đích công khai qua cây Android Accessibility.
- Debugger từng bước, breakpoint, biến theo dõi và tô màu cú pháp.

Các mục này không được xem là hỗ trợ cho đến khi có AST, giới hạn an toàn, quyền người dùng, runtime thật, test và kiểm thử Android thật. Xem `docs/08-AUTU-LANGUAGE-REFERENCE.md` để tra cứu chính xác cú pháp đang hoạt động.

## 7. Ngân sách thực thi

- Mã nguồn 32.768 ký tự; 1.000 câu lệnh; 8 cấp lồng khối.
- LẶP 1–100; LẶP_KHI tối đa 900 vòng.
- Hàm tối đa 16 tham số, độ sâu gọi tối đa 16.
- Tối đa 5.000 lượt thực thi thực tế; 10 phút thời lượng tĩnh.
- Tọa độ 0–10.000 pixel; CHỜ 1–60.000 ms; NHẤN_GIỮ 100–10.000 ms; VUỐT 1–10.000 ms.
- CHỜ_ĐẾN_KHI timeout 1–60.000 ms; chu kỳ 50–5.000 ms.

## 8. Điều kiện nghiệm thu

Mỗi tính năng mới cần:
1. Cú pháp HOA, đúng dấu và tài liệu tiếng Việt.
2. AST/IR và kiểm tra cú pháp/tham số/kiểu.
3. Giới hạn ngân sách và chống lạm dụng.
4. Runtime Android thật hoặc xử lý trạng thái CHƯA_HỖ_TRỢ rõ ràng.
5. Unit tests parser, runtime, lỗi, giới hạn và hủy.
6. Android source checks xanh; sau đó kiểm thử hành vi trên thiết bị thật.
