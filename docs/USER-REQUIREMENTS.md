# Sổ yêu cầu người dùng — AUTU-LIO

**Ngày rà soát:** 26/11/2026  
**Repository:** HUY-HOA/AUTU-LIO  
**Mục đích:** Ghi lại yêu cầu sản phẩm đã thống nhất và các điều kiện nghiệm thu. Các tài liệu kỹ thuật hiện có vẫn là nguồn chi tiết cho cú pháp, runtime, API, mã hóa và triển khai.

## 1. Quy tắc bắt buộc khi phát triển

1. Đọc sổ này, README và tài liệu liên quan trước khi thay đổi mã nguồn.
2. Người dùng cần chức năng hoạt động thật, không chỉ có giao diện, nút bấm hoặc dữ liệu mẫu.
3. Không tự ý đổi application ID, vai trò, backend, quyền, giao thức package, database hoặc khóa ký.
4. Không xóa tính năng/luồng đã được chốt khi chưa có yêu cầu rõ ràng.
5. Tách rõ: đã triển khai trong mã nguồn, đã qua CI, đã thử trên thiết bị thật và đã nghiệm thu. Không đánh đồng các trạng thái này.
6. Ưu tiên thao tác và hướng dẫn có thể thực hiện trên điện thoại; cung cấp đúng việc cần người dùng làm, không yêu cầu gửi bí mật qua chat.
7. Chỉ build APK khi nhận đúng lệnh viết hoa chính xác: BUILD APK. Sửa tài liệu/source check không tự động đồng nghĩa yêu cầu build.

## 2. Sản phẩm, phân phối và nền tảng

- AUTU-LIO là ứng dụng Android native Kotlin/Jetpack Compose, một APK duy nhất, application ID com.autu.lio.
- Không chuyển sản phẩm chính thành web app, web editor hoặc web dashboard.
- Website công khai chỉ phục vụ giới thiệu, tải APK, chính sách quyền riêng tư và điều khoản; AUTU Script được soạn và chạy trong ứng dụng Android.
- Giai đoạn hiện tại không phát hành trên Google Play. Phân phối bằng APK Release đã ký qua GitHub Releases/website tải ứng dụng.
- Có yêu cầu xem trước giao diện nhanh mà không cần build APK mỗi lần; ưu tiên preview web hoặc preview kỹ thuật khi khả thi. Preview chỉ giúp xem giao diện, không được trình bày như bằng chứng runtime Android, Accessibility Service, overlay hoặc chức năng nền đã hoạt động.
- Bản Release ký ổn định mới được phân phối. APK Debug chỉ dành cho nội bộ thử nghiệm và không dùng cập nhật đè Release.
- Cần kiểm tra cập nhật khi khởi động hoặc khi người dùng chủ động chọn kiểm tra; giữ cơ chế công bố phiên bản trên backend/website đã thiết kế.

## 3. Đăng nhập, phân quyền và kiểm soát truy cập

- Sử dụng Google Credential Manager để xác thực; Cloudflare Worker xác minh Google ID token, session và quyền. Không dùng Firebase.
- Chỉ có hai vai trò vận hành trong phạm vi hiện tại: OWNER và CUSTOMER.
- OWNER được xác định bằng OpenID Connect subject đã xác minh ở phía máy chủ qua OWNER_GOOGLE_SUB; không tự cấp OWNER dựa vào email nhập tay hoặc bộ chọn vai trò trong bản Release.
- Mọi tài khoản Google khác là CUSTOMER trừ khi máy chủ có chính sách khác được chủ dự án phê duyệt.
- CUSTOMER phải có license hợp lệ và thiết bị đã đăng ký/ACTIVE mới được tải và chạy AUTU được cấp.
- Chủ sở hữu cần có khả năng quản trị khách hàng, license, thiết bị, package và thu hồi quyền truy cập trực tuyến.
- Khi license/thiết bị/package bị thu hồi hoặc session không hợp lệ, runner phải khóa/dừng theo quy tắc an toàn; kiểm tra quyền ở server, không tin cờ quyền từ client.
- Không cấp quyền quản trị cho CUSTOMER. Không đưa lại Developer/Designer/Reviewer, lời mời cộng tác viên, shared workspace hoặc quy trình review đã bị loại khỏi phạm vi.
- Đăng xuất/phiên bị từ chối phải dừng quyền chạy; xử lý khối LUÔN_THỰC_HIỆN theo đặc tả trước khi xóa trạng thái runtime.

## 4. AUTU Script và runtime Android

