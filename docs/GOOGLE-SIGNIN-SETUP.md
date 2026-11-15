# AUTU-LIO — Cấu hình đăng nhập Google

## 1. Thành phần hiện có

- Android Credential Manager: `androidx.credentials:credentials:1.6.0`.
- Google ID: `com.google.android.libraries.identity.googleid:googleid:1.2.1`.
- Android lấy một nonce ngắn hạn từ Auth API, yêu cầu Google phát hành ID token với nonce đó, rồi gửi ID token về Worker.
- Worker xác minh chữ ký Google, issuer, audience, thời hạn, email đã xác minh và nonce trước khi tạo session.
- Session bearer token được lưu dưới dạng mã hóa AES-GCM bằng khóa Android Keystore. Chỉ lưu token session, không lưu Google ID token.
- Mỗi lần mở app, ứng dụng hỏi máy chủ `/api/auth/me`; UI role chỉ mở sau khi máy chủ xác minh phiên.

Đây là phần khung tích hợp. Auth API chưa deploy và Google OAuth chưa cấu hình, do đó nút đăng nhập sẽ bị khóa cho tới khi có đủ cấu hình.

## 2. Google Cloud Console

Tạo hoặc chọn một Google Cloud project và cấu hình OAuth consent screen theo chính sách của Google.

Tạo OAuth Client ID loại **Web application**. Đây là Server Client ID / audience mà Android sẽ truyền cho Credential Manager và Worker sẽ dùng để xác minh claim `aud`.

Tạo OAuth Client ID loại **Android** với:
- Package name: `com.autulio.app`.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Debug dùng để thử nghiệm.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Release trước khi phát hành thật.

Sau khi có Android Client ID, thêm ID đó vào biến Worker `GOOGLE_ALLOWED_AZP` (nếu token thực tế có claim `azp` khác Web Client ID) để Worker xác minh đúng ứng dụng Android được phép trình token.

Có thể lấy fingerprint của cấu hình ký Android bằng tác vụ Gradle `signingReport` trong môi trường phát triển. Không gửi file keystore hoặc mật khẩu ký cho cộng tác viên.

## 3. Cloudflare Worker secrets

Sau khi xác nhận đúng tài khoản Google của chủ ứng dụng và Web Client ID, đặt hai secret ở thư mục `backend`:

```sh
npx wrangler secret put GOOGLE_CLIENT_ID
npx wrangler secret put OWNER_EMAIL
```

- `GOOGLE_CLIENT_ID`: Web application Client ID; phải giống Server Client ID được cấu hình cho app.
- `OWNER_EMAIL`: email Google chính xác của chủ ứng dụng. Worker chỉ gán OWNER nếu email được Google xác minh trùng giá trị này và chưa có OWNER nào khác.
- `GOOGLE_ALLOWED_AZP`: biến Worker tùy chọn, danh sách cách nhau bằng dấu phẩy gồm Android OAuth Client ID được phép xuất hiện trong claim `azp`. Claim `aud` vẫn bắt buộc phải trùng chính xác Web/Server Client ID; `azp` không được bỏ qua hoặc chấp nhận tùy tiện.

Hai giá trị không được commit vào Git. Không đưa Google OAuth client secret, Cloudflare API token hoặc khóa ký Android vào APK.

## 4. Cấu hình Android build

Worker hiện chưa deploy, nên chưa có URL API thực tế. Sau khi deploy và xác nhận URL, đặt hai Gradle properties ở máy build, ưu tiên file `~/.gradle/gradle.properties` bên ngoài repository:

```properties
AUTU_LIO_AUTH_API_BASE_URL=https://<worker-url>
AUTU_LIO_GOOGLE_SERVER_CLIENT_ID=<web-application-client-id>
```

Có thể truyền cùng tên qua environment variables:
- `AUTU_LIO_AUTH_API_BASE_URL`
- `AUTU_LIO_GOOGLE_SERVER_CLIENT_ID`

Các giá trị này không phải khóa bí mật. Nếu để trống, app vẫn có thể biên dịch nhưng đăng nhập sẽ bị khóa và giải thích cấu hình còn thiếu.

## 5. Triển khai Worker

Trước khi deploy:
1. Xác nhận `GOOGLE_CLIENT_ID` và `OWNER_EMAIL` đã được cấu hình an toàn.
2. Kiểm tra binding D1 `autu_lio_auth` trong `backend/wrangler.jsonc`.
3. Cài Wrangler theo phiên bản ổn định phù hợp và chạy `npm run check`.
4. Áp dụng migration nếu thiết lập một môi trường mới; database AUTU-LIO hiện tại đã được khởi tạo và xác minh.
5. Deploy Worker, gọi `GET /health`, xác nhận database và cấu hình Google đều báo sẵn sàng.
6. Kiểm thử đăng nhập bằng tài khoản chủ sở hữu trước khi mời tài khoản khác.

Không deploy Auth API trước khi hoàn tất cấu hình OAuth và xác nhận email chủ sở hữu. Không tự ý thay thế email chủ sở hữu bằng email GitHub.

## 6. Kiểm tra quyền

- OWNER: được mở Admin Studio và quản lý cộng tác viên.
- DEVELOPER: chỉ mở không gian viết mã / bản nháp.
- DESIGNER: chỉ mở không gian thiết kế và xem trước.
- REVIEWER: chỉ mở hàng chờ kiểm duyệt.
- CUSTOMER: chỉ mở AutoClick và kịch bản được cấp phép.

Các không gian cộng tác viên hiện là prototype cục bộ. API kiểm tra quyền thật đã có trong Worker, nhưng luồng API mời / cập nhật cộng tác viên chưa được nối vào giao diện Android. Không được hiểu bản xem thử Debug hoặc bản nháp cục bộ là quyền thật.

## 7. Phạm vi chưa hoàn tất

- Chưa có Google OAuth Client IDs.
- Chưa có email OWNER được chủ ứng dụng xác nhận cho cấu hình secret.
- Worker chưa được deploy và chưa có URL.
- Chưa thử đăng nhập thực trên điện thoại.
- Màn hình Android đã nối API quản lý, thu hồi lời mời và chấp nhận mã; chưa thể dùng trực tuyến cho tới khi Worker deploy và secrets Google được cấu hình.
- Chưa có runtime AutoClick thật, cấp phép thật hoặc tự động gửi email lời mời.
