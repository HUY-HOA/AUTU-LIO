# AUTU-LIO — Bảo mật mã nguồn, APK và khóa ký

## 1. Mục tiêu

- Hạn chế khả năng đọc/ngược mã ứng dụng khi phát hành.
- Mã hóa bản nháp/kịch bản lưu cục bộ trên điện thoại.
- Chỉ phát hành APK Release có chữ ký ổn định do chủ sở hữu kiểm soát.
- Không để keystore, mật khẩu, token hoặc khóa riêng trong Git.

## 2. Điều quan trọng về "mã hóa mã nguồn"

Không thể bảo đảm mã Kotlin/Dex đã chạy trên thiết bị Android luôn bí mật hoặc không thể dịch ngược. APK phải chứa mã máy có thể thực thi; người có thiết bị và công cụ phù hợp vẫn có thể phân tích, hook hoặc trích xuất dữ liệu trong bộ nhớ.

Các lớp bảo vệ được sử dụng:

1. Repository GitHub hiện ở chế độ Public. Hãy coi mã nguồn, workflow, lịch sử commit và issue là thông tin công khai; không dựa vào việc giấu mã để bảo vệ tài khoản, dữ liệu hay máy chủ. Bí mật phải ở GitHub Actions Secrets hoặc Cloudflare Worker Secrets.
2. Bản Release bật R8: shrink, optimize và obfuscate tên class/method/field; bật shrink resources.
3. Không nhúng khóa mã hóa chung, signing key, mật khẩu hoặc bí mật máy chủ vào APK.
4. Logic quyết định quyền, chủ sở hữu, license và phát hành phải được kiểm tra ở Worker/máy chủ; không tin cờ quyền do client tự gửi.
5. Bản nháp kịch bản trên thiết bị được mã hóa AES-256-GCM; khóa AES được tạo và giữ trong Android Keystore.
6. Khi triển khai phân phối kịch bản: lưu ciphertext AES-256-GCM, khóa duy nhất theo gói/đối tượng và cấp quyền giải mã sau khi máy chủ xác minh giấy phép. Không dùng một khóa tĩnh nhúng trong APK.

R8 là làm rối và thu gọn mã, không phải mã hóa tuyệt đối. Mã giải mã và nội dung cần chạy vẫn có thể xuất hiện trong bộ nhớ khi ứng dụng hoạt động.

## 3. Bảo vệ API đăng nhập

- Hai endpoint công khai `/api/auth/challenge` và `/api/auth/google` được gắn Cloudflare Workers Rate Limiting: tối đa 90 lần tạo challenge/phút và 30 lần gửi đăng nhập/phút theo địa chỉ IP Cloudflare nhận được.
- Nếu môi trường production thiếu binding rate limit hoặc dịch vụ giới hạn lỗi, endpoint đăng nhập từ chối yêu cầu bằng HTTP 503 thay vì âm thầm bỏ qua lớp bảo vệ.
- Giới hạn của Workers Rate Limiting là gần đúng và được tính riêng tại từng Cloudflare location; IP di động có thể được nhiều người dùng chung. Đây là lớp giảm lạm dụng, không phải giới hạn toàn cầu tuyệt đối.
- CORS mặc định không cho phép origin trình duyệt nào. Chỉ thêm `ALLOWED_ORIGINS` nếu sau này có giao diện web được kiểm soát. CORS không thay thế xác thực; API vẫn phải kiểm tra bearer session và quyền ở Worker.

## 4. Android Release signing

### GitHub Actions secrets bắt buộc

Tạo bốn **Repository secrets** tại GitHub → Settings → Secrets and variables → Actions:

- `AILIO_KEYSTORE_BASE64`: toàn bộ file keystore ở dạng Base64.
- `AILIO_KEYSTORE_PASSWORD`: mật khẩu keystore.
- `AILIO_KEY_ALIAS`: alias của khóa ký.
- `AILIO_KEY_PASSWORD`: mật khẩu private key.

