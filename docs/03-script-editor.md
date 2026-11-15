# AUTU-LIO — Trình soạn thảo và ngôn ngữ kịch bản

**Phiên bản ngôn ngữ đầu tiên:** AUTU-LIO Script 0.1  
**Trạng thái:** Bộ phân tích cú pháp Kotlin và nút Kiểm tra mã đã được triển khai trên nhánh phát triển; đang chờ CI. Runtime Android chưa có.

## 1. Mục tiêu

Trình soạn thảo và ngôn ngữ kịch bản chỉ xuất hiện trong không gian Chủ sở hữu/Nhà phát triển phù hợp quyền. Người dùng cuối chỉ nhận và chạy package đã được phát hành; không được mở IDE hoặc xem mã nguồn.

Quy trình tách rõ:
1. Viết và lưu bản nháp.
2. Kiểm tra cú pháp và giới hạn an toàn.
3. Kiểm thử sau khi runtime được hoàn thiện.
4. Gửi duyệt theo quy trình dự án.
5. Chủ sở hữu kiểm tra và phát hành chính thức.

## 2. Cú pháp AUTU-LIO Script 0.1

Mỗi lệnh nằm trên một dòng hoặc kết thúc bằng dấu chấm phẩy. Có thể thêm chú thích một dòng bằng `//`.

```text
// Chạm vào tọa độ 120, 480
tap(120, 480)

wait(500)
longPress(300, 700, 800)
swipe(500, 900, 500, 300, 450)

repeat(3) {
    tap(120, 480)
    wait(250)
}
```

### Lệnh được hỗ trợ

| Lệnh | Tham số | Ý nghĩa |
|---|---|---|
| `tap(x, y)` | Hai số nguyên | Chạm một lần |
| `longPress(x, y, durationMs)` | Tọa độ và thời lượng | Nhấn giữ |
| `swipe(startX, startY, endX, endY, durationMs)` | Điểm đầu, điểm cuối và thời lượng | Vuốt theo đường thẳng |
| `wait(durationMs)` | Thời lượng | Chờ trước lệnh tiếp theo |
| `repeat(count) { ... }` | Số lần lặp và khối lệnh | Lặp nhóm lệnh |

Tên lệnh phân biệt chữ hoa/chữ thường. Tham số ở phiên bản 0.1 là số nguyên không âm; biến, biểu thức số học và hàm tự định nghĩa chưa được hỗ trợ.

## 3. Giới hạn an toàn khi phân tích

- Kích thước mã tối đa: 32.768 ký tự.
- Tối đa 1.000 câu lệnh khai báo.
- Tọa độ nằm trong khoảng 0–10.000 pixel; runtime vẫn phải kiểm tra lại theo kích thước màn hình thật trước khi thao tác.
- `wait`: 1–60.000 ms.
- `longPress`: 100–10.000 ms.
- `swipe`: 1–10.000 ms.
- `repeat`: 1–100 lần, tối đa 8 cấp lồng nhau.
- Sau khi mở rộng vòng lặp: tối đa 5.000 lượt thực thi lệnh.
- Tổng thời lượng chờ và thao tác tĩnh: tối đa 600.000 ms (10 phút).

Bộ phân tích tạo cây lệnh có cấu trúc (AST), đếm số lệnh sau khi mở rộng vòng lặp và ước tính thời lượng. Nó không dùng JavaScript `eval`, reflection, shell hay thực thi mã tùy ý. Các giới hạn này chỉ là bước kiểm tra tĩnh; runtime phải áp dụng lại ngân sách khi chạy thực tế và có cơ chế Dừng.

## 4. Hành vi trình soạn thảo

- Ô mã lưu bản nháp theo luồng hiện có.
- Nút **Kiểm tra mã** gọi ScriptCompiler 0.1 và hiển thị lỗi theo dòng/cột hoặc số lệnh/thời lượng tĩnh.
- Sửa mã sẽ xóa kết quả kiểm tra cũ để tránh dùng nhầm kết quả không còn khớp nội dung.
- Nút **Chạy thử** chưa thực thi; giao diện phải thông báo runtime chưa sẵn sàng.
- Kiểm tra mã không yêu cầu quyền Trợ năng và không tạo thao tác trên màn hình.

## 5. Quyền và an toàn khi thực thi sau này

- Chỉ tài khoản có quyền sửa/kiểm thử kịch bản mới được mở công cụ tương ứng.
- Người dùng phải tự bật quyền Trợ năng trong phần Cài đặt Android; ứng dụng giải thích rõ mục đích và cung cấp nút Dừng.
- Mỗi lần chạy có giới hạn số lệnh, thời lượng, vòng lặp và có thể hủy.
- Không tự động cấp quyền Android, không thao tác ẩn, không gửi thao tác khi chưa có lệnh chạy rõ ràng.
- Tọa độ ngoài vùng màn hình, lệnh không hỗ trợ, timeout hoặc mất dịch vụ phải dừng an toàn.
- Không đưa token, khóa ký hoặc bí mật máy chủ vào mã kịch bản.

## 6. Kiểm thử tự động

Bộ JUnit của ScriptCompiler kiểm tra lệnh hợp lệ, vòng lặp, chú thích, số tham số, lệnh không biết, số âm, giới hạn tọa độ, giới hạn repeat, ngân sách số lệnh/thời lượng và mã quá dài. GitHub Actions chạy unit test cùng bước kiểm tra Kotlin, không tạo APK phát hành.

## 7. Phạm vi chưa hoàn tất

- Biến, kiểu dữ liệu, biểu thức, điều kiện, hàm tự định nghĩa và thư viện.
- Gợi ý mã/tô màu cú pháp/nút chuyển tới lỗi.
- Interpreter/runtime và bộ thực thi lệnh Android.
- AccessibilityService, cấp quyền minh bạch, hủy phiên chạy và kiểm thử trên nhiều kích thước màn hình.
- Kiểm thử thiết bị thật, package có chữ ký và phát hành.
