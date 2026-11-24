# AUTU-LIO — Thiết kế giao diện và trạng thái triển khai

**Cập nhật:** 21/11/2026

## 1. Phạm vi sản phẩm

- AUTU-LIO là ứng dụng Android native viết bằng Kotlin và Jetpack Compose, dùng một APK với application ID `com.autu.lio`.
- Cloudflare Worker và D1 là backend REST/API của ứng dụng; đây không phải website hay web dashboard.
- Cùng một APK phục vụ hai vai trò: OWNER và CUSTOMER. Vai trò do máy chủ xác minh, không do người dùng chọn trong bản Release.
- Bản Debug có bộ chọn giao diện xem trước để kiểm tra bố cục; bộ chọn này không đăng nhập và không cấp quyền thật.

## 2. Điều hướng hiện tại

### OWNER

Thanh điều hướng chính gồm:

1. **Tổng quan:** số liệu khách hàng, thiết bị, giấy phép và gói AUTU, tải từ API quản trị.
2. **Kịch bản:** danh sách bản nháp cục bộ và đường vào trình soạn thảo AUTU Script.
3. **Khách hàng:** màn hình quản lý khách hàng, giấy phép và thiết bị.
4. **Thêm:** thư viện mẫu AUTU, gói AUTU đã phát hành, nhật ký quản trị và cài đặt.

Trong khu vực quản lý truy cập, OWNER có thể tìm kiếm và lọc khách hàng/giấy phép/thiết bị; cấp, gia hạn, tạm dừng, kích hoạt lại hoặc thu hồi giấy phép; thu hồi thiết bị; xem trạng thái và lần hoạt động gần nhất.

### CUSTOMER

Thanh điều hướng chính gồm:

1. **Trang chủ:** thông tin tài khoản, giấy phép và số thiết bị hoạt động.
2. **Kịch bản:** kho AUTU đã phát hành, cho phép tải, giải mã, xác minh và chuẩn bị chạy nếu giấy phép cùng thiết bị hợp lệ.
3. **Thiết bị:** xem giấy phép, đăng ký thiết bị hiện tại và gỡ thiết bị khỏi tài khoản.
4. **Cài đặt:** giao diện, thông tin ứng dụng, sao lưu/khôi phục và tệp AUTU Script.

CUSTOMER không có đường truy cập Admin Studio hoặc trình soạn thảo tạo mã của OWNER.

## 3. Trình soạn thảo và runtime

- Trình soạn thảo hỗ trợ tạo/lưu bản nháp, kiểm tra mã, xem hướng dẫn cú pháp và chèn mẫu.
- AUTU Script hiện ở phiên bản ngôn ngữ 1.22.
- Runtime sử dụng Android Accessibility Service và cửa sổ điều khiển nổi để chạy, tạm dừng, tiếp tục và dừng kịch bản.
- Trạng thái phiên đăng nhập được kiểm tra lại định kỳ; phiên mất hiệu lực sẽ khóa trình chạy.
- Một số thao tác phụ thuộc quyền Trợ năng, API Android, ứng dụng đích và khả năng của thiết bị; giao diện không được diễn giải việc chuẩn bị kịch bản là bằng chứng thao tác đã chạy thành công.

## 4. Phát hành AUTU

- OWNER lưu bản nháp riêng trên thiết bị, sau đó chuyển sang màn hình gói AUTU để phát hành.
- Ứng dụng kiểm tra cú pháp và phiên bản ngôn ngữ trước khi gửi mã nguồn qua HTTPS.
- Worker nén khi có lợi, mã hóa AES-256-GCM và lưu ciphertext cùng metadata; khóa dữ liệu được bọc riêng cho khóa công khai của thiết bị CUSTOMER.
- CUSTOMER chỉ nhận package khi tài khoản, giấy phép và thiết bị đáp ứng điều kiện ở máy chủ.
- OWNER có thể xem mã gốc và phong bì mã hóa của package. Việc xem mã gốc chỉ qua API quản trị dành cho OWNER và có ghi audit event.

Đây là mã hóa khi lưu trữ và phân phối có kiểm soát, không phải mã hóa đầu-cuối. Source vẫn phải được giải mã trong bộ nhớ để biên dịch và chạy; thiết bị đã root/debug/instrument có thể trích xuất dữ liệu trong lúc thực thi.

## 5. Quy chuẩn giao diện

