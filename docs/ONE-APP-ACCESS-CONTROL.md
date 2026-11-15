# AUTU-LIO — Một ứng dụng, phân quyền theo tài khoản

## 1. Quyết định kiến trúc

AUTU-LIO chỉ phát hành **một ứng dụng Android và một APK** với application ID `com.autulio.app`. Không dùng hai product flavor Admin/Client và không tạo hai application ID.

Cùng một ứng dụng hiển thị các khu vực khác nhau sau khi Google xác thực tài khoản và máy chủ trả về vai trò hợp lệ:

- Chủ sở hữu
- Nhà phát triển
- Nhà thiết kế
- Người kiểm duyệt
- Người dùng

Bộ chọn giao diện trong bản Debug chỉ để kiểm tra bố cục và vai trò mẫu. Nó không đăng nhập, không cấp quyền và không tồn tại trong Release. Bản Release chỉ mở màn hình theo quyền do Worker xác minh.

## 2. Vai trò và quyền

| Vai trò | Quyền mặc định | Ranh giới |
|---|---|---|
| Chủ sở hữu | Toàn quyền quản trị, mời/thu hồi cộng tác viên, quản lý khách hàng/giấy phép, duyệt và phát hành | Không chia sẻ khóa ký hoặc bí mật máy chủ |
| Nhà phát triển | Viết/sửa mã kịch bản, kiểm thử, gửi bản để duyệt | Không quản lý cộng tác viên/giấy phép, không ký hoặc phát hành chính thức |
| Nhà thiết kế | Thiết kế giao diện, xem trước và gửi bản thiết kế để duyệt | Không xem/sửa mã kịch bản, runtime, giấy phép hoặc phát hành cuối cùng |
| Người kiểm duyệt | Kiểm thử và duyệt/từ chối bản gửi | Không sửa mã, thay đổi vai trò hoặc phát hành cuối cùng |
| Người dùng | Xem tài khoản, chạy kịch bản đã phát hành và được cấp phép | Không truy cập IDE, mã nguồn và công cụ quản trị |

Chủ sở hữu có thể tùy chỉnh từng quyền trong phạm vi cho phép của vai trò Nhà phát triển, Nhà thiết kế hoặc Người kiểm duyệt. Không thể cấp quyền độc quyền của Chủ sở hữu cho cộng tác viên. Khi đổi vai trò, các quyền tùy chỉnh cũ được xóa và vai trò mới trở về quyền mặc định.

Các quyền chi tiết được lưu ở D1, trả về từ API và được dùng để điều khiển giao diện cộng tác viên. Các API thực thi mã, phát hành package và dữ liệu khách hàng sẽ phải kiểm tra quyền tương ứng ở phía Worker trước khi những chức năng đó được triển khai.

## 3. Google Sign-In không cần Firebase

Luồng xác thực trong mã Android/Worker:

1. Android dùng Credential Manager để hiển thị giao diện đăng nhập Google.
2. Ứng dụng lấy nonce ngắn hạn từ Auth API, yêu cầu Google cấp ID token có nonce đó.
3. Ứng dụng gửi ID token tới Cloudflare Worker qua HTTPS.
4. Worker xác minh chữ ký Google, issuer, audience, thời hạn, email đã xác minh và nonce.
5. Worker tra cứu định danh Google `sub`, vai trò và quyền từ D1, sau đó tạo bearer session.
6. Android mã hóa session bằng AES-GCM với khóa trong Android Keystore; Google ID token không được lưu.
7. Khi mở ứng dụng, Android gọi `/api/auth/me`; giao diện chỉ mở sau khi session được máy chủ xác minh.

Không tin vào email hoặc vai trò do ứng dụng tự gửi lên. Không hardcode email quản trị, không đặt client secret hoặc khóa máy chủ trong APK. Email có thể đổi; định danh Google `sub` là khóa liên kết tài khoản.

**Tình trạng:** mã đăng nhập Android và Worker đã được thêm vào repository, nhưng Google OAuth chưa cấu hình và Worker chưa deploy. Do đó đăng nhập Google thật vẫn chưa được bật.

## 4. Dịch vụ máy chủ, không dùng Firebase

- **Cloudflare Workers:** API xác thực, session, phân quyền, mời cộng tác viên và quản lý dữ liệu.
- **Cloudflare D1:** tài khoản, vai trò, nonce, session, lời mời, quyền ghi đè và nhật ký.
- **Cloudflare R2:** dự kiến dùng cho package kịch bản được ký/mã hóa và tài nguyên phát hành ở giai đoạn sau.

Không sử dụng Firebase Authentication, Firestore, Realtime Database hay Firebase Storage.

D1 `autu_lio_auth` đã tồn tại trong tài khoản Cloudflare kết nối. Worker source có trong repo nhưng chưa deploy. Không tuyên bố backend đang hoạt động cho tới khi triển khai và kiểm tra endpoint thật.

## 5. Lời mời cộng tác viên

Luồng API đã được viết:

1. Chủ sở hữu nhập Gmail và chọn Nhà phát triển, Nhà thiết kế hoặc Người kiểm duyệt.
2. Worker tạo lời mời có mã ngẫu nhiên, thời hạn và trạng thái chưa sử dụng.
3. Chủ sở hữu sao chép mã hiển thị một lần và tự chia sẻ riêng cho người được mời.
4. Người nhận đăng nhập đúng tài khoản Google tương ứng rồi nhập mã trong AUTU-LIO.
5. Worker so khớp email, đánh dấu mã đã dùng, gán vai trò và ghi nhật ký.
6. Chủ sở hữu có thể xem danh sách, đổi vai trò, tùy chỉnh quyền, thu hồi cộng tác viên hoặc hủy lời mời đang chờ.

