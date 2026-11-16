# AUTU-LIO

AUTU-LIO là **ứng dụng Android native**, phát hành dưới dạng một APK duy nhất với application ID `com.autulio.app`. Sản phẩm không có website, web dashboard hoặc bản web preview.

Thư mục `backend/` chứa Cloudflare Worker REST API phục vụ trực tiếp ứng dụng Android (đăng nhập, phân quyền, dữ liệu dự án, thiết bị, giấy phép và gói AUTU); đây không phải ứng dụng web.

## Vai trò trong ứng dụng

- **Chủ sở hữu:** quản trị ứng dụng, cộng tác viên, khách hàng, thiết bị, giấy phép, kịch bản và phát hành.
- **Nhà phát triển:** viết, sửa và kiểm thử kịch bản theo quyền máy chủ cấp.
- **Nhà thiết kế:** chỉnh sửa cấu hình giao diện của ứng dụng.
- **Người kiểm duyệt:** xem xét và duyệt bản phát hành.
- **Người dùng:** chỉ tải và chạy kịch bản đã phát hành, có giấy phép hợp lệ.

Mọi bản Debug và Release đều đi qua cùng luồng đăng nhập và xác minh vai trò. Ứng dụng không có bộ chọn vai trò giả hoặc tài khoản xem thử để cấp quyền.

## Cấu trúc

- `app/`: ứng dụng Android Kotlin/Jetpack Compose, AUTU Script, runtime và Accessibility Service.
- `backend/`: REST API và D1 migrations dành cho ứng dụng Android.
- `docs/`: tài liệu kiến trúc, vận hành, bảo mật và quy trình phát hành.

## Build và phát hành

- Chỉ APK Release được ký bằng keystore phát hành ổn định mới được phân phối cho người dùng.
- APK Debug chỉ dùng nội bộ phát triển, không phải bản phát hành và không dùng để cập nhật đè bản Release.
- Build APK chỉ được thực hiện khi chủ ứng dụng gửi đúng lệnh `BUILD APK`.
- Không tạo website hoặc web preview trong quy trình phát triển ứng dụng.

Xem [hướng dẫn bảo mật và ký APK](docs/SECURITY-AND-SIGNING.md) và [quy trình GitHub](docs/GITHUB-WORKFLOW.md).

## Trạng thái phát triển

Ứng dụng Android đang được hoàn thiện theo hướng sản phẩm thực tế, không dùng màn hình demo làm chức năng. Luồng đăng nhập, xác minh quyền, quản lý dự án, ngôn ngữ AUTU Script, runtime và cửa sổ chạy nổi đã có mã nguồn trong nhánh tích hợp Android. Trước khi phát hành cần hoàn tất cấu hình Google OAuth và Cloudflare Worker, đồng bộ dữ liệu/giấy phép, hoàn thiện đóng gói kịch bản, ký Release và kiểm thử trực tiếp trên thiết bị Android thật.

Xem [trạng thái và các điều kiện còn thiếu để phát hành](docs/PROJECT-STATUS.md).
