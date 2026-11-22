# AUTU-LIO — Trạng thái phát triển ứng dụng Android

**Cập nhật:** 23/11/2026  
**Repository:** `HUY-HOA/AUTU-LIO`  
**Nhánh tích hợp:** `main`

## 1. Phạm vi sản phẩm và vai trò

- Chỉ phát triển **ứng dụng Android native**, một APK duy nhất: `com.autulio.app`.
- Không có website, web dashboard, web app hoặc web preview.
- `backend/` là Cloudflare Worker REST API phục vụ ứng dụng Android; đây không phải giao diện web.
- Phạm vi vận hành hiện tại chỉ gồm **OWNER** và **CUSTOMER**. Chỉ tài khoản Google đã xác minh theo secret `OWNER_GOOGLE_SUB` được bootstrap thành OWNER; tài khoản Google khác mặc định là CUSTOMER.
- Chỉ hỗ trợ OWNER/CUSTOMER; không còn lời mời, cộng tác viên, shared workspace hoặc review submission.
- Debug và Release dùng cùng luồng xác thực; không cấp quyền bằng bộ chọn vai trò cục bộ.

## 2. Hạ tầng đã triển khai và xác nhận

- Cloudflare Worker `autu-lio-auth-api` đã triển khai tại `https://autu-lio-auth-api.coc-lio-data.workers.dev`.
- Cloudflare D1 `autu_lio_auth` đã áp dụng đầy đủ migrations `0001`–`0008`; migration 0008 bổ sung thời điểm hiện diện, thời điểm ứng dụng ở nền trước và trạng thái runtime. 7 bảng cộng tác đã bị xóa; vẫn còn 10 bảng ứng dụng.
- Worker `autu-lio-auth-api` phiên bản 22 đã triển khai Production với 100% lưu lượng.
- D1 binding, hai rate-limit bindings, các biến công khai và ba Worker secrets hiện có đã được xác minh sau triển khai.
- D1 có đúng một tài khoản OWNER và một CUSTOMER theo lần kiểm tra trước; đăng nhập và nghiệm thu trên thiết bị thật vẫn cần kiểm tra lại sau khi cài APK mới.

## 3. Mã nguồn chức năng hiện có

### Ứng dụng và xác thực
- Ứng dụng Kotlin/Jetpack Compose, một application ID.
- Google Credential Manager lấy Google ID token; Worker xác minh chữ ký, issuer, audience, expiry, email đã xác minh và nonce.
- Session lưu bằng Android Keystore; API kiểm tra vai trò và trạng thái tài khoản ở phía máy chủ.
- Accessibility Service không tự mở bảng chạy khi vừa kết nối; chỉ chuẩn bị kịch bản sau khi phiên đã được xác minh.
- Màn hình đăng nhập và Accessibility Service cùng theo dõi phiên; dịch vụ xác minh lại với máy chủ mỗi 60 giây. Với CUSTOMER, dịch vụ còn kiểm tra kho package bằng fingerprint để xác nhận giấy phép và thiết bị hiện tại vẫn ACTIVE; bị từ chối quyền sẽ khóa runner nhưng không xóa phiên Google chỉ vì giấy phép/thiết bị bị thu hồi. OWNER không bị áp dụng điều kiện giấy phép CUSTOMER.
- Cửa sổ soạn mã nổi, lưu bản nháp và chuẩn bị script tự tạo chỉ dành cho OWNER. CUSTOMER chỉ nhận điều khiển chạy/tạm dừng/dừng package đã tải và xác minh từ máy chủ.
- Đăng xuất/phiên bị từ chối xóa quyền chạy, dừng kịch bản và cho phép khối LUÔN_THỰC_HIỆN hoàn tất trước khi xóa lớp đánh dấu.
- Admin Studio tải dữ liệu quản trị từ API; giao diện Customer có luồng tài khoản, package, thiết bị và cài đặt.

### Theo dõi trạng thái sử dụng
- D1 migration `0008_device_presence.sql` bổ sung thời điểm tín hiệu hiện diện, thời điểm ứng dụng ở nền trước và trạng thái bộ chạy; tách biệt hoàn toàn với trạng thái cấp quyền thiết bị.
- CUSTOMER gửi tín hiệu khi ứng dụng đang ở nền trước mỗi 45 giây. Accessibility Service chỉ gửi tín hiệu nền khi AUTU đang chạy hoặc tạm dừng.
- Màn hình quản trị phân loại: đang kết nối (tối đa 2 phút), vừa hoạt động (2–10 phút), ngoại tuyến (trên 10 phút), hoặc chưa có dữ liệu. Trạng thái AUTU cũ không được hiển thị như trạng thái hiện tại khi tín hiệu đã quá hạn.
- Chỉ lưu dấu thời gian và trạng thái ứng dụng/bộ chạy; không thu thập nội dung màn hình, thao tác cá nhân hoặc vị trí.
- Migration 0008 đã áp dụng lên Production và Worker phiên bản 22 có endpoint `POST /api/customer/presence`; dữ liệu hiện diện mới sẽ xuất hiện sau khi CUSTOMER cài bản APK mới, có thiết bị đã đăng ký và mở ứng dụng.