- AUTU Script phải có parser/compiler, kiểm tra cú pháp, thông báo lỗi vị trí dòng/cột và runtime thực thi thật.
- Tên lệnh, từ khóa và hàm dựng sẵn ưu tiên tiếng Việt viết HOA, đủ dấu và nhất quán với tài liệu ngôn ngữ.
- Cần hỗ trợ chỉnh sửa tham số, bản nháp, kiểm tra mã, hướng dẫn cú pháp và mẫu.
- Runtime phải có trạng thái chạy, tạm dừng, tiếp tục, dừng, hủy và lỗi; không để nút điều khiển chỉ thay đổi giao diện mà không ảnh hưởng tác vụ thật.
- Có cửa sổ điều khiển nổi và Accessibility Service Android để thực hiện thao tác được cấp quyền; phải giải thích và yêu cầu quyền hệ điều hành rõ ràng.
- Người dùng có quyền bắt đầu, tạm dừng, tiếp tục và dừng thao tác; có thể chỉnh thông số kịch bản đã được thiết kế cho phép.
- Chạy nền chỉ trong giới hạn Android/OEM và trạng thái service/quyền. Không hứa chạy liên tục khi Android force-stop, thu hồi quyền Trợ năng, tiết kiệm pin hoặc dừng tiến trình.
- Không tự khởi chạy hoặc thao tác ứng dụng đích khi người dùng chưa bật cấu hình rõ ràng; không vượt khóa bảo mật, né cơ chế bảo vệ, trích xuất credential hoặc giám sát ẩn.
- Retry phải opt-in, giới hạn số lần và có backoff; mặc định tắt khi việc chạy lại có thể lặp tác động một phần.
- Hàng đợi script, khôi phục sau khi service kết nối lại và hành vi không tự chạy lại một tác vụ đang dở phải theo docs/PROJECT-STATUS.md và docs/09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md.
- ML Kit OCR, QR/barcode, image labels và object detection chạy cục bộ trên thiết bị; không tự thêm Gemini hoặc AI tạo sinh, không tải ảnh/màn hình riêng tư lên máy chủ nếu người dùng không chủ động cấu hình.

## 5. Gói AUTU, bảo vệ và phân phối

- OWNER tạo/chỉnh sửa script trong ứng dụng, kiểm tra cú pháp/phiên bản rồi mới phát hành.
- Bản nháp cục bộ được mã hóa bằng AES-GCM với khóa Android Keystore.
- Package phát hành mã hóa AES-256-GCM; khóa dữ liệu được quản lý ở Worker, bọc riêng theo public key RSA của thiết bị CUSTOMER. Không nhúng khóa giải mã chung/master key vào APK.
- Server kiểm tra tài khoản, license, trạng thái thiết bị và phạm vi phân phối trước khi trả package.
- OWNER có thể xem mã gốc qua API quản trị có kiểm tra quyền và audit; CUSTOMER chỉ nhận package đã được cấp phép.
- Package/version đã phát hành là bất biến; cập nhật tạo version mới. Việc thu hồi chặn lần tải/kiểm tra tiếp theo nhưng không thể xóa bản đã tải hoặc plaintext đã trích xuất từ thiết bị đã bị kiểm soát.
- Phải trình bày trung thực: mã hóa khi lưu trữ/phân phối không phải mã hóa đầu-cuối; khi chạy, source/AST cần giải mã trong bộ nhớ và có thể bị trích xuất trên thiết bị root/debug/instrument.

## 6. Giao diện và trải nghiệm

- Tối ưu điện thoại: bố cục gọn, chữ dễ đọc, vùng chạm đủ lớn; hỗ trợ sáng/tối.
- Giữ bốn mục điều hướng OWNER: Tổng quan, Kịch bản, Khách hàng, Thêm; các công cụ con nhóm đúng chức năng.
- Giữ bốn mục CUSTOMER: Trang chủ, Kho AUTU, Thiết bị, Cài đặt.
- Không hiển thị tab/ trạng thái chạy giả nếu chức năng thật vẫn do cửa sổ nổi Accessibility Service điều khiển.
- Các trang tải dữ liệu cần trạng thái rõ: đang tải, rỗng, lỗi, thành công; lỗi tiếng Anh từ API/Android phải được ánh xạ sang tiếng Việt.
- Dữ liệu OWNER không xuất hiện trong giao diện CUSTOMER; không tạo quyền từ bộ chọn giao diện Debug.
- Cung cấp import/export script và sao lưu/khôi phục qua Android SAF theo phạm vi đã triển khai; không yêu cầu người dùng nhập đường dẫn tùy tiện hoặc cấp quyền tệp quá rộng.

## 7. Ngôn ngữ và nội dung

