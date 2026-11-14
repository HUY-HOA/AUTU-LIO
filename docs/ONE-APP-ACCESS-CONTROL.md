# AUTU-LIO — Một ứng dụng, phân quyền theo tài khoản

## 1. Quyết định kiến trúc

AUTU-LIO chỉ phát hành **một ứng dụng Android và một APK**. Không còn hai product flavor Admin/Client và không tạo hai application ID.

Cùng một ứng dụng hiển thị các khu vực khác nhau sau khi tài khoản Google được xác thực và máy chủ trả về vai trò hợp lệ:

- Chủ sở hữu
- Nhà phát triển
- Nhà thiết kế
- Người kiểm duyệt
- Người dùng

Bộ chọn giao diện trong bản Debug chỉ để kiểm tra bố cục. Nó không đăng nhập và không cấp quyền. Bản Release không được cung cấp bộ chọn vai trò cục bộ.

## 2. Ma trận quyền

| Vai trò | Quyền chính | Không được tự ý |
|---|---|---|
| Chủ sở hữu | Toàn quyền; mời/thu hồi cộng tác viên; quản lý khách hàng, giấy phép; duyệt và phát hành | Chia sẻ khóa ký hoặc bí mật máy chủ |
| Nhà phát triển | Viết/sửa mã kịch bản, kiểm thử, gửi bản phát hành để duyệt | Quản lý chủ sở hữu, cấp quyền Admin, phát hành chính thức |
| Nhà thiết kế | Thiết kế giao diện, tài nguyên và xem trước | Sửa mã kịch bản/runtime, quản lý giấy phép, phát hành |
| Người kiểm duyệt | Kiểm thử và duyệt/từ chối bản phát hành | Sửa quyền tài khoản hoặc tự phát hành bản chính thức |
| Người dùng | Xem tài khoản của mình, tải và chạy kịch bản đã được cấp | Truy cập IDE, thư viện nội bộ, mã nguồn và công cụ quản trị |

Mỗi tài khoản cộng tác viên được gán vai trò bởi chủ sở hữu. Có thể đổi hoặc thu hồi quyền từ trang Quản lý cộng tác viên.

## 3. Google Sign-In không cần Firebase

Luồng xác thực đã được chuẩn bị trong mã Android và Worker:

1. Android dùng Credential Manager để hiển thị giao diện đăng nhập Google.
2. Ứng dụng nhận Google ID token và gửi token qua HTTPS đến Cloudflare Worker.
3. Worker xác minh chữ ký, issuer, audience và thời hạn token với Google.
4. Worker dùng định danh Google `sub` đã xác minh để tìm tài khoản và vai trò trong D1.
5. Ứng dụng chỉ mở màn hình tương ứng với vai trò do máy chủ trả về.

Không được tin vào email hoặc vai trò do ứng dụng tự gửi lên. Không hardcode địa chỉ Gmail quản trị, không đặt client secret hoặc khóa máy chủ trong APK. Email có thể thay đổi; định danh Google `sub` là khóa liên kết ổn định phù hợp hơn.

Google ID token chỉ dùng để xác thực danh tính ban đầu. Các API quản lý cộng tác viên, giấy phép, package và phát hành phải tiếp tục kiểm tra session và quyền trên máy chủ.

## 4. Dịch vụ máy chủ, không dùng Firebase

Kiến trúc dự kiến:

- **Cloudflare Workers:** API xác thực, session, phân quyền, mời cộng tác viên và quản lý dữ liệu.
- **Cloudflare D1:** tài khoản, vai trò, lời mời, thiết bị, giấy phép, phiên bản và nhật ký tối thiểu.
- **Cloudflare R2:** package kịch bản đã biên dịch/mã hóa và tài nguyên phát hành; bucket riêng tư.

Không sử dụng Firebase Authentication, Firestore, Realtime Database hay Firebase Storage.

Cloudflare vẫn cần được cấu hình và triển khai riêng. Tài liệu này chưa có nghĩa là Worker hoặc D1 đã hoạt động.

## 5. Lời mời cộng tác viên

Quy trình dự kiến:

1. Chủ sở hữu nhập Gmail của người cần mời.
2. Chọn một vai trò: Nhà phát triển, Nhà thiết kế hoặc Người kiểm duyệt.
3. Máy chủ tạo lời mời có mã ngẫu nhiên, thời hạn và trạng thái chưa sử dụng.
4. Người được mời đăng nhập Google; Worker xác minh danh tính và email phù hợp với lời mời.
5. Sau khi chấp nhận, Worker gắn vai trò vào tài khoản; mã mời không được dùng lại.
6. Chủ sở hữu có thể đổi vai trò, thu hồi lời mời hoặc thu hồi cộng tác viên.

Không tự động cấp quyền chỉ vì một người biết hoặc nhập được địa chỉ Gmail. Không gửi lời mời hoặc cấp quyền cho tới khi chủ sở hữu xác nhận thao tác.

## 6. Ranh giới bảo mật

- Ẩn nút trong giao diện không phải là bảo vệ quyền; API phải kiểm tra quyền trên từng hành động.
- Chỉ chủ sở hữu được mời/thu hồi cộng tác viên, thay đổi giấy phép và phát hành chính thức.
- Nhà phát triển gửi bản build/kịch bản để duyệt; người kiểm duyệt phản hồi; chủ sở hữu quyết định phát hành.
- Kịch bản phát hành cần có phiên bản, chữ ký và kiểm tra tính toàn vẹn. Có thể biên dịch/mã hóa package để hạn chế đọc mã nguồn, nhưng không tuyên bố chống sao chép tuyệt đối.
- Quyền Android như Accessibility chỉ được yêu cầu khi tính năng thật cần, có giải thích rõ và người dùng tự cấp theo cơ chế hệ điều hành.
- Thu thập tối thiểu dữ liệu tài khoản/thiết bị cần cho giấy phép và bảo mật; không thu thập nội dung riêng tư không cần thiết.

## 7. Trạng thái triển khai

Đã cập nhật cấu hình dự án để tạo một APK duy nhất và thêm mô hình vai trò/quyền phía Android. Bộ chọn Admin/khách hàng hiện tại chỉ chạy trong bản Debug để kiểm tra giao diện.

Chưa tích hợp Credential Manager vào giao diện Android, chưa cấu hình Google OAuth client ID, chưa tạo D1 database hoặc triển khai Worker, chưa có session thật trong ứng dụng và chưa kiểm thử end-to-end lời mời cộng tác viên. Worker source chỉ mới nằm trong GitHub, chưa hoạt động trên Cloudflare. Không coi mô hình quyền Kotlin phía client là lớp bảo mật cuối cùng.
