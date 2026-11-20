package com.autulio.app

data class EditorGuideCommand(
    val syntax: String,
    val description: String,
    val example: String? = null
)

data class EditorGuideSection(
    val id: String,
    val title: String,
    val summary: String,
    val commands: List<EditorGuideCommand>,
    val notes: List<String> = emptyList()
)

object EditorGuideCatalog {
    val sections = listOf(
        EditorGuideSection(
            id = "gestures",
            title = "Thao tác chạm và điều hướng",
            summary = "Điều khiển ứng dụng đang mở bằng tọa độ pixel và các thao tác hệ thống được Android cho phép.",
            commands = listOf(
                EditorGuideCommand("CHẠM(x, y)", "Chạm một lần tại tọa độ X/Y.", "CHẠM(200, 400)"),
                EditorGuideCommand("NHẤN_GIỮ(x, y, ms)", "Nhấn giữ tại tọa độ trong số mili giây đã đặt.", "NHẤN_GIỮ(200, 400, 800)"),
                EditorGuideCommand("VUỐT(x1, y1, x2, y2, ms)", "Vuốt từ điểm bắt đầu đến điểm kết thúc.", "VUỐT(360, 850, 360, 350, 450)"),
                EditorGuideCommand("CHẠM_ĐA_ĐIỂM(CÁC_ĐIỂM, ms)", "Gửi thao tác đồng thời từ 2 đến 5 điểm."),
                EditorGuideCommand("VUỐT_ĐA_ĐIỂM(CÁC_ĐƯỜNG, ms)", "Gửi các đường vuốt đồng thời, tối đa 5 ngón."),
                EditorGuideCommand("CHỜ(ms)", "Tạm dừng kịch bản trong thời gian đã đặt.", "CHỜ(500)"),
                EditorGuideCommand("MỞ_ỨNG_DỤNG(\"tên.gói\")", "Mở ứng dụng đã cài đặt theo tên gói Android.", "MỞ_ỨNG_DỤNG(\"com.example.game\")"),
                EditorGuideCommand("QUAY_LẠI() / VỀ_MÀN_HÌNH_CHÍNH()", "Gọi thao tác điều hướng hệ thống Android.")
            ),
            notes = listOf(
                "Tọa độ thao tác dùng pixel trên toàn màn hình; cần tính lại khi đổi hướng hoặc độ phân giải.",
                "Ứng dụng đích, Android hoặc màn hình bảo mật có thể chặn một số thao tác."
            )
        ),
        EditorGuideSection(
            id = "accessibility",
            title = "Đọc và thao tác thành phần giao diện",
            summary = "Tìm nội dung và điều khiển các thành phần mà ứng dụng đích công khai qua Trợ năng Android.",
            commands = listOf(
                EditorGuideCommand("ĐỌC_VĂN_BẢN_MÀN_HÌNH()", "Đọc văn bản nhận diện được trên màn hình.", "ĐẶT NOI_DUNG: CHUỖI = ĐỌC_VĂN_BẢN_MÀN_HÌNH()"),
                EditorGuideCommand("ĐỌC_CẤU_TRÚC_VĂN_BẢN_MÀN_HÌNH()", "Đọc văn bản có cấu trúc và thông tin vị trí."),
                EditorGuideCommand("MÀN_HÌNH_CHỨA_CHỮ(\"nội dung\")", "Kiểm tra văn bản có xuất hiện trên màn hình không."),
                EditorGuideCommand("TÌM_VỊ_TRÍ_CHỮ(\"nội dung\")", "Trả về tọa độ tâm của văn bản tìm được."),
                EditorGuideCommand("TÌM_THÀNH_PHẦN_VĂN_BẢN(\"nội dung\")", "Tìm thành phần có nhãn văn bản tương ứng."),
                EditorGuideCommand("NHẤN_THÀNH_PHẦN_VĂN_BẢN(\"nội dung\")", "Nhấn thành phần tìm được theo văn bản."),
                EditorGuideCommand("NHẬP_VĂN_BẢN_VÀO_THÀNH_PHẦN(\"Tên\", \"LIO\")", "Nhập văn bản vào trường có nhãn tương ứng."),
                EditorGuideCommand("CUỘN_XUỐNG() / CUỘN_LÊN()", "Cuộn thành phần có thể cuộn trong cửa sổ hiện tại.")
            ),
            notes = listOf(
                "Một số ứng dụng không công khai cây Trợ năng hoặc giới hạn nội dung; khi đó có thể không tìm thấy thành phần.",
                "Có thể phối hợp OCR với dữ liệu Trợ năng để đọc chữ và vị trí."
            )
        ),
        EditorGuideSection(
            id = "vision",
            title = "Nhận diện hình ảnh và màu sắc",
            summary = "Phân tích ảnh chụp màn hình ngay trên thiết bị bằng các bộ nhận diện đang có trong AUTU-LIO.",
            commands = listOf(
                EditorGuideCommand("NHẬN_DIỆN_NHÃN_ẢNH_MÀN_HÌNH()", "Nhận diện nhãn mô tả nội dung hình ảnh."),
                EditorGuideCommand("ĐỌC_VĂN_BẢN_MÀN_HÌNH()", "Dùng OCR để đọc chữ trong ảnh chụp màn hình."),
                EditorGuideCommand("MÀU_TẠI(x, y)", "Đọc màu tại một điểm trên màn hình.", "ĐẶT MAU: BẢN_ĐỒ = MÀU_TẠI(200, 400)"),
                EditorGuideCommand("TÌM_VỊ_TRÍ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)", "Tìm vị trí có màu gần với màu RGB cần tìm."),
                EditorGuideCommand("MÀN_HÌNH_CÓ_MÀU(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ)", "Kiểm tra màn hình có màu gần với màu RGB cần tìm."),
                EditorGuideCommand("TỶ_LỆ_MÀU_TRONG_VÙNG(ĐỎ, XANH_LÁ, XANH_DƯƠNG, SAI_SỐ, X, Y, RỘNG, CAO)", "Ước lượng tỷ lệ pixel gần màu mục tiêu trong vùng.")
            ),
            notes = listOf(
                "Nhận diện hình ảnh, OCR, nhãn ảnh, mã vạch và vật thể phụ thuộc khả năng xử lý và quyền chụp màn hình của Android.",
                "Kết quả màu trong vùng là giá trị ước lượng theo mẫu pixel, không phải phép đo màu chuyên dụng."
            )
        ),
        EditorGuideSection(
            id = "display",
            title = "Kích thước và vùng an toàn màn hình",
            summary = "Đọc độ phân giải, mật độ, hướng màn hình, lề an toàn và thông tin vùng khuyết để tính tọa độ phù hợp.",
            commands = listOf(
                EditorGuideCommand("CHIỀU_RỘNG_MÀN_HÌNH() / CHIỀU_CAO_MÀN_HÌNH()", "Lấy kích thước toàn màn hình theo pixel."),
                EditorGuideCommand("CHIỀU_RỘNG_DP() / CHIỀU_CAO_DP()", "Lấy kích thước quy đổi theo đơn vị DP."),
                EditorGuideCommand("MẬT_ĐỘ_MÀN_HÌNH() / TỶ_LỆ_KHUNG_HÌNH()", "Đọc mật độ điểm ảnh và tỷ lệ khung hình."),
                EditorGuideCommand("HƯỚNG_MÀN_HÌNH() / GÓC_XOAY_MÀN_HÌNH()", "Đọc hướng và góc xoay hiện tại."),
                EditorGuideCommand("CHIỀU_RỘNG_VÙNG_AN_TOÀN() / CHIỀU_CAO_VÙNG_AN_TOÀN()", "Lấy kích thước sau khi trừ lề hệ thống và vùng khuyết."),
                EditorGuideCommand("LỀ_AN_TOÀN_TRÁI() / LỀ_AN_TOÀN_TRÊN()", "Đọc lề an toàn bên trái và phía trên."),
                EditorGuideCommand("LỀ_AN_TOÀN_PHẢI() / LỀ_AN_TOÀN_DƯỚI()", "Đọc lề an toàn bên phải và phía dưới."),
                EditorGuideCommand("CÓ_THỂ_XÁC_ĐỊNH_VÙNG_KHUYẾT_MÀN_HÌNH() / CÓ_VÙNG_KHUYẾT_MÀN_HÌNH()", "Phân biệt không có vùng khuyết với trường hợp không thể xác định."),
                EditorGuideCommand("SỐ_VÙNG_KHUYẾT_MÀN_HÌNH() / TỌA_ĐỘ_VÙNG_KHUYẾT_MÀN_HÌNH()", "Lấy số lượng và tọa độ các vùng khuyết.")
            ),
            notes = listOf(
                "Android 9 trở lên mới có API DisplayCutout tiêu chuẩn. Trên phiên bản cũ hơn, AUTU-LIO báo chưa thể xác định thay vì kết luận không có vùng khuyết.",
                "Lề an toàn là hợp nhất các lề hệ thống và vùng khuyết, tránh trừ trùng."
            )
        ),
        EditorGuideSection(
            id = "overlay",
            title = "Hiển thị chữ và đánh dấu trên màn hình",
            summary = "Vẽ chữ, khung, hình tròn và đường đánh dấu trực tiếp trên ứng dụng đang mở. Lớp hiển thị không chặn thao tác chạm.",
            commands = listOf(
                EditorGuideCommand(
                    "HIỂN_THỊ_CHỮ(NỘI_DUNG, X, Y, MÀU, CỠ_CHỮ)",
                    "Hiển thị chữ đậm tại tọa độ X/Y. Y là đường cơ sở của chữ; màu dùng #RRGGBB hoặc #AARRGGBB.",
                    "HIỂN_THỊ_CHỮ(\"ĐANG KIỂM TRA\", 80, 180, \"#FFFF3B30\", 32)"
                ),
                EditorGuideCommand(
                    "VẼ_KHUNG(X, Y, RỘNG, CAO, MÀU, ĐỘ_DÀY)",
                    "Vẽ hình chữ nhật viền màu tại vùng đã chọn.",
                    "VẼ_KHUNG(40, 210, 300, 120, \"#FF30D158\", 4)"
                ),
                EditorGuideCommand(
                    "VẼ_HÌNH_TRÒN(TÂM_X, TÂM_Y, BÁN_KÍNH, MÀU, ĐỘ_DÀY)",
                    "Vẽ đường tròn viền quanh vị trí cần đánh dấu.",
                    "VẼ_HÌNH_TRÒN(200, 300, 45, \"#FFFFC107\", 4)"
                ),
                EditorGuideCommand(
                    "VẼ_ĐƯỜNG(X1, Y1, X2, Y2, MÀU, ĐỘ_DÀY)",
                    "Vẽ đường thẳng nối hai tọa độ trên màn hình.",
                    "VẼ_ĐƯỜNG(40, 270, 340, 270, \"#FF0A84FF\", 3)"
                ),
                EditorGuideCommand(
                    "VẼ_MŨI_TÊN(X1, Y1, X2, Y2, MÀU, ĐỘ_DÀY)",
                    "Vẽ đường có đầu mũi tên để chỉ vào vị trí cần chú ý.",
                    "VẼ_MŨI_TÊN(40, 270, 340, 270, \"#FF0A84FF\", 4)"
                ),
                EditorGuideCommand(
                    "XÓA_ĐÁNH_DẤU_MÀN_HÌNH()",
                    "Xóa toàn bộ chữ và hình đánh dấu do AUTU-LIO tạo."
                )
            ),
            notes = listOf(
                "Tọa độ và cỡ chữ tính bằng pixel trên toàn màn hình.",
                "Màu hỗ trợ chuỗi HEX #RRGGBB hoặc #AARRGGBB.",
                "Lớp đánh dấu trong suốt, không nhận thao tác chạm; thao tác vẫn đi tới ứng dụng bên dưới.",
                "Tối đa 200 mục đánh dấu cho mỗi phiên. Các mục tồn tại đến khi xóa hoặc dịch vụ AUTU-LIO kết thúc."
            )
        ),
        EditorGuideSection(
            id = "logic",
            title = "Biến, điều kiện và vòng lặp",
            summary = "Tổ chức kịch bản thành các bước có điều kiện, lặp lại, hàm dùng chung và xử lý lỗi.",
            commands = listOf(
                EditorGuideCommand("ĐẶT TÊN_BIẾN: SỐ = 10", "Khai báo biến có kiểu.", "ĐẶT SO_LAN: SỐ = 3"),
                EditorGuideCommand("NẾU (điều_kiện) { ... } NGƯỢC_LẠI { ... }", "Chạy một nhánh theo điều kiện."),
                EditorGuideCommand("LẶP(số_lần) { ... }", "Lặp một khối lệnh theo số lần."),
                EditorGuideCommand("LẶP_KHI (điều_kiện) { ... }", "Lặp khi điều kiện còn đúng."),
                EditorGuideCommand("LẶP_ĐẾN_KHI(điều_kiện) { ... }", "Lặp cho đến khi đạt điều kiện dừng."),
                EditorGuideCommand("LẶP_MỖI (phần_tử TRONG danh_sách) { ... }", "Duyệt lần lượt các phần tử."),
                EditorGuideCommand("ĐỊNH_NGHĨA_HÀM TEN(THAM_SO: SỐ): SỐ { ... }", "Tạo hàm để tái sử dụng đoạn lệnh."),
                EditorGuideCommand("THỬ { ... } BẮT_LỖI (LỖI) { ... } LUÔN_THỰC_HIỆN { ... }", "Bắt lỗi và thực hiện phần dọn dẹp.")
            ),
            notes = listOf(
                "Bộ chạy có giới hạn số lệnh, số vòng lặp và thời lượng để tránh kịch bản chạy vô hạn.",
                "Tên hàm tự định nghĩa phải viết hoa và khai báo ở cấp cao nhất."
            )
        ),
        EditorGuideSection(
            id = "data",
            title = "Danh sách, dữ liệu và tệp",
            summary = "Lưu trữ và xử lý dữ liệu cấu trúc trong vùng riêng của AUTU-LIO.",
            commands = listOf(
                EditorGuideCommand("TẠO_DANH_SÁCH(...)", "Tạo danh sách giá trị."),
                EditorGuideCommand("TẠO_BẢN_ĐỒ(\"KHOA\", GIA_TRI, ...)", "Tạo bản đồ khóa và giá trị."),
                EditorGuideCommand("LẤY_PHẦN_TỬ(DANH_SÁCH, CHI_SO)", "Đọc một phần tử trong danh sách."),
                EditorGuideCommand("THÊM_PHẦN_TỬ(DANH_SÁCH, GIA_TRI)", "Thêm giá trị vào danh sách."),
                EditorGuideCommand("LẤY_GIÁ_TRỊ(BẢN_ĐỒ, \"KHOA\")", "Đọc giá trị theo khóa."),
                EditorGuideCommand("PHÂN_TÍCH_JSON(\"{\\\"A\\\":1}\") / CHUYỂN_JSON(GIA_TRI)", "Chuyển đổi giữa văn bản JSON và dữ liệu AUTU."),
                EditorGuideCommand("ĐỌC_TỆP(\"settings/a.txt\") / GHI_TỆP(\"settings/a.txt\", NOI_DUNG)", "Đọc hoặc ghi tệp trong vùng riêng."),
                EditorGuideCommand("ĐỌC_JSON_TỆP(\"settings/a.json\") / GHI_JSON_TỆP(...)", "Đọc hoặc ghi dữ liệu JSON trong tệp."),
                EditorGuideCommand("LIỆT_KÊ_TỆP(\"settings\") / XÓA_TỆP(\"settings/a.txt\")", "Liệt kê hoặc xóa tệp trong vùng riêng.")
            ),
            notes = listOf(
                "Tệp được lưu trong vùng riêng của AUTU-LIO, không tự ý truy cập thư mục riêng của ứng dụng khác.",
                "Danh sách, bản đồ và tệp có giới hạn dung lượng để bảo vệ bộ nhớ."
            )
        ),
        EditorGuideSection(
            id = "network",
            title = "Kết nối mạng",
            summary = "Gửi yêu cầu HTTPS từ kịch bản và xử lý kết quả trả về.",
            commands = listOf(
                EditorGuideCommand("GỬI_YÊU_CẦU_HTTP(PHUONG_THUC, URL, NOI_DUNG, TIÊU_ĐỀ, TIMEOUT)", "Gửi yêu cầu mạng và nhận dữ liệu phản hồi.", "GỬI_YÊU_CẦU_HTTP(\"GET\", \"https://api.example.com/status\", RỖNG, TẠO_BẢN_ĐỒ(), 10000)")
            ),
            notes = listOf(
                "Chỉ chấp nhận HTTPS; hỗ trợ GET, POST, PUT, DELETE và HEAD.",
                "Dung lượng yêu cầu/phản hồi tối đa 1 MiB; thời gian chờ tối đa 30 giây."
            )
        )
    )
}