Hiện chưa có gửi email tự động. Mã lời mời và session được lưu dạng SHA-256 hash trong D1; mã lời mời chỉ trả về một lần cho chủ sở hữu sao chép. Thu hồi cộng tác viên cũng thu hồi các phiên hiện tại của tài khoản đó.

## 6. Quyền chi tiết

Các quyền có tên rõ ràng gồm truy cập khu vực quản trị, quản lý cộng tác viên, quản lý khách hàng/giấy phép, sửa mã kịch bản, kiểm thử, sửa UI, xem trước, gửi duyệt, duyệt, phát hành, chạy kịch bản đã phát hành và xem tài khoản cá nhân.

Chủ sở hữu chỉ có thể bật/tắt quyền nằm trong allowlist của vai trò:
- Nhà phát triển: sửa mã, kiểm thử, gửi duyệt.
- Nhà thiết kế: sửa UI, xem trước UI, gửi bản thiết kế để duyệt.
- Người kiểm duyệt: kiểm thử, duyệt bản gửi.

Các quyền quản lý cộng tác viên, quản lý giấy phép, ký và phát hành chính thức không thể cấp qua màn hình quyền chi tiết. Mọi cập nhật quyền được ghi vào audit log.

## 7. Dự án cộng tác và quy trình duyệt

Mã Worker trên nhánh `feature/shared-project-workspaces` bổ sung API dự án dùng chung:

- Chủ sở hữu tạo dự án và thêm/xóa tài khoản Nhà phát triển, Nhà thiết kế hoặc Người kiểm duyệt.
- Nhà phát triển lưu bản nháp mã kịch bản vào D1; Nhà thiết kế lưu bản thiết kế UI dạng JSON vào D1.
- Mỗi bản nháp có số revision. Khi hai người cùng sửa, lần lưu dùng revision cũ sẽ nhận HTTP 409 để tải lại, không âm thầm ghi đè.
- Người có quyền gửi duyệt tạo submission chứa snapshot bất biến của revision được gửi.
- Người kiểm duyệt xem hàng chờ và duyệt/từ chối; không được tự duyệt bản gửi của chính mình.
- Mọi hành động dự án/lưu bản nháp/gửi duyệt được ghi audit log, không ghi nội dung mã nguồn vào audit log.

Quyền trong dự án hiện kết hợp vai trò/quyền tài khoản và tư cách thành viên dự án. Chưa có quyền tùy chỉnh riêng từng dự án, tự động gửi email, so sánh diff trực quan hoặc khôi phục phiên bản mã bất kỳ. Duyệt submission chưa đồng nghĩa với phát hành package; phát hành cuối cùng vẫn là quyền riêng của Chủ sở hữu và pipeline package chưa triển khai.

## 8. Ranh giới bảo mật và giới hạn prototype

- Ẩn nút trong giao diện không phải là bảo vệ quyền; mọi API thay đổi dữ liệu phải kiểm tra session và quyền ở phía Worker.
- Chỉ Chủ sở hữu được quản lý cộng tác viên, giấy phép, bí mật ký và phát hành cuối cùng.
- Nhà phát triển hiện lưu bản nháp cục bộ; chưa đồng bộ mã lên máy chủ, chưa có compiler/runtime và chưa có thao tác gửi duyệt thật.
- Nhà thiết kế hiện thay đổi màu/bố cục ở màn hình preview cục bộ; chưa ghi thay đổi UI vào kho thiết kế trung tâm.
- Người kiểm duyệt hiện có workspace preview; hàng đợi duyệt và quyết định duyệt chưa kết nối backend.
- Kịch bản phát hành sau này cần phiên bản, chữ ký và kiểm tra tính toàn vẹn. Không tuyên bố chống sao chép tuyệt đối.
- Accessibility/overlay chỉ xin quyền Android khi chức năng thực tế cần và giải thích rõ.
- Thu thập tối thiểu dữ liệu tài khoản/thiết bị cần cho bảo mật và giấy phép; không lấy dữ liệu riêng tư không cần thiết.

## 9. Trạng thái triển khai

Đã hợp nhất vào `main` nền tảng một APK, Google Credential Manager, xác minh session theo role, workspace riêng cho các vai trò và giao diện quản lý cộng tác viên nối API. Đây là mã nguồn đã qua kiểm tra biên dịch Kotlin và kiểm tra Worker ở PR; không phải xác nhận đăng nhập Google thật đã hoạt động.

Quyền chi tiết theo cộng tác viên đã được hợp nhất vào `main`. API dự án dùng chung và lưu bản nháp từ máy chủ hiện nằm trên nhánh `feature/shared-project-workspaces`, chưa được hợp nhất và chưa triển khai lên Worker.

Chưa có Worker deployed, Google OAuth Client ID/OWNER_EMAIL, đăng nhập thật, gửi email mời tự động, runtime Auto Click, compiler, UI Android kết nối API dự án, so sánh diff/rollback tổng quát, pipeline phát hành package hoặc cập nhật package từ xa.
