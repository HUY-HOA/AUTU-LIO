# AUTU-LIO — Trạng thái phát triển ứng dụng Android

**Cập nhật:** 19/11/2026  
**Repository:** `HUY-HOA/AUTU-LIO`  
**Nhánh tích hợp:** `main`

## 1. Phạm vi sản phẩm

- Chỉ phát triển **ứng dụng Android native**, một APK duy nhất: `com.autulio.app`.
- Sản phẩm không có website, web dashboard, web app hoặc trang web xem trước.
- `backend/` là Cloudflare Worker REST API để ứng dụng Android gọi; đây không phải giao diện web và cần được giữ lại cho đăng nhập, phân quyền, dự án, thiết bị và giấy phép.
- Debug và Release cùng dùng luồng xác thực và xác minh vai trò; không cấp quyền bằng bộ chọn vai trò cục bộ.

## 2. Mã nguồn đã có trong nhánh tích hợp Android

### Ứng dụng và xác thực
- Ứng dụng Kotlin/Jetpack Compose với một application ID.
- Google Credential Manager lấy Google ID token; AuthRepository gọi API để máy chủ xác minh người dùng và trả về vai trò/quyền.
- Session lưu bằng Android Keystore, có xác thực dữ liệu và cơ chế xóa phiên.
- Màn hình được chọn theo vai trò OWNER, DEVELOPER, DESIGNER, REVIEWER hoặc CUSTOMER do máy chủ xác minh.
- Source editor kiểm tra quyền máy chủ, giới hạn truy cập và bảo vệ nội dung bằng FLAG_SECURE.

### Quản trị và cộng tác
- Trang quản trị khách hàng, thiết bị, giấy phép, thư viện, phát hành và nhật ký.
- Mời cộng tác viên, chấp nhận lời mời, phân quyền và thu hồi quyền.
- Workspace dự án dùng chung; bản nháp mã/UI có revision; gửi yêu cầu và quy trình duyệt.
- Bản nháp cục bộ và dữ liệu dự án được mã hóa theo thiết kế bảo mật.

### AUTU Script và chạy tự động
- Parser/compiler tạo AST, báo lỗi theo dòng/cột và giới hạn tài nguyên.
- Runtime có luồng thực thi riêng, trạng thái chạy/tạm dừng/tiếp tục/dừng và kiểm soát ngân sách thực thi.
- Accessibility Service điều phối thao tác Android và cung cấp cửa sổ điều khiển nổi.
- Nút chạy, tạm dừng, tiếp tục, dừng và mở trình soạn thảo được nối vào runtime; khi sửa script đang chạy, engine tạm dừng và chỉ thay script sau khi kiểm tra biên dịch thành công.
- Hành vi thực tế của gesture, cửa sổ nổi và chạy nền vẫn phải được xác nhận trên thiết bị Android thật.

## 3. Những điều kiện còn thiếu trước khi phát hành

1. **Máy chủ:** triển khai Cloudflare Worker, áp dụng đầy đủ D1 migrations cần thiết, cấu hình secrets và kiểm tra endpoint thật.
2. **Google OAuth:** tạo Web Client ID và Android Client ID đúng package name/chữ ký; cấu hình OAuth phía Worker và ứng dụng.
3. **Dữ liệu thực:** kiểm thử đăng nhập chủ sở hữu, mời cộng tác viên, vai trò/quyền, đồng bộ workspace, thiết bị, giấy phép, thư viện và package với máy chủ.
4. **Đóng gói kịch bản:** hoàn thiện quy trình ký, mã hóa, phát hành, tải xuống, xác minh quyền chạy, cập nhật và thu hồi package.
5. **Kiểm thử thiết bị:** xác nhận thao tác chạm/vuốt, quyền Trợ năng, overlay, vòng đời service, dừng khẩn cấp và hành vi khi chuyển ứng dụng; ưu tiên kiểm tra Xiaomi/HyperOS cùng các Android phiên bản khác.
6. **Chất lượng:** tất cả Android unit tests, lint, Worker tests và kiểm tra bảo mật phải đạt trước phát hành.

**Tình trạng hạ tầng đã kiểm tra ngày 19/11/2026:** tài khoản Cloudflare được kết nối chưa có Worker script triển khai; D1 `autu_lio_auth` hiện báo 0 bảng. Các migration trong Git chỉ được kiểm tra bằng SQLite tạm ở CI, chưa được áp dụng vào D1 này. Do đó backend đăng nhập/giấy phép chưa hoạt động thực tế.

## 4. Bản build Release gần nhất

GitHub Actions run [43972068339](https://github.com/HUY-HOA-1/AUTU-LIO/actions/runs/43972068339) đã hoàn tất thành công từ nhánh `build-requests/release-autulio-20261117`.

- Variant: Release.
- APK: `AUTU-LIO-v0.1.10.apk`.
- `:app:lintRelease` và `:app:assembleRelease` thành công.
- `apksigner verify` xác nhận chữ ký APK hợp lệ theo APK Signature Scheme v2.
- SHA-256 của APK: `b3e17afbc5f582c371c394c94981b9e51b6b20e2f05a038cf11fcbe4546c7b3e`.
- Đây là artifact lịch sử được build ngày 17/11/2026 từ nhánh yêu cầu build; không xác nhận các thay đổi trên `main` ngày 19/11/2026.
- Artifact: `AUTU-LIO-Release`; artifact ZIP SHA-256: `71af6297d250889ce5ea91949d9a575d7554e089a42ea8d23a0c76b01576983d`.
- Build thành công không thay thế cho việc cấu hình máy chủ, OAuth và kiểm thử ứng dụng trên thiết bị Android thật.

## 5. Backend API

- `backend/` chỉ chứa Cloudflare Worker REST API và D1 migrations để ứng dụng Android sử dụng.
- Không triển khai giao diện web từ thư mục backend.
- Không tạo lại website, dashboard trên web hoặc trang web xem trước.

## 6. Quy tắc an toàn và phát hành

- Không báo một chức năng đã hoạt động nếu mới có giao diện hoặc chưa kiểm thử hành vi thật.
- Không cấp quyền OWNER/DEVELOPER bằng lựa chọn cục bộ; quyền phải do máy chủ xác minh.
- Không đưa token, mật khẩu, khóa riêng hoặc keystore vào Git.
- Không phát hành APK Debug.
- Chỉ chạy workflow tạo APK khi chủ ứng dụng gửi đúng cụm `BUILD APK`.
- Chỉ xác nhận có APK khi workflow hoàn tất và artifact tải được.