### AUTU Script và chạy tự động
- Parser/compiler tạo AST, báo lỗi theo dòng/cột; có giới hạn mã nguồn, số lệnh và thời lượng.
- AUTU Script 1.22 dùng `BIẾN` để khai báo biến và `HÀM` để định nghĩa hàm; `ĐẶT` và `ĐỊNH_NGHĨA_HÀM` được giữ làm bí danh tương thích, các ví dụ mới trong hướng dẫn dùng tên chuẩn.
- Runtime chạy trên luồng riêng; có trạng thái chạy, tạm dừng, tiếp tục, dừng, hủy và lỗi; kiểm thử hồi quy bao gồm dừng trong lúc chạy và dừng khi đang tạm dừng để xác nhận khối LUÔN_THỰC_HIỆN.
- Accessibility Service phát gesture Android, gọi hành động hệ thống được hỗ trợ và tạo cửa sổ điều khiển nổi.
- AUTU Script 1.22 có chuyển đổi DP/pixel, tọa độ vùng an toàn và hướng xoay màn hình; thao tác thực tế theo pixel nguyên.
- HTTP client dùng OkHttp, hỗ trợ HTTPS GET/POST/PUT/PATCH/DELETE/HEAD, chặn redirect, giới hạn header/body/timeout và không trả các header xác thực nhạy cảm.
- CSV parser/serializer có dấu nháy, dấu phân cách tùy chọn, BOM và CRLF; giới hạn 64 dòng/64 cột/512 ô/1 MiB; gọi qua PHÂN_TÍCH_CSV và CHUYỂN_CSV.
- HTTP multipart upload/download cho tệp trong kho riêng: POST/PUT/PATCH upload, GET download, HTTPS-only, redirect off, giới hạn 1 MiB kể cả multipart overhead; MIME/tên tệp/header được kiểm tra; download chỉ lưu khi HTTP 2xx.
- SQLite riêng của ứng dụng có giới hạn 8 MiB; SQL allow-list, bind tham số, giới hạn kết quả và transaction cho từng câu ghi.
- Di trú schema theo phiên bản 1..100.000: chạy tuần tự, transaction nguyên tử, checksum SHA-256, gọi lặp cùng checksum không chạy lại; thay đổi nội dung cùng phiên bản bị từ chối. Bảng lịch sử di trú bị ẩn khỏi SQL của script.
- Hàm KIỂM_TRA_TOÀN_VẸN_CƠ_SỞ_DỮ_LIỆU() chạy integrity_check, foreign_key_check và xác thực lịch sử di trú; kết quả bị giới hạn.
- Backup/restore SQLite đã có mã nguồn: archive ZIP `.autubak` gồm manifest và database, SHA-256, giới hạn database 8 MiB/archive 8 MiB + 128 KiB; restore kiểm tra định dạng, hash, integrity, khóa ngoại và schema version trước khi thay thế, có bản rollback nếu kiểm tra database mới thất bại.
- Cài đặt OWNER/CUSTOMER có nút Nhập/Xuất tệp AUTU Script qua Android SAF; runtime có CHỌN_TỆP(MIME) và LƯU_TỆP(ĐƯỜNG_DẪN, TÊN_GỢI_Ý), mở SAF trực tiếp, chờ tối đa 120 giây, hủy được khi dừng script và không lưu quyền URI lâu dài; tệp được sao chép vào kho riêng, tên được làm sạch, không ghi đè khi trùng tên, giới hạn 1 MiB và không giữ URI bên ngoài; script dùng đường dẫn tương đối trong kho.
- Cài đặt OWNER/CUSTOMER có nút Sao lưu/Khôi phục qua Android SAF; thao tác bị từ chối khi runtime đang chạy/tạm dừng và yêu cầu phiên đã xác minh nếu dùng Accessibility Service.
- ML Kit được bật trong mọi Android build: OCR, QR/mã vạch, nhãn ảnh và phát hiện vật thể chạy cục bộ; không có Gemini.
- Android source checks run 44696987580 đã xanh trên commit 45fefc70 (biên dịch Kotlin, unit tests và lint; gồm HTTP multipart upload/download). Worker checks run 44676593746 đã xanh trên commit backend a3192286; backend không thay đổi trong các commit Android sau đó.
- Unit tests hiện có cho parser/compiler, runtime, HTTP/multipart, binary file store, CSV, di trú SQLite, integrity policy và codec backup/restore; CI chạy unit tests và lint. Chưa có kiểm thử Android instrumented/device cho luồng đổi file SQLite khi restore.
- Mã nguồn có tính năng không đồng nghĩa đã nghiệm thu trên thiết bị thật. Cần kiểm tra độ chính xác gesture, vòng đời service, quyền Trợ năng, overlay và khả năng dừng trong các tình huống thực tế.

