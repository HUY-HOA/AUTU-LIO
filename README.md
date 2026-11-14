# AUTU-LIO

Ứng dụng tự động hóa thao tác trên Android, phát hành dưới dạng **một APK duy nhất**. Sau khi đăng nhập và xác minh vai trò, ứng dụng mở giao diện và quyền phù hợp:

- **Chủ sở hữu:** quản trị toàn bộ ứng dụng, cộng tác viên, kịch bản và phát hành.
- **Nhà phát triển:** viết, sửa và kiểm thử kịch bản theo quyền được cấp.
- **Nhà thiết kế:** thiết kế giao diện và xem trước.
- **Người kiểm duyệt:** kiểm tra và duyệt bản phát hành.
- **Người dùng:** chỉ sử dụng kịch bản đã phát hành.

## Repository

- Chủ sở hữu: HUY-HOA-1
- Repository: [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)
- Nhánh chính: main

## Backend

- [Cloudflare API, Google token và phân quyền cộng tác viên](backend/README.md)

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
- [Quy trình GitHub](docs/GITHUB-WORKFLOW.md)
- [Hướng dẫn kiểm thử prototype Android](docs/ANDROID-PROTOTYPE.md)

## Build

Một APK debug duy nhất được tạo từ ứng dụng có giao diện theo quyền tài khoản:

- `debug` — AUTU-LIO (bản thử nghiệm một ứng dụng, có bộ chọn giao diện preview chỉ dành cho Debug).

Build chỉ được khởi chạy theo đúng quy tắc trong [GITHUB-WORKFLOW.md](docs/GITHUB-WORKFLOW.md). Workflow APK chỉ chạy qua `workflow_dispatch` hoặc nhánh yêu cầu build chuyên biệt `build-requests/**`; push thông thường lên `main` không tự build.

## Trạng thái

Đã chuyển sang một APK Kotlin/Jetpack Compose. Mã Android cho Google Credential Manager, session Android Keystore và màn hình quản lý cộng tác viên đã được thêm; mã Cloudflare Worker/D1 cho xác minh token, vai trò và lời mời cũng đã được chuẩn bị. Các phần này chưa hoạt động live cho đến khi cấu hình OAuth, tạo D1, triển khai Worker và kiểm thử. Bộ chọn giao diện Debug chỉ là preview, không cấp quyền thật. Trình biên dịch/ngôn ngữ riêng và engine Auto Click vẫn chưa hoàn thiện. Chưa có APK build thành công được xác minh. Xem [trạng thái dự án](docs/PROJECT-STATUS.md) để biết chi tiết.
