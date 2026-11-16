# AUTU-LIO

Ứng dụng tự động hóa thao tác trên Android, phát hành dưới dạng **một APK duy nhất**. Sau khi đăng nhập và xác minh vai trò, ứng dụng mở giao diện và quyền phù hợp:

- **Chủ sở hữu:** quản trị toàn bộ ứng dụng, cộng tác viên, kịch bản và phát hành.
- **Nhà phát triển:** viết, sửa và kiểm thử kịch bản theo quyền được cấp.
- **Nhà thiết kế:** chỉnh tiêu đề, nội dung, màu và thành phần UI với bản xem trước.
- **Người kiểm duyệt:** kiểm tra và duyệt bản phát hành.
- **Người dùng:** chỉ sử dụng kịch bản đã phát hành.

## Repository

- Chủ sở hữu: HUY-HOA-1
- Repository: [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)
- Nhánh chính: main

## Tài liệu thiết kế

- [Định hướng UI tổng thể](docs/UI-DESIGN-PLAN.md)
- [Trạng thái và lộ trình dự án](docs/PROJECT-STATUS.md)
- [Màn hình Tổng quan Admin](docs/01-admin-dashboard.md)
- [Quản lý kịch bản](docs/02-script-management.md)
- [Trình soạn thảo ngôn ngữ riêng](docs/03-script-editor.md)
- [Thư viện và Phát hành](docs/04-library-and-release.md)
- [Ứng dụng khách hàng](docs/05-customer-app.md)
- [Quản lý khách hàng, thiết bị và giấy phép](docs/06-admin-customers-devices-license.md)
- [Một ứng dụng và phân quyền](docs/ONE-APP-ACCESS-CONTROL.md)
- [Cấu hình đăng nhập Google và Cloudflare](docs/GOOGLE-SIGNIN-SETUP.md)
- [Quy trình GitHub](docs/GITHUB-WORKFLOW.md)
- [Bảo mật mã nguồn, APK và khóa ký](docs/SECURITY-AND-SIGNING.md)
- [Cửa sổ chạy nổi và chỉnh sửa kịch bản khi chạy](docs/08-floating-runner-and-edit.md)
- [Hướng dẫn kiểm thử prototype Android](docs/ANDROID-PROTOTYPE.md)

## Build

Workflow tạo đúng một APK cho mỗi lượt build:
- Khi đã cài đặt đủ GitHub Actions signing secrets: `release` — APK Release đã ký bằng khóa ổn định, bật R8 và shrink resources.
- Khi chưa cài đặt khóa: `debug` — APK thử nghiệm, không dùng phát hành hoặc cập nhật đè ổn định.

Xem [hướng dẫn bảo mật và ký APK](docs/SECURITY-AND-SIGNING.md) trước khi phát hành cho người dùng.

Build chỉ được khởi chạy theo đúng quy tắc trong [GITHUB-WORKFLOW.md](docs/GITHUB-WORKFLOW.md). Workflow APK chỉ chạy qua `workflow_dispatch` hoặc nhánh yêu cầu build chuyên biệt `build-requests/**`; push thông thường lên `main` không tự build. Chỉ khởi chạy khi tin nhắn của chủ sở hữu có cụm `BUILD APK`.

## Trạng thái

Đã có prototype Android Kotlin/Jetpack Compose trong một APK duy nhất. Quản lý cộng tác viên, phân quyền chi tiết, workspace dự án, lưu bản nháp mã/UI và quy trình duyệt đã được thêm vào `main`; các kiểm tra Kotlin compilation và Worker/D1 đã thành công. Migration workspace đã áp dụng vào D1. Tuy vậy Worker chưa deploy và Google OAuth chưa cấu hình nên đăng nhập/sync thật chưa hoạt động. Compiler/runtime Auto Click và engine thao tác chưa hoàn thành. Chưa tạo APK artifact; build chỉ thực hiện khi nhận đúng lệnh `BUILD APK`. Xem [trạng thái dự án](docs/PROJECT-STATUS.md) để biết chi tiết.
