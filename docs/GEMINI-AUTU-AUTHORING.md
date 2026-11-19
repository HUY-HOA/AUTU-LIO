# Gemini Live — viết AUTU Script trong ứng dụng

## Phạm vi được phép

Gemini Live có thể được chủ sở hữu bật quyền **Viết AUTU** để thực hiện các thao tác sau trong AUTU-LIO:

1. Đọc tài liệu cú pháp AUTU Script phiên bản hiện hành.
2. Liệt kê và đọc các bản nháp AUTU của chủ sở hữu.
3. Tạo hoặc chỉnh sửa nội dung AUTU Script theo yêu cầu của người dùng.
4. Gửi mã qua ScriptCompiler để kiểm tra cú pháp, tham số, giới hạn lệnh và thời lượng.
5. Sửa các lỗi biên dịch và lưu bản nháp hợp lệ vào kho bản nháp mã hóa cục bộ.

Gemini chỉ làm việc với nội dung AUTU Script. Không cấp quyền truy cập hệ thống tệp tùy ý hoặc API sửa mã nguồn ứng dụng.

## Những việc bị cấm

Gemini không được:
- Đọc, tạo, sửa, xóa, thay thế hoặc triển khai mã nguồn AUTU-LIO.
- Tạo hoặc sửa Kotlin, Java, Gradle, XML, mã Cloudflare Worker, schema/migration cơ sở dữ liệu hoặc cấu hình ứng dụng.
- Truy cập GitHub, repository, nhánh, commit, workflow, secrets, keystore hoặc APK.
- Thay đổi quyền, tài khoản, giấy phép, cấu hình bảo mật hoặc cài đặt của AUTU-LIO.
- Tự chạy, phát hành hoặc gửi bản AUTU cho khách hàng qua công cụ viết kịch bản.

Không có công cụ tương ứng trong danh sách function tools của Gemini. Chỉ thị hệ thống cũng nêu rõ ranh giới này; tuy nhiên, lớp bảo vệ chính là việc ứng dụng chỉ đăng ký năm công cụ AUTU có phạm vi giới hạn.

## Công cụ AUTU được cung cấp

| Công cụ | Chức năng |
|---|---|
| get_autu_script_reference | Đọc hướng dẫn cú pháp AUTU |
| list_autu_script_drafts | Liệt kê tên bản nháp cục bộ |
| read_autu_script_draft | Đọc một bản nháp đã lưu |
| validate_autu_script | Biên dịch/kiểm tra mà không lưu hoặc chạy |
| save_autu_script_draft | Lưu mã đã biên dịch thành công |

Giới hạn hiện tại: tên bản nháp tối đa 100 ký tự, tối đa 100 bản nháp trong luồng AI, mã nguồn tối đa 32.768 ký tự, tối đa 5.000 lệnh mở rộng và thời lượng ước tính không quá 10 phút theo giới hạn của compiler. Các giới hạn runtime thực tế vẫn do ScriptCompiler và ScriptExecutionEngine kiểm soát.

## Quyền và lưu trữ

- Worker chỉ khai báo năm công cụ viết AUTU nếu phiên đăng nhập có vai trò `OWNER`.
- Phiên `CUSTOMER` chỉ nhận các công cụ Gemini Live để đọc nhãn màn hình và thao tác màn hình được cho phép; không nhận công cụ soạn thảo AUTU.
- Trong ứng dụng, chủ sở hữu còn phải bật riêng công tắc **Cho phép AI viết AUTU**. Mặc định tắt.
- Bản nháp được lưu qua `SecureScriptDraftStore` với Android Keystore AES-GCM, namespace `admin`, trên thiết bị hiện tại. Nội dung bản nháp không được lưu vào Worker/D1 bởi các công cụ này.
- Nếu chủ sở hữu tắt công tắc, các function call viết AUTU bị từ chối ở phía ứng dụng. Quyền điều khiển màn hình là công tắc riêng.

## Quy trình sử dụng

1. Chủ sở hữu mở Gemini Live và bắt đầu phiên.
2. Bật **Cho phép AI viết AUTU** khi cần.
3. Nói hoặc nhập yêu cầu, ví dụ: “Tạo kịch bản mở ứng dụng, chờ 2 giây, kiểm tra chữ trên màn hình rồi chạm nút Tiếp tục.”
4. Gemini đọc hướng dẫn AUTU, có thể đọc bản nháp trước đó, viết mã và gọi validator.
5. Sau khi hợp lệ, Gemini lưu bản nháp. Nếu lỗi, Gemini phải sửa và kiểm tra lại.
6. Người dùng quay lại tab **Kịch bản**, mở bản nháp, xem nội dung và tự bấm kiểm tra/chạy.

**Lưu bản nháp không khởi chạy kịch bản.** Không có tool `run_autu_script` hoặc `publish_autu_script`. Việc chạy vẫn phải qua trình soạn thảo, compiler, Accessibility Service và thao tác xác nhận của người dùng trên cửa sổ chạy.

## Chạy AUTU không cần Gemini

AUTU Script compiler và runtime vẫn hoạt động độc lập với Gemini. Người dùng có thể tự viết, lưu, kiểm tra và chạy các kịch bản hiện có khi không kết nối Gemini hoặc không cấu hình API key. Chỉ những chức năng hiểu yêu cầu bằng ngôn ngữ tự nhiên và tạo mã bằng mô hình AI mới cần Gemini.

## Phân biệt điều khiển màn hình và viết AUTU

Hai công tắc độc lập:
- **Cho phép AI điều khiển:** cấp các thao tác màn hình cơ bản đã khai báo và được kiểm soát.
- **Cho phép AI viết AUTU:** cấp các công cụ đọc/kiểm tra/lưu bản nháp AUTU cục bộ.

Bật quyền viết AUTU không tự động bật quyền điều khiển màn hình. Bật quyền điều khiển màn hình không cấp quyền sửa mã nguồn hoặc lưu kịch bản.

## Kiểm thử bắt buộc

- Backend: kiểm tra OWNER nhận đúng 11 tool tổng cộng và CUSTOMER chỉ nhận 6 tool điều khiển màn hình.
- Android: kiểm tra tool AUTU bị từ chối khi công tắc tắt; compiler từ chối mã sai; save chỉ ghi bản hợp lệ; dữ liệu được lưu trong namespace admin; không có lệnh chạy/phát hành.
- Thiết bị thật: xác nhận bản nháp mới xuất hiện trong tab Kịch bản sau khi đóng Gemini Live; người dùng phải tự mở và chạy.
