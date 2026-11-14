# AUTU-LIO — Hướng dẫn kiểm thử prototype Android

## 1. Mục đích
Bản prototype dùng để kiểm tra bố cục, màu sắc, điều hướng và trải nghiệm trên điện thoại. Đây chưa phải bản Auto Click có thể thực hiện tự động hóa thật.

## 2. Một APK duy nhất
- **AUTU-LIO** — biến thể `debug`, application ID `com.autulio.app`.
- Giao diện Admin và giao diện người dùng là hai khu vực của cùng ứng dụng.
- Trong bản Debug, bộ chọn giao diện cho phép mở bản xem thử Admin hoặc người dùng để kiểm tra bố cục. Đây không phải đăng nhập và không xác minh quyền.
- Bản Release phải lấy vai trò từ máy chủ sau Google Sign-In; không được cung cấp bộ chọn vai trò cục bộ.

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
- Màn hình chào và đăng nhập Google ở dạng giao diện.
- Nút đăng nhập Google hiện chỉ thông báo OAuth chưa được tích hợp.
- Nút xem trước để vào giao diện khách hàng mà không đăng nhập thật.
- Trang chủ, danh sách kịch bản, thiết bị/giấy phép và cài đặt.
- Danh sách có một mục **mẫu xem trước**, được đánh dấu rõ là không phải package thật.
- Mở được chi tiết mẫu; nút Chạy/Tạm dừng/Dừng chỉ hiển thị thông báo, không điều khiển màn hình hoặc ứng dụng khác.
- Trang thiết bị/giấy phép giải thích dữ liệu nào sẽ hiển thị sau khi đăng nhập thật.
- Bật/tắt giao diện sáng và tối.

## 4. Những chức năng chưa hoạt động
- Google OAuth và tài khoản thật.
- Kết nối Cloudflare Workers / D1 / R2.
- Đăng ký và đồng bộ thiết bị thật.
- Giấy phép, thanh toán, gia hạn và thu hồi.
- Ngôn ngữ lập trình riêng, compiler và runtime.
- Chạy thao tác Auto Click, Accessibility Service và overlay.
- Cập nhật package từ xa.
- Báo lỗi và nhật ký gửi về máy chủ.

Nút Kiểm tra / Chạy thử trong editor chỉ thông báo trạng thái chưa tích hợp; không thực thi mã.

## 5. Quy trình tạo APK thử nghiệm
1. Mở tab GitHub Actions của repository.
2. Chọn workflow **AUTU-LIO Android APK**.
3. Chọn **Run workflow** trên nhánh `main`.
4. Chờ job chạy hoàn tất; kiểm tra các bước lint và assemble.
5. Khi thành công, tải artifact duy nhất `AUTU-LIO-Debug`.
6. Giải nén ZIP artifact rồi cài file APK trên Android.

Liên kết workflow: https://github.com/HUY-HOA-1/AUTU-LIO/actions/workflows/build-apks.yml

## 6. Lưu ý an toàn
- Bộ chọn preview chỉ tồn tại trong Debug; không được dùng để cấp quyền Admin ở bản phát hành.
- Tài khoản/role thực phải được Cloudflare Worker xác minh từ Google ID token và dữ liệu quyền phía máy chủ.
- Không dùng màn hình đăng nhập prototype với tài khoản thật cho đến khi OAuth được tích hợp.
- Đây là APK debug để thử giao diện, không phải bản phát hành thương mại.
- Không dùng APK prototype để bán hoặc cấp cho khách hàng.
- Không nhập mật khẩu Google thật vào màn hình thử nghiệm.
- Chỉ cấp quyền Android khi chức năng thực tế cần quyền đó và đã có giải thích.
- Chỉ chuyển sang bản release sau khi hoàn thành backend, bảo mật, runtime, kiểm thử thiết bị và cấu hình ký phát hành.
