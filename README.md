# AUTU-LIO

AUTU-LIO là **ứng dụng Android native**, phát hành dưới dạng một APK duy nhất với application ID `com.autulio.app`. Sản phẩm không có website, web dashboard hoặc bản web preview.

Thư mục `backend/` chứa Cloudflare Worker REST API phục vụ trực tiếp ứng dụng Android (đăng nhập, phân quyền, dữ liệu dự án, thiết bị, giấy phép và gói AUTU); đây không phải ứng dụng web.

## Vai trò hiện dùng

- **OWNER (Chủ sở hữu):** quản trị AUTU-LIO, khách hàng, thiết bị, giấy phép, kịch bản và phát hành.
- **CUSTOMER (Người dùng AUTU):** sử dụng các AUTU đã phát hành khi tài khoản, giấy phép và thiết bị đáp ứng điều kiện.

Phạm vi vận hành hiện tại chỉ dùng OWNER và CUSTOMER. Quy trình cộng tác viên/nhà phát triển được để dành cho giai đoạn sau; hiện chưa triển khai onboarding người viết AUTU. Mọi quyền vẫn do máy chủ xác minh, không cấp quyền bằng bộ chọn vai trò cục bộ.

## Ngôn ngữ giao diện

Tiếng Việt là ngôn ngữ mặc định trên mọi màn hình OWNER và CUSTOMER, kể cả thông báo, lỗi, cửa sổ nổi và Trợ năng. Xem [quy tắc ngôn ngữ mặc định](docs/NGON_NGU_MAC_DINH_VI.md).

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

## Đóng gói và bảo vệ AUTU

Quy trình mã hóa, phân phối theo giấy phép/thiết bị, xem mã gốc và phong bì mã hóa được mô tả tại [docs/10-AUTU-PACKAGE-SECURITY.md](docs/10-AUTU-PACKAGE-SECURITY.md).

## Trạng thái phát triển

AUTU-LIO đã đăng nhập Google thành công trên thiết bị và tài khoản chủ sở hữu đã được máy chủ xác nhận là OWNER. Cloudflare Worker và D1 đã triển khai. Bản Release v0.1.37 đã build thành công, ký và xác minh chữ ký, sau đó tải artifact lên GitHub Actions (workflow run 44791724413). Các hạng mục còn phải nghiệm thu là luồng CUSTOMER/giấy phép/thiết bị, đóng gói và phân phối AUTU đầu-cuối, cùng kiểm thử thao tác Trợ năng và cửa sổ nổi trên thiết bị Android thật.

Xem [trạng thái và các điều kiện còn thiếu để phát hành](docs/PROJECT-STATUS.md).

Xem [phạm vi toàn diện và lộ trình AUTU Script](docs/09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md) để phân biệt tính năng đã có, còn thiếu và giới hạn của Android.
