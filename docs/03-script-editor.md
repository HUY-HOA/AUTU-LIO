# AUTU-LIO — Trình soạn thảo và ngôn ngữ kịch bản

**Phiên bản ngôn ngữ đầu tiên:** AUTU-LIO Script 0.1  
**Trạng thái:** Parser/validator và runtime core Kotlin đã có trong nhánh phát triển; runtime core đã có kiểm thử tự động. AccessibilityService adapter và kết nối nút Chạy thử vào thao tác Android vẫn đang triển khai.

## 1. Mục tiêu

Trình soạn thảo và ngôn ngữ kịch bản chỉ xuất hiện trong không gian Chủ sở hữu/Nhà phát triển phù hợp quyền. Người dùng cuối chỉ nhận và chạy package đã được phát hành; không được mở IDE hoặc xem mã nguồn.

Quy trình tách rõ:
1. Viết và lưu bản nháp.
2. Kiểm tra cú pháp và giới hạn an toàn.
3. Chạy thử qua runtime core sau khi có dispatcher Android và quyền Trợ năng được người dùng tự bật.
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

Bộ phân tích tạo cây lệnh có cấu trúc (AST), đếm số lệnh sau khi mở rộng vòng lặp và ước tính thời lượng. Runtime core kiểm tra lại cấu trúc AST, phiên bản ngôn ngữ, số lệnh và tổng thời lượng trước khi chạy; hỗ trợ chạy tuần tự, tạm dừng, tiếp tục và dừng. Nó không dùng JavaScript `eval`, reflection, shell hay thực thi mã tùy ý. Dispatcher Android vẫn phải kiểm tra lại tọa độ theo kích thước màn hình thật.

## 4. Hành vi trình soạn thảo

- Ô mã lưu bản nháp theo luồng hiện có.
- Nút **Kiểm tra mã** gọi ScriptCompiler 0.1 và hiển thị lỗi theo dòng/cột hoặc số lệnh/thời lượng tĩnh.
- Sửa mã sẽ xóa kết quả kiểm tra cũ để tránh dùng nhầm kết quả không còn khớp nội dung.
- Runtime core hiện chỉ chạy qua `ScriptActionDispatcher`; nút **Chạy thử** trên giao diện vẫn chưa gọi runtime cho tới khi AccessibilityService adapter, quyền Trợ năng và điều khiển phiên chạy được nối hoàn chỉnh.
- Kiểm tra mã không yêu cầu quyền Trợ năng và không tạo thao tác trên màn hình.

## 5. Quyền và an toàn khi thực thi sau này

- Chỉ tài khoản có quyền sửa/kiểm thử kịch bản mới được mở công cụ tương ứng.
- Người dùng phải tự bật quyền Trợ năng trong phần Cài đặt Android; ứng dụng giải thích rõ mục đích và cung cấp nút Dừng.
- Mỗi lần chạy có giới hạn số lệnh, thời lượng, vòng lặp và có thể hủy.
- Không tự động cấp quyền Android, không thao tác ẩn, không gửi thao tác khi chưa có lệnh chạy rõ ràng.
- Tọa độ ngoài vùng màn hình, lệnh không hỗ trợ, timeout hoặc mất dịch vụ phải dừng an toàn.
- Không đưa token, khóa ký hoặc bí mật máy chủ vào mã kịch bản.

## 6. Kiểm thử tự động

Bộ JUnit của ScriptCompiler kiểm tra cú pháp, giới hạn và ngân sách. Bộ JUnit của ScriptExecutionEngine kiểm tra thứ tự lệnh, repeat lồng nhau, pause/resume, stop trong thời gian chờ, từ chối metadata giả mạo, giới hạn runtime và lỗi dispatcher. GitHub Actions chạy unit test cùng bước kiểm tra Kotlin, không tạo APK phát hành.

## 7. Phạm vi chưa hoàn tất

- Biến, kiểu dữ liệu, biểu thức, điều kiện, hàm tự định nghĩa và thư viện.
- Gợi ý mã/tô màu cú pháp/nút chuyển tới lỗi.
- AccessibilityService dispatcher để thực hiện gesture Android thật.
- Kết nối runtime với giao diện Chạy thử, trạng thái tiến độ, pause/resume/stop.
- Cấp quyền Trợ năng minh bạch do người dùng tự bật, xử lý mất dịch vụ và hủy gesture đang chạy.
- Kiểm tra tọa độ theo kích thước/mật độ màn hình và thử nghiệm trên nhiều thiết bị.
- Kiểm thử thiết bị thật, package có chữ ký và phát hành.
