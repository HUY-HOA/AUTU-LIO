# AUTU-LIO — Cấu hình đăng nhập Google

## 1. Thành phần hiện có

- Android Credential Manager: `androidx.credentials:credentials:1.6.0`.
- Google ID: `com.google.android.libraries.identity.googleid:googleid:1.2.1`.
- Android lấy một nonce ngắn hạn từ Auth API, yêu cầu Google phát hành ID token với nonce đó, rồi gửi ID token về Worker.
- Worker xác minh chữ ký Google, issuer, audience, thời hạn, email đã xác minh và nonce trước khi tạo session.
- Session bearer token được lưu dưới dạng mã hóa AES-GCM bằng khóa Android Keystore. Chỉ lưu token session, không lưu Google ID token.
- Mỗi lần mở app, ứng dụng hỏi máy chủ `/api/auth/me`; UI role chỉ mở sau khi máy chủ xác minh phiên.

Luồng Android và Worker đã được triển khai. Worker URL: `https://autu-lio-auth-api.coc-lio-data.workers.dev`. Google Web Client ID, Android Client ID allowlist, các Worker secret cần thiết và package master key đã có binding. Worker chỉ cấp OWNER cho Google `sub` khớp `OWNER_GOOGLE_SUB`; tài khoản khác nhận CUSTOMER. Phiên bản Worker mới nhất đã được triển khai sau khi gỡ API cộng tác.

## 2. Google Cloud Console

Tạo hoặc chọn một Google Cloud project và cấu hình OAuth consent screen theo chính sách của Google.

Tạo OAuth Client ID loại **Web application**. Đây là Server Client ID / audience mà Android sẽ truyền cho Credential Manager và Worker sẽ dùng để xác minh claim `aud`.

Tạo OAuth Client ID loại **Android** với:
- Package name: `com.autulio.app`.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Debug dùng để thử nghiệm.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Release trước khi phát hành thật.

Sau khi có Android Client ID, thêm ID đó vào biến Worker `GOOGLE_ALLOWED_AZP` (nếu token thực tế có claim `azp` khác Web Client ID) để Worker xác minh đúng ứng dụng Android được phép trình token.

Có thể lấy fingerprint của cấu hình ký Android bằng tác vụ Gradle `signingReport` trong môi trường phát triển. Không gửi file keystore hoặc mật khẩu ký cho cộng tác viên.

## 3. Cloudflare Worker variables and secrets

Sau khi xác nhận đúng Google Web Client ID và định danh chủ sở hữu, đặt các giá trị trong thư mục `backend`:

```sh
npx wrangler secret put OWNER_GOOGLE_SUB
```

- `GOOGLE_CLIENT_ID`: Web application Client ID công khai; được khai báo trong `vars.GOOGLE_CLIENT_ID` của `backend/wrangler.jsonc`, phải trùng Server Client ID mà Android dùng để yêu cầu ID token. Không tạo Worker Secret cho giá trị này.
- `OWNER_GOOGLE_SUB`: claim `sub` của Google ID token thuộc tài khoản chủ sở hữu. Worker chỉ cấp OWNER khi chữ ký, issuer, audience, thời hạn, email đã xác minh và nonce của ID token hợp lệ, sau đó claim `sub` trùng chính xác secret này.
- Không dùng Gmail/email làm điều kiện cấp OWNER. Đổi email không làm thay đổi cách Worker nhận diện chủ sở hữu.
- Không đưa Google ID token, client secret hoặc Cloudflare API token lên GitHub, issue, chat hay APK. Chỉ lưu giá trị `sub` trong Worker Secret.

### Lấy Google `sub` an toàn

Lấy claim `sub` từ ID token mới do Google cấp cho đúng tài khoản chủ sở hữu và đúng Web Client ID AUTU-LIO. Chỉ giải mã phần payload cục bộ để đọc `sub`; việc giải mã payload không thay thế xác minh chữ ký. Không gửi toàn bộ ID token cho người khác hoặc dán vào website giải mã JWT công khai. Worker vẫn phải tự xác minh ID token bằng Google signing keys trước khi dùng claim này.

