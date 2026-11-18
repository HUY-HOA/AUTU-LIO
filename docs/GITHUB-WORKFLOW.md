# AUTU-LIO — Quy trình làm việc với GitHub

## 1. Repository chính thức
- Repository: `HUY-HOA/AUTU-LIO`
- Nhánh mặc định: `main`
- Repository đang ở chế độ public.
- Chủ repository hiện tại: `HUY-HOA`.
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
- Chỉ bắt đầu build khi chủ ứng dụng gửi đúng toàn bộ nội dung tin nhắn `BUILD APK` viết hoa.
- Không chạy build khi người dùng chỉ hỏi trạng thái, nhắc đến APK hoặc dùng cụm từ này trong câu khác.
- Khi có cụm `BUILD APK`, kiểm tra mã nguồn và workflow trước; chỉ kích hoạt quy trình build nếu repository có cấu hình phù hợp và thao tác đó được kết nối hỗ trợ.
- Nếu không thể kích hoạt build qua kết nối hiện có, phải nói rõ tình trạng; không giả vờ build.
- Workflow hiện tại là `.github/workflows/build-apks.yml`: có `workflow_dispatch` và chỉ nhận push từ nhánh `build-requests/**` khi có file trong `.build-requests/**`. Push thông thường lên `main` không chạy build.
- Khi đủ bốn GitHub Actions secrets ký APK, workflow tạo một APK Release đã ký bằng khóa cố định, bật R8 và shrink resources. Khi chưa cấu hình khóa, workflow chỉ tạo một APK Debug thử nghiệm; không dùng Debug làm bản phát hành hoặc để cập nhật đè ổn định.
- Nếu kết nối GitHub không hỗ trợ dispatch trực tiếp, sau lệnh độc lập `BUILD APK`, tạo nhánh `build-requests/<mã-yêu-cầu>` từ `main` và thêm đúng một file marker trong `.build-requests/` để kích hoạt workflow. Không tạo nhánh/file marker trước khi nhận đúng lệnh.
- Nếu trigger bằng nhánh không xuất hiện run, chủ ứng dụng mở GitHub Actions → AUTU-LIO Android APK → Run workflow. Không được báo đã bắt đầu cho đến khi nhìn thấy một workflow run thực tế.
- Chỉ kiểm tra trạng thái, log và artifact của lần chạy khi chủ ứng dụng yêu cầu theo dõi hoặc cung cấp lần chạy cần kiểm tra.
- Khi báo cáo build, phân biệt rõ: đang chờ, đang chạy, thành công, thất bại, bị hủy; không suy đoán kết quả.
- GitHub Actions hiển thị tiến độ theo các bước lint, assemble và upload. Không tự đặt phần trăm giả hoặc thời gian còn lại nếu Gradle không cung cấp số liệu đáng tin cậy.
- Không tuyên bố APK đã được tạo nếu chưa xác minh artifact tải được.
- Không tự động tải lên hoặc phát hành APK cho người dùng cuối.

## 5. Bộ công cụ build hiện tại

- Ứng dụng chỉ có một application ID `com.autulio.app`; không sử dụng product flavor Admin/Client.
- GitHub Actions chạy lint/build cho một variant duy nhất: Release có ký khi đủ secrets; nếu chưa có secrets thì Debug để kiểm thử. Artifact được đặt tên tương ứng `AUTU-LIO-Release` hoặc `AUTU-LIO-Debug`.

- AGP `9.3.1`, Kotlin Gradle Plugin `2.4.21`, Compose Compiler plugin `2.4.21`.
- GitHub Actions dùng JDK `21`, Gradle `9.6.0`, Android SDK API `37` và Build Tools `36.0.0`.
- Ứng dụng vẫn tạo bytecode tương thích Java/Kotlin `17`.
- `gradle.properties` đang tạm tắt built-in Kotlin và Android new DSL để dùng Kotlin Android plugin 2.4.21. Phải rà soát lại khi nâng AGP hoặc chuyển sang built-in Kotlin.
- GitHub Actions đã chạy `:app:compileDebugKotlin` thành công trên nhánh PR cộng tác. Đây chỉ là xác nhận biên dịch Kotlin; chưa xác nhận `:app:lintDebug`/`:app:assembleDebug` và chưa có APK artifact.

## 6. Phân biệt hai tài khoản GitHub
- Repository AUTU-LIO hiện thuộc HUY-HOA; các thay đổi phải dùng đúng kết nối có quyền quản trị repository.
- Quy trình của COC-LIO không được áp dụng máy móc sang tài khoản/repository khác khi quyền truy cập khác nhau.

## 7. Kiểm tra chất lượng
- Đọc lại file sau khi tạo/sửa.
- Kiểm tra tên file, đường dẫn, nhánh và commit.
- Khi đã có mã nguồn: rà soát lỗi biên dịch, cảnh báo, quyền Android, bảo mật và khả năng tương thích trước khi đề nghị build.
- Chỉ nói đã kiểm thử khi thực sự có kết quả kiểm thử.

## 8. Trạng thái hiện tại
- Repository có prototype Android Kotlin/Jetpack Compose trong một APK duy nhất, có giao diện theo vai trò.
- Đã tích hợp mã nguồn Google Credential Manager, Cloudflare Worker API, phân quyền cộng tác viên, workspace dự án dùng chung và kiểm duyệt; các kiểm tra Kotlin compilation và Worker/D1 đã đạt trên PR.
- Worker chưa deploy, D1 `autu_lio_auth` có 0 bảng và Google OAuth chưa cấu hình; đăng nhập/API thật chưa hoạt động. Các migration trong Git mới được kiểm tra cục bộ.
- Không lưu keystore, mật khẩu hoặc khóa riêng trong repository. Release signing chỉ sử dụng GitHub Actions secrets có tên quy định trong `docs/SECURITY-AND-SIGNING.md`.
- Chưa có giấy phép thật, compiler/runtime AUTU-LIO hoặc engine Auto Click.
