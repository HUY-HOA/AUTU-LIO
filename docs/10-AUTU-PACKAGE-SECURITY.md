# AUTU-LIO — Đóng gói và phân phối AUTU đã mã hóa

## Luồng chủ sở hữu

1. Tạo và kiểm tra script trong trình soạn thảo AUTU-LIO. Bản nháp được lưu cục bộ trong kho mã hóa AES-GCM, khóa lưu trong Android Keystore của thiết bị chủ sở hữu.
2. Nhấn **Chuyển sang mã hóa & phát hành**. Ứng dụng quay về trang Gói AUTU và tự chọn đúng bản nháp vừa sửa.
3. Chọn mô tả và phát hành. Ứng dụng biên dịch/kiểm tra phiên bản ngôn ngữ trước khi gửi nguồn qua HTTPS đến Worker.
4. Worker nén GZIP ở mức 9 khi dữ liệu nén nhỏ hơn dữ liệu gốc, sau đó mã hóa AES-256-GCM bằng khóa dữ liệu ngẫu nhiên riêng cho từng phiên bản. IV và dữ liệu xác thực gắn với ID gói + phiên bản.
5. Khóa dữ liệu được mã hóa dưới master key có phiên bản. D1 chỉ lưu ciphertext, IV, khóa dữ liệu đã bọc, mã kiểm tra nguồn và phiên bản khóa; không lưu source plaintext.
6. Chủ sở hữu có thể mở **Mã gốc / mã hóa** tại từng gói. API preview chỉ chấp nhận vai trò OWNER, giải mã trong bộ nhớ, xác minh SHA-256 và ghi audit event `package.source_viewed`. Chế độ bản mã hóa hiển thị đầy đủ envelope để đối chiếu.

## Luồng khách hàng

1. Khách hàng phải đăng nhập, có giấy phép ACTIVE chưa hết hạn và đăng ký thiết bị đang dùng.
2. Máy chủ chỉ trả danh sách gói ACTIVE sau khi xác minh giấy phép và thiết bị.
3. Khi tải, Worker bọc khóa dữ liệu riêng cho public key của thiết bị bằng RSA-3072 OAEP SHA-256. Thiết bị khác không thể dùng gói khóa đã bọc này.
4. Ứng dụng giải mã gói trong bộ nhớ, giải nén nếu có marker `GZ1.`, kiểm tra SHA-256 trên source gốc, biên dịch đúng language version rồi chuyển chương trình đã biên dịch cho runtime. Mã nguồn không được xuất thành tệp dùng chung.

## Định dạng và tương thích

- `GZ1.` ở đầu ciphertext cho biết payload sau AES-GCM được nén GZIP; ciphertext không có marker là định dạng raw cũ. Client mới yêu cầu `compression=GZIP`; client cũ nhận bản raw được Worker giải nén và mã hóa lại trong bộ nhớ.
- Gói cũ vẫn giải mã được sau nâng cấp vì dữ liệu lưu cũ không thay đổi. Client mới yêu cầu OAEP SHA-256; client cũ mặc định được Worker dùng OAEP SHA-1 tương thích. Cả hai đều yêu cầu khóa RSA tối thiểu 3072-bit.
- Không thay đổi D1 schema hoặc các secrets hiện hữu.
- Giới hạn source đầu vào vẫn là 32.768 ký tự. Ứng dụng giới hạn kích thước sau giải nén ở mức 262.144 byte để tránh payload nén bất thường.

## Giới hạn bảo mật cần hiểu đúng

Đây là mã hóa mạnh khi lưu trữ và phân phối có kiểm soát, không phải mã hóa đầu-cuối: Worker nhận source qua HTTPS để kiểm tra và mã hóa bằng master key. Chủ tài khoản Cloudflare có quyền quản trị Worker/secret là một phần của trust boundary.

Trong APK không nhúng mã nguồn AUTU của khách hàng; gói được tải dưới dạng ciphertext. Tuy vậy, để chạy, thiết bị phải giải mã/biên dịch trong bộ nhớ. Thiết bị root, bị debug, bị instrument hoặc runtime bị sửa đổi có thể trích xuất source/AST trong lúc chạy. Không có biện pháp phía ứng dụng nào bảo đảm "không thể xem trong mọi tình huống" trên thiết bị do người nhận kiểm soát.

Không nên gửi mật khẩu, khóa API, thông tin cá nhân hoặc bí mật có giá trị cao trong script phát hành. Thu hồi giấy phép/gói ngăn lần tải tiếp theo, nhưng không thể xóa bản đã tải/đã giải mã khỏi một thiết bị ngoại tuyến hoặc đã bị can thiệp.
