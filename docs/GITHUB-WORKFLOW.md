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
- Chỉ cần tin nhắn của chủ ứng dụng có chứa cụm viết hoa `BUILD APK` là cho phép bắt đầu quy trình build; không bắt buộc cụm từ phải đứng riêng một dòng.
- Tin nhắn chỉ hỏi trạng thái hoặc nhắc đến build nhưng không có đúng cụm viết hoa `BUILD APK` không tự động cho phép bắt đầu.
- Khi có cụm `BUILD APK`, kiểm tra mã nguồn và workflow trước; chỉ kích hoạt quy trình build nếu repository có cấu hình phù hợp và thao tác đó được kết nối hỗ trợ.
- Nếu không thể kích hoạt build qua kết nối hiện có, phải nói rõ tình trạng; không giả vờ build.
- Workflow hiện tại là `.github/workflows/build-apks.yml`: có `workflow_dispatch` và chỉ nhận push từ nhánh `build-requests/**` khi có file trong `.build-requests/**`. Push thông thường lên `main` không chạy build.
- Nhánh yêu cầu `build-requests/release-*` luôn yêu cầu đủ bốn GitHub Actions secrets và chỉ tạo APK Release đã ký bằng khóa cố định, bật R8/shrink resources. Nếu thiếu bất kỳ secret nào, workflow phải thất bại và không tạo APK Debug thay thế.
- Nhánh `build-requests/debug-*` chỉ tạo APK Debug phục vụ kiểm thử nội bộ; không dùng Debug làm bản phát hành hoặc để cập nhật đè ổn định.
- `workflow_dispatch` mặc định chọn Release. Nếu chủ sở hữu chủ động chọn Debug, artifact phải được ghi rõ là bản kiểm thử.
- Nếu kết nối GitHub không hỗ trợ dispatch trực tiếp, sau lệnh độc lập `BUILD APK`, tạo nhánh `build-requests/release-<mã-yêu-cầu>` từ commit Android đã được kiểm tra và thêm đúng một file marker trong `.build-requests/` để kích hoạt Release workflow. Chỉ dùng `build-requests/debug-<mã-yêu-cầu>` khi chủ sở hữu yêu cầu Debug rõ ràng. Không tạo nhánh/file marker trước khi nhận đúng lệnh.
- Nếu trigger bằng nhánh không xuất hiện run, chủ ứng dụng mở GitHub Actions → AUTU-LIO Android APK → Run workflow. Không được báo đã bắt đầu cho đến khi nhìn thấy một workflow run thực tế.
- Chỉ kiểm tra trạng thái, log và artifact của lần chạy khi chủ ứng dụng yêu cầu theo dõi hoặc cung cấp lần chạy cần kiểm tra.
- Khi báo cáo build, phân biệt rõ: đang chờ, đang chạy, thành công, thất bại, bị hủy; không suy đoán kết quả.
- GitHub Actions hiển thị tiến độ theo các bước lint, assemble và upload. Không tự đặt phần trăm giả hoặc thời gian còn lại nếu Gradle không cung cấp số liệu đáng tin cậy.
- Không tuyên bố APK đã được tạo nếu chưa xác minh artifact tải được.
- Không tự động tải lên hoặc phát hành APK cho người dùng cuối.

## 5. Bộ công cụ build hiện tại

- Ứng dụng chỉ có một application ID `com.autulio.app`; không sử dụng product flavor Admin/Client.
- GitHub Actions chạy một variant cho mỗi lượt: Release bắt buộc có đủ bốn signing secrets; thiếu secrets thì dừng. Debug chỉ chạy khi nhánh hoặc lựa chọn workflow chỉ định Debug rõ ràng. Artifact tương ứng là `AUTU-LIO-Release` hoặc `AUTU-LIO-Debug`.

- AGP `9.3.1`, Kotlin Gradle Plugin `2.4.21`, Compose Compiler plugin `2.4.21`.
- GitHub Actions dùng JDK `21`, Gradle `9.6.0`, Android SDK API `37` và Build Tools `36.0.0`.
- Ứng dụng vẫn tạo bytecode tương thích Java/Kotlin `17`.
- `gradle.properties` đang tạm tắt built-in Kotlin và Android new DSL để dùng Kotlin Android plugin 2.4.21. Phải rà soát lại khi nâng AGP hoặc chuyển sang built-in Kotlin.
- GitHub Actions đã chạy `:app:compileDebugKotlin` thành công trên nhánh PR cộng tác. Đây chỉ là xác nhận biên dịch Kotlin; chưa xác nhận `:app:lintDebug`/`:app:assembleDebug` và chưa có APK artifact.

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

- AUTU-LIO là ứng dụng Android native một APK, chỉ có đúng hai vai trò: OWNER và CUSTOMER.
- OWNER duy nhất là `phunghuyhoahaikba@gmail.com`; tất cả tài khoản Google khác được Worker xác định là CUSTOMER.
- CUSTOMER chỉ có màn hình thư viện script được cấp phép để tải và chạy; không có IDE, menu quản trị, cộng tác viên, dự án hoặc role preview.
- Màn hình cộng tác, API invitation/collaborator và shared-workspace đã bị gỡ khỏi nhánh tích hợp.
- Android kiểm tra quyền Trợ năng khi vào app, trước khi mở editor, trước khi chuẩn bị script và khi nhấn Chạy/Tiếp tục. Khi chỉnh sửa/chạy, AccessibilityService mở cửa sổ điều khiển nổi `TYPE_ACCESSIBILITY_OVERLAY`.
- Worker checks đã thành công trên commit `4447cbb8a187d59f04b56b4c7af90572718bbc39`; Android source checks cần xác nhận kết quả cuối cùng.
- Worker AUTU-LIO chưa deploy, Google OAuth chưa cấu hình và D1 migration `0006_single_owner_customer_only.sql` chưa áp dụng production. Chưa xác nhận đăng nhập/API hoạt động thật.
- Cần kiểm thử thực tế trên Android/Xiaomi HyperOS trước khi coi cửa sổ nổi, gesture và chạy nền đã được chấp nhận.
- Không có website, web dashboard hoặc web preview. `backend/` là REST API cho Android, không phải website.
- Không phát hành APK Debug. Chỉ xác nhận APK Release mới khi workflow hoàn tất và artifact tải được.
