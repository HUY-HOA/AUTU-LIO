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

Sau khi xác nhận đúng Google Web Client ID và định danh chủ sở hữu, đặt các giá trị trong thư mục `backend`:

```sh
npx wrangler secret put GOOGLE_CLIENT_ID
npx wrangler secret put OWNER_GOOGLE_SUB
```

- `GOOGLE_CLIENT_ID`: Web application Client ID; phải trùng Server Client ID mà Android dùng để yêu cầu ID token.
- `OWNER_GOOGLE_SUB`: claim `sub` của Google ID token thuộc tài khoản chủ sở hữu. Worker chỉ cấp OWNER khi chữ ký, issuer, audience, thời hạn, email đã xác minh và nonce của ID token hợp lệ, sau đó claim `sub` trùng chính xác secret này.
- Không dùng Gmail/email làm điều kiện cấp OWNER. Đổi email không làm thay đổi cách Worker nhận diện chủ sở hữu.
- Không đưa Google ID token, client secret hoặc Cloudflare API token lên GitHub, issue, chat hay APK. Chỉ lưu giá trị `sub` trong Worker Secret.

### Lấy Google `sub` an toàn

Lấy claim `sub` từ ID token mới do Google cấp cho đúng tài khoản chủ sở hữu và đúng Web Client ID AUTU-LIO. Chỉ giải mã phần payload cục bộ để đọc `sub`; việc giải mã payload không thay thế xác minh chữ ký. Không gửi toàn bộ ID token cho người khác hoặc dán vào website giải mã JWT công khai. Worker vẫn phải tự xác minh ID token bằng Google signing keys trước khi dùng claim này.

Nếu chưa có ID token để đọc claim `sub`, hãy hoàn tất cấu hình Google OAuth và lấy token trong môi trường phát triển cục bộ, không log token ra console. Không tự suy ra `sub` từ Gmail, Firebase UID hoặc GitHub account ID.

- `GOOGLE_ALLOWED_AZP`: biến Worker tùy chọn, danh sách cách nhau bằng dấu phẩy gồm Android OAuth Client ID được phép xuất hiện trong claim `azp`. Claim `aud` vẫn bắt buộc phải trùng chính xác Web/Server Client ID; `azp` không được bỏ qua hoặc chấp nhận tùy tiện.

Không commit các giá trị cấu hình hoặc bí mật vào Git. Không đưa Google OAuth client secret, Cloudflare API token hoặc khóa ký Android vào APK.

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
1. Xác nhận `GOOGLE_CLIENT_ID` và `OWNER_GOOGLE_SUB` đã được cấu hình an toàn.
2. Kiểm tra binding D1 `autu_lio_auth` trong `backend/wrangler.jsonc`.
3. Cài Wrangler theo phiên bản ổn định phù hợp và chạy `npm run check`.
4. Database AUTU-LIO hiện tại đã áp dụng migration `0001`–`0003`; khi thiết lập môi trường D1 mới, áp dụng toàn bộ migration theo thứ tự.
5. Deploy Worker, gọi `GET /health`, xác nhận database và cấu hình Google đều báo sẵn sàng.
6. Kiểm thử đăng nhập bằng tài khoản chủ sở hữu trước khi mời tài khoản khác.

Không deploy Auth API trước khi hoàn tất cấu hình OAuth và xác nhận Google `sub` của chủ sở hữu. Không tự ý thay thế Google `sub` của chủ sở hữu bằng email GitHub.

## 6. Kiểm tra quyền

- OWNER: được mở Admin Studio và quản lý cộng tác viên.
- DEVELOPER: chỉ mở không gian viết mã / bản nháp.
- DESIGNER: chỉ mở không gian thiết kế và xem trước.
- REVIEWER: chỉ mở hàng chờ kiểm duyệt.
- CUSTOMER: chỉ mở AutoClick và kịch bản được cấp phép.

Màn hình mời/quản lý cộng tác viên và workspace dự án đã được nối với API trong mã Android. Chủ sở hữu tạo dự án, phân công cộng tác viên; Developer lưu script, Designer lưu UI draft và Reviewer kiểm duyệt snapshot. Các màn hình này chưa thể hoạt động trực tuyến cho tới khi Worker deploy và Android được cấu hình Auth API URL + Server Client ID. Bản xem thử Debug không cấp quyền thật.

## 7. Phạm vi chưa hoàn tất

- Chưa có Google OAuth Client IDs.
- Chưa có email OWNER được chủ ứng dụng xác nhận cho cấu hình secret.
- Worker chưa được deploy và chưa có URL.
- Chưa thử đăng nhập thực trên điện thoại.
- Màn hình Android đã nối API quản lý cộng tác viên, thu hồi lời mời, chấp nhận mã, quản lý dự án, lưu script/UI draft và duyệt submission; chưa thể dùng trực tuyến cho tới khi Worker deploy và secrets Google được cấu hình.
- `AUTU_LIO_AUTH_API_BASE_URL` và `AUTU_LIO_GOOGLE_SERVER_CLIENT_ID` hiện chưa được đặt trong môi trường build; để trống sẽ khóa đăng nhập có chủ đích.
- Chưa có runtime AutoClick thật, cấp phép thật hoặc tự động gửi email lời mời.
