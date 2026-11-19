# AUTU-LIO — Trạng thái phát triển ứng dụng Android

**Cập nhật:** 19/11/2026  
**Repository:** `HUY-HOA/AUTU-LIO`  
**Nhánh tích hợp:** `main`

## 1. Phạm vi sản phẩm và vai trò

- Chỉ phát triển **ứng dụng Android native**, một APK duy nhất: `com.autulio.app`.
- Không có website, web dashboard, web app hoặc web preview.
- `backend/` là Cloudflare Worker REST API phục vụ ứng dụng Android; đây không phải giao diện web.
- Phạm vi vận hành hiện tại chỉ gồm **OWNER** và **CUSTOMER**. Chỉ tài khoản Google đã xác minh theo secret `OWNER_GOOGLE_SUB` được bootstrap thành OWNER; tài khoản Google khác mặc định là CUSTOMER.
- Quy trình Collaboration invitations and shared workspaces have been removed from the current scope.
- Debug và Release dùng cùng luồng xác thực; không cấp quyền bằng bộ chọn vai trò cục bộ.

## 2. Hạ tầng đã triển khai và xác nhận

- Cloudflare Worker `autu-lio-auth-api` đã triển khai tại `https://autu-lio-auth-api.coc-lio-data.workers.dev`.
- Cloudflare D1 `autu_lio_auth` có 20 bảng ứng dụng; migrations `0001`–`0005` đã áp dụng.
- Worker có D1 binding, hai rate-limit bindings, Google Web Client ID, Android OAuth Client ID allowlist và package master key.
- Secret `OWNER_GOOGLE_SUB` đã được cấu hình trong Production và Worker đã triển khai phiên bản mới.
- Đăng nhập Google trên thiết bị đã thành công. Truy vấn D1 xác nhận tài khoản chủ sở hữu có `role=OWNER`, `status=ACTIVE`.
- Dữ liệu D1 tại lần rà soát gần nhất: có một tài khoản OWNER; chưa có khách hàng, thiết bị, giấy phép, package hoặc dự án. Các số 0 ở Admin Studio phản ánh dữ liệu chưa phát sinh, không phải lỗi xác thực.

## 3. Mã nguồn chức năng hiện có

### Ứng dụng và xác thực
- Ứng dụng Kotlin/Jetpack Compose, một application ID.
- Google Credential Manager lấy Google ID token; Worker xác minh chữ ký, issuer, audience, expiry, email đã xác minh và nonce.
- Session lưu bằng Android Keystore; API kiểm tra vai trò và trạng thái tài khoản ở phía máy chủ.
- Admin Studio tải dữ liệu quản trị từ API; giao diện Customer có luồng tài khoản, package, thiết bị và cài đặt.

### AUTU Script và chạy tự động
- Parser/compiler tạo AST, báo lỗi theo dòng/cột; có giới hạn mã nguồn, số lệnh và thời lượng.
- Runtime chạy trên luồng riêng; có trạng thái chạy, tạm dừng, tiếp tục, dừng, hủy và lỗi.
- Accessibility Service phát gesture Android, gọi hành động hệ thống được hỗ trợ và tạo cửa sổ điều khiển nổi.
- Unit tests hiện có cho parser/compiler và runtime; CI chạy unit tests và lint.
- Mã nguồn có tính năng không đồng nghĩa đã nghiệm thu trên thiết bị thật. Cần kiểm tra độ chính xác gesture, vòng đời service, quyền Trợ năng, overlay và khả năng dừng trong các tình huống thực tế.

### Quản lý và phân phối AUTU
- Backend có API quản lý khách hàng, thiết bị, giấy phép và package.
- Có cấu trúc mã hóa package AES-GCM, bọc khóa theo thiết bị RSA và xác minh hash ở phía Android.
- Quy trình end-to-end từ tạo package, phát hành, cấp giấy phép, đăng ký thiết bị, tải xuống, giải mã đến chạy vẫn cần kiểm thử nghiệm thu bằng tài khoản CUSTOMER thật.

## 4. Bản Release hiện có

- GitHub Actions đã hoàn tất workflow Release APK run `44486235658`; bước ký và xác minh chữ ký APK thành công, artifact `AUTU-LIO-Release` đã tải lên.
- Bản ứng dụng người dùng đang mở hiển thị v0.1.12.
- Không build APK mới nếu chưa nhận đúng lệnh `BUILD APK`.
- Sửa tài liệu hoặc chạy source checks không đồng nghĩa tạo APK; workflow source checks không đóng gói APK.

## 5. Việc cần hoàn thiện tiếp theo

1. **Nghiệm thu tài khoản CUSTOMER:** đăng nhập bằng một Google account khác, xác nhận vai trò mặc định là CUSTOMER và không truy cập được Admin API.
2. **Giấy phép và thiết bị:** OWNER tạo giấy phép; CUSTOMER đăng ký thiết bị; kiểm tra giới hạn số thiết bị, hết hạn, thu hồi và phiên đăng nhập.
3. **Đóng gói AUTU:** OWNER tạo package, phát hành phiên bản, xác minh dữ liệu mã hóa; CUSTOMER tải package khi có giấy phép và thiết bị hợp lệ.
4. **Runtime trên thiết bị thật:** kiểm tra CHẠM, NHẤN_GIỮ, VUỐT, CHỜ, mở ứng dụng, chạy nền có kiểm soát, tạm dừng/tiếp tục/dừng và lỗi quyền Trợ năng.
5. **Độ ổn định Android:** kiểm tra trên Xiaomi/HyperOS và Android khác; xử lý tối ưu pin, service bị hệ thống dừng, đổi hướng màn hình và độ phân giải.
6. **Chất lượng:** giữ Worker tests, Kotlin unit tests và lint xanh; kiểm tra bảo mật và không đưa secret vào APK/Git.

## 6. Quy tắc an toàn và phát hành

- Không đánh dấu chức năng hoàn thành chỉ vì có giao diện hoặc API; phải kiểm tra hành vi thật.
- Chỉ OWNER được quản trị; CUSTOMER chỉ sử dụng AUTU đã phát hành với giấy phép hợp lệ.
- Không đưa token, mật khẩu, khóa riêng hoặc keystore vào Git.
- Không phát hành APK Debug cho người dùng.
- Chỉ chạy workflow tạo APK khi chủ ứng dụng gửi đúng cụm `BUILD APK`.
- Mọi báo cáo Release phải có trạng thái workflow, xác nhận chữ ký và artifact thực tế.
