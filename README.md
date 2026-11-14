# AUTU-LIO

Ứng dụng tự động hóa thao tác trên Android, được phát triển theo hai biến thể:

- **Admin Studio:** dành cho chủ ứng dụng để quản lý và phát triển kịch bản.
- **AutoClick:** dành cho khách hàng để sử dụng các kịch bản đã được phát hành.

## Repository

- Chủ sở hữu: HUY-HOA-1
- Repository: [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)
- Nhánh chính: main

## Tài liệu thiết kế

- [Định hướng UI tổng thể](docs/UI-DESIGN-PLAN.md)
- [Màn hình Tổng quan Admin](docs/01-admin-dashboard.md)
- [Quản lý kịch bản](docs/02-script-management.md)
- [Trình soạn thảo ngôn ngữ riêng](docs/03-script-editor.md)
- [Thư viện và Phát hành](docs/04-library-and-release.md)
- [Ứng dụng khách hàng](docs/05-customer-app.md)
- [Quy trình GitHub](docs/GITHUB-WORKFLOW.md)

## Build

Hai APK debug được tạo từ cùng codebase bằng product flavors:

- `adminDebug` — AUTU-LIO Admin Studio
- `clientDebug` — AUTU-LIO AutoClick

Build chỉ được khởi chạy theo đúng quy tắc trong [GITHUB-WORKFLOW.md](docs/GITHUB-WORKFLOW.md). Workflow APK chỉ có `workflow_dispatch`, không build tự động theo mỗi lần push.

## Trạng thái

Giai đoạn hiện tại là prototype giao diện Android. Google OAuth, Cloudflare, giấy phép thực tế, trình biên dịch/ngôn ngữ riêng và engine tự động hóa chưa được tích hợp. Dữ liệu hiển thị trong prototype không phải dữ liệu khách hàng thật.