- Tối ưu cho màn hình điện thoại, bố cục gọn, chữ dễ đọc và vùng chạm đủ lớn.
- Hỗ trợ giao diện sáng/tối.
- Ngôn ngữ theo hệ thống: tiếng Việt khi ngôn ngữ hệ thống là tiếng Việt; tiếng Anh với ngôn ngữ hệ thống khác.
- Các màn hình tải dữ liệu cần phân biệt trạng thái đang tải, rỗng, lỗi và thành công.
- Không hiển thị dữ liệu quản trị cho CUSTOMER; không thu thập IMEI, số sê-ri, MAC, GPS hoặc dữ liệu riêng tư không cần thiết.

## 6. Những phần chỉ dùng để xem trước

- Bộ chọn OWNER/CUSTOMER trong bản Debug chỉ chuyển giao diện để kiểm tra bố cục, không tạo phiên đăng nhập hay quyền máy chủ.
- Các mẫu trong thư viện mẫu là mã nguồn tham khảo; không mặc nhiên là package đã phát hành cho CUSTOMER.
- Không dùng dữ liệu mẫu hoặc màn hình xem trước để báo cáo rằng luồng giấy phép, tải package hay chạy tự động đã được nghiệm thu.

## 7. Phần còn thiếu hoặc cần hoàn thiện

- Lập lịch chạy, hàng đợi nhiều kịch bản và tác vụ nền theo sự kiện.
- Thư viện module dùng chung và cơ chế import/version cho module.
- Form giao diện tương tác do AUTU tạo.
- Regex an toàn, autocomplete/định dạng mã đầy đủ và debugger từng bước.
- Nghiệm thu toàn luồng CUSTOMER: cấp giấy phép, đăng ký thiết bị, tải package, giải mã và thực thi.
- Kiểm tra gesture, vòng đời Accessibility Service, overlay, quyền, pin và tương thích trên Xiaomi/HyperOS cùng các thiết bị Android khác.
- Kiểm tra backup/restore SQLite thực tế, bao gồm lỗi file, thiếu dung lượng và rollback.

## 8. Tiêu chí nghiệm thu

Một tính năng chỉ được coi là nghiệm thu khi:
1. Có mã nguồn thực thi, không chỉ có nút hoặc bố cục.
2. API và phân quyền phía máy chủ được kiểm tra khi có liên quan.
3. Có kiểm thử tự động phù hợp và CI thành công.
4. Các luồng phụ thuộc phần cứng/quyền Android được thử trên thiết bị thật.
5. Ghi rõ giới hạn Android hoặc ứng dụng đích không cho phép thao tác.

Xem [trạng thái phát triển](PROJECT-STATUS.md), [phạm vi AUTU Script](09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md) và [bảo mật package](10-AUTU-PACKAGE-SECURITY.md) để biết chi tiết kỹ thuật.


## 9. Chuẩn hóa trải nghiệm ngày 23/11/2026

- Điều hướng OWNER giữ bốn mục dưới cùng: Tổng quan, Kịch bản, Khách hàng và Thêm. Giấy phép, thiết bị, gói AUTU, thư viện mẫu, nhật ký và cài đặt được gom theo nhóm chức năng; không tạo thêm tab chỉ để tách một mục con.
- Trang Tổng quan OWNER có hai đường tắt hoạt động thật: tạo AUTU và mở quản lý khách hàng.
- Điều hướng CUSTOMER giữ bốn mục: Trang chủ, Kho AUTU, Thiết bị và Cài đặt. Tên Kho AUTU mô tả đúng nội dung package đã phát hành hơn tên chung Kịch bản.
- Trang chủ CUSTOMER có đường tắt trực tiếp tới Kho AUTU và Thiết bị; trạng thái tài khoản có thể thử tải lại khi API gặp lỗi. Email không lặp lại trong nội dung trang chủ vì đã nằm ở thanh tài khoản.
- Bảng màu được chuẩn hóa theo nền xanh than/xanh lam ở chế độ tối và nền sáng trung tính ở chế độ sáng; màu nhấn dành cho hành động và trạng thái, tránh nền đen thuần hoặc xám ngả vàng.
- Không bổ sung mục Đang chạy riêng khi thao tác thực tế vẫn được điều khiển bằng cửa sổ nổi của Accessibility Service. Không hiển thị trạng thái chạy mẫu giả.
- Nhãn điều hướng tăng từ 10sp lên 11sp; vùng thao tác chính có chiều cao tối thiểu 48–52dp.

