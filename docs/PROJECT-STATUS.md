# AUTU-LIO — Trạng thái dự án

**Cập nhật:** 15/11/2026  
**Nhánh chính:** `main`  
**Repository:** [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)

## 1. Mốc hiện tại

- Quyết định kiến trúc mới: một APK, phân quyền trong ứng dụng; không dùng Firebase.
- Tài liệu phân quyền: `docs/ONE-APP-ACCESS-CONTROL.md`.
- Bộ chọn Admin/khách hàng trong Debug chỉ là preview, không phải đăng nhập hoặc xác minh quyền.

Dự án đã chuyển từ tài liệu thiết kế sang **prototype Android Kotlin/Jetpack Compose**. Đã chuyển dự án sang **một APK duy nhất** với application ID `com.autulio.app`. Các giao diện Admin và AutoClick là các khu vực trong cùng ứng dụng, được mở theo vai trò do máy chủ xác minh. Bản Debug hiện có bộ chọn giao diện thử nghiệm để kiểm tra bố cục; bộ chọn này không cấp quyền thật.

Các màn hình prototype có thể điều hướng và kiểm tra bố cục. Đây chưa phải bản ứng dụng có backend và chức năng tự động hóa thực tế.

## 2. Phần đã có trong mã nguồn

### Admin Studio
- Trang tổng quan.
- Danh sách kịch bản, tìm kiếm và lọc Bản nháp / Đã phát hành.
- Tạo kịch bản, lưu tên và nội dung bản nháp vào vùng lưu trữ riêng của ứng dụng trên thiết bị; nội dung được khôi phục sau khi đóng/mở lại app.
- Bản nháp chưa đồng bộ máy chủ và chưa được mã hóa riêng ở tầng ứng dụng.
- Trang Khách hàng và Thiết bị.
- Trang Giấy phép, Thư viện, Phát hành, Nhật ký và Cài đặt.
- Trang Quản lý cộng tác viên prototype: nhập Gmail, chọn Nhà phát triển / Nhà thiết kế / Người kiểm duyệt, lưu và xóa bản nháp lời mời cục bộ. Chưa gửi lời mời, chưa gán vai trò trên máy chủ.
- Chuyển giao diện sáng / tối.

### Giao diện người dùng
- Màn hình đăng nhập prototype và chế độ xem trước.
- Trang chủ.
- Danh sách và chi tiết một kịch bản mẫu được ghi rõ là dữ liệu xem trước.
- Các nút Chạy / Tạm dừng / Dừng mẫu chỉ hiển thị thông báo; không thao tác trên màn hình.
- Trang Thiết bị / Giấy phép và Cài đặt.

## 3. Build

- Đã có workflow `.github/workflows/build-apks.yml`.
- Workflow được chỉnh để kiểm tra lint và tạo một APK debug duy nhất (`AUTU-LIO-Debug`).
- Trigger được thiết lập theo `workflow_dispatch` hoặc nhánh `build-requests/**`; push thông thường lên `main` không tự build.
- **Chưa có workflow run được xác minh và chưa có APK artifact được xác minh.**
- Tuân thủ quy tắc: chỉ bắt đầu build khi chủ ứng dụng gửi riêng đúng lệnh `BUILD APK`.

### Cấu hình build đã được đồng bộ tĩnh (chưa chạy thử)

- Android Gradle Plugin: `9.3.1`.
- Kotlin Gradle Plugin và Compose Compiler plugin: `2.4.21`.
- Gradle trong GitHub Actions: `9.6.0`.
- JDK chạy build: `21`; bytecode ứng dụng vẫn đặt Java/Kotlin target `17`.
- Android SDK: `compileSdk 37`, Build Tools `36.0.0`.
- Tạm đặt `android.builtInKotlin=false` và `android.newDsl=false` để dùng Kotlin Android plugin 2.4.21 trong bộ phiên bản tương thích đã chọn. Đây là bước tương thích tạm thời; cần chuyển sang Kotlin tích hợp của AGP trước khi nâng AGP 10.
- Cấu hình trên đã được rà soát theo bảng tương thích và tài liệu công cụ, nhưng **chưa được kiểm chứng bằng một lần chạy Gradle thực tế** vì chưa nhận lệnh build độc lập.

## 4. Phần chưa triển khai

- Android đã tích hợp khung Google Credential Manager 1.6.0 / Google ID 1.2.1, nonce, gọi Auth API và xác minh session trên máy chủ; cần Google OAuth Client ID + Worker URL để bật đăng nhập thật.
- Cloudflare D1 database `autu_lio_auth` đã được tạo ở APAC; bảng tài khoản, nonce, session, lời mời, quyền ghi đè, nhật ký và cấu hình đã được khởi tạo. Worker source và cấu hình binding đã được thêm vào repository nhưng chưa deploy.
- Google OAuth Client ID và OWNER_EMAIL chưa được cấu hình; Worker chưa deploy nên đăng nhập thật và xác minh vai trò vẫn chưa hoạt động.
- Android lưu session bằng AES-GCM/Android Keystore; bản Release chỉ mở UI theo role xác minh, bản Debug có bộ chọn preview role riêng.
- Developer/Designer/Reviewer có không gian UI prototype riêng; nháp Developer và tùy chọn Designer hiện chỉ lưu/xem cục bộ, chưa đồng bộ máy chủ.
- API tạo/nhận lời mời, liệt kê/đổi quyền/thu hồi cộng tác viên đã có trong Worker source; chưa hoạt động với app do Worker chưa deploy và chưa có OAuth secrets.
- Đồng bộ tài khoản, thiết bị, giấy phép và dữ liệu khách hàng.
- Trình biên dịch, parser, bytecode và runtime của ngôn ngữ AUTU-LIO.
- Engine thực hiện Auto Click, Accessibility Service, overlay và điều khiển vòng đời tác vụ.
- Đóng gói, ký, mã hóa và phân phối kịch bản thật.
- Cập nhật từ xa, nhật ký máy chủ và quy trình thu hồi package.
- Kiểm thử đăng nhập Google thực trên thiết bị; nối giao diện cộng tác viên vào API; kiểm thử compile Kotlin Android.
- Kiểm thử lint/build APK và thử nghiệm APK trên thiết bị thật.

## 5. Việc tiếp theo

1. Hoàn thiện cấu hình Google OAuth, xác nhận chính xác Gmail chủ sở hữu, đặt Worker secrets rồi deploy API.
2. Kết nối Android Credential Manager với API, lưu bearer session an toàn và mở màn hình theo vai trò máy chủ.
3. Bổ sung test API/role, giới hạn tần suất đăng nhập, kiểm thử lời mời dùng một lần và thu hồi phiên.
4. Thiết kế đặc tả ngôn ngữ AUTU-LIO trước khi viết parser/compiler/runtime.
5. Triển khai engine tự động hóa có giới hạn quyền và xin quyền Android minh bạch.
6. Tích hợp backend, đăng nhập, giấy phép và cập nhật package sau khi kiến trúc runtime đã ổn định.

## 6. Quy tắc báo cáo

- Phân biệt rõ tài liệu thiết kế, prototype giao diện, chức năng đã hoạt động và chức năng máy chủ.
- Không báo một chức năng đã hoạt động nếu mới chỉ có nút hoặc màn hình.
- Không báo build thành công nếu chưa có workflow run thành công và APK artifact tải được.
- Không tự động kích hoạt workflow; tuân thủ lệnh build độc lập trong `docs/GITHUB-WORKFLOW.md`.
