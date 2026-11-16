# AUTU-LIO Web Preview

Bản xem trước giao diện dành cho điện thoại và máy tính, tách biệt với APK Android.

## Nội dung
- `index.html`: cấu trúc trang.
- `styles.css`: giao diện responsive.
- `app.js`: điều hướng vai trò, màn hình mẫu, modal tạo kịch bản và bảng điều khiển nổi mô phỏng.

## Cảnh báo
Đây chỉ là UI prototype chạy trong trình duyệt. Nút chạy trong cửa sổ nổi là mô phỏng, không điều khiển Android Accessibility Service, không chạy AUTU Script thật, không xác thực tài khoản và không kết nối dữ liệu khách hàng.

## Cloudflare Pages
Cấu hình root directory `/`, build output directory `web-preview`, không cần build command. Nên dùng branch `preview/web-live` làm production branch riêng để không ảnh hưởng nhánh Android `main`.
