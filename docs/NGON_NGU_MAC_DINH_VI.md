# Quy tắc ngôn ngữ mặc định của AUTU-LIO

## Quy định bắt buộc

- Tiếng Việt là ngôn ngữ mặc định của toàn bộ giao diện AUTU-LIO, không phụ thuộc ngôn ngữ hệ thống của điện thoại.
- Mọi nội dung do ứng dụng tự hiển thị phải dùng tiếng Việt: giao diện OWNER và CUSTOMER, tiêu đề, nút, nhãn, mô tả, tìm kiếm, hộp thoại, trạng thái rỗng, thông báo, lỗi, xác nhận, hướng dẫn, cửa sổ nổi, dịch vụ Trợ năng và thông báo nền.
- Không hiển thị song ngữ Việt–Anh trong giao diện thông thường. Nếu mã nguồn dùng `tr(tiengViet, english)`, đối số đầu tiên phải là bản tiếng Việt và hàm `tr` tiếp tục trả về tiếng Việt.
- Không đưa nguyên văn lỗi tiếng Anh từ máy chủ, Android hoặc thư viện vào giao diện. Hãy ánh xạ sang thông báo tiếng Việt hoặc dùng thông báo dự phòng tiếng Việt.
- Khi bổ sung tính năng mới, phải viết sẵn chuỗi hiển thị bằng tiếng Việt; không chờ người dùng nhắc lại.

## Nội dung được giữ nguyên

Giữ nguyên tên AUTU-LIO, Google, Android, tên sản phẩm/dịch vụ, cú pháp và từ khóa của AUTU Script, tên API/JSON/HTTP, mã lỗi hoặc trạng thái giao thức, định dạng tệp, tên trường dữ liệu, thuật toán và các thuật ngữ kỹ thuật không có cách dịch an toàn. Có thể kèm giải thích tiếng Việt khi cần.

## Giới hạn của nền tảng

Màn hình do Android hoặc Google tự sở hữu, ví dụ hộp thoại chọn tài khoản Google, bộ chọn tệp hệ thống và màn hình cấp quyền hệ điều hành, dùng ngôn ngữ do hệ thống/dịch vụ quyết định. AUTU-LIO không thể ép ngôn ngữ các màn hình đó; mọi phần giải thích, hướng dẫn, nhãn và thông báo do AUTU-LIO tạo ra vẫn phải là tiếng Việt.

## Kiểm tra khi thay đổi

- Rà cả OWNER, CUSTOMER, đăng nhập, quản lý, trình soạn thảo, thư viện mẫu, cửa sổ nổi, thông báo, Trợ năng, nhập/xuất tệp và sao lưu/khôi phục.
- Kiểm tra thông báo ngoại lệ và phản hồi API để không lộ tiếng Anh.
- Chạy kiểm tra biên dịch, unit test và lint. Không tạo APK nếu chưa nhận đúng lệnh `BUILD APK`.