Không gửi các giá trị này qua chat, issue, commit hoặc log.

### Keystore AILIO đang dùng

Theo cấu hình đã chọn, AUTU-LIO, COC-LIO và GPS-LIO sử dụng cùng một keystore AILIO và cùng chứng thư ký. Không chạy lệnh tạo keystore mới cho cấu hình hiện tại vì sẽ tạo SHA-1 khác và làm lệch cấu hình OAuth. Giữ file keystore gốc cùng mật khẩu ở nơi an toàn, ngoại tuyến; tạo bản sao lưu thứ hai được bảo vệ.

Bốn Repository secrets phải được tạo riêng trong từng kho GitHub vì secrets không tự đồng bộ giữa các repository. Nếu khóa ký bị lộ, cả ba ứng dụng đều bị ảnh hưởng và cần kế hoạch thay khóa, cập nhật SHA-1 OAuth, phát hành APK mới cho cả ba ứng dụng.

Chuyển file sang Base64 để nhập vào secret:

- Linux/macOS: `base64 < autu-lio-release.p12 | tr -d '\n'`
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("autu-lio-release.p12"))`

Trong từng kho `AUTU-LIO`, `COC-LIO` và `GPS-LIO`, nhập chuỗi Base64 vào `AILIO_KEYSTORE_BASE64`; nhập mật khẩu keystore vào `AILIO_KEYSTORE_PASSWORD`, alias vào `AILIO_KEY_ALIAS`, mật khẩu khóa vào `AILIO_KEY_PASSWORD`. Nếu ba ứng dụng dùng chung một chứng thư thì bốn giá trị phải tương ứng với cùng một keystore AILIO. Không commit file .p12 hoặc file chứa mật khẩu. Bản Debug đang cài có thể phải gỡ trước khi cài Release lần đầu vì chứng thư ký khác nhau; sau đó mọi bản Release cập nhật phải dùng cùng keystore.

### Cách workflow xử lý

- Khi đủ cả bốn secrets: giải mã keystore trong thư mục tạm của GitHub runner, kiểm tra alias, chạy lint Release, build Release có R8, kiểm tra chữ ký APK, xóa file keystore tạm và tải artifact `AUTU-LIO-Release`.
- Khi chưa có secret nào: chỉ tạo `AUTU-LIO-Debug` để thử nghiệm. Bản này không phải bản phát hành dùng để cập nhật đè ổn định.
- Nếu chỉ cấu hình một phần: workflow dừng và báo thiếu cấu hình, không âm thầm tạo APK Release không ký.
- VersionCode được tăng theo số lần chạy workflow để các bản cập nhật tiếp theo có versionCode cao hơn.
- Không dùng khóa debug tự sinh của GitHub runner làm khóa phát hành.

## 5. Mã hóa bản nháp trên thiết bị

`SecureScriptDraftStore` sử dụng AES/GCM/NoPadding với khóa AES-256 sinh bởi Android Keystore. Tên kịch bản và nội dung nháp được mã hóa chung thành một payload; dữ liệu bản nháp cũ dạng plaintext được chuyển sang định dạng mã hóa ở lần đọc đầu tiên rồi mới xóa bản cũ sau khi ghi thành công.

Dữ liệu cần ở dạng plaintext trong editor khi chủ sở hữu chỉnh sửa; thiết bị đã root hoặc tiến trình bị hook vẫn có thể đọc nội dung lúc đang mở. Mã hóa lưu trữ giúp bảo vệ dữ liệu khi file preferences bị lấy riêng lẻ, không chống được mọi hình thức tấn công trên thiết bị đã bị kiểm soát.

## 6. Mã hóa và phân phối gói AUTU

Worker hiện có mã nguồn cho quy trình phát hành gói AUTU theo phiên bản; API chỉ hoạt động sau khi Worker được triển khai, D1 migration và secret được cấu hình.

- Mỗi phiên bản tạo khóa dữ liệu AES-256 ngẫu nhiên riêng và nonce AES-GCM 96-bit riêng.
- Mã nguồn được mã hóa bằng AES-256-GCM với tag 128-bit và AAD gắn với package ID + version để ngăn tráo dữ liệu giữa các phiên bản.
- D1 chỉ lưu ciphertext, nonce nội dung, khóa dữ liệu đã mã hóa, nonce của khóa, phiên bản master key và SHA-256; không lưu source dạng rõ trong bảng phiên bản.
- Khóa dữ liệu được bảo vệ bằng PACKAGE_MASTER_KEY_V1 (32 byte ngẫu nhiên) lưu trong Worker secret, không nhúng vào APK. Mỗi version ghi lại key_version để có thể xoay master key có kiểm soát.
- Khi khách hàng tải gói, Worker kiểm tra tài khoản, license đang hiệu lực và thiết bị RSA-3072 đang ACTIVE thuộc chính tài khoản đó.
- Worker chỉ bọc lại khóa AES 32 byte cho public key của thiết bị; private key không xuất được nằm trong Android Keystore. Android giải mã AES-GCM, xác minh SHA-256 rồi mới biên dịch/chạy.
- Phiên bản đã phát hành là bất biến; cập nhật tạo version mới. Chủ sở hữu có thể lưu trữ hoặc khôi phục package.

RSA-OAEP SHA-1/MGF1-SHA1 hiện chỉ dùng để bọc khóa AES 32 byte nhằm tương thích với Android Keystore OAEP MGF1 mặc định trên thiết bị đích. Nội dung script vẫn được mã hóa bằng AES-256-GCM. SHA-1 ở bước wrap không phải thuật toán mã hóa nội dung. Có thể nâng cấp giao thức theo khả năng OAEP SHA-256/MGF1 của thiết bị trong một phiên bản tương lai.

Không có khóa giải mã chung trong APK. Tuy nhiên, khi script được giải mã và chạy, nội dung sẽ có trong bộ nhớ tiến trình; không thể bảo đảm chống sao chép tuyệt đối trên thiết bị đã bị kiểm soát. R8 chỉ shrink/obfuscate mã APK, không phải mã hóa tuyệt đối.

## 7. Điều kiện trước khi phát hành thật

- API Worker `autu-lio-auth-api` và website Worker `autu-lio` đã được triển khai trên Cloudflare; D1 migrations `0001`–`0008` đã áp dụng.
- GitHub Actions deploy dùng hai repository secrets `CLOUDFLARE_API_TOKEN` và `CLOUDFLARE_ACCOUNT_ID`. Chúng độc lập với bốn signing secrets của APK.
- Các Worker secrets `GOOGLE_CLIENT_ID`, `OWNER_GOOGLE_SUB` và `PACKAGE_MASTER_KEY_V1` được cấu hình riêng trong Cloudflare. `OWNER_GOOGLE_SUB` là claim `sub` từ ID token của chủ sở hữu đã xác minh; không dùng email, Firebase UID hoặc GitHub ID.
- Cần tiếp tục nghiệm thu đăng nhập thật, cấp license, đăng ký thiết bị, phát hành, tải/giải mã và thu hồi trên thiết bị Android thật.
- Cần duy trì keystore ký Release AILIO dùng chung cho AUTU-LIO, COC-LIO và GPS-LIO; cấu hình đủ bốn GitHub Actions secrets `AILIO_*` trong từng repository và lưu bản sao lưu an toàn.
- Kiểm thử cài đặt/cập nhật Release bằng cùng một chữ ký ổn định; không tự động xoay hoặc thay signing key sau khi đã phát hành.
- Tiếp tục bổ sung kiểm thử runtime và chống giả mạo phù hợp; không xem R8 hay mã hóa package là ranh giới bảo mật duy nhất.
