# AUTU-LIO — Hướng dẫn kiểm thử prototype Android

## 1. Mục đích
Bản prototype dùng để kiểm tra bố cục, màu sắc, điều hướng và trải nghiệm trên điện thoại. Đây chưa phải bản Auto Click có thể thực hiện tự động hóa thật.

## 2. Một APK duy nhất
- **AUTU-LIO** — biến thể `debug`, application ID `com.autulio.app`.
- Giao diện Admin và giao diện người dùng là hai khu vực của cùng ứng dụng.
- Trong bản Debug, bộ chọn có ba lối: preview Admin, preview người dùng và thử đăng nhập Google thật. Hai lối preview không cấp quyền; lối đăng nhập thật cần OAuth và Worker đã cấu hình.
- Bản Release chỉ mở màn hình theo vai trò do Worker trả về sau Google Sign-In; không có bộ chọn vai trò cục bộ.

## 3. Các mục có thể kiểm tra trong prototype

### Admin Studio
- Trang Tổng quan và thẻ thống kê trống.
- Điều hướng Tổng quan / Kịch bản / Khách hàng / Thiết bị / Thêm.
- Tạo kịch bản, nhập nội dung và lưu bản nháp cục bộ.
- Bản nháp được giữ trong vùng lưu trữ riêng của AUTU-LIO Admin Studio và khôi phục sau khi đóng/mở lại ứng dụng.
- Nội dung chưa đồng bộ máy chủ và chưa được mã hóa riêng ở tầng ứng dụng.
- Bật/tắt giao diện sáng và tối trong mục quản lý thêm.
- Thông báo rõ các chức năng chưa kết nối.
- Mục Thêm mở được các trang Giấy phép, Thư viện, Phát hành, Nhật ký và Cài đặt.
- Trang Khách hàng và Thiết bị có bố cục quản trị riêng, hiện trạng thái chưa có dữ liệu máy chủ.
- Bộ lọc Kịch bản phân biệt Bản nháp và Đã phát hành.

### AutoClick
- Màn hình đăng nhập Google thật dùng Credential Manager nằm trong luồng xác thực Release và nút thử đăng nhập thật ở Debug.
- Đăng nhập chỉ hoạt động sau khi có Google Web Client ID, API HTTPS và Worker đã triển khai; nếu thiếu cấu hình app sẽ báo rõ.
- Nút xem trước để vào giao diện khách hàng mà không đăng nhập thật.
- Trang chủ, danh sách kịch bản, thiết bị/giấy phép và cài đặt.
- Danh sách có một mục **mẫu xem trước**, được đánh dấu rõ là không phải package thật.
- Mở được chi tiết mẫu; nút Chạy/Tạm dừng/Dừng chỉ hiển thị thông báo, không điều khiển màn hình hoặc ứng dụng khác.
- Trang thiết bị/giấy phép giải thích dữ liệu nào sẽ hiển thị sau khi đăng nhập thật.
- Bật/tắt giao diện sáng và tối.

## 4. Những chức năng chưa hoạt động hoặc chưa xác minh
- Đăng nhập Google chưa được chạy end-to-end do chưa cấu hình OAuth client ID.
- Cloudflare Worker/D1 source đã có nhưng chưa tạo database hoặc triển khai.
- Đăng ký và đồng bộ thiết bị thật.
- Giấy phép, thanh toán, gia hạn và thu hồi.
- Ngôn ngữ lập trình riêng, compiler và runtime.
- Chạy thao tác Auto Click, Accessibility Service và overlay.
- Cập nhật package từ xa.
- Báo lỗi và nhật ký gửi về máy chủ.

Nút Kiểm tra / Chạy thử trong editor chỉ thông báo trạng thái chưa tích hợp; không thực thi mã.

## 5. Cấu hình để thử đăng nhập thật

Trước khi thử Google Sign-In, cần:
- Tạo Google OAuth Android client cho package `com.autulio.app` và SHA-1 của chứng thư ký APK; tạo Web application client ID để dùng làm server client ID.
- Cấu hình Cloudflare D1/Worker và `OWNER_BOOTSTRAP_EMAIL` secret.
- Thêm GitHub Actions Variables `AUTULIO_API_BASE_URL` và `AUTULIO_GOOGLE_WEB_CLIENT_ID` trong repository. Với build local, đặt hai Gradle properties tương ứng trong `~/.gradle/gradle.properties`.
- Không đưa Google client secret, Cloudflare token hoặc khóa ký vào APK hay repository.

## 6. Quy trình tạo APK thử nghiệm
1. Mở tab GitHub Actions của repository.
2. Chọn workflow **AUTU-LIO Android APK**.
3. Chọn **Run workflow** trên nhánh `main`.
4. Chờ job chạy hoàn tất; kiểm tra các bước lint và assemble.
5. Khi thành công, tải artifact duy nhất `AUTU-LIO-Debug`.
6. Giải nén ZIP artifact rồi cài file APK trên Android.

Liên kết workflow: https://github.com/HUY-HOA-1/AUTU-LIO/actions/workflows/build-apks.yml

## 7. Lưu ý an toàn
- Bộ chọn preview chỉ tồn tại trong Debug; không được dùng để cấp quyền Admin ở bản phát hành.
- Tài khoản/role thực phải được Cloudflare Worker xác minh từ Google ID token và dữ liệu quyền phía máy chủ.
- Chỉ thử đăng nhập thật sau khi OAuth client ID và Worker được cấu hình; ứng dụng dùng giao diện hệ thống Credential Manager, không yêu cầu người dùng nhập mật khẩu Google trong AUTU-LIO.
- Đây là APK debug để thử giao diện, không phải bản phát hành thương mại.
- Không dùng APK prototype để bán hoặc cấp cho khách hàng.
- Chỉ cấp quyền Android khi chức năng thực tế cần quyền đó và đã có giải thích.
- Chỉ chuyển sang bản release sau khi hoàn thành backend, bảo mật, runtime, kiểm thử thiết bị và cấu hình ký phát hành.