### Quản lý và phân phối AUTU
- Backend có API quản lý khách hàng, thiết bị, giấy phép và package.
- Có cấu trúc mã hóa package AES-GCM, bọc khóa theo thiết bị RSA và xác minh hash ở phía Android.
- Quy trình end-to-end từ tạo package, phát hành, cấp giấy phép, đăng ký thiết bị, tải xuống, giải mã đến chạy vẫn cần kiểm thử nghiệm thu bằng tài khoản CUSTOMER thật.

## 4. Bản Release hiện có

- GitHub Actions đã hoàn tất workflow Release APK run `44791724413` trên commit `eaad93232c9765750a76da4e3b1ed7383c5ec840`; build Release thành công, APK đã được ký, xác minh chữ ký và tải artifact `AUTU-LIO-Release` lên GitHub Actions.
- Bản Release đã xác minh gần nhất là v0.1.37 (APK chưa được nghiệm thu cài đặt/chạy trên điện thoại thật trong lần build này).
- Không build APK mới nếu chưa nhận đúng lệnh `BUILD APK`.
- Sửa tài liệu hoặc chạy source checks không đồng nghĩa tạo APK; workflow source checks không đóng gói APK.

## 5. Việc cần hoàn thiện tiếp theo

1. **CUSTOMER — mã nguồn đã có, nghiệm thu thực tế còn thiếu:** đăng nhập bằng Google account khác, xác nhận vai trò mặc định là CUSTOMER và xác nhận Worker từ chối Admin API.
2. **Giấy phép/thiết bị — API và màn hình đã có, nghiệm thu end-to-end còn thiếu:** OWNER tạo giấy phép; CUSTOMER đăng ký thiết bị; kiểm tra giới hạn thiết bị, ngày hết hạn, thu hồi và phiên đăng nhập.
3. **Package — mã hóa và API đã có, nghiệm thu phát hành/tải xuống còn thiếu:** OWNER tạo và phát hành package; CUSTOMER tải, giải mã, kiểm tra hash và chạy package bằng giấy phép/thiết bị hợp lệ.
4. **Runtime — mã nguồn và unit test đã có, kiểm thử thiết bị thật còn thiếu:** kiểm tra CHẠM, NHẤN_GIỮ, VUỐT, CHỜ, mở ứng dụng, pause/resume/stop, LUÔN_THỰC_HIỆN và lỗi quyền Trợ năng.
5. **Sao lưu/khôi phục SQLite — mã nguồn và CI đã có, thiết bị thật còn thiếu:** thử xuất qua SAF, thử khôi phục đúng/sai checksum, file hỏng, thiếu dung lượng, đang chạy kịch bản và tình huống rollback; xác nhận database mở lại và dữ liệu giữ nguyên.
6. **Độ ổn định Android — chưa nghiệm thu:** kiểm tra Xiaomi/HyperOS và Android khác; tối ưu pin, service bị hệ thống dừng, đổi hướng màn hình, khuyết màn hình và độ phân giải.
7. **Chất lượng — kiểm tra tự động đang được duy trì:** giữ Worker tests, Kotlin unit tests và lint xanh; đối chiếu commit CI với main; kiểm tra bảo mật và không đưa secret vào APK/Git.
8. **Phạm vi AUTU Script tiếp theo:** sự kiện/tác vụ nền; regex an toàn; module dùng chung và debugger theo lộ trình trong docs/09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md. SAF picker đã được nối vào runtime, vẫn cần nghiệm thu trên thiết bị thật. Ngày giờ ISO 8601 đã được bổ sung mã nguồn/unit tests; multipart upload/download đã có CI xanh; SQLite migration, integrity check và backup/restore vẫn cần nghiệm thu trên thiết bị thật.

## 6. Quy tắc an toàn và phát hành

- Không đánh dấu chức năng hoàn thành chỉ vì có giao diện hoặc API; phải kiểm tra hành vi thật.
- Chỉ OWNER được quản trị; CUSTOMER chỉ sử dụng AUTU đã phát hành với giấy phép hợp lệ.
- Không đưa token, mật khẩu, khóa riêng hoặc keystore vào Git.
- Không phát hành APK Debug cho người dùng.
- Chỉ chạy workflow tạo APK khi chủ ứng dụng gửi đúng cụm `BUILD APK`.
- Mọi báo cáo Release phải có trạng thái workflow, xác nhận chữ ký và artifact thực tế.