Nếu chưa có ID token để đọc claim `sub`, hãy hoàn tất cấu hình Google OAuth và lấy token trong môi trường phát triển cục bộ, không log token ra console. Không tự suy ra `sub` từ Gmail, Firebase UID hoặc GitHub account ID.

- `GOOGLE_ALLOWED_AZP`: biến Worker tùy chọn, danh sách cách nhau bằng dấu phẩy gồm Android OAuth Client ID được phép xuất hiện trong claim `azp`. Claim `aud` vẫn bắt buộc phải trùng chính xác Web/Server Client ID; `azp` không được bỏ qua hoặc chấp nhận tùy tiện.

Không commit các giá trị cấu hình hoặc bí mật vào Git. Không đưa Google OAuth client secret, Cloudflare API token hoặc khóa ký Android vào APK.

## 4. Cấu hình Android build

Worker đã được deploy. Workflow GitHub Actions đã truyền hai cấu hình sau vào cả Debug và Release; URL API không phải bí mật:

```properties
AUTU_LIO_AUTH_API_BASE_URL=https://autu-lio-auth-api.coc-lio-data.workers.dev
AUTU_LIO_GOOGLE_SERVER_CLIENT_ID=45851070932-nm7p19g66jgbvrervag9f1gg4n7bavvo.apps.googleusercontent.com
```

Có thể truyền cùng tên qua environment variables:
- `AUTU_LIO_AUTH_API_BASE_URL`
- `AUTU_LIO_GOOGLE_SERVER_CLIENT_ID`

Các giá trị này không phải khóa bí mật. Nếu để trống, app vẫn có thể biên dịch nhưng đăng nhập sẽ bị khóa và giải thích cấu hình còn thiếu.

## 5. Triển khai Worker

Trước khi deploy:
1. Xác nhận biến `GOOGLE_CLIENT_ID` trong `backend/wrangler.jsonc` và Secret `OWNER_GOOGLE_SUB` đã được cấu hình đúng.
2. Kiểm tra binding D1 `autu_lio_auth` trong `backend/wrangler.jsonc`.
3. Cài Wrangler theo phiên bản ổn định phù hợp và chạy `npm run check`.
4. D1 `autu_lio_auth` đã áp dụng migrations `0001`–`0006`; migration `0006` đã xóa các bảng cộng tác và chuyển mọi vai trò cũ khác OWNER/CUSTOMER về CUSTOMER.
5. Worker phiên bản hiện tại đã deploy 100% lưu lượng; sau các lần chỉnh sửa tiếp theo, deploy rồi kiểm tra `GET /health`.
6. Kiểm thử đăng nhập OWNER và CUSTOMER; Google account khác OWNER chỉ nhận CUSTOMER.

Không deploy Auth API trước khi hoàn tất cấu hình OAuth và xác nhận Google `sub` của chủ sở hữu. Không tự ý thay thế Google `sub` của chủ sở hữu bằng email GitHub.

## 6. Kiểm tra quyền

- OWNER: mở Admin Studio, quản lý khách hàng, giấy phép, thiết bị và gói AUTU.
- CUSTOMER: dùng AutoClick và package đã được cấp phép.

AUTU-LIO chỉ còn OWNER và CUSTOMER. Vai trò Developer/Designer/Reviewer, lời mời, cộng tác viên, dự án dùng chung và review đã bị gỡ khỏi app và Worker.

## 7. Phạm vi chưa hoàn tất

- Worker phiên bản 17 đã được triển khai 100% lưu lượng; các secret Google và khóa package vẫn còn binding. Cần kiểm thử đăng nhập trên điện thoại với bản APK mới.
- Chưa kiểm thử lại đăng nhập thực tế trên điện thoại sau lần deploy Worker mới nhất.
- Cần nghiệm thu runtime AutoClick, cấp phép và đăng ký thiết bị trên thiết bị thật.