- Quy tắc hiện tại trong repository: giao diện do AUTU-LIO sở hữu luôn dùng tiếng Việt, kể cả OWNER, CUSTOMER, thông báo, lỗi, cửa sổ nổi và Trợ năng.
- Giữ nguyên cú pháp AUTU Script, tên API/JSON/HTTP, mã giao thức, định dạng tệp và thuật ngữ kỹ thuật không có bản dịch an toàn; thêm giải thích tiếng Việt khi cần.
- Không để lỗi tiếng Anh từ backend hoặc Android hiển thị nguyên văn trong giao diện.
- Khi thêm màn hình/tính năng, bổ sung chuỗi tiếng Việt ngay trong cùng thay đổi.

## 8. Ý tưởng bổ sung đã nêu — chưa được coi là cam kết đã triển khai

- Có nút mở ChatGPT với câu hỏi/prompt được điền sẵn từ ngữ cảnh của ứng dụng.
- Trước mắt có thể mở ứng dụng/trang ChatGPT bằng liên kết hoặc Intent với prompt; không mặc định tích hợp OpenAI API hay phát sinh chi phí API.
- Nếu có tích hợp AI trực tiếp về sau, phải nêu rõ chi phí, giới hạn, quyền dữ liệu và việc gửi nội dung lên dịch vụ bên ngoài; không tự gửi script hoặc dữ liệu nhạy cảm.

## 9. Khóa ký, workflow và phát hành APK

- AUTU-LIO, COC-LIO và GPS-LIO sử dụng cùng keystore AILIO đã thống nhất. Không tạo khóa mới hoặc thay chứng thư trong quá trình cập nhật thông thường.
- Bốn GitHub Repository Secrets phải có riêng trong từng repository: AILIO_KEYSTORE_BASE64, AILIO_KEYSTORE_PASSWORD, AILIO_KEY_ALIAS, AILIO_KEY_PASSWORD.
- Không bao giờ ghi giá trị secret, file .p12/.jks hoặc mật khẩu vào source, commit, issue, log hoặc chat.
- GitHub Actions chỉ tạo APK phát hành khi chủ dự án gửi đúng lệnh BUILD APK; build Release phải xác minh chữ ký, tăng versionCode, tải artifact và công bố GitHub Release nếu toàn bộ bước thành công.
- Không dùng Debug APK làm bản nâng cấp chính thức; cài đè Release phải cùng application ID và chữ ký tương thích.
- Không nói build thành công trước khi run có conclusion=success, artifact tồn tại và chữ ký được xác minh.

- Backend security: every authenticated request is rate limited by the verified account ID. Package download/source and broad listing endpoints consume higher weights. Cloudflare rate-limit counters are approximate per location, not strict global counters.
- Server sessions have a 90-day absolute lifetime and a 30-day idle timeout. The Worker enforces both; local token storage does not extend server authorization.

## 10. Các hạng mục phải nghiệm thu, không được báo hoàn thành sớm

Theo trạng thái repository, các hạng mục cần tiếp tục kiểm tra trên thiết bị thật gồm:
- Luồng CUSTOMER end-to-end: đăng nhập, license, đăng ký thiết bị, tải package, giải mã, kiểm tra hash và chạy.
- Quản trị cấp/thu hồi license, thiết bị, phạm vi phân phối và việc CUSTOMER bị từ chối tải trực tiếp khi không còn quyền.
- Accessibility Service, gesture, vòng đời service, overlay, chạy/tạm dừng/tiếp tục/dừng và dừng khẩn cấp.
- Hàng đợi, retry, backup/restore SQLite qua SAF, WAL, dung lượng thấp và rollback.
- Tương thích thiết bị Android thực tế, đặc biệt Xiaomi/HyperOS và các chính sách tiết kiệm pin/OEM.
- Các phần còn thiếu trong ma trận AUTU Script phải được đánh dấu chưa triển khai; không đưa vào quảng cáo như tính năng đã sẵn sàng.

## 11. Tài liệu tham chiếu phải đọc

- README.md
- docs/PROJECT-STATUS.md
- docs/GITHUB-WORKFLOW.md
- docs/09-AUTU-SCRIPT-COMPREHENSIVE-SCOPE.md
- docs/10-AUTU-PACKAGE-SECURITY.md
- docs/ONE-APP-ACCESS-CONTROL.md
- docs/SECURITY-AND-SIGNING.md
- docs/UI-DESIGN-PLAN.md
- docs/NGON_NGU_MAC_DINH_VI.md

**Quy tắc cập nhật sổ:** Yêu cầu mới phải được thêm vào đây và tài liệu kỹ thuật có liên quan. Khi hoàn thành, cập nhật trạng thái và bằng chứng kiểm thử; không đánh dấu hoàn thành chỉ vì có giao diện hoặc CI compile thành công.

