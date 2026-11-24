# Cấu hình Firebase Cloud Messaging (FCM)

Tài liệu này dành cho AUTU-LIO. Firebase chỉ được dùng để nhận/gửi thông báo đẩy về bản cập nhật; đăng nhập Google, dữ liệu tài khoản, API và dữ liệu ứng dụng vẫn do Cloudflare xử lý.

## 1. Phạm vi Firebase

Khuyến nghị dùng **một Firebase project chung** và đăng ký hai Android App trong project đó:

| Ứng dụng | Android package name |
|---|---|
| COC-LIO | `com.coc.lio` |
| AUTU-LIO | `com.autu.lio` |

Không đăng ký GPS-LIO vào Firebase: GPS-LIO hiện không sử dụng FCM.

FCM là sản phẩm không tính phí của Firebase và có thể dùng trên gói Spark; không cần thêm phương thức thanh toán chỉ để sử dụng FCM. Không bật Analytics nếu không có nhu cầu.

## 2. Tạo Android App trong Firebase

Mở [Firebase Console](https://console.firebase.google.com/) và chọn project mới.

1. Vào Project Overview → **Add app** → chọn Android.
2. Đăng ký app với package name chính xác:
   - `com.coc.lio` cho COC-LIO.
   - `com.autu.lio` cho AUTU-LIO.
3. Đặt nickname tùy ý, ví dụ `COC-LIO` và `AUTU-LIO`.
4. Tải file `google-services.json` riêng của từng Android App để đọc các giá trị cấu hình.

**Không cần nhập SHA-1/SHA-256 ở bước đăng ký Firebase App chỉ để dùng FCM.** SHA-1 của Android OAuth Client là cấu hình Google Sign-In riêng, không phải Firebase FCM.

Mỗi file `google-services.json` có các trường cần lấy:

| GitHub Actions Variable | Trường trong `google-services.json` |
|---|---|
| `FIREBASE_API_KEY` | `client[0].api_key[0].current_key` |
| `FIREBASE_PROJECT_ID` | `project_info.project_id` |
| `FIREBASE_SENDER_ID` | `project_info.project_number` |
| `FIREBASE_APP_ID` | `client[0].client_info.mobilesdk_app_id` |

Kiểm tra `client[0].client_info.android_client_info.package_name` đúng với package name của app. Trong cùng một Firebase project, API key, Project ID và Sender ID dùng chung; **FIREBASE_APP_ID khác nhau cho từng Android App**.

Repository hiện khởi tạo FirebaseOptions trực tiếp từ 4 biến BuildConfig. Không cần thêm Google Services Gradle plugin và không cần commit `google-services.json` vào GitHub.

## 3. Thêm 4 GitHub Actions Variables

Mở trang Variables của từng repository:

- [COC-LIO → Settings → Secrets and variables → Actions → Variables](https://github.com/HUY-HOA/COC-LIO/settings/variables/actions)
- [AUTU-LIO → Settings → Secrets and variables → Actions → Variables](https://github.com/HUY-HOA/AUTU-LIO/settings/variables/actions)

Tạo 4 **Variables** (không phải Secrets) với tên viết đúng chữ hoa:

- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`
- `FIREBASE_APP_ID`

Nhập API key, Project ID và Sender ID từ project Firebase vừa tạo. Nhập App ID tương ứng của đúng Android App trong repository đó. Không dùng App ID của COC-LIO cho AUTU-LIO hoặc ngược lại.

Đường dẫn cài đặt:
- COC-LIO: https://github.com/HUY-HOA/COC-LIO/settings/variables/actions
- AUTU-LIO: https://github.com/HUY-HOA/AUTU-LIO/settings/variables/actions

## 4. Cấp quyền gửi thông báo cho Cloudflare Worker

Backend gửi thông báo bằng FCM HTTP v1. Nó cần khóa JSON của Service Account, độc lập với 4 GitHub Variables ở trên.

1. Trong Firebase Console, vào **Project settings → Service accounts**.
2. Chọn **Generate new private key** để tải JSON Service Account.
3. Giữ file JSON riêng tư. Không commit lên GitHub, không đưa vào APK và không gửi khóa qua chat.
4. Trong Google Cloud Console, kiểm tra **Firebase Cloud Messaging API** đã được bật cho project. Service Account phải có quyền **Firebase Cloud Messaging API Admin** trên project đích.
5. Mở Cloudflare Dashboard, chọn đúng account và Worker tương ứng:
   - COC-LIO: Worker `coc-lio-auth-api`.
   - AUTU-LIO: Worker `autu-lio-auth-api`.
6. Vào Worker → **Settings → Variables and Secrets** → **Add** → chọn **Secret**.
7. Tên Secret phải là `FCM_SERVICE_ACCOUNT_JSON`. Giá trị là toàn bộ nội dung JSON Service Account vừa tải. Lưu lại.

Cùng một Service Account JSON có thể dùng cho hai Worker khi cả hai app nằm trong cùng Firebase project. Không cần tạo Secret cho Worker cập nhật tĩnh `coc-lio-updates` / `autu-lio-updates` hoặc cho GPS-LIO.

## 5. Kiểm tra sau khi cấu hình

- COC-LIO đăng ký topic `coc-lio-update-notices`.
- AUTU-LIO đăng ký topic `autu-lio-update-notices`.
- Android 13 trở lên cần người dùng cấp quyền thông báo.
- Thiết bị cần Google Play services và kết nối mạng để nhận FCM.
- Nếu 4 GitHub Variables chưa được điền, app tự bỏ qua khởi tạo FCM; các tính năng khác vẫn chạy.
- Nếu Worker chưa có Secret `FCM_SERVICE_ACCOUNT_JSON`, backend không thể gửi thông báo FCM.

Sau khi bạn hoàn thành các bước Firebase và Cloudflare, báo lại để kiểm tra cấu hình tiếp. Việc build APK vẫn chỉ thực hiện khi có lệnh chính xác `BUILD APK`.

## 6. Những cấu hình không cần thay đổi

- Không tạo keystore mới.
- Không thay 4 GitHub Secrets ký APK: `AILIO_KEYSTORE_BASE64`, `AILIO_KEYSTORE_PASSWORD`, `AILIO_KEY_ALIAS`, `AILIO_KEY_PASSWORD`.
- Không thay SHA-1/SHA-256 chỉ vì đăng ký lại Firebase.
- Không thay Google OAuth Web Client ID hoặc cấu hình đăng nhập Google.
- Không tạo Firebase Authentication, Firestore, Realtime Database, Storage hay Hosting cho chức năng FCM hiện tại.
