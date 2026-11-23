# AUTU-LIO

AUTU-LIO là **ứng dụng Android native**, phát hành dưới dạng một APK duy nhất với application ID `com.autulio.app`.

Website công khai: **https://autu-lio.coc-lio-data.workers.dev/**. Website có trang giới thiệu, tải ứng dụng, chính sách quyền riêng tư và điều khoản sử dụng. Đây là website thông tin/phát hành, không phải trình soạn AUTU Script hay web dashboard.

API máy chủ: **https://autu-lio-auth-api.coc-lio-data.workers.dev/**. API phục vụ đăng nhập, phân quyền, tài khoản, thiết bị, giấy phép và gói AUTU.

## Vai trò hiện dùng

- **OWNER (Chủ sở hữu):** quản trị AUTU-LIO, khách hàng, thiết bị, giấy phép, kịch bản và phát hành.
- **CUSTOMER (Người dùng AUTU):** sử dụng các AUTU đã phát hành khi tài khoản, giấy phép và thiết bị đáp ứng điều kiện.

Phạm vi vận hành hiện tại chỉ dùng OWNER và CUSTOMER. Quy trình cộng tác viên/nhà phát triển được để dành cho giai đoạn sau; hiện chưa triển khai onboarding người viết AUTU. Mọi quyền vẫn do máy chủ xác minh, không cấp quyền bằng bộ chọn vai trò cục bộ.

## Website công khai

- [Trang chủ AUTU-LIO](https://autu-lio.coc-lio-data.workers.dev/)
- [Tải ứng dụng](https://autu-lio.coc-lio-data.workers.dev/download/)
- [Chính sách quyền riêng tư](https://autu-lio.coc-lio-data.workers.dev/privacy/)
- [Điều khoản sử dụng](https://autu-lio.coc-lio-data.workers.dev/terms/)
- [GitHub Releases — APK chính thức](https://github.com/HUY-HOA/AUTU-LIO/releases)

Website chạy trên Cloudflare Worker riêng tên `autu-lio`; API vẫn chạy trên Worker `autu-lio-auth-api`. Hai dịch vụ được triển khai tự động bằng GitHub Actions khi backend thay đổi trên nhánh `main`.

## Ngôn ngữ giao diện

Tiếng Việt là ngôn ngữ mặc định trên mọi màn hình OWNER và CUSTOMER, kể cả thông báo, lỗi, cửa sổ nổi và Trợ năng. Xem [quy tắc ngôn ngữ mặc định](docs/NGON_NGU_MAC_DINH_VI.md).

## Cấu trúc

- `app/`: ứng dụng Android Kotlin/Jetpack Compose, AUTU Script, runtime và Accessibility Service.
- `backend/`: REST API, D1 migrations, website tĩnh và cấu hình triển khai Cloudflare.
- `docs/`: tài liệu kiến trúc, vận hành, bảo mật và quy trình phát hành.

## Build và phát hành

- Chỉ APK Release được ký bằng keystore phát hành ổn định mới được phân phối cho người dùng.
- APK Debug chỉ dùng nội bộ phát triển, không phải bản phát hành và không dùng để cập nhật đè bản Release.
- Build APK chỉ được thực hiện khi chủ ứng dụng gửi đúng lệnh `BUILD APK`.
- Khi build Release thành công, GitHub Actions xác minh chữ ký, tải artifact và tạo GitHub Release chứa file `AUTU-LIO.apk`.
- Website tải ứng dụng dẫn tới trang GitHub Releases. Không cài APK Debug làm bản chính thức.
- AUTU-LIO không phát hành trên Google Play ở giai đoạn hiện tại; APK được phân phối qua GitHub Releases.

Xem [hướng dẫn bảo mật và ký APK](docs/SECURITY-AND-SIGNING.md) và [quy trình GitHub](docs/GITHUB-WORKFLOW.md).

## Đóng gói và bảo vệ AUTU

Quy trình mã hóa, phân phối theo giấy phép/thiết bị, xem mã gốc và phong bì mã hóa được mô tả tại [docs/10-AUTU-PACKAGE-SECURITY.md](docs/10-AUTU-PACKAGE-SECURITY.md).

## Trạng thái phát triển

Website và API Cloudflare đã được triển khai riêng; cả hai Worker đều nhận 100% lưu lượng phiên bản mới. GitHub Actions tự kiểm tra backend, áp dụng migration D1 còn thiếu và triển khai hai Worker. Quy trình tạo GitHub Release cho APK đã được bổ sung nhưng chưa phát hành bản APK mới bằng quy trình này. Các hạng mục còn phải nghiệm thu là luồng CUSTOMER/giấy phép/thiết bị, đóng gói và phân phối AUTU đầu-cuối, cùng kiểm thử thao tác Trợ năng và cửa sổ nổi trên thiết bị Android thật.

Xem [trạng thái và các điều kiện còn thiếu để phát hành](docs/PROJECT-STATUS.md).

Xem [phạm vi toàn diện và lộ trình AUTU Script](docs/09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md) để phân biệt tính năng đã có, còn thiếu và giới hạn của Android.
