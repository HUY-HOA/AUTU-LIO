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
- Workflow hiện tại là `.github/workflows/build-apks.yml`: có `workflow_dispatch` và chỉ nhận push từ nhánh `build-requests/**` khi có file trong `.build-requests/**`. Push thông thường lên `main` không chạy build.
- Workflow tạo một artifact debug duy nhất: `AUTU-LIO-Debug`.
- Nếu kết nối GitHub không hỗ trợ dispatch trực tiếp, sau lệnh độc lập `BUILD APK`, tạo nhánh `build-requests/<mã-yêu-cầu>` từ `main` và thêm đúng một file marker trong `.build-requests/` để kích hoạt workflow. Không tạo nhánh/file marker trước khi nhận đúng lệnh.
- Nếu trigger bằng nhánh không xuất hiện run, chủ ứng dụng mở GitHub Actions → AUTU-LIO Android APK → Run workflow. Không được báo đã bắt đầu cho đến khi nhìn thấy một workflow run thực tế.
- Chỉ kiểm tra trạng thái, log và artifact của lần chạy khi chủ ứng dụng yêu cầu theo dõi hoặc cung cấp lần chạy cần kiểm tra.
- Khi báo cáo build, phân biệt rõ: đang chờ, đang chạy, thành công, thất bại, bị hủy; không suy đoán kết quả.
- GitHub Actions hiển thị tiến độ theo các bước lint, assemble và upload. Không tự đặt phần trăm giả hoặc thời gian còn lại nếu Gradle không cung cấp số liệu đáng tin cậy.
- Không tuyên bố APK đã được tạo nếu chưa xác minh artifact tải được.
- Không tự động tải lên hoặc phát hành APK cho người dùng cuối.

## 5. Bộ công cụ build hiện tại

- Ứng dụng chỉ có một application ID `com.autulio.app`; không sử dụng product flavor Admin/Client.
- GitHub Actions chạy `:app:lintDebug` và `:app:assembleDebug`, sau đó tải artifact `AUTU-LIO-Debug`.
- Workflow đọc hai GitHub Actions Variables công khai `AUTULIO_API_BASE_URL` và `AUTULIO_GOOGLE_WEB_CLIENT_ID` rồi truyền vào Gradle. Nếu chưa đặt, APK vẫn có thể build nhưng đăng nhập Google sẽ báo thiếu cấu hình.

- AGP `9.3.1`, Kotlin Gradle Plugin `2.4.21`, Compose Compiler plugin `2.4.21`.
- GitHub Actions dùng JDK `21`, Gradle `9.6.0`, Android SDK API `37` và Build Tools `36.0.0`.
- Ứng dụng vẫn tạo bytecode tương thích Java/Kotlin `17`.
- `gradle.properties` đang tạm tắt built-in Kotlin và Android new DSL để dùng Kotlin Android plugin 2.4.21. Phải rà soát lại khi nâng AGP hoặc chuyển sang built-in Kotlin.
- Các phiên bản này mới được kiểm tra tĩnh qua tài liệu tương thích; không được gọi là build thành công cho đến khi GitHub Actions chạy thật và tạo artifact.

## 6. Phân biệt hai tài khoản GitHub
- Repository AUTU-LIO hiện thuộc HUY-HOA-1 và tài khoản này có quyền ghi. Các commit trong repository này sử dụng kết nối HUY-HOA-1 để bảo đảm quyền truy cập đúng chủ sở hữu.
- Quy trình của COC-LIO không được áp dụng máy móc sang tài khoản/repository khác khi quyền truy cập khác nhau.
- Nếu chủ ứng dụng muốn chuyển AUTU-LIO sang HUY-HOA, cần thực hiện chuyển quyền hoặc cấp quyền repository trước; không tự ý di chuyển.

## 7. Kiểm tra chất lượng
- Đọc lại file sau khi tạo/sửa.
- Kiểm tra tên file, đường dẫn, nhánh và commit.
- Khi đã có mã nguồn: rà soát lỗi biên dịch, cảnh báo, quyền Android, bảo mật và khả năng tương thích trước khi đề nghị build.
- Chỉ nói đã kiểm thử khi thực sự có kết quả kiểm thử.

## 8. Trạng thái hiện tại
- Repository có prototype Kotlin/Jetpack Compose một APK, mã Android Google Credential Manager và màn hình cộng tác viên.
- Đã thêm mã nguồn Cloudflare Worker/D1 cho xác minh Google token, session và API quản lý cộng tác viên; chưa tạo D1 database hoặc deploy Worker.
- Google OAuth client IDs và các biến cấu hình chưa được thiết lập; chưa kiểm thử đăng nhập thật.
- GitHub Actions workflow đã tạo một artifact debug, nhưng chưa có lần chạy build được xác minh.
- Giấy phép thật, compiler/runtime và engine tự động hóa vẫn chưa hoàn thiện.
