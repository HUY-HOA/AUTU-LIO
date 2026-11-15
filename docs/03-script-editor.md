# AUTU-LIO — Trình soạn thảo và ngôn ngữ kịch bản

**Phiên bản ngôn ngữ đầu tiên:** AUTU-LIO Script 0.1  
**Trạng thái:** Parser/validator và runtime core Kotlin đã hợp nhất vào main. PR #18 tích hợp cửa sổ soạn thảo riêng, bộ thực thi gesture qua AccessibilityService, cửa sổ điều khiển nổi, thư viện mẫu mã nguồn và hướng dẫn ngay trong trình soạn thảo. CI và kiểm thử thiết bị thật vẫn cần hoàn tất trước khi xác nhận hoạt động thực tế.

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
- Giá trị tọa độ được parser giới hạn 0–10.000; dispatcher Android coi đây là pixel tuyệt đối và từ chối tọa độ ngoài kích thước màn hình thực tế.
- `wait`: 1–60.000 ms.
- `longPress`: 100–10.000 ms.
- `swipe`: 1–10.000 ms.
- `repeat`: 1–100 lần, tối đa 8 cấp lồng nhau.
- Sau khi mở rộng vòng lặp: tối đa 5.000 lượt thực thi lệnh.
- Tổng thời lượng chờ và thao tác tĩnh: tối đa 600.000 ms (10 phút).

Bộ phân tích tạo cây lệnh có cấu trúc (AST), đếm số lệnh sau khi mở rộng vòng lặp và ước tính thời lượng. Runtime core kiểm tra lại cấu trúc AST, phiên bản ngôn ngữ, số lệnh và tổng thời lượng trước khi chạy; hỗ trợ chạy tuần tự, tạm dừng, tiếp tục và dừng. Nó không dùng JavaScript `eval`, reflection, shell hay thực thi mã tùy ý. Dispatcher Android vẫn phải kiểm tra lại tọa độ theo kích thước màn hình thật.

## 4. Hành vi trình soạn thảo

- Trình soạn thảo được mở bằng một Activity riêng, không nằm chen trong màn hình quản lý chính.
- Bản nháp được lưu cục bộ qua `SecureScriptDraftStore` và mã hóa AES-GCM bằng Android Keystore.
- Nút **Mẫu có sẵn** cung cấp các kịch bản chạm, chạm theo chuỗi, nhấn giữ, vuốt và lặp; chọn mẫu sẽ chèn toàn bộ mã vào vùng soạn thảo để người dùng tiếp tục sửa.
- Người dùng cũng có thể tự viết mã từ đầu hoặc kết hợp mẫu với mã tự viết trong cùng vùng soạn thảo.
- Nút **Hướng dẫn** giải thích cú pháp, tham số, tọa độ pixel, kiểm tra mã, cấp quyền Trợ năng và cách chạy trên ứng dụng mục tiêu.
- Nút **Kiểm tra mã** gọi ScriptCompiler 0.1 và hiển thị lỗi theo dòng/cột hoặc số lệnh/thời lượng tĩnh.
- Nút **Chuẩn bị chạy** chỉ biên dịch kịch bản và yêu cầu AccessibilityService hiển thị cửa sổ nổi ở trạng thái chờ. Nó không tự chạy thao tác.
- Người dùng chuyển sang ứng dụng mục tiêu, sau đó chủ động nhấn **Chạy** trên cửa sổ nổi. Cửa sổ có Tạm dừng/Tiếp tục, Dừng, thu nhỏ, di chuyển và đóng.
- Runtime core chạy tuần tự ở worker riêng; dispatcher gửi gesture qua Android `AccessibilityService.dispatchGesture()`.
- Kiểm tra mã không yêu cầu quyền Trợ năng và không gửi thao tác lên màn hình.

## 5. Quyền và an toàn khi thực thi sau này

- Chỉ tài khoản có quyền sửa/kiểm thử kịch bản mới được mở công cụ tương ứng.
- Người dùng phải tự bật AUTU-LIO trong Cài đặt Android > Trợ năng; ứng dụng không thể tự bật quyền này.
- Cấu hình dịch vụ đặt `canPerformGestures=true`, `canRetrieveWindowContent=false`; dịch vụ không đọc cây giao diện, văn bản hoặc nội dung ứng dụng mục tiêu.
- Cửa sổ điều khiển sử dụng `TYPE_ACCESSIBILITY_OVERLAY`, chỉ được tạo bởi AccessibilityService đang kết nối; không yêu cầu quyền vẽ đè ứng dụng riêng.
- Người dùng chuyển sang ứng dụng mục tiêu rồi chủ động nhấn **Chạy**. Khi chạy có nút Tạm dừng/Tiếp tục và Dừng; đóng cửa sổ cũng gửi lệnh dừng.
- Mỗi lần chạy vẫn chịu giới hạn số lệnh, thời lượng và vòng lặp của compiler/runtime.
- Không tự động cấp quyền Android, không thao tác ẩn, không gửi thao tác trước khi có lệnh Chạy rõ ràng.
- Tọa độ ngoài vùng màn hình, gesture bị từ chối, timeout hoặc mất dịch vụ phải dừng an toàn.
- Không đưa token, khóa ký hoặc bí mật máy chủ vào mã kịch bản.

## 6. Kiểm thử tự động

Bộ JUnit của ScriptCompiler kiểm tra cú pháp, giới hạn và ngân sách. Bộ JUnit của ScriptExecutionEngine kiểm tra thứ tự lệnh, repeat lồng nhau, pause/resume, stop trong thời gian chờ, từ chối metadata giả mạo, giới hạn runtime và lỗi dispatcher. GitHub Actions chạy unit test cùng bước kiểm tra Kotlin, không tạo APK phát hành.

## 7. Phạm vi chưa hoàn tất

- Biến, kiểu dữ liệu, biểu thức, điều kiện, hàm tự định nghĩa và thư viện.
- Gợi ý mã theo ngữ cảnh, tô màu cú pháp và nhảy đến vị trí lỗi.
- Hoàn thiện thông báo trạng thái dịch vụ Trợ năng và xử lý Android thu hồi dịch vụ khi cửa sổ chạy đang mở.
- Thử nghiệm dispatchGesture, cửa sổ nổi và điều khiển phiên chạy trên thiết bị Android thật, gồm xoay màn hình, nhiều mật độ và nhiều phiên bản Android.
- Kiểm tra hành vi dừng khi gesture đang diễn ra, hệ thống thu hồi dịch vụ hoặc tiến trình bị kết thúc.
- Mở trình soạn thảo riêng cho mọi vai trò Chủ sở hữu/Nhà phát triển có quyền chỉnh sửa; hiện Activity riêng đã nối từ Admin Studio, cần rà soát workspace Nhà phát triển.
- Đóng gói và phát hành kịch bản đã ký/mã hóa, đồng thời nối luồng khách hàng chỉ có quyền chạy package đã phát hành.
