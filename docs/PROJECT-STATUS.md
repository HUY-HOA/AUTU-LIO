# AUTU-LIO — Trạng thái dự án

**Cập nhật:** 15/11/2026  
**Nhánh chính:** `main`  
**Repository:** [HUY-HOA-1/AUTU-LIO](https://github.com/HUY-HOA-1/AUTU-LIO)

## 1. Mốc hiện tại

Dự án đã chuyển từ tài liệu thiết kế sang **prototype Android Kotlin/Jetpack Compose**. Có hai product flavors được cấu hình trong cùng codebase:

- `adminDebug` — AUTU-LIO Admin Studio (`com.autulio.app.admin`).
- `clientDebug` — AUTU-LIO AutoClick (`com.autulio.app.client`).

Các màn hình prototype có thể điều hướng và kiểm tra bố cục. Đây chưa phải bản ứng dụng có backend và chức năng tự động hóa thực tế.

## 2. Phần đã có trong mã nguồn

### Admin Studio
- Trang tổng quan.
- Danh sách kịch bản, tìm kiếm và lọc Bản nháp / Đã phát hành.
- Tạo bản nháp và mở trình soạn thảo prototype.
- Trang Khách hàng và Thiết bị.
- Trang Giấy phép, Thư viện, Phát hành, Nhật ký và Cài đặt.
- Chuyển giao diện sáng / tối.

### AutoClick khách hàng
- Màn hình đăng nhập prototype và chế độ xem trước.
- Trang chủ.
- Danh sách và chi tiết một kịch bản mẫu được ghi rõ là dữ liệu xem trước.
- Các nút Chạy / Tạm dừng / Dừng mẫu chỉ hiển thị thông báo; không thao tác trên màn hình.
- Trang Thiết bị / Giấy phép và Cài đặt.

## 3. Build

- Đã có workflow `.github/workflows/build-apks.yml`.
- Workflow kiểm tra lint và tạo hai APK debug riêng.
- Trigger được thiết lập theo `workflow_dispatch` hoặc nhánh `build-requests/**`; push thông thường lên `main` không tự build.
- **Chưa có workflow run được xác minh và chưa có APK artifact được xác minh.**
- Tuân thủ quy tắc: chỉ bắt đầu build khi chủ ứng dụng gửi riêng đúng lệnh `BUILD APK`.

## 4. Phần chưa triển khai

- Google OAuth và hệ thống tài khoản thực.
- Cloudflare Workers / D1 / R2 và API.
- Đồng bộ tài khoản, thiết bị, giấy phép và dữ liệu khách hàng.
- Trình biên dịch, parser, bytecode và runtime của ngôn ngữ AUTU-LIO.
- Engine thực hiện Auto Click, Accessibility Service, overlay và điều khiển vòng đời tác vụ.
- Đóng gói, ký, mã hóa và phân phối kịch bản thật.
- Cập nhật từ xa, nhật ký máy chủ và quy trình thu hồi package.
- Kiểm thử lint/build và thử nghiệm APK trên thiết bị thật.

## 5. Việc tiếp theo

1. Khi có lệnh build hợp lệ, chạy workflow để kiểm tra khả năng biên dịch và lấy APK debug.
2. Sửa các lỗi lint / build phát hiện được trước khi phát hành APK thử nghiệm.
3. Thử hai APK trên điện thoại, thu thập lỗi giao diện và điều chỉnh.
4. Thiết kế đặc tả ngôn ngữ AUTU-LIO trước khi viết parser/compiler/runtime.
5. Triển khai engine tự động hóa có giới hạn quyền và xin quyền Android minh bạch.
6. Tích hợp backend, đăng nhập, giấy phép và cập nhật package sau khi kiến trúc runtime đã ổn định.

## 6. Quy tắc báo cáo

- Phân biệt rõ tài liệu thiết kế, prototype giao diện, chức năng đã hoạt động và chức năng máy chủ.
- Không báo một chức năng đã hoạt động nếu mới chỉ có nút hoặc màn hình.
- Không báo build thành công nếu chưa có workflow run thành công và APK artifact tải được.
- Không tự động kích hoạt workflow; tuân thủ lệnh build độc lập trong `docs/GITHUB-WORKFLOW.md`.
