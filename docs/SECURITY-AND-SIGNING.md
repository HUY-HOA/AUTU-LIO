# AUTU-LIO — Bảo mật mã nguồn, APK và khóa ký

## 1. Mục tiêu

- Hạn chế khả năng đọc/ngược mã ứng dụng khi phát hành.
- Mã hóa bản nháp/kịch bản lưu cục bộ trên điện thoại.
- Chỉ phát hành APK Release có chữ ký ổn định do chủ sở hữu kiểm soát.
- Không để keystore, mật khẩu, token hoặc khóa riêng trong Git.

## 2. Điều quan trọng về "mã hóa mã nguồn"

Không thể bảo đảm mã Kotlin/Dex đã chạy trên thiết bị Android luôn bí mật hoặc không thể dịch ngược. APK phải chứa mã máy có thể thực thi; người có thiết bị và công cụ phù hợp vẫn có thể phân tích, hook hoặc trích xuất dữ liệu trong bộ nhớ.

Các lớp bảo vệ được sử dụng:

1. Repository GitHub để chế độ private và giới hạn người có quyền truy cập.
2. Bản Release bật R8: shrink, optimize và obfuscate tên class/method/field; bật shrink resources.
3. Không nhúng khóa mã hóa chung, signing key, mật khẩu hoặc bí mật máy chủ vào APK.
4. Logic quyết định quyền, chủ sở hữu, license và phát hành phải được kiểm tra ở Worker/máy chủ; không tin cờ quyền do client tự gửi.
5. Bản nháp kịch bản trên thiết bị được mã hóa AES-256-GCM; khóa AES được tạo và giữ trong Android Keystore.
6. Khi triển khai phân phối kịch bản: lưu ciphertext AES-256-GCM, khóa duy nhất theo gói/đối tượng và cấp quyền giải mã sau khi máy chủ xác minh giấy phép. Không dùng một khóa tĩnh nhúng trong APK.

R8 là làm rối và thu gọn mã, không phải mã hóa tuyệt đối. Mã giải mã và nội dung cần chạy vẫn có thể xuất hiện trong bộ nhớ khi ứng dụng hoạt động.

## 3. Android Release signing

### GitHub Actions secrets bắt buộc

Tạo bốn **Repository secrets** tại GitHub → Settings → Secrets and variables → Actions:

- `AUTU_LIO_RELEASE_KEYSTORE_BASE64`: toàn bộ file keystore ở dạng Base64.
- `AUTU_LIO_RELEASE_STORE_PASSWORD`: mật khẩu keystore.
- `AUTU_LIO_RELEASE_KEY_ALIAS`: alias của khóa ký.
- `AUTU_LIO_RELEASE_KEY_PASSWORD`: mật khẩu private key.

Không gửi các giá trị này qua chat, issue, commit hoặc log.

### Tạo khóa ký trên máy tính do chủ sở hữu kiểm soát

Dùng JDK `keytool` trên máy tính riêng, tạo một lần duy nhất:

```bash
keytool -genkeypair -v \
  -keystore autu-lio-release.p12 \
  -storetype PKCS12 \
  -alias autu-lio-release \
  -keyalg RSA -keysize 3072 -validity 10000
```

Hãy lưu file `autu-lio-release.p12` và mật khẩu ở nơi an toàn, ngoại tuyến; tạo ít nhất một bản sao lưu thứ hai được bảo vệ. Mất khóa ký có thể khiến không thể cập nhật đè APK đã cài bằng khóa đó.

Chuyển file sang Base64 để nhập vào secret:

- Linux/macOS: `base64 < autu-lio-release.p12 | tr -d '\n'`
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("autu-lio-release.p12"))`

Chỉ nhập chuỗi Base64 vào `AUTU_LIO_RELEASE_KEYSTORE_BASE64`; nhập riêng ba giá trị còn lại vào các secret tương ứng. Không commit file .p12 hoặc file chứa mật khẩu.

### Cách workflow xử lý

- Khi đủ cả bốn secrets: giải mã keystore trong thư mục tạm của GitHub runner, kiểm tra alias, chạy lint Release, build Release có R8, kiểm tra chữ ký APK, xóa file keystore tạm và tải artifact `AUTU-LIO-Release`.
- Khi chưa có secret nào: chỉ tạo `AUTU-LIO-Debug` để thử nghiệm. Bản này không phải bản phát hành dùng để cập nhật đè ổn định.
- Nếu chỉ cấu hình một phần: workflow dừng và báo thiếu cấu hình, không âm thầm tạo APK Release không ký.
- VersionCode được tăng theo số lần chạy workflow để các bản cập nhật tiếp theo có versionCode cao hơn.
- Không dùng khóa debug tự sinh của GitHub runner làm khóa phát hành.

## 4. Mã hóa bản nháp trên thiết bị

`SecureScriptDraftStore` sử dụng AES/GCM/NoPadding với khóa AES-256 sinh bởi Android Keystore. Tên kịch bản và nội dung nháp được mã hóa chung thành một payload; dữ liệu bản nháp cũ dạng plaintext được chuyển sang định dạng mã hóa ở lần đọc đầu tiên rồi mới xóa bản cũ sau khi ghi thành công.

Dữ liệu cần ở dạng plaintext trong editor khi chủ sở hữu chỉnh sửa; thiết bị đã root hoặc tiến trình bị hook vẫn có thể đọc nội dung lúc đang mở. Mã hóa lưu trữ giúp bảo vệ dữ liệu khi file preferences bị lấy riêng lẻ, không chống được mọi hình thức tấn công trên thiết bị đã bị kiểm soát.

## 5. Những lớp cần tiếp tục hoàn thiện

- Xác minh quyền và license phía Worker trước mọi API quản trị/phát hành.
- Mã hóa package kịch bản trước khi lưu và phân phối từ R2; dùng khóa ngẫu nhiên theo package và cơ chế xoay/thu hồi khóa.
- Thêm kiểm tra integrity/anti-tamper phù hợp, nhưng không xem chúng là ranh giới bảo mật duy nhất.
- Kiểm thử cài đặt/cập nhật Release bằng cùng một khóa trên thiết bị thật.
- Lưu backup signing key riêng; không tự động xoay hoặc thay khóa ký của ứng dụng đã phát hành.