## 12. Quyết định kiến trúc thông báo — dùng chung chuẩn không Firebase/FCM

Quyết định ngày 26/11/2026: AUTU-LIO dùng Cloudflare Workers + D1 + WorkManager + Android Local Notification làm phương án thông báo mặc định. Không yêu cầu Firebase hoặc FCM.

- Workers cung cấp API thông báo; D1 lưu thông báo, trạng thái đã nhận/đã đọc và phạm vi tài khoản/thiết bị khi cần.
- WorkManager kiểm tra API khi có mạng; Android NotificationManager/NotificationCompat hiển thị thông báo. Dùng notification channel phù hợp, quyền POST_NOTIFICATIONS và cơ chế chống trùng theo notification ID.
- Công việc định kỳ WorkManager có chu kỳ tối thiểu 15 phút; Android Doze, tiết kiệm pin và chính sách OEM có thể trì hoãn thêm. Không quảng cáo đây là push tức thời hoặc bảo đảm thông báo đúng phút.
- Payload thông báo chỉ chứa metadata tối thiểu. Không đưa mã AUTU Script, nội dung package, khóa, token, dữ liệu đăng nhập hoặc bí mật vào notification payload hay log.
- Thông báo thay đổi license, thiết bị, package và cập nhật chỉ mang tính thông tin. Thu hồi quyền phải được kiểm tra và thực thi ở Worker/API trong mọi thao tác nhạy cảm; không dựa vào việc thiết bị đã nhận thông báo để bảo vệ quyền truy cập.
- Không tự động bắt đầu/chạy lại runtime khi nhận thông báo. Người dùng phải chủ động mở app và thực hiện thao tác được phép.
- Không thêm Firebase/FCM, OneSignal, Pushy, ntfy hoặc dịch vụ push bên ngoài cho kiến trúc mặc định. Chỉ xem xét nhà cung cấp push khác nếu chủ dự án yêu cầu riêng.

## 13. Baseline bảo mật cho repository Public

Quyết định bảo mật ngày 26/11/2026:

- GitHub là nguồn mã chính; Cloudflare là môi trường chạy Worker, lưu D1 và bí mật. Không sửa nóng Production làm lệch repository.
- Pull Request và workflow kiểm tra PR chỉ được dùng quyền tối thiểu contents: read; tuyệt đối không nhận Cloudflare deployment token, OWNER secret, package master key, GitHub release token riêng hoặc signing secrets AILIO.
- Workflow PR không được giải mã keystore, ký Release APK hoặc tải artifact đã ký. Chỉ workflow phát hành trên main sau khi chủ dự án chấp thuận và phát lệnh BUILD APK mới được dùng signing secrets.
- Cloudflare API Token triển khai AUTU-LIO phải tách riêng, giới hạn Account AUTU-LIO và đúng quyền Workers Scripts/D1 cần thiết. Không dùng token quản trị toàn tài khoản hoặc chia sẻ với hai ứng dụng khác.
- Worker Secrets lưu OWNER_GOOGLE_SUB, PACKAGE_MASTER_KEY_V1 và secret vận hành. vars chỉ chứa cấu hình công khai. Không nhúng khóa mã hóa hoặc quyền OWNER vào APK.
- GitHub token trong Worker cập nhật APK chỉ được cấp fine-grained quyền đọc Contents/Metadata của repository AUTU-LIO; không cấp write, administration, workflow hoặc quyền ở repository khác nếu không cần.
- Bảo vệ nhánh main bằng branch protection/ruleset: giới hạn người ghi, bắt buộc PR review/status checks, chặn force-push và xóa nhánh. Phải kiểm tra cấu hình trực tiếp trong GitHub Settings vì kết nối hiện tại không có quyền đọc/chỉnh branch protection.
- Bật Secret scanning, Push protection, Dependabot alerts và security updates nếu gói/tính năng cho phép. Nếu phát hiện secret từng bị commit thì thu hồi/rotate trước, sau đó mới làm sạch lịch sử.
- Worker phải xác minh tài khoản, license, trạng thái thiết bị và quyền gói ở phía server cho mọi thao tác nhạy cảm. Thông báo chỉ là thông tin, không phải cơ chế cấp/thu hồi quyền.
- Không đưa dữ liệu khách hàng, script chưa công bố, package plaintext, private key hoặc token vào log/issue/commit. Không tạo endpoint chẩn đoán trả secret hoặc dump toàn bộ environment.
- Không tự xoay/xóa secret đang chạy khi chưa đánh giá ảnh hưởng và chuẩn bị rollback.
