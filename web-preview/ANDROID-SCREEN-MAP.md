# AUTU-LIO Android screen map

**Đối chiếu với mã nguồn:** nhánh `main` của repository `HUY-HOA-1/AUTU-LIO`.

## Luồng ứng dụng

| Luồng | Điểm vào | Trạng thái |
|---|---|---|
| Release | `auth/AuthenticatedAppHost.kt`, `auth/AuthRepository.kt` | Google Sign-In và khôi phục phiên đã có mã; cần cấu hình OAuth và Worker để đăng nhập thật |
| Debug | `MainActivity.kt`, `RolePreviewChooser` | Bộ chọn 5 vai trò chỉ để xem giao diện, không xác minh quyền |

## Màn hình theo vai trò

| Vai trò | Màn hình | Mã Android |
|---|---|---|
| Chủ sở hữu | Tổng quan | `MainActivity.kt`, `OverviewScreen` |
| Chủ sở hữu | AUTU / danh sách kịch bản, tìm kiếm, lọc, tạo và mở bản nháp | `MainActivity.kt`, `ScriptsScreen` |
| Chủ sở hữu | Trình soạn thảo, thư viện mẫu, kiểm tra mã, lưu và chuẩn bị chạy | `MainActivity.kt`, `ScriptEditorActivity`, `script/ScriptCompiler.kt` |
| Chủ sở hữu | Khách hàng, thiết bị, giấy phép, gói AUTU | `LiveManagementScreens.kt` |
| Chủ sở hữu | Cộng tác viên | `CollaboratorsScreen.kt`, `CollaboratorsServerScreen.kt` |
| Chủ sở hữu | Dự án cộng tác | `SharedProjectScreens.kt` |
| Chủ sở hữu | Thư viện mẫu | `AdminTemplateLibraryScreen.kt` |
| Chủ sở hữu | Phát hành, nhật ký, cài đặt | `MainActivity.kt`, `AdminAuditLogsScreen.kt` |
| Nhà phát triển | Không gian riêng, bản nháp mã hóa, editor riêng | `CollaboratorWorkspace.kt`, `SecureScriptDraftStore.kt`, `MainActivity.kt` |
| Nhà thiết kế | Không gian thiết kế và xem trước UI | `CollaboratorWorkspace.kt` |
| Người kiểm duyệt | Hàng đợi và thao tác duyệt/từ chối | `CollaboratorWorkspace.kt`, `SharedProjectScreens.kt` |
| Các cộng tác viên | Dự án được giao và bản nháp dùng chung theo revision | `SharedProjectScreens.kt` |
| Người dùng | Trang chủ và trạng thái giấy phép | `MainActivity.kt`, `CustomerHome` |
| Người dùng | Kho AUTU đã phát hành | `MainActivity.kt`, `CustomerPackagesScreen` |
| Người dùng | Thiết bị và giấy phép; đăng ký/gỡ thiết bị | `LiveManagementScreens.kt`, `SecureDeviceIdentityStore.kt` |
| Người dùng | Cài đặt, nhập mã lời mời, đăng xuất | `MainActivity.kt`, `SettingsScreen` |

## Nền tảng thực thi

| Thành phần | Mã nguồn | Trạng thái cần hiểu đúng |
|---|---|---|
| Parser/compiler | `script/ScriptCompiler.kt` | Có parser/AST, chẩn đoán cú pháp và giới hạn chương trình |
| Interpreter | `runtime/ScriptExecutionEngine.kt` | Có vòng đời chạy, tạm dừng, tiếp tục, dừng và xử lý lỗi |
| Android gestures / floating runner | `accessibility/AutoClickAccessibilityService.kt` | Có AccessibilityService, dispatcher cử chỉ và cửa sổ điều khiển nổi; cần cấp quyền và kiểm thử trên thiết bị thật |
| Mã hóa bản nháp | `SecureScriptDraftStore.kt` | Lưu bản nháp riêng bằng Android Keystore |
| Xác thực / API | `auth/AuthenticatedAppHost.kt`, Worker source | Cần cấu hình OAuth, Worker URL và secrets; không được coi giao diện là đăng nhập thật |

## Quy tắc đối chiếu Web

- Web có trang **Đối chiếu Android** để tra danh sách màn hình, vai trò và tệp Kotlin tương ứng.
- Web không đọc kho dữ liệu Android, không đồng bộ bản nháp Android, không gọi compiler/interpreter Kotlin và không thực hiện thao tác chạm/vuốt trên ứng dụng khác.
- Các nút mô phỏng trên Web chỉ thay đổi trạng thái trong trình duyệt; không được hiểu là kiểm thử chức năng Android.
- Khi thêm, xóa hoặc đổi màn hình Android, phải cập nhật bảng này và giao diện Web trong cùng đợt thay đổi.
- Chỉ đánh dấu một tính năng là hoạt động thực tế sau khi đã có kiểm thử phù hợp; riêng Accessibility/overlay cần kiểm thử trên điện thoại thật.
