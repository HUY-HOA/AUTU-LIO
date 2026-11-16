# AUTU-LIO — Cửa sổ chạy nổi và chỉnh sửa kịch bản

## Mục tiêu

Cửa sổ chạy nổi được quản lý bởi `AutoClickAccessibilityService` và dùng `TYPE_ACCESSIBILITY_OVERLAY`. Bộ thực thi `ScriptExecutionEngine` tiếp tục sống trong Accessibility Service khi Activity soạn thảo đóng hoặc khi người dùng chuyển sang ứng dụng mục tiêu.

Không tạo một bộ chạy thứ hai trong cửa sổ nổi. Các nút điều khiển phải gọi trực tiếp cùng một `ScriptExecutionEngine` đang thực thi chương trình.

## Cách sử dụng dự kiến

1. Cài AUTU-LIO và bật dịch vụ AUTU-LIO tại Cài đặt Android > Trợ năng. Android yêu cầu người dùng tự bật Accessibility Service.
2. Mở kịch bản trong trình soạn thảo, kiểm tra cú pháp và chọn thao tác chuẩn bị chạy.
3. AUTU-LIO biên dịch kịch bản, chuyển chương trình đã kiểm tra cho Accessibility Service và hiển thị cửa sổ nổi.
4. Chuyển sang ứng dụng mục tiêu rồi nhấn Chạy. Có thể kéo cửa sổ bằng nút di chuyển; nút thu gọn giảm phần điều khiển đang hiển thị.
5. Nút chính chuyển giữa Chạy, Tạm dừng và Tiếp tục. Nút Dừng yêu cầu bộ thực thi hủy ở điểm an toàn tiếp theo. Đóng cửa sổ sẽ dừng kịch bản và gỡ cửa sổ nổi.

## Chỉnh sửa khi kịch bản đang chạy

Không thay mã trực tiếp bên trong luồng đang thực thi vì vị trí lệnh, biến và vòng lặp có thể thay đổi giữa chừng. Luồng an toàn được thiết kế như sau:

1. Nhấn Sửa trên cửa sổ nổi. Nếu kịch bản đang chạy, AUTU-LIO tự tạm dừng trước khi mở trình soạn thảo.
2. Cửa sổ nổi được ẩn trong lúc soạn thảo; bộ thực thi vẫn nằm trong Accessibility Service và giữ nguyên trạng thái tạm dừng.
3. Người dùng sửa mã, lưu bản nháp và chạy kiểm tra/biên dịch.
4. Khi chọn chuẩn bị chạy với mã mới, chương trình mới được biên dịch và xác thực trước. Nếu phiên cũ đang tạm dừng, AUTU-LIO yêu cầu dừng phiên cũ, giữ chương trình mới làm phiên chờ và mở lại cửa sổ nổi.
5. Quay về ứng dụng mục tiêu và nhấn Chạy. Phiên bản mới bắt đầu từ đầu; không tiếp tục ở vị trí lệnh của phiên bản cũ.

Nếu người dùng chỉ lưu bản nháp rồi quay lại mà không chọn chuẩn bị chạy với mã mới, phiên cũ vẫn ở trạng thái tạm dừng và có thể được tiếp tục. Bản nháp mới chỉ được áp dụng cho lần chạy sau khi biên dịch/chuẩn bị thành công.

## Điều kiện Android

- Cửa sổ dùng `TYPE_ACCESSIBILITY_OVERLAY`, vì vậy nó được tạo bởi Accessibility Service; không dùng `TYPE_APPLICATION_OVERLAY` và không yêu cầu quyền `SYSTEM_ALERT_WINDOW` cho cách triển khai này.
- Người dùng phải bật Accessibility Service trong Cài đặt Android. Nếu dịch vụ bị tắt hoặc hệ thống ngắt dịch vụ, bộ thực thi sẽ dừng; không hứa hẹn chạy bất tử hoặc tự vượt qua giới hạn nền của Android.
- Việc mở Activity soạn thảo từ cửa sổ dịch vụ cần được kiểm tra trên Android mục tiêu vì Android có cơ chế hạn chế khởi chạy Activity từ nền. Hành động này chỉ được thực hiện sau thao tác nhấn rõ ràng của người dùng.
- Trên thiết bị Xiaomi/HyperOS, cần kiểm thử thêm hành vi sau khi khóa màn hình, chuyển ứng dụng, chuyển sang chế độ tiết kiệm pin và sau khi hệ thống thu hồi/khởi động lại dịch vụ.

## Ma trận nghiệm thu

| Kiểm tra | Kết quả mong đợi |
|---|---|
| Chuẩn bị chương trình hợp lệ | Cửa sổ nổi xuất hiện, trạng thái Sẵn sàng |
| Nhấn Chạy | Bộ thực thi bắt đầu; số lệnh hoàn thành tăng |
| Chuyển sang ứng dụng khác | Cửa sổ nổi còn điều khiển được và thao tác vẫn chạy |
| Nhấn Tạm dừng | Không phát sinh lệnh tiếp theo sau khi thao tác hiện tại kết thúc |
| Nhấn Tiếp tục | Tiếp tục chương trình đang tạm dừng |
| Nhấn Dừng | Hủy chương trình tại điểm kiểm tra an toàn |
| Nhấn Sửa khi đang chạy | Kịch bản tạm dừng và trình soạn thảo mở |
| Biên dịch bản sửa | Bản mới được kiểm tra trước khi thay thế chương trình chờ |
| Áp dụng bản sửa | Phiên cũ được dừng; cửa sổ nổi hiển thị yêu cầu chạy phiên bản mới từ đầu |
| Chỉ lưu bản nháp | Phiên cũ vẫn tạm dừng, có thể tiếp tục |
| Mở trình sửa từ cửa sổ nổi ở Release | Xác minh phiên đăng nhập và quyền Owner/Developer với máy chủ; không xác minh được thì từ chối mở |
| Tắt Accessibility Service | Không tiếp tục thao tác; bộ thực thi bị dừng khi dịch vụ bị ngắt |
| Lỗi quyền/thao tác | Hiển thị trạng thái lỗi; không giả lập thành công |

## Giới hạn xác minh

Unit test của ScriptExecutionEngine chỉ xác minh logic thông dịch và điều khiển luồng. Nó không thay thế kiểm thử cửa sổ nổi, GestureDescription, khởi chạy Activity từ cửa sổ dịch vụ, tương tác game hoặc hành vi quản lý pin trên điện thoại thật. Chỉ đánh dấu các thao tác Android là đã xác minh sau khi kiểm thử trên thiết bị thực tế.
