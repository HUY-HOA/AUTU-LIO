# AUTU-LIO — Cửa sổ điều khiển nổi và chỉnh sửa script

## 1. Mô hình hoạt động

- Chỉ OWNER `phunghuyhoahaikba@gmail.com` được tạo và chỉnh sửa mã script.
- CUSTOMER chỉ tải và chạy script đã được phát hành/cấp phép.
- Một `ScriptExecutionEngine` duy nhất chạy trong `AutoClickAccessibilityService`; cửa sổ nổi chỉ điều khiển engine đó, không tạo bộ chạy thứ hai.

## 2. Quyền Android

1. Khi mở ứng dụng, AUTU-LIO kiểm tra dịch vụ của app đã được bật trong Android Settings → Accessibility chưa.
2. Nếu chưa, app hiển thị hướng dẫn và mở trang cài đặt để người dùng chủ động bật.
3. Trước khi mở editor, quyền được kiểm tra lại và app xác minh session OWNER cùng quyền EDIT_SCRIPTS từ Worker.
4. Trước khi chuẩn bị chương trình, quyền được kiểm tra lại lần nữa.
5. Khi nhấn Chạy/Tiếp tục trên cửa sổ nổi, service kiểm tra quyền trước khi điều khiển engine. Nếu quyền bị thu hồi, thao tác bị chặn và người dùng được yêu cầu cấp lại.
6. Khi ứng dụng/editor quay lại foreground, trạng thái quyền được kiểm tra lại.

Cửa sổ dùng `TYPE_ACCESSIBILITY_OVERLAY`; không cần cấp `SYSTEM_ALERT_WINDOW` riêng theo thiết kế này. Android không cho phép app tự bật Accessibility cho người dùng; người dùng phải xác nhận trong Cài đặt.

## 3. Tạo và chỉnh sửa script

1. OWNER vào màn hình Script và chọn tạo mới hoặc mở script đã có.
2. AUTU-LIO kiểm tra quyền Trợ năng và session OWNER.
3. `AutoClickAccessibilityService` mở bảng điều khiển nổi. Bảng này hiện cả khi editor đang mở.
4. Mã nguồn được nhập/chỉnh sửa trong Activity editor bảo mật của AUTU-LIO, có `FLAG_SECURE`.
5. Khi đang chỉnh sửa, nút Chạy trên bảng nổi bị khóa để tránh chạy mã cũ trong lúc nội dung đang thay đổi.
6. Khi chọn chuẩn bị chạy, app kiểm tra quyền, biên dịch script và chuyển chương trình hợp lệ sang service. Nếu lỗi, chương trình cũ không bị thay thế tùy tiện.
7. Khi thoát editor, bảng điều khiển nổi trở về trạng thái sẵn sàng.

Bảng điều khiển nổi là cửa sổ nổi thật trên ứng dụng khác; phần nhập mã nguồn vẫn là màn hình editor bên trong AUTU-LIO, không phải một cửa sổ nhập mã độc lập.

## 4. Chạy trên ứng dụng khác

1. Chuẩn bị script hợp lệ và xác nhận cửa sổ điều khiển nổi xuất hiện.
2. Chuyển sang ứng dụng mục tiêu.
3. Nhấn Chạy trên cửa sổ nổi.
4. Theo dõi trạng thái và số lệnh hoàn thành.
5. Có thể Tạm dừng, Tiếp tục hoặc Dừng từ cùng cửa sổ.
6. Kéo nút di chuyển để đổi vị trí; nút thu gọn giảm phần nội dung đang hiển thị.
7. Nếu quyền Trợ năng bị tắt, thao tác phải dừng/bị chặn và app yêu cầu bật lại.

Không hứa hẹn service chạy bất tử hoặc tự vượt qua giới hạn nền của Android.

## 5. Chỉnh sửa khi script đang chạy

1. OWNER nhấn Sửa trên bảng nổi.
2. Nếu chương trình đang chạy, engine được tạm dừng trước khi mở editor.
3. Cửa sổ điều khiển nổi vẫn hiển thị; editor bảo mật mở trong AUTU-LIO.
4. OWNER sửa mã, lưu bản nháp và biên dịch kiểm tra.
5. Khi chương trình mới được chuẩn bị thành công, phiên cũ được dừng an toàn, chương trình mới thành phiên chờ.
6. Quay lại ứng dụng mục tiêu và nhấn Chạy. Phiên bản mới bắt đầu từ đầu, không tiếp tục vị trí lệnh của phiên bản cũ.

Nếu chỉ lưu bản nháp mà không chuẩn bị chương trình mới, phiên cũ vẫn ở trạng thái tạm dừng và có thể được tiếp tục.

## 6. Ma trận nghiệm thu trên điện thoại thật

| Kiểm tra | Kết quả mong đợi |
|---|---|
| Mở app khi chưa bật Accessibility | Màn hình yêu cầu cấp quyền; chưa vào chức năng |
| Bật Accessibility và quay lại | App nhận quyền và tiếp tục |
| OWNER tạo/mở script | Editor bảo mật và bảng điều khiển nổi xuất hiện |
| Chỉnh sửa script | Bảng nổi vẫn hiện; nút Chạy bị khóa trong lúc sửa |
| Chuẩn bị script hợp lệ | Bảng nổi chuyển sang trạng thái Sẵn sàng |
| Chuyển sang ứng dụng mục tiêu | Bảng nổi vẫn hiển thị trên ứng dụng đó |
| Nhấn Chạy/Tạm dừng/Tiếp tục/Dừng | Cùng engine thực thi nhận đúng lệnh |
| Tắt Accessibility khi editor đang mở | Editor đóng hoặc chặn thao tác và yêu cầu cấp lại |
| Tắt Accessibility khi script đang chạy | Engine dừng; không tiếp tục dispatch thao tác |
| Chỉnh mã khi đang chạy | Phiên hiện tại tạm dừng; bản mới chỉ được áp dụng sau khi biên dịch thành công |

## 7. Giới hạn xác minh

Unit test của ScriptExecutionEngine xác minh logic điều khiển luồng, không thay thế kiểm thử GestureDescription, cửa sổ nổi, khởi chạy Activity từ service và quản lý pin trên thiết bị thật. Cần nghiệm thu trên Android mục tiêu, đặc biệt Xiaomi/HyperOS, trước khi công bố hành vi chạy nền đã được xác nhận.
