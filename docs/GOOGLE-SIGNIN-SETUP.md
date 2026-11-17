# AUTU-LIO — Cấu hình đăng nhập Google

## 1. Mô hình xác thực

AUTU-LIO chỉ có hai loại tài khoản:

- OWNER duy nhất: `phunghuyhoahaikba@gmail.com`.
- CUSTOMER: mọi tài khoản Google khác.

Android dùng Credential Manager để lấy Google ID token. Cloudflare Worker xác minh chữ ký, issuer, audience, thời hạn, email đã xác minh và nonce; sau đó Worker tự tính role từ email, không nhận role do APK gửi lên. Session bearer được mã hóa AES-GCM trong Android Keystore; ứng dụng không lưu Google ID token.

Không có Firebase Authentication, Firestore, Realtime Database hoặc Firebase Storage.

**Tình trạng:** mã Android và Worker đã có trong repository nhưng Worker AUTU-LIO chưa deploy và Google OAuth chưa cấu hình, vì vậy đăng nhập thật chưa hoạt động.

## 2. Google Cloud Console

Tạo hoặc chọn một Google Cloud project và cấu hình OAuth consent screen theo chính sách của Google.

Tạo OAuth Client ID loại **Web application**. Đây là Server Client ID/audience mà Android truyền cho Credential Manager và Worker dùng xác minh claim `aud`.

Tạo OAuth Client ID loại **Android** với:
- Package name: `com.autulio.app`.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Debug dùng nội bộ.
- SHA-1 và SHA-256 fingerprint của chứng thư ký Release trước khi phát hành.

Sau khi có Android Client ID, thêm ID đó vào biến Worker `GOOGLE_ALLOWED_AZP` nếu token thực tế có claim `azp` khác Web Client ID.

Không gửi file keystore, mật khẩu ký hoặc OAuth client secret qua chat hay commit.

## 3. Cấu hình Cloudflare Worker

Trong `backend/wrangler.jsonc`, biến `OWNER_EMAIL` đã được đặt thành `phunghuyhoahaikba@gmail.com`. Giữ nguyên giá trị này nếu đó vẫn là tài khoản chủ sở hữu duy nhất.

Đặt các secret:
```sh
npx wrangler secret put GOOGLE_CLIENT_ID
```

- `GOOGLE_CLIENT_ID`: Web application Client ID; phải giống Server Client ID được cấu hình cho Android.
- `GOOGLE_ALLOWED_AZP`: biến tùy chọn, danh sách Android OAuth Client ID được phép xuất hiện trong claim `azp` nếu token yêu cầu.
- `OWNER_EMAIL`: là biến cấu hình thông thường trong wrangler.jsonc, không phải một secret. Nó phải khớp chính xác Gmail Owner.

Không đặt Google OAuth client secret, Cloudflare API token hoặc khóa ký Android trong APK hoặc Git.

## 4. Cấu hình Android build

Sau khi deploy Worker và xác nhận URL thật, đặt hai Gradle properties ngoài repository, ưu tiên `~/.gradle/gradle.properties`:

```properties
AUTU_LIO_AUTH_API_BASE_URL=https://<worker-url>
AUTU_LIO_GOOGLE_SERVER_CLIENT_ID=<web-application-client-id>
```

Có thể truyền cùng tên qua environment variables:
- `AUTU_LIO_AUTH_API_BASE_URL`
- `AUTU_LIO_GOOGLE_SERVER_CLIENT_ID`

Nếu để trống, app có thể biên dịch nhưng đăng nhập bị khóa có chủ đích.

## 5. D1 và triển khai

1. Kiểm tra binding D1 `autu_lio_auth` trong `backend/wrangler.jsonc`.
2. Đảm bảo các migrations `0001` đến `0005` được áp dụng theo thứ tự nếu môi trường D1 chưa có đủ schema.
3. Áp dụng migration `0006_single_owner_customer_only.sql`. Migration này đưa Gmail Owner về OWNER, mọi tài khoản khác về CUSTOMER, xóa lời mời/permission override và các bảng workspace cộng tác cũ.
4. Deploy Worker từ `backend/`.
5. Gọi `GET /health` và xác nhận API/D1 sẵn sàng.
6. Kiểm thử đăng nhập bằng Gmail Owner và một tài khoản Google CUSTOMER khác.
7. Kiểm thử phân quyền: CUSTOMER chỉ được xem thư viện script đã cấp phép, không gọi được API quản trị hoặc sửa/phát hành script.

Không deploy Worker trước khi cấu hình Google OAuth. Không tạo thêm OWNER hoặc tài khoản cộng tác viên.

## 6. Quy tắc quyền

- OWNER duy nhất được mở Admin Studio, tạo/sửa/kiểm tra script, quản lý khách hàng, thiết bị, giấy phép, thư viện, nhật ký và phát hành.
- CUSTOMER chỉ được vào màn hình thư viện script để tải và chạy package đã cấp phép.
- Không có vai trò Developer, Designer, Reviewer, lời mời hoặc workspace dự án.
- Mọi lần mở editor và mọi API quản trị phải xác minh session/role phía Worker; ẩn nút trong Android không thay thế kiểm tra quyền máy chủ.

## 7. Phạm vi chưa hoàn tất

- Chưa tạo Google OAuth Web Client ID và Android Client ID.
- Worker AUTU-LIO chưa deploy và chưa có URL API hoạt động.
- Migration 0006 chưa áp dụng vào D1 production.
- Chưa thử đăng nhập OWNER/CUSTOMER thực trên điện thoại.
- Chưa xác nhận license, đăng ký thiết bị RSA, tải package mã hóa và quyền Trợ năng/overlay trên thiết bị thật.
