# AUTU-LIO — Quy trình làm việc với GitHub

## 1. Repository chính thức
- Repository: HUY-HOA-1/AUTU-LIO
- Nhánh mặc định hiện tại: main
- Repository đang ở chế độ private.
- Tài khoản kết nối HUY-HOA-1 hiện có quyền quản trị repository.
- Không tạo repository trùng lặp và không chuyển quyền sở hữu nếu chưa có yêu cầu rõ ràng.

## 2. Quy tắc trước khi thay đổi
1. Đọc cấu trúc repository và nội dung file liên quan trước khi sửa.
2. Không ghi đè hoặc xóa file khi chưa đọc nội dung và kiểm tra mục đích sử dụng.
3. Giữ các thay đổi trong phạm vi yêu cầu; không tự ý thay đổi kiến trúc hoặc tính năng đã thống nhất.
4. Với mã nguồn ứng dụng, ưu tiên nhánh riêng cho từng nhóm công việc và kiểm tra diff trước khi đưa vào nhánh chính.
5. Với tài liệu thiết kế ban đầu của repository trống, có thể commit trực tiếp lên main để lưu tiến độ.

## 3. Quy tắc commit
- Mỗi nhóm thay đổi có một commit riêng, nội dung ngắn gọn, mô tả đúng việc đã làm.
- Không commit file bí mật, token, mật khẩu, khóa riêng, keystore hoặc thông tin OAuth client secret.
- Không đưa dữ liệu cá nhân thật của khách hàng vào repository.
- Sau mỗi lần ghi file phải đọc lại hoặc kiểm tra metadata để xác nhận nội dung đã được lưu.
- Báo cáo rõ file nào được thêm/sửa, commit nào được tạo và đường dẫn GitHub để người dùng kiểm tra.

## 4. Quy tắc build APK và GitHub Actions
- Chỉ bắt đầu quy trình build khi chủ ứng dụng gửi đúng nội dung lệnh: `BUILD APK` (viết hoa, đúng cụm từ).
- Các tin nhắn hỏi tiến độ, yêu cầu kiểm tra, sửa lỗi, hoặc nhắc đến cụm từ BUILD APK nhưng không phải lệnh độc lập không tự động cho phép bắt đầu build.
- Không tự kích hoạt, dispatch, rerun hoặc khởi chạy workflow build APK khi chưa có lệnh `BUILD APK` độc lập.
- Khi có lệnh hợp lệ `BUILD APK`, kiểm tra mã nguồn và workflow trước; chỉ kích hoạt quy trình build nếu repository đã có cấu hình build phù hợp và thao tác đó được kết nối hỗ trợ.
- Nếu chưa có mã nguồn, chưa có workflow hoặc không thể kích hoạt build qua kết nối hiện có, phải nói rõ tình trạng; không giả vờ build.
- Workflow hiện tại là `.github/workflows/build-apks.yml`, chỉ có `workflow_dispatch`; không chạy tự động khi push hoặc theo lịch.
- Workflow tạo hai artifact debug riêng: `AUTU-LIO-Admin-Debug` và `AUTU-LIO-AutoClick-Debug`.
- Nếu kết nối GitHub hiện tại không hỗ trợ thao tác dispatch, chủ ứng dụng cần mở GitHub Actions → AUTU-LIO Android APK → Run workflow. Không được báo đã bắt đầu cho đến khi nhìn thấy một workflow run thực tế.
- Chỉ kiểm tra trạng thái, log và artifact của lần chạy khi chủ ứng dụng yêu cầu theo dõi hoặc cung cấp lần chạy cần kiểm tra.
- Khi báo cáo build, phân biệt rõ: đang chờ, đang chạy, thành công, thất bại, bị hủy; không suy đoán kết quả.
- Không tuyên bố APK đã được tạo nếu chưa xác minh artifact tải được.
- Không tự động tải lên hoặc phát hành APK cho người dùng cuối.

## 5. Phân biệt hai tài khoản GitHub
- Repository AUTU-LIO hiện thuộc HUY-HOA-1 và tài khoản này có quyền ghi. Các commit trong repository này sử dụng kết nối HUY-HOA-1 để bảo đảm quyền truy cập đúng chủ sở hữu.
- Quy trình của COC-LIO không được áp dụng máy móc sang tài khoản/repository khác khi quyền truy cập khác nhau.
- Nếu chủ ứng dụng muốn chuyển AUTU-LIO sang HUY-HOA, cần thực hiện chuyển quyền hoặc cấp quyền repository trước; không tự ý di chuyển.

## 6. Kiểm tra chất lượng
- Đọc lại file sau khi tạo/sửa.
- Kiểm tra tên file, đường dẫn, nhánh và commit.
- Khi đã có mã nguồn: rà soát lỗi biên dịch, cảnh báo, quyền Android, bảo mật và khả năng tương thích trước khi đề nghị build.
- Chỉ nói đã kiểm thử khi thực sự có kết quả kiểm thử.

## 7. Trạng thái hiện tại
- Repository có tài liệu thiết kế, project Gradle Android và prototype giao diện Kotlin/Jetpack Compose.
- Đã cấu hình hai product flavor: Admin và Client.
- Đã tạo GitHub Actions workflow thủ công để build hai APK debug; chưa có lần chạy build được xác minh.
- Chưa tích hợp backend, Google OAuth, giấy phép thật, compiler/runtime hoặc engine tự động hóa.
