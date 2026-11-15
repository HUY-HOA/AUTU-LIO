# AUTU-LIO — Trạng thái dự án

**Cập nhật:** 15/11/2026, 10:20 (Asia/Ho_Chi_Minh)  
**Nhánh chính:** `main`  
**Repository:** [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)

## 1. Mốc hiện tại

- Quyết định kiến trúc mới: một APK, phân quyền trong ứng dụng; không dùng Firebase.
- Tài liệu phân quyền: `docs/ONE-APP-ACCESS-CONTROL.md`.
- Bộ chọn Admin/khách hàng trong Debug chỉ là preview, không phải đăng nhập hoặc xác minh quyền.

Dự án đã chuyển từ tài liệu thiết kế sang **prototype Android Kotlin/Jetpack Compose**. Đã chuyển dự án sang **một APK duy nhất** với application ID `com.autulio.app`. Các giao diện Admin và AutoClick là các khu vực trong cùng ứng dụng, được mở theo vai trò do máy chủ xác minh. Bản Debug hiện có bộ chọn giao diện thử nghiệm để kiểm tra bố cục; bộ chọn này không cấp quyền thật.

Các màn hình prototype có thể điều hướng và kiểm tra bố cục. Mã nguồn workspace cộng tác và Worker API đã có trong `main`; schema D1 cho workspace đã áp dụng. Bộ test Worker mới gồm 10 ca về health/config, xác thực, quyền OWNER-only, kiểm tra đầu vào và CORS đã chạy thành công trên nhánh PR và sau merge vào `main` (PR #13, commit `9fb9c5db`). Tài khoản Cloudflare đã có D1 nhưng danh sách Worker đang trống; Worker chưa deploy và Google OAuth chưa cấu hình, nên đăng nhập thật và đồng bộ giữa các tài khoản chưa hoạt động. PR #14 đã đưa AUTU-LIO Script 0.1 vào `main` ở commit `51460f66`: parser Kotlin tạo AST, chẩn đoán lỗi dòng/cột, giới hạn vòng lặp/số lệnh/thời lượng và nút Kiểm tra mã trong editor. GitHub Actions chạy `:app:testDebugUnitTest` thành công (12 test); đây là kiểm thử mã nguồn, không tạo APK. PR runtime core đang bổ sung interpreter tuần tự cho AST, trạng thái chạy/tạm dừng/tiếp tục/dừng và kiểm tra lại ngân sách runtime; phần này chưa tự thực hiện thao tác màn hình vì AccessibilityService dispatcher và kết nối giao diện chưa tích hợp.

## 2. Bảo mật và ký APK

- Trước thay đổi: workflow chỉ tạo Debug APK, chưa bật R8/minify và chưa có chữ ký Release ổn định.
- Đã bổ sung cấu hình R8 và shrink resources cho variant Release; Android Manifest tắt cleartext traffic và giữ `allowBackup=false`.
- Đã bổ sung mã hóa AES-256-GCM cho bản nháp cục bộ bằng Android Keystore, kèm chuyển đổi dữ liệu nháp cũ từ plaintext.
- Đã chuẩn bị workflow tự chọn Release đã ký khi có đủ bốn GitHub Actions secrets; nếu chưa có secret thì chỉ tạo Debug APK thử nghiệm. Keystore tạm được xóa khỏi runner sau build.
- Chưa thể ký Release cho tới khi chủ sở hữu tự tạo keystore và nhập bốn secrets. Không có khóa nào được lưu trong Git.
- Bảo vệ bằng R8 là obfuscation/shrinking, không phải mã hóa tuyệt đối; xem `docs/SECURITY-AND-SIGNING.md`.

## 3. Phần đã có trong mã nguồn

### Admin Studio
- Trang tổng quan.
- Danh sách kịch bản, tìm kiếm và lọc Bản nháp / Đã phát hành.
- Tạo kịch bản, lưu tên và nội dung bản nháp vào vùng lưu trữ riêng của ứng dụng trên thiết bị; nội dung được khôi phục sau khi đóng/mở lại app.
- Bản nháp chưa đồng bộ máy chủ và chưa được mã hóa riêng ở tầng ứng dụng.
- Trang Khách hàng và Thiết bị.
- Trang Giấy phép, Thư viện, Phát hành, Nhật ký và Cài đặt.
- Trang Quản lý cộng tác viên được nối API cho bản Release: tải danh sách, tạo lời mời, hiển thị mã một lần để sao chép/chia sẻ, đổi vai trò, thu hồi cộng tác viên và hủy lời mời đang chờ. Debug preview vẫn dùng bản nháp cục bộ.
- Người dùng đã đăng nhập có thể dán mã lời mời trong Cài đặt; máy chủ kiểm tra đúng email Google, chấp nhận token một lần và app tải lại role. Chưa gửi email tự động.
- Chuyển giao diện sáng / tối.
- Trang Dự án cộng tác: chủ sở hữu tạo dự án, thêm/gỡ cộng tác viên đã chấp nhận lời mời.
- Nhà phát triển có workspace lưu bản nháp mã kịch bản dùng chung theo revision và gửi duyệt.
- Nhà thiết kế có trình chỉnh UI cơ bản với tiêu đề, mô tả, màu, thành phần và bản xem trước trực tiếp; lưu thiết kế dùng chung và gửi duyệt.
- Người kiểm duyệt xem snapshot, duyệt/từ chối và ghi chú; không tự duyệt bản của mình.
- API kiểm tra vai trò, quyền và membership phía Worker; xung đột revision trả HTTP 409.

### Giao diện người dùng
- Màn hình đăng nhập prototype và chế độ xem trước.
- Trang chủ.
- Danh sách và chi tiết một kịch bản mẫu được ghi rõ là dữ liệu xem trước.
- Các nút Chạy / Tạm dừng / Dừng mẫu chỉ hiển thị thông báo; không thao tác trên màn hình.
- Trang Thiết bị / Giấy phép và Cài đặt.

## 4. Build

- Đã có workflow `.github/workflows/build-apks.yml`.
- Workflow được chỉnh để tạo một APK duy nhất: Release có R8 và chữ ký ổn định khi đã có secrets; nếu chưa có secrets thì tạo Debug test APK.
- Trigger được thiết lập theo `workflow_dispatch` hoặc nhánh `build-requests/**`; push thông thường lên `main` không tự build.
- PR #12 đã được hợp nhất vào `main` ở commit `941d9dbb`.
- GitHub Actions đã xác nhận Kotlin compilation và Worker/D1 migration checks thành công trên commit PR trước khi merge; workflow kiểm tra Worker sau merge đang/đã chạy riêng.
- Bộ kiểm tra Worker sau merge tại [GitHub Actions](https://github.com/HUY-HOA-1/AUTU-LIO/actions/runs/43671159108) đã thành công.
- PR #14 đã hợp nhất tại commit `51460f660a55f91d4025e1d29b3ecbb90f10b948`; Android source checks và unit tests thành công tại [GitHub Actions](https://github.com/HUY-HOA-1/AUTU-LIO/actions/runs/43671662017).
- Lượt build #1 đã thành công ngày 15/11/2026: `lintDebug`, `assembleDebug`, upload artifact `AUTU-LIO-Debug` đều thành công. Đây là bản Debug thử nghiệm, không có chữ ký phát hành ổn định.
- Tuân thủ quy tắc mới: chỉ cần tin nhắn chủ ứng dụng có chứa cụm viết hoa `BUILD APK` là cho phép bắt đầu build; không yêu cầu đứng riêng một dòng.

### Cấu hình build và kiểm tra nguồn

- Android Gradle Plugin: `9.3.1`.
- Kotlin Gradle Plugin và Compose Compiler plugin: `2.4.21`.
- Gradle trong GitHub Actions: `9.6.0`.
- JDK chạy build: `21`; bytecode ứng dụng vẫn đặt Java/Kotlin target `17`.
- Android SDK: `compileSdk 37`, Build Tools `36.0.0`.
- Tạm đặt `android.builtInKotlin=false` và `android.newDsl=false` để dùng Kotlin Android plugin 2.4.21 trong bộ phiên bản tương thích đã chọn. Đây là bước tương thích tạm thời; cần chuyển sang Kotlin tích hợp của AGP trước khi nâng AGP 10.
- Cấu hình Android đã được kiểm chứng bằng tác vụ Gradle `:app:testDebugUnitTest` trong CI, bao gồm biên dịch Kotlin và chạy 12 unit test Script Compiler. Đây không chạy `assembleDebug`, không tạo APK và chưa thay thế lint hoặc kiểm thử trên thiết bị.

## 5. Phần chưa triển khai

- Android đã tích hợp Google Credential Manager 1.6.0 / Google ID 1.2.1, nonce, gọi Auth API, xác minh session trên máy chủ và lưu session mã hóa bằng Android Keystore; cần Google OAuth Client ID + Worker URL để bật đăng nhập thật.
- Cloudflare D1 database `autu_lio_auth` ở APAC đã áp dụng ba migration `0001`, `0002`, `0003_shared_project_workspaces.sql`; có các bảng tài khoản, session, lời mời, quyền, nhật ký, dự án, thành viên dự án, bản nháp mã/UI và yêu cầu duyệt. `PRAGMA foreign_key_check` trả về rỗng.
- Worker source và D1 binding đã có trong `main`, nhưng Worker chưa deploy.
- Google OAuth Client ID và OWNER_EMAIL chưa được cấu hình; Worker chưa deploy nên đăng nhập thật và xác minh vai trò vẫn chưa hoạt động.
- Các thao tác quản lý cộng tác viên, quyền chi tiết, workspace dự án, lưu bản nháp mã/UI và quy trình duyệt đã được hợp nhất vào `main`. App gọi API nhưng chỉ dùng trực tuyến được sau khi Worker deploy và cấu hình OAuth secrets.
- Android lưu session bằng AES-GCM/Android Keystore; bản Release chỉ mở UI theo role xác minh, bản Debug có bộ chọn preview role riêng.
- Developer/Designer/Reviewer có workspace riêng cho dự án được giao. API lưu nội dung lên D1 theo revision; giao diện mới đã biên dịch thành công nhưng chưa được thử với tài khoản Google thật do Worker chưa deploy.
- Worker source có API xác thực, lời mời, quyền chi tiết, tạo dự án, membership, script/UI drafts, submission và review. Các endpoint chưa hoạt động công khai vì Worker chưa deploy và OAuth secrets chưa cấu hình.
- Đồng bộ tài khoản, thiết bị, giấy phép và dữ liệu khách hàng.
- Parser/validator AUTU-LIO Script 0.1 đã có; runtime core interpreter đang được bổ sung trong PR #17. Còn thiếu AccessibilityService dispatcher, nối UI điều khiển, bytecode/package, biến, điều kiện, hàm tự định nghĩa và thư viện.
- AccessibilityService dispatcher để thực hiện thao tác AutoClick thật, overlay và kết nối điều khiển vòng đời tác vụ vào giao diện.
- Đóng gói, ký, mã hóa và phân phối kịch bản thật.
- Đã cấu hình bốn GitHub Actions secrets cho khóa ký Release riêng của AUTU-LIO; lượt build Release #2 đã xác thực chữ ký APK thành công. Không thay hoặc làm mất bản sao lưu keystore.
- Cập nhật từ xa, nhật ký máy chủ và quy trình thu hồi package.
- Xác nhận Gmail chính xác của chủ sở hữu và tạo OAuth Client IDs trong Google Cloud; cấu hình `GOOGLE_CLIENT_ID`, `OWNER_EMAIL`, `GOOGLE_ALLOWED_AZP` và Worker URL.
- Deploy Worker, kiểm thử `/health`, đăng nhập Google thật, mời cộng tác viên, phân quyền, lưu bản nháp, xử lý revision conflict và duyệt submission.
- Chạy lint/full Android checks, sau đó chỉ tạo APK khi nhận đúng lệnh `BUILD APK`; cài thử trên thiết bị thật.

## 6. Việc tiếp theo

1. Xác nhận Gmail Google chính xác của chủ sở hữu và tạo OAuth Web Client ID + Android Client ID trong Google Cloud.
2. Cấu hình Worker secrets `GOOGLE_CLIENT_ID`, `OWNER_EMAIL`, `GOOGLE_ALLOWED_AZP`; deploy Worker và kiểm tra `/health`.
3. Đặt `AUTU_LIO_AUTH_API_BASE_URL` và `AUTU_LIO_GOOGLE_SERVER_CLIENT_ID` trong môi trường Gradle; thử đăng nhập chủ sở hữu, mời cộng tác viên và kiểm tra workspace trên điện thoại.
4. Bổ sung test API/role, giới hạn tần suất đăng nhập, kiểm thử lời mời dùng một lần, thu hồi phiên và kiểm thử xung đột revision.
5. Mở rộng ngôn ngữ AUTU-LIO (biến, điều kiện, hàm, thư viện); xây interpreter/runtime và engine Auto Click, sau đó xin quyền Android minh bạch.
6. Hoàn thiện giấy phép, ký/mã hóa package, cập nhật từ xa và kiểm thử phát hành.

## 7. Quy tắc báo cáo

- Phân biệt rõ tài liệu thiết kế, prototype giao diện, chức năng đã hoạt động và chức năng máy chủ.
- Không báo một chức năng đã hoạt động nếu mới chỉ có nút hoặc màn hình.
- Không báo build thành công nếu chưa có workflow run thành công và APK artifact tải được.
- Không tự động kích hoạt workflow; tuân thủ lệnh build độc lập trong `docs/GITHUB-WORKFLOW.md`.
