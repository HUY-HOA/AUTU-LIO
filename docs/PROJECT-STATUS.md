# AUTU-LIO — Trạng thái phát triển ứng dụng Android

**Cập nhật:** 17/11/2026  
**Repository:** `HUY-HOA-1/AUTU-LIO`  
**Nhánh tích hợp đang làm việc:** `feature/floating-runner-secure-20261117` (PR #33)

## 1. Phạm vi sản phẩm

- Chỉ phát triển **ứng dụng Android native**, một APK duy nhất: `com.autulio.app`.
- Không làm website, web dashboard, web app hoặc trang web preview.
- `backend/` là Cloudflare Worker REST API để ứng dụng Android gọi; không phải giao diện web và không được xóa vì app cần nó cho đăng nhập, phân quyền, dự án, thiết bị và giấy phép.
- Debug và Release cùng dùng luồng xác thực thật; không cho phép chọn vai trò giả trên thiết bị.

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
5. **Ký APK:** tạo và lưu an toàn keystore phát hành; cấu hình đủ bốn GitHub Actions secrets. Không dùng chứng thư Android Debug để phân phối người dùng.
6. **Kiểm thử thiết bị:** xác nhận thao tác chạm/vuốt, quyền Trợ năng, overlay, vòng đời service, dừng khẩn cấp và hành vi khi chuyển ứng dụng; ưu tiên kiểm tra Xiaomi/HyperOS cùng các Android phiên bản khác.
7. **Chất lượng:** tất cả Android unit tests, lint, Worker tests và kiểm tra bảo mật phải đạt trước phát hành.

## 4. Bản build gần nhất

GitHub Actions run #9 đã tạo artifact `AUTU-LIO-Debug`, phiên bản `0.1.9`. Log xác nhận APK Debug được ký bằng chứng thư **Android Debug**; bước build Release đã bị bỏ qua. Đây chỉ là kết quả kiểm tra quy trình build, **không phải APK phát hành** và không được dùng để phân phối khách hàng.

## 5. Web

Phần `web-preview/` và workflow `web-preview-check.yml` đã bị loại khỏi nhánh `preview/web-live`. Sản phẩm tiếp tục được phát triển trong ứng dụng Android; không xây dựng lại phiên bản web. Cloudflare Worker vẫn được giữ lại vì đây là API backend của ứng dụng.

## 6. Quy tắc an toàn và phát hành

- Không báo một chức năng đã hoạt động nếu mới có giao diện hoặc chưa kiểm thử hành vi thật.
- Không cấp quyền OWNER/DEVELOPER bằng lựa chọn cục bộ; quyền phải do máy chủ xác minh.
- Không đưa token, mật khẩu, khóa riêng hoặc keystore vào Git.
- Không phát hành APK Debug.
- Chỉ chạy workflow tạo APK khi chủ ứng dụng gửi đúng cụm `BUILD APK`.
- Chỉ xác nhận có APK khi workflow hoàn tất và artifact tải được.
