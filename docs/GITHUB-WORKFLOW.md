# AUTU-LIO — Quy trình GitHub, Cloudflare và phát hành APK

## 1. Repository và website

- Repository chính thức: `HUY-HOA/AUTU-LIO`, nhánh `main`, chế độ Public.
- Website công khai: https://autu-lio.coc-lio-data.workers.dev/
- API: https://autu-lio-auth-api.coc-lio-data.workers.dev/
- Website là trang thông tin và tải APK; không phải web editor hoặc dashboard. Ứng dụng chính vẫn là Android native `com.autulio.app`.

## 2. Triển khai Cloudflare tự động

Workflow `.github/workflows/worker-deploy.yml` chạy khi backend thay đổi trên `main`, hoặc khi chạy thủ công bằng `workflow_dispatch`. Các bước:

1. Cài Node.js, Python và Wrangler.
2. Chạy kiểm tra cú pháp Worker, 52 bài kiểm tra API hiện có và kiểm tra toàn bộ D1 migrations trên SQLite tạm.
3. Xác nhận hai GitHub Secrets Cloudflare.
4. Áp dụng các D1 migrations còn thiếu cho `autu_lio_auth`.
5. Triển khai API Worker `autu-lio-auth-api`.
6. Triển khai website Worker `autu-lio` từ `backend/website/`.

### Hai GitHub Secrets Cloudflare

- `CLOUDFLARE_API_TOKEN`: API Token giới hạn vào tài khoản AUTU-LIO; cần quyền Workers Scripts Edit và D1 Read/Edit.
- `CLOUDFLARE_ACCOUNT_ID`: `b1e6af9f0ef491fb7557747974a863f9`.

Đây là hai secret triển khai backend, không thay thế bốn secret ký APK. Token không được đưa vào mã nguồn hoặc APK.

### Cloudflare Worker Secrets

Các giá trị runtime `GOOGLE_CLIENT_ID`, `OWNER_GOOGLE_SUB` và `PACKAGE_MASTER_KEY_V1` được lưu riêng trong Cloudflare Worker Secrets. Workflow deploy không yêu cầu người dùng gửi chúng qua GitHub hoặc chat. Không xóa hoặc thay thế các khóa mã hóa package nếu chưa có quy trình xoay khóa và di chuyển dữ liệu.

## 3. Bốn GitHub Secrets ký APK dùng chung AILIO

Trong từng repository `AUTU-LIO`, `COC-LIO` và `GPS-LIO`, vào GitHub → Settings → Secrets and variables → Actions → Repository secrets và dùng đúng bốn tên:

- `AILIO_KEYSTORE_BASE64`
- `AILIO_KEYSTORE_PASSWORD`
- `AILIO_KEY_ALIAS`
- `AILIO_KEY_PASSWORD`

Cả ba repository dùng chung một keystore/chứng thư AILIO. GitHub secrets không tự đồng bộ giữa các repository, vì vậy từng kho phải có đủ bốn mục tương ứng. Không commit hoặc gửi các giá trị qua chat, issue hay log. Không thay keystore sau khi đã phát hành APK chính thức.

Đã kiểm tra trực tiếp workflow trên cả ba repository: keystore giải mã được, alias và hai mật khẩu được chấp nhận; SHA-1 chung là `2E:65:5B:0D:B5:8C:CF:A5:84:B9:37:1C:39:80:28:95:38:60:85:6D`.

## 4. Build và phát hành APK

- Workflow: `.github/workflows/build-apks.yml`.
- Workflow chỉ được phép tạo Release khi có push lên `main` với thông điệp commit chính xác `BUILD APK`; các push khác sẽ bị bỏ qua.
- Release build cần đủ cả bốn signing secrets. Workflow giải mã keystore trong vùng tạm, kiểm tra alias, chạy lint, build APK Release, xác minh chữ ký, xóa keystore tạm và tải artifact Actions.
- Sau khi xác minh thành công, workflow tạo GitHub Release có tag `v0.1.<run_number>`, đồng thời đính kèm APK với tên tải `AUTU-LIO.apk`.
- Website `/download/` dẫn đến trang GitHub Releases.
- APK Debug không được đưa vào GitHub Releases.
- Không chạy build khi người dùng chỉ hỏi trạng thái, nhắc đến APK hoặc dùng cụm từ trong câu khác. Chỉ build sau lệnh độc lập chính xác `BUILD APK`.

## 5. Kiểm tra sau phát hành

- Kiểm tra Actions run thành công, artifact có thể tải và release/tag xuất hiện trên GitHub.
- Xác minh APK có chữ ký hợp lệ; kiểm tra SHA-256 trong summary của workflow.
- Cài bản Release trên thiết bị Android thật, kiểm tra đăng nhập OWNER/CUSTOMER, giấy phép, đăng ký thiết bị, tải package, giải mã và chạy AUTU.
- Không xem việc build thành công là bằng chứng toàn bộ chức năng đã nghiệm thu.

## 6. Bảo mật repository

- Mã nguồn và lịch sử GitHub là công khai; mọi bí mật chỉ để trong GitHub Actions Secrets hoặc Cloudflare Worker Secrets.
- Không đưa keystore, mật khẩu, API token, session token, khóa mã hóa hoặc dữ liệu cá nhân thật vào repository.
- Mỗi nhóm thay đổi cần được kiểm tra CI; triển khai backend chỉ xảy ra khi thay đổi nằm trong `backend/**` trên `main`.
