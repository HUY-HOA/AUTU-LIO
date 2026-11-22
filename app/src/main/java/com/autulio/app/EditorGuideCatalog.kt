package com.autulio.app

data class EditorGuideCommand(
    val syntax: String,
    val description: String,
    val example: String? = null,
    val minAndroidApi: Int = 26,
    val androidNote: String? = null
) {
    val androidRequirement: String
        get() = when (minAndroidApi) {
            26 -> "Android 8.0 (API 26) trở lên"
            28 -> "Android 9.0 (API 28) trở lên"
            30 -> "Android 11.0 (API 30) trở lên"
            else -> "Android API $minAndroidApi trở lên"
        }
}

data class EditorGuideSection(
    val id: String,
    val title: String,
    val summary: String,
    val commands: List<EditorGuideCommand>,
    val notes: List<String> = emptyList()
)

/**
 * Command reference organized by what each AUTU feature does.
 * Keep entries aligned with ScriptCompiler's registered commands/functions.
 */
object EditorGuideCatalog {
    val sections = listOf(
        EditorGuideSection(
            id = "gestures",
            title = "1. Thao tác chạm và vuốt",
            summary = "Nhóm thao tác trực tiếp trên màn hình: chạm, nhấn giữ, vuốt, đa điểm và chờ.",
            commands = listOf(
                EditorGuideCommand("NHẤN(x, y)", "Chạm một lần tại tọa độ X/Y.", "NHẤN(200, 400)"),
                EditorGuideCommand("NHẤN_GIỮ(x, y, ms)", "Nhấn giữ tại tọa độ trong thời gian đã đặt.", "NHẤN_GIỮ(200, 400, 800)"),
                EditorGuideCommand("VUỐT(x1, y1, x2, y2, ms)", "Vuốt từ điểm bắt đầu đến điểm kết thúc.", "VUỐT(360, 850, 360, 350, 450)"),
                EditorGuideCommand("NHẤN_ĐA_ĐIỂM(CÁC_ĐIỂM, ms)", "Gửi thao tác chạm đồng thời từ 2 đến 5 điểm."),
                EditorGuideCommand("VUỐT_ĐA_ĐIỂM(CÁC_ĐƯỜNG, ms)", "Gửi nhiều đường vuốt đồng thời, tối đa 5 ngón."),
                EditorGuideCommand("CHỜ(ms)", "Tạm dừng kịch bản trong số mili giây đã đặt.", "CHỜ(500)")
            ),
            notes = listOf(
                "Tọa độ thao tác tính bằng pixel trên toàn màn hình.",
                "Thời lượng và số điểm có giới hạn để tránh thao tác kéo dài hoặc vượt khả năng Android."
            )
        ),
        EditorGuideSection(
            id = "system",
            title = "2. Lệnh hệ thống Android",
            summary = "Điều khiển điều hướng và một số bảng hệ thống Android. Các thao tác phụ thuộc phiên bản Android và chính sách thiết bị.",
            commands = listOf(
                EditorGuideCommand("MỞ_ỨNG_DỤNG(\"tên.gói\")", "Mở ứng dụng đã cài đặt theo tên gói Android.", "MỞ_ỨNG_DỤNG(\"com.example.game\")"),
                EditorGuideCommand("QUAY_LẠI()", "Gửi thao tác Quay lại của Android."),
                EditorGuideCommand("VỀ_MÀN_HÌNH_CHÍNH()", "Chuyển về màn hình chính Android."),
                EditorGuideCommand("MỞ_ỨNG_DỤNG_GẦN_ĐÂY()", "Mở màn hình ứng dụng gần đây."),
                EditorGuideCommand("MỞ_THANH_THÔNG_BÁO()", "Mở ngăn thông báo."),
                EditorGuideCommand("MỞ_BẢNG_CÀI_ĐẶT_NHANH()", "Mở bảng cài đặt nhanh."),
                EditorGuideCommand("MỞ_HỘP_THOẠI_NGUỒN()", "Mở hộp thoại nguồn nếu hệ thống cho phép."),
                EditorGuideCommand("KHÓA_MÀN_HÌNH()", "Yêu cầu Android khóa màn hình; cần quyền và phiên bản hệ thống phù hợp.", minAndroidApi = 28),
                EditorGuideCommand("BẬT_TẮT_CHIA_MÀN_HÌNH()", "Yêu cầu Android chuyển chế độ chia màn hình nếu thiết bị hỗ trợ.", minAndroidApi = 30)
            ),
            notes = listOf(
                "Một số lệnh hệ thống có thể bị Android, ROM của nhà sản xuất hoặc chính sách quản trị chặn.",
                "Lệnh không có tham số phải được gọi với cặp ngoặc rỗng ()."
            )
        ),
        EditorGuideSection(
            id = "accessibility",
            title = "3. Thành phần giao diện và Trợ năng",
            summary = "Tìm, nhấn, nhập văn bản hoặc cuộn các thành phần mà ứng dụng đang mở công khai qua cây Trợ năng Android.",
            commands = listOf(
                EditorGuideCommand("TÌM_THÀNH_PHẦN_VĂN_BẢN(\"nội dung\")", "Tìm thành phần theo nhãn hoặc văn bản hiển thị."),
                EditorGuideCommand("NHẤN_THÀNH_PHẦN_VĂN_BẢN(\"nội dung\")", "Nhấn thành phần tìm được theo văn bản."),
                EditorGuideCommand("NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN(\"nhãn\", \"nội dung\")", "Nhập văn bản vào trường tìm được theo nhãn."),
                EditorGuideCommand("TÌM_TỌA_ĐỘ_THÀNH_PHẦN(\"nội dung\")", "Tìm tọa độ thành phần giao diện theo văn bản."),
                EditorGuideCommand("TÌM_THÀNH_PHẦN_THEO_ID(\"resource_id\")", "Tìm thành phần theo resource ID."),
                EditorGuideCommand("NHẤN_THÀNH_PHẦN_THEO_ID(\"resource_id\")", "Nhấn thành phần theo resource ID."),
                EditorGuideCommand("NHẬP_VĂN_BẢN_THEO_ID(\"resource_id\", \"nội dung\")", "Nhập văn bản vào thành phần theo resource ID."),
                EditorGuideCommand("CUỘN_XUỐNG() / CUỘN_LÊN()", "Cuộn thành phần có thể cuộn trong cửa sổ hiện tại.")
            ),
            notes = listOf(
                "Một số ứng dụng không công khai cây Trợ năng hoặc giới hạn nội dung; khi đó không thể tìm thấy thành phần bằng nhóm lệnh này.",
                "Nếu ứng dụng không cung cấp dữ liệu Trợ năng, có thể cân nhắc nhóm OCR hoặc thao tác tọa độ."
            )
        ),
        EditorGuideSection(
            id = "ocr",
            title = "4. OCR và tìm chữ trên màn hình",
            summary = "Đọc văn bản trong ảnh chụp màn hình, kiểm tra chữ có xuất hiện hay tìm vị trí chữ theo toàn màn hình hoặc một vùng.",
            commands = listOf(
                EditorGuideCommand("ĐỌC_VĂN_BẢN_MÀN_HÌNH()", "Đọc văn bản OCR nhận diện được trên màn hình.", "BIẾN NOI_DUNG: CHUỖI = ĐỌC_VĂN_BẢN_MÀN_HÌNH()", minAndroidApi = 30),
                EditorGuideCommand("ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()", "Đọc kết quả OCR có cấu trúc và thông tin vị trí.", minAndroidApi = 30),
                EditorGuideCommand("KIỂM_TRA_CHỮ_TRÊN_MÀN_HÌNH(\"nội dung\")", "Kiểm tra một đoạn chữ có xuất hiện trên màn hình không.", minAndroidApi = 26, androidNote = "OCR trực tiếp từ ảnh màn hình cần Android 11.0 (API 30) trở lên; Android 8–10 chỉ tìm trong cây Trợ năng."),
                EditorGuideCommand("TÌM_VỊ_TRÍ_TRONG_CHUỖI_CHỮ(\"nội dung\")", "Tìm vị trí của đoạn chữ được OCR nhận diện.", minAndroidApi = 26, androidNote = "OCR trực tiếp từ ảnh màn hình cần Android 11.0 (API 30) trở lên; Android 8–10 chỉ tìm trong cây Trợ năng."),
                EditorGuideCommand("ĐỌC_VĂN_BẢN_TRONG_VÙNG_MÀN_HÌNH(x, y, rộng, cao)", "Chỉ đọc OCR bên trong vùng màn hình đã chọn.", minAndroidApi = 30),
                EditorGuideCommand("KIỂM_TRA_CHỮ_TRONG_VÙNG_MÀN_HÌNH(\"chữ\", x, y, rộng, cao)", "Kiểm tra chữ trong một vùng giới hạn.", minAndroidApi = 30),
                EditorGuideCommand("TÌM_VỊ_TRÍ_TRONG_CHUỖI_CHỮ_TRONG_VÙNG_MÀN_HÌNH(\"chữ\", x, y, rộng, cao)", "Tìm vị trí chữ chỉ trong vùng giới hạn.", minAndroidApi = 30)
            ),
            notes = listOf(
                "OCR phụ thuộc quyền chụp màn hình, chất lượng ảnh, cỡ chữ và độ tương phản.",
                "Kết quả OCR là dữ liệu nhận diện, không bảo đảm chính xác tuyệt đối."
            )
        ),
        EditorGuideSection(
            id = "vision",
            title = "5. Nhận diện hình ảnh, QR và vật thể",
            summary = "Nhận diện nhãn hình ảnh, mã QR, mã vạch và vật thể trên màn hình.",
            commands = listOf(
                EditorGuideCommand("NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()", "Trả về các nhãn mô tả nội dung hình ảnh.", minAndroidApi = 30),
                EditorGuideCommand("KIỂM_TRA_NHÃN_ẢNH_TRÊN_MÀN_HÌNH(\"nhãn\")", "Kiểm tra nhãn hình ảnh có trong kết quả nhận diện.", minAndroidApi = 30),
                EditorGuideCommand("CHI_TIẾT_MÃ_QR_MÀN_HÌNH()", "Đọc thông tin chi tiết của mã QR nhận diện được.", minAndroidApi = 30),
                EditorGuideCommand("CHI_TIẾT_MÃ_VẠCH_MÀN_HÌNH()", "Đọc thông tin chi tiết của mã vạch nhận diện được.", minAndroidApi = 30),
                EditorGuideCommand("QUÉT_MÃ_QR_MÀN_HÌNH()", "Quét mã QR đang hiển thị trên màn hình.", minAndroidApi = 30),
                EditorGuideCommand("QUÉT_MÃ_VẠCH_MÀN_HÌNH()", "Quét mã vạch đang hiển thị trên màn hình.", minAndroidApi = 30),
                EditorGuideCommand("PHÁT_HIỆN_VẬT_THỂ_MÀN_HÌNH()", "Phát hiện các vật thể mà mô hình nhận diện hỗ trợ.", minAndroidApi = 30),
                EditorGuideCommand("KIỂM_TRA_VẬT_THỂ_TRÊN_MÀN_HÌNH(\"nhãn\")", "Kiểm tra một loại vật thể có xuất hiện không.", minAndroidApi = 30),
                EditorGuideCommand("TÌM_TỌA_ĐỘ_VẬT_THỂ(\"nhãn\")", "Tìm vị trí của vật thể theo nhãn.", minAndroidApi = 30),
                EditorGuideCommand("ĐẾM_VẬT_THỂ_MÀN_HÌNH()", "Đếm số vật thể nhận diện được.", minAndroidApi = 30)
            ),
            notes = listOf(
                "Nhận diện phụ thuộc quyền chụp màn hình và khả năng mô hình trên thiết bị.",
                "Tên nhãn trả về phụ thuộc mô hình; nên kiểm tra kết quả thực tế trước khi dùng làm điều kiện."
            )
        ),
        EditorGuideSection(
            id = "color",
            title = "6. Phân tích màu sắc",
            summary = "Đọc màu pixel, tìm vùng có màu gần đúng và ước lượng tỷ lệ màu trong vùng.",
            commands = listOf(
                EditorGuideCommand("LẤY_MÀU_TẠI_TỌA_ĐỘ_MÀN_HÌNH(x, y)", "Đọc màu tại một điểm trên màn hình.", "BIẾN MAU: BẢN_ĐỒ = LẤY_MÀU_TẠI_TỌA_ĐỘ_MÀN_HÌNH(200, 400)", minAndroidApi = 30),
                EditorGuideCommand("KIỂM_TRA_MÀU_TRÊN_MÀN_HÌNH(đỏ, xanh_lá, xanh_dương, sai_số)", "Kiểm tra màu mục tiêu có xuất hiện trong màn hình.", minAndroidApi = 30),
                EditorGuideCommand("TÌM_VỊ_TRÍ_TRONG_CHUỖI_MÀU_TRÊN_MÀN_HÌNH(đỏ, xanh_lá, xanh_dương, sai_số)", "Tìm tọa độ có màu gần giá trị RGB mục tiêu.", minAndroidApi = 30),
                EditorGuideCommand("TÍNH_TỶ_LỆ_MÀU_TRONG_VÙNG_MÀN_HÌNH(đỏ, xanh_lá, xanh_dương, sai_số, x, y, rộng, cao)", "Ước lượng tỷ lệ pixel gần màu mục tiêu trong vùng.", minAndroidApi = 30)
            ),
            notes = listOf(
                "Các thành phần màu RGB và sai số phải nằm trong phạm vi mà hàm quy định.",
                "Kết quả là phép so sánh pixel gần đúng, không phải phép đo màu chuyên dụng."
            )
        ),
        EditorGuideSection(
            id = "device",
            title = "7. Thông tin thiết bị và trạng thái hệ thống",
            summary = "Lấy thông tin pin, mạng, ứng dụng, phiên bản Android và trạng thái khóa màn hình.",
            commands = listOf(
                EditorGuideCommand("LẤY_PHẦN_TRĂM_PIN() / LẤY_PHẦN_TRĂM_LẤY_PHẦN_TRĂM_PIN()", "Đọc mức pin hoặc phần trăm pin còn lại."),
                EditorGuideCommand("KIỂM_TRA_ĐANG_SẠC()", "Kiểm tra thiết bị có đang sạc không."),
                EditorGuideCommand("KIỂM_TRA_KẾT_NỐI_MẠNG()", "Kiểm tra trạng thái kết nối mạng theo dữ liệu thiết bị."),
                EditorGuideCommand("KIỂM_TRA_MÀN_HÌNH_ĐANG_SÁNG()", "Kiểm tra màn hình đang bật hay tắt."),
                EditorGuideCommand("KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI(\"tên.gói\")", "Kiểm tra ứng dụng theo tên gói đã cài trên thiết bị."),
                EditorGuideCommand("KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ(\"tên.gói\")", "Kiểm tra ứng dụng theo tên gói có đang mở hay không."),
                EditorGuideCommand("GÓI_KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ()", "Lấy tên gói của ứng dụng đang ở foreground."),
                EditorGuideCommand("HÃNG_SẢN_XUẤT() / MẪU_THIẾT_BỊ()", "Đọc hãng sản xuất và model thiết bị."),
                EditorGuideCommand("PHIÊN_BẢN_ANDROID() / MỨC_API_ANDROID()", "Đọc phiên bản Android và mức API."),
                EditorGuideCommand("MÀN_HÌNH_BỊ_KHÓA()", "Kiểm tra trạng thái khóa màn hình."),
                EditorGuideCommand("THIẾT_BỊ_ĐƯỢC_BẢO_MẬT()", "Kiểm tra thiết bị có cấu hình bảo mật khóa màn hình."),
                EditorGuideCommand("THỜI_GIAN_MS()", "Lấy thời gian hiện tại dạng mili giây."),
                EditorGuideCommand("LẤY_ÂM_LƯỢNG() / LẤY_ĐỘ_SÁNG()", "Đọc âm lượng media và độ sáng màn hình hiện tại."),
                EditorGuideCommand("ĐẶT_LẤY_ÂM_LƯỢNG(phần_trăm)", "Đặt âm lượng media từ 0 đến 100%."),
                EditorGuideCommand("ĐẶT_LẤY_ĐỘ_SÁNG(phần_trăm)", "Đặt độ sáng từ 0 đến 100%; cần cấp quyền sửa đổi cài đặt hệ thống."),
                EditorGuideCommand("RUNG(mili_giây)", "Rung thiết bị từ 1 đến 5.000 ms nếu thiết bị có bộ rung.")
            ),
            notes = listOf(
                "Trên Android 11.0 (API 30) trở lên, quy tắc package visibility có thể giới hạn các gói mà KIỂM_TRA_ỨNG_DỤNG_ĐÃ_CÀI() truy vấn được.",
                "ĐẶT_LẤY_ĐỘ_SÁNG() cần quyền 'Cho phép sửa đổi cài đặt hệ thống'; quyền này không được tự cấp bởi AUTU-LIO.",
                "Trạng thái mạng không thay thế việc kiểm tra phản hồi thực tế từ máy chủ."
            )
        ),
        EditorGuideSection(
            id = "display",
            title = "8. Màn hình, tọa độ và vùng an toàn",
            summary = "Đọc kích thước, mật độ, hướng xoay và quy đổi tọa độ để kịch bản thích ứng với nhiều màn hình.",
            commands = listOf(
                EditorGuideCommand("CHIỀU_RỘNG_MÀN_HÌNH() / CHIỀU_CAO_MÀN_HÌNH()", "Lấy kích thước toàn màn hình theo pixel."),
                EditorGuideCommand("CHIỀU_RỘNG_DP() / CHIỀU_CAO_DP()", "Lấy kích thước màn hình theo đơn vị DP."),
                EditorGuideCommand("MẬT_ĐỘ_MÀN_HÌNH() / TỶ_LỆ_KHUNG_HÌNH()", "Đọc mật độ điểm ảnh và tỷ lệ khung hình."),
                EditorGuideCommand("HƯỚNG_MÀN_HÌNH() / XOAY_MÀN_HÌNH() / GÓC_XOAY_MÀN_HÌNH()", "Đọc hướng và góc xoay hiện tại."),
                EditorGuideCommand("DP_SANG_PIXEL(dp) / PIXEL_SANG_DP(px)", "Chuyển đổi giữa DP và pixel."),
                EditorGuideCommand("TỌA_ĐỘ_X_THEO_TỶ_LỆ(tỷ_lệ) / TỌA_ĐỘ_Y_THEO_TỶ_LỆ(tỷ_lệ)", "Tính tọa độ pixel từ tỷ lệ màn hình."),
                EditorGuideCommand("TỶ_LỆ_X_THEO_TỌA_ĐỘ(x) / TỶ_LỆ_Y_THEO_TỌA_ĐỘ(y)", "Đổi tọa độ pixel thành tỷ lệ màn hình."),
                EditorGuideCommand("TỌA_ĐỘ_X_VÙNG_AN_TOÀN(tỷ_lệ) / TỌA_ĐỘ_Y_VÙNG_AN_TOÀN(tỷ_lệ)", "Tính tọa độ bên trong vùng thao tác an toàn."),
                EditorGuideCommand("TỶ_LỆ_X_VÙNG_AN_TOÀN(x) / TỶ_LỆ_Y_VÙNG_AN_TOÀN(y)", "Đổi tọa độ vùng an toàn thành tỷ lệ."),
                EditorGuideCommand("TỌA_ĐỘ_TỪ_HƯỚNG_GỐC(x, y) / TỌA_ĐỘ_VỀ_HƯỚNG_GỐC(x, y)", "Quy đổi tọa độ theo hướng màn hình gốc."),
                EditorGuideCommand("CHIỀU_RỘNG_VÙNG_AN_TOÀN() / CHIỀU_CAO_VÙNG_AN_TOÀN()", "Lấy kích thước vùng an toàn sau khi trừ lề hệ thống và vùng khuyết."),
                EditorGuideCommand("LỀ_AN_TOÀN_TRÁI() / LỀ_AN_TOÀN_TRÊN() / LỀ_AN_TOÀN_PHẢI() / LỀ_AN_TOÀN_DƯỚI()", "Lấy các lề an toàn theo pixel."),
                EditorGuideCommand("CÓ_THỂ_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH()", "Kiểm tra Android có cung cấp dữ liệu để xác định vùng khuyết; Android 8 có thể trả về SAI.", minAndroidApi = 28),
                EditorGuideCommand("CÓ_VÙNG_KHUYẾT_MÀN_HÌNH() / SỐ_VÙNG_KHUYẾT_MÀN_HÌNH()", "Kiểm tra có vùng khuyết và lấy số lượng vùng khuyết.", minAndroidApi = 28),
                EditorGuideCommand("TỌA_ĐỘ_VÙNG_KHUYẾT_MÀN_HÌNH()", "Lấy các hình chữ nhật vùng khuyết theo thứ tự trái, trên, phải, dưới.", minAndroidApi = 28),
            ),
            notes = listOf(
                "Tọa độ thao tác tính bằng pixel; nên dùng hàm quy đổi khi cần hỗ trợ nhiều kích thước màn hình.",
                "Vùng an toàn giúp tránh lề hệ thống và các vùng khuyết được Android cung cấp.",
                "Dữ liệu vùng khuyết (DisplayCutout) cần Android 9.0 (API 28) trở lên; trên Android cũ, hàm xác định vùng khuyết có thể trả về SAI.",
            )
        ),
        EditorGuideSection(
            id = "logic",
            title = "9. Cấu trúc điều khiển kịch bản",
            summary = "Tạo biến, điều kiện, vòng lặp, hàm tự định nghĩa và nhánh xử lý lỗi.",
            commands = listOf(
                EditorGuideCommand("BIẾN TÊN: KIỂU = GIÁ_TRỊ", "Khai báo biến mới.", "BIẾN SO_LAN: SỐ = 3"),
                EditorGuideCommand("NẾU (điều_kiện) { ... } NGƯỢC_LẠI { ... }", "Rẽ nhánh theo điều kiện."),
                EditorGuideCommand("LẶP(số_lần) { ... }", "Lặp lại khối lệnh theo số lần."),
                EditorGuideCommand("LẶP_KHI (điều_kiện) { ... }", "Lặp khi điều kiện còn đúng."),
                EditorGuideCommand("LẶP_ĐẾN_KHI(điều_kiện) { ... }", "Lặp cho đến khi điều kiện dừng đúng."),
                EditorGuideCommand("LẶP_MỖI (phần_tử TRONG danh_sách) { ... }", "Duyệt lần lượt các phần tử trong danh sách."),
                EditorGuideCommand("CHỜ_ĐẾN_KHI(điều_kiện, hết_hạn_ms, chu_kỳ_ms)", "Chờ điều kiện đúng, có thời hạn và chu kỳ kiểm tra."),
                EditorGuideCommand("HÀM TEN(A: SỐ): SỐ { ... }", "Định nghĩa hàm để tái sử dụng đoạn mã."),
                EditorGuideCommand("TRẢ_VỀ giá_trị", "Trả kết quả từ hàm."),
                EditorGuideCommand("THOÁT_LẶP / BỎ_QUA_LƯỢT", "Thoát vòng lặp hoặc bỏ qua lượt lặp hiện tại."),
                EditorGuideCommand("THỬ { ... } BẮT_LỖI (LOI) { ... } LUÔN_THỰC_HIỆN { ... }", "Bắt lỗi và chạy phần dọn dẹp nếu có.")
            ),
            notes = listOf(
                "Tên từ khóa, hàm, biến và kiểu dữ liệu phải viết HOA và đúng dấu tiếng Việt.",
                "Dùng BIẾN để khai báo biến và HÀM để định nghĩa hàm."
            )
        ),
        EditorGuideSection(
            id = "text_math",
            title = "10. Hàm số học và xử lý chuỗi",
            summary = "Các hàm dựng sẵn để tính toán, chuyển kiểu, tìm kiếm và chỉnh sửa văn bản.",
            commands = listOf(
                EditorGuideCommand("SỐ(giá_trị) / CHUỖI(giá_trị)", "Chuyển giá trị sang số hoặc chuỗi."),
                EditorGuideCommand("LÀM_TRÒN(số) / LÀM_TRÒN_XUỐNG(số) / LÀM_TRÒN_LÊN(số)", "Làm tròn gần nhất, xuống hoặc lên."),
                EditorGuideCommand("CĂN_BẬC_HAI(số) / TUYỆT_ĐỐI(số)", "Lấy căn bậc hai của số không âm hoặc giá trị tuyệt đối."),
                EditorGuideCommand("SIN(radian) / COS(radian) / TAN(radian)", "Tính lượng giác với góc đầu vào theo radian."),
                EditorGuideCommand("SỐ_NGẪU_NHIÊN() / SỐ_NGẪU_NHIÊN(min, max)", "Sinh số ngẫu nhiên từ 0 đến dưới 1 hoặc trong khoảng [min, max)."),
                EditorGuideCommand("NHỎ_NHẤT(a, b) / LỚN_NHẤT(a, b)", "Lấy giá trị nhỏ nhất hoặc lớn nhất."),
                EditorGuideCommand("ĐỘ_DÀI(giá_trị)", "Lấy số ký tự hoặc số phần tử."),
                EditorGuideCommand("CHỮ_HOA(chuỗi) / CHỮ_THƯỜNG(chuỗi)", "Chuyển đổi chữ hoa hoặc chữ thường."),
                EditorGuideCommand("CẮT_KHOẢNG_TRẮNG(chuỗi)", "Loại bỏ khoảng trắng ở hai đầu chuỗi."),
                EditorGuideCommand("CHỨA_CHUỖI(a, b)", "Kiểm tra chuỗi a có chứa chuỗi b."),
                EditorGuideCommand("TÌM_VỊ_TRÍ_TRONG_CHUỖI(chuỗi, chuỗi_cần_tìm)", "Trả vị trí bắt đầu, tính từ 0; trả về -1 nếu không tìm thấy."),
                EditorGuideCommand("KIỂM_TRA_RỖNG(giá_trị)", "Kiểm tra chuỗi, danh sách, bản đồ hoặc giá trị RỖNG."),
                EditorGuideCommand("BẮT_ĐẦU_BẰNG(a, b) / KẾT_THÚC_BẰNG(a, b)", "Kiểm tra tiền tố hoặc hậu tố."),
                EditorGuideCommand("THAY_THẾ(chuỗi, cũ, mới)", "Thay các phần chuỗi khớp."),
                EditorGuideCommand("TÁCH_CHUỖI(chuỗi, phân_cách) / NỐI_CHUỖI(danh_sách, phân_cách)", "Tách hoặc nối các phần tử văn bản."),
                EditorGuideCommand("KÝ_TỰ_TẠI(chuỗi, vị_trí) / CẮT_CHUỖI(chuỗi, vị_trí, độ_dài)", "Đọc ký tự hoặc cắt chuỗi theo vị trí.")
            )
        ),
        EditorGuideSection(
            id = "collections",
            title = "11. Danh sách và bản đồ dữ liệu",
            summary = "Tạo, đọc, thêm, sửa, xóa và tìm dữ liệu dạng danh sách hoặc cặp khóa–giá trị.",
            commands = listOf(
                EditorGuideCommand("TẠO_DANH_SÁCH(a, b, ...)", "Tạo danh sách, tối đa 64 phần tử."),
                EditorGuideCommand("LẤY_PHẦN_TỬ_TỪ_DANH_SÁCH(danh_sách, chỉ_số)", "Đọc phần tử theo chỉ số, bắt đầu từ 0."),
                EditorGuideCommand("THÊM_PHẦN_TỬ_VÀO_DANH_SÁCH(danh_sách, giá_trị)", "Thêm phần tử vào cuối danh sách."),
                EditorGuideCommand("ĐẶT_PHẦN_TỬ_TRONG_DANH_SÁCH(danh_sách, chỉ_số, giá_trị)", "Thay giá trị tại một chỉ số."),
                EditorGuideCommand("XÓA_PHẦN_TỬ_KHỎI_DANH_SÁCH(danh_sách, chỉ_số)", "Xóa phần tử theo chỉ số."),
                EditorGuideCommand("KIỂM_TRA_PHẦN_TỬ_TRONG_DANH_SÁCH(danh_sách, giá_trị) / TÌM_VỊ_TRÍ_TRONG_CHUỖI_PHẦN_TỬ_TRONG_DANH_SÁCH(danh_sách, giá_trị)", "Kiểm tra phần tử hoặc tìm chỉ số đầu tiên; không thấy trả về -1."),
                EditorGuideCommand("SẮP_XẾP_DANH_SÁCH(danh_sách)", "Tạo danh sách mới đã sắp xếp tăng dần; chỉ nhận toàn số hoặc toàn chuỗi."),
                EditorGuideCommand("ĐẢO_NGƯỢC_DANH_SÁCH(danh_sách)", "Tạo danh sách mới theo thứ tự ngược, không sửa danh sách gốc."),
                EditorGuideCommand("LỌC_DANH_SÁCH(danh_sách, tên_hàm)", "Lọc phần tử bằng hàm tự định nghĩa nhận một phần tử và trả về ĐÚNG/SAI."),
                EditorGuideCommand("TẠO_BẢN_ĐỒ(khóa, giá_trị, ...)", "Tạo bản đồ theo từng cặp khóa và giá trị."),
                EditorGuideCommand("LẤY_GIÁ_TRỊ_TỪ_BẢN_ĐỒ(bản_đồ, khóa) / ĐẶT_GIÁ_TRỊ_TRONG_BẢN_ĐỒ(bản_đồ, khóa, giá_trị)", "Đọc hoặc cập nhật giá trị theo khóa."),
                EditorGuideCommand("XÓA_KHÓA_KHỎI_BẢN_ĐỒ(bản_đồ, khóa) / KIỂM_TRA_KHÓA_TRONG_BẢN_ĐỒ(bản_đồ, khóa)", "Xóa khóa hoặc kiểm tra khóa tồn tại."),
                EditorGuideCommand("LẤY_DANH_SÁCH_KHÓA_TỪ_BẢN_ĐỒ(bản_đồ)", "Lấy danh sách khóa của bản đồ.")
            ),
            notes = listOf(
                "Danh sách và bản đồ có giới hạn số phần tử, độ sâu lồng nhau và tổng kích thước.",
                "Chỉ dùng kiểu giá trị mà AUTU Script hỗ trợ."
            )
        ),
        EditorGuideSection(
            id = "data_formats",
            title = "12. JSON, CSV và thời gian",
            summary = "Chuyển đổi dữ liệu JSON/CSV và xử lý thời gian ISO 8601.",
            commands = listOf(
                EditorGuideCommand("RỖNG", "Giá trị rỗng tương ứng null trong JSON."),
                EditorGuideCommand("PHÂN_TÍCH_JSON(chuỗi_json) / CHUYỂN_JSON(giá_trị)", "Chuyển đổi giữa JSON và kiểu dữ liệu AUTU."),
                EditorGuideCommand("PHÂN_TÍCH_CSV(nội_dung, dấu_tách)", "Đọc CSV thành danh sách các dòng."),
                EditorGuideCommand("CHUYỂN_CSV(bảng, dấu_tách)", "Chuyển bảng dữ liệu thành văn bản CSV."),
                EditorGuideCommand("THỜI_GIAN_ISO()", "Lấy thời gian hiện tại theo ISO 8601 kèm múi giờ."),
                EditorGuideCommand("PHÂN_TÍCH_THỜI_GIAN_ISO(chuỗi)", "Chuyển thời gian ISO 8601 thành epoch milliseconds."),
                EditorGuideCommand("ĐỊNH_DẠNG_THỜI_GIAN(epoch_ms, mẫu)", "Định dạng mốc thời gian theo mẫu.")
            ),
            notes = listOf(
                "Chuỗi ISO đầu vào phải có múi giờ rõ ràng, ví dụ Z hoặc +07:00.",
                "CSV/JSON bị giới hạn dung lượng và độ sâu dữ liệu."
            )
        ),
        EditorGuideSection(
            id = "files",
            title = "13. Tệp và thư mục riêng AUTU",
            summary = "Đọc, ghi, chọn, lưu, liệt kê và xóa tệp trong vùng dữ liệu riêng của AUTU-LIO.",
            commands = listOf(
                EditorGuideCommand("ĐỌC_TỆP(đường_dẫn)", "Đọc văn bản UTF-8 trong vùng tệp riêng."),
                EditorGuideCommand("GHI_TỆP(đường_dẫn, nội_dung)", "Ghi hoặc thay thế tệp UTF-8."),
                EditorGuideCommand("KIỂM_TRA_TỆP_TỒN_TẠI(đường_dẫn)", "Kiểm tra một tệp có tồn tại."),
                EditorGuideCommand("XÓA_TỆP(đường_dẫn)", "Xóa tệp trong vùng riêng."),
                EditorGuideCommand("TẠO_THƯ_MỤC(đường_dẫn)", "Tạo thư mục trong vùng riêng."),
                EditorGuideCommand("LIỆT_KÊ_TỆP(đường_dẫn)", "Liệt kê tệp trực tiếp trong thư mục."),
                EditorGuideCommand("ĐỌC_JSON_TỆP(đường_dẫn) / GHI_JSON_TỆP(đường_dẫn, giá_trị)", "Đọc hoặc ghi dữ liệu JSON trong tệp."),
                EditorGuideCommand("CHỌN_TỆP(MIME)", "Mở bộ chọn Android và nhập tệp vào vùng AUTU."),
                EditorGuideCommand("LƯU_TỆP(đường_dẫn, tên_gợi_ý)", "Mở bộ chọn Android để xuất tệp ra nơi người dùng chọn."),
                EditorGuideCommand("TẢI_LÊN_TỆP_HTTP(...) / TẢI_XUỐNG_TỆP_HTTP(...)", "Tải lên hoặc tải xuống tệp qua HTTPS; xem thêm nhóm Mạng.")
            ),
            notes = listOf(
                "Đường dẫn chỉ được nằm trong vùng dữ liệu riêng của AUTU-LIO; không hỗ trợ đường dẫn tuyệt đối hoặc thoát khỏi thư mục riêng.",
                "Giới hạn kích thước tệp được áp dụng để bảo vệ bộ nhớ."
            )
        ),
        EditorGuideSection(
            id = "database",
            title = "14. Cơ sở dữ liệu SQLite",
            summary = "Tạo bảng, truy vấn và quản lý phiên bản schema SQLite riêng của AUTU-LIO.",
            commands = listOf(
                EditorGuideCommand("SQL_THỰC_THI(câu_sql, tham_số)", "Thực thi câu lệnh SQL ghi dữ liệu hoặc thay đổi schema được phép."),
                EditorGuideCommand("SQL_TRUY_VẤN(câu_sql, tham_số)", "Chạy truy vấn SELECT và trả về danh sách dòng."),
                EditorGuideCommand("PHIÊN_BẢN_CƠ_SỞ_DỮ_LIỆU()", "Đọc phiên bản schema đã áp dụng."),
                EditorGuideCommand("DI_TRÚ_CƠ_SỞ_DỮ_LIỆU(phiên_bản, danh_sách)", "Áp dụng các bước di trú schema theo phiên bản."),
                EditorGuideCommand("KIỂM_TRA_TOÀN_VẸN_CƠ_SỞ_DỮ_LIỆU()", "Kiểm tra toàn vẹn SQLite và lịch sử di trú.")
            ),
            notes = listOf(
                "Cơ sở dữ liệu nằm trong vùng riêng của AUTU-LIO; script không được chọn đường dẫn hoặc truy cập bảng nội bộ.",
                "Câu SQL được kiểm tra loại lệnh, số tham số và giới hạn trước khi thực thi."
            )
        ),
        EditorGuideSection(
            id = "network",
            title = "15. Kết nối mạng và HTTP",
            summary = "Gửi yêu cầu HTTPS và trao đổi dữ liệu với API bên ngoài.",
            commands = listOf(
                EditorGuideCommand("GỬI_YÊU_CẦU_HTTP(phương_thức, URL, nội_dung, tiêu_đề, timeout)", "Gửi yêu cầu HTTP qua HTTPS và nhận phản hồi.", "GỬI_YÊU_CẦU_HTTP(\"GET\", \"https://api.example.com/status\", RỖNG, TẠO_BẢN_ĐỒ(), 10000)"),
                EditorGuideCommand("TẢI_LÊN_TỆP_HTTP(...) ", "Upload tệp bằng multipart/form-data; chi tiết tham số nằm trong tài liệu ngôn ngữ."),
                EditorGuideCommand("TẢI_XUỐNG_TỆP_HTTP(URL, đường_dẫn_đích, tiêu_đề, timeout)", "Tải tệp qua HTTPS và lưu vào vùng riêng AUTU.")
            ),
            notes = listOf(
                "Chỉ chấp nhận HTTPS và các phương thức mạng được runtime cho phép.",
                "Dung lượng, timeout và redirect có giới hạn bảo vệ."
            )
        ),
        EditorGuideSection(
            id = "overlay",
            title = "16. Vẽ và đánh dấu trên màn hình",
            summary = "Hiển thị chữ, khung, hình tròn, đường và mũi tên trên ứng dụng đang mở.",
            commands = listOf(
                EditorGuideCommand("HIỂN_THỊ_CHỮ(nội_dung, x, y, màu, cỡ_chữ)", "Hiển thị chữ tại tọa độ X/Y.", "HIỂN_THỊ_CHỮ(\"ĐANG KIỂM TRA\", 80, 180, \"#FFFF3B30\", 32)"),
                EditorGuideCommand("VẼ_KHUNG(x, y, rộng, cao, màu, độ_dày)", "Vẽ hình chữ nhật viền màu."),
                EditorGuideCommand("VẼ_HÌNH_TRÒN(tâm_x, tâm_y, bán_kính, màu, độ_dày)", "Vẽ đường tròn viền quanh một vị trí."),
                EditorGuideCommand("VẼ_ĐƯỜNG(x1, y1, x2, y2, màu, độ_dày)", "Vẽ đường thẳng nối hai tọa độ."),
                EditorGuideCommand("VẼ_MŨI_TÊN(x1, y1, x2, y2, màu, độ_dày)", "Vẽ mũi tên chỉ vào một vị trí."),
                EditorGuideCommand("XÓA_ĐÁNH_DẤU_MÀN_HÌNH()", "Xóa toàn bộ chữ và hình do AUTU-LIO tạo.")
            ),
            notes = listOf(
                "Tọa độ và cỡ chữ tính bằng pixel trên toàn màn hình.",
                "Màu hỗ trợ HEX #RRGGBB hoặc #AARRGGBB; lớp đánh dấu cần quyền hiển thị phù hợp."
            )
        ),
        EditorGuideSection(
            id = "logging",
            title = "17. Nhật ký, in nội dung và thông báo",
            summary = "Ghi lại thông tin chạy kịch bản vào tệp riêng AUTU hoặc hiển thị thông báo ngắn.",
            commands = listOf(
                EditorGuideCommand("IN(nội_dung)", "Ghi nội dung vào nhật ký hệ thống Android (Logcat)."),
                EditorGuideCommand("GHI_NHẬT_KÝ(nội_dung) / GHI_NHẬT_KÝ(mức, nội_dung)", "Ghi nhật ký có thời gian vào logs/script.log trong vùng dữ liệu AUTU."),
                EditorGuideCommand("THÔNG_BÁO(nội_dung)", "Hiển thị thông báo nổi ngắn trên màn hình.")
            ),
            notes = listOf(
                "Có thể đọc nhật ký đã lưu bằng ĐỌC_TỆP(\"logs/script.log\").",
                "IN() chỉ dành cho nhật ký kỹ thuật; GHI_NHẬT_KÝ() lưu thêm bản ghi trong vùng riêng của ứng dụng."
            )
        ),
        EditorGuideSection(
            id = "screenshots",
            title = "18. Chụp và lưu ảnh màn hình",
            summary = "Chụp màn hình qua AccessibilityService và lưu JPEG vào vùng dữ liệu riêng AUTU.",
            commands = listOf(
                EditorGuideCommand("CHỤP_ẢNH_MÀN_HÌNH(đường_dẫn) / CHỤP_ẢNH_MÀN_HÌNH(đường_dẫn)", "Chụp và lưu JPEG; trả về bản đồ gồm đường dẫn, kích thước và dung lượng.", minAndroidApi = 30)
            ),
            notes = listOf(
                "Cần bật AccessibilityService cho AUTU-LIO và Android 11.0 (API 30) trở lên.",
                "Tệp ảnh giới hạn 1 MB sau khi nén; đường dẫn phải nằm trong vùng dữ liệu riêng AUTU."
            )
        ),
    )
}
