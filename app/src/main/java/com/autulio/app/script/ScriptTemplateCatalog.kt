package com.autulio.app.script

data class ScriptTemplate(
    val id: String,
    val nameVi: String,
    val nameEn: String,
    val descriptionVi: String,
    val descriptionEn: String,
    val source: String
)

object ScriptTemplateCatalog {
    val templates = listOf(
        ScriptTemplate(
            id = "single_tap",
            nameVi = "Chạm một lần",
            nameEn = "Single tap",
            descriptionVi = "Chạm tại một tọa độ trên màn hình.",
            descriptionEn = "Tap at a screen coordinate.",
            source = """
                // Thay tọa độ X và Y theo ứng dụng mục tiêu
                NHẤN(200, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "tap_sequence",
            nameVi = "Chuỗi chạm có thời gian chờ",
            nameEn = "Tap sequence with delay",
            descriptionVi = "Chạm vị trí thứ nhất, chờ 500 ms rồi chạm vị trí thứ hai.",
            descriptionEn = "Tap the first point, wait 500 ms, then tap the second point.",
            source = """
                NHẤN(200, 400)
                CHỜ(500)
                NHẤN(500, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "long_press",
            nameVi = "Nhấn giữ",
            nameEn = "Long press",
            descriptionVi = "Nhấn giữ tại một vị trí trong 800 ms.",
            descriptionEn = "Hold one point for 800 ms.",
            source = """
                NHẤN_GIỮ(200, 400, 800)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "swipe_up",
            nameVi = "Vuốt lên",
            nameEn = "Swipe up",
            descriptionVi = "Vuốt từ dưới lên trên trong 450 ms.",
            descriptionEn = "Swipe from lower to upper screen in 450 ms.",
            source = """
                VUỐT(360, 850, 360, 350, 450)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "repeat_tap",
            nameVi = "Lặp thao tác chạm",
            nameEn = "Repeat tap",
            descriptionVi = "Lặp thao tác chạm theo số lần đã đặt.",
            descriptionEn = "Repeat a tap for the chosen number of iterations.",
            source = """
                BIẾN SO_LAN = 3
                LẶP(SO_LAN) {
                    NHẤN(200, 400)
                    CHỜ(500)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "if_battery",
            nameVi = "Kiểm tra pin trước khi chạy",
            nameEn = "Check battery before running",
            descriptionVi = "Chỉ tiếp tục thao tác khi pin còn trên 20%.",
            descriptionEn = "Only continue when the battery is above 20%.",
            source = """
                NẾU (LẤY_PHẦN_TRĂM_PIN() > 20) {
                    NHẤN(200, 400)
                } NGƯỢC_LẠI {
                    CHỜ(1000)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "open_app",
            nameVi = "Mở ứng dụng",
            nameEn = "Open an app",
            descriptionVi = "Mở ứng dụng bằng tên gói Android hợp lệ.",
            descriptionEn = "Launch an app using its Android package name.",
            source = """
                MỞ_ỨNG_DỤNG("com.example.game")
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "while_loop",
            nameVi = "Lặp theo điều kiện",
            nameEn = "Repeat while condition is true",
            descriptionVi = "Tăng biến đếm và dừng lặp khi đạt điều kiện.",
            descriptionEn = "Increase a counter and stop when the condition is met.",
            source = """
                BIẾN DEM = 0
                LẶP_KHI (DEM < 5) {
                    DEM = DEM + 1
                    NHẤN(200, 400)
                    CHỜ(300)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "wait_until_condition",
            nameVi = "Chờ ứng dụng hoặc điều kiện sẵn sàng",
            nameEn = "Wait for an app or condition",
            descriptionVi = "Kiểm tra điều kiện định kỳ và tự dừng khi hết thời hạn.",
            descriptionEn = "Poll a condition and stop safely when the timeout is reached.",
            source = """
                CHỜ_ĐẾN_KHI(KIỂM_TRA_ỨNG_DỤNG_ĐANG_MỞ("com.example.game"), 15000, 250)
                NHẤN(200, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "until_loop",
            nameVi = "Lặp đến khi đạt mục tiêu",
            nameEn = "Loop until a goal is reached",
            descriptionVi = "Thực hiện thao tác cho đến khi biến đạt điều kiện dừng.",
            descriptionEn = "Repeat actions until a variable reaches the stop condition.",
            source = """
                BIẾN DEM = 0
                LẶP_ĐẾN_KHI(DEM >= 5) {
                    DEM = DEM + 1
                    NHẤN(200, 400)
                    CHỜ(300)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "iterate_list",
            nameVi = "Tạo và duyệt danh sách",
            nameEn = "Create and iterate a list",
            descriptionVi = "Tạo danh sách tọa độ và thực hiện thao tác cho từng phần tử.",
            descriptionEn = "Create a coordinate list and perform an action for each item.",
            source = """
                BIẾN CAC_TOA_DO: DANH_SÁCH = TẠO_DANH_SÁCH(120, 240, 360)
                LẶP_MỖI (TOA_DO_X TRONG CAC_TOA_DO) {
                    NHẤN(TOA_DO_X, 400)
                    CHỜ(300)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "map_key_values",
            nameVi = "Tạo và duyệt bản đồ khóa–giá trị",
            nameEn = "Create and iterate a key-value map",
            descriptionVi = "Tạo bản đồ cài đặt và duyệt từng khóa để lấy giá trị tương ứng.",
            descriptionEn = "Create a settings map and iterate through keys to read their values.",
            source = """
                BIẾN CAU_HINH: BẢN_ĐỒ = TẠO_BẢN_ĐỒ("TOA_DO_X", 120, "TOA_DO_Y", 400)
                BIẾN DANH_SACH_KHOA: DANH_SÁCH = LẤY_DANH_SÁCH_KHÓA_TỪ_BẢN_ĐỒ(CAU_HINH)
                LẶP_MỖI (KHOA TRONG DANH_SACH_KHOA) {
                    NHẤN(LẤY_GIÁ_TRỊ_TỪ_BẢN_ĐỒ(CAU_HINH, KHOA), 500)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "go_back",
            nameVi = "Quay lại",
            nameEn = "Go back",
            descriptionVi = "Thực hiện thao tác Quay lại của Android.",
            descriptionEn = "Run Android's Back global action.",
            source = "QUAY_LẠI()"
        ),
        ScriptTemplate(
            id = "go_home",
            nameVi = "Về màn hình chính",
            nameEn = "Go to Home",
            descriptionVi = "Đưa thiết bị về màn hình chính Android.",
            descriptionEn = "Run Android's Home global action.",
            source = "VỀ_MÀN_HÌNH_CHÍNH()"
        ),
        ScriptTemplate(
            id = "show_recents",
            nameVi = "Mở ứng dụng gần đây",
            nameEn = "Show recent apps",
            descriptionVi = "Mở giao diện ứng dụng gần đây của Android.",
            descriptionEn = "Open Android's recent apps overview.",
            source = "MỞ_ỨNG_DỤNG_GẦN_ĐÂY()"
        ),
        ScriptTemplate(
            id = "show_notifications",
            nameVi = "Mở thanh thông báo",
            nameEn = "Show notifications",
            descriptionVi = "Mở ngăn thông báo Android.",
            descriptionEn = "Open the Android notification shade.",
            source = "MỞ_THANH_THÔNG_BÁO()"
        ),
        ScriptTemplate(
            id = "show_quick_settings",
            nameVi = "Mở bảng cài đặt nhanh",
            nameEn = "Show quick settings",
            descriptionVi = "Mở bảng cài đặt nhanh Android.",
            descriptionEn = "Open Android quick settings.",
            source = "MỞ_BẢNG_CÀI_ĐẶT_NHANH()"
        ),
        ScriptTemplate(
            id = "show_power_dialog",
            nameVi = "Mở hộp thoại nguồn",
            nameEn = "Show power dialog",
            descriptionVi = "Mở menu nguồn bằng thao tác hệ thống Android.",
            descriptionEn = "Open Android's power dialog.",
            source = "MỞ_HỘP_THOẠI_NGUỒN()"
        ),
        ScriptTemplate(
            id = "lock_screen",
            nameVi = "Khóa màn hình",
            nameEn = "Lock screen",
            descriptionVi = "Khóa màn hình nếu phiên bản Android hỗ trợ.",
            descriptionEn = "Lock the screen on supported Android versions.",
            source = "KHÓA_MÀN_HÌNH()"
        ),
        ScriptTemplate(
            id = "split_screen",
            nameVi = "Bật/tắt chia đôi màn hình",
            nameEn = "Toggle split screen",
            descriptionVi = "Bật chế độ chia đôi nếu Android cung cấp thao tác này.",
            descriptionEn = "Toggle split screen when Android exposes the action.",
            source = "BẬT_TẮT_CHIA_MÀN_HÌNH()"
        ),
        ScriptTemplate(
            id = "show_colored_text",
            nameVi = "Hiển thị chữ có màu",
            nameEn = "Show colored text",
            descriptionVi = "Hiển thị chữ đậm tại tọa độ đã chọn trên màn hình ứng dụng.",
            descriptionEn = "Draw bold colored text at a selected screen coordinate.",
            source = """HIỂN_THỊ_CHỮ("AUTU-LIO", 80, 180, "#FF00C8FF", 36)"""
        ),
        ScriptTemplate(
            id = "mark_screen_region",
            nameVi = "Đánh dấu vùng trên màn hình",
            nameEn = "Mark a screen region",
            descriptionVi = "Vẽ khung, đường và mũi tên đánh dấu; dùng lệnh xóa khi không cần hiển thị nữa.",
            descriptionEn = "Draw a rectangle, line and arrow; clear the annotation when no longer needed.",
            source = """VẼ_KHUNG(40, 210, 300, 120, "#FFFFC107", 4)
VẼ_ĐƯỜNG(40, 270, 340, 270, "#FF30D158", 3)
VẼ_MŨI_TÊN(340, 270, 300, 270, "#FF0A84FF", 4)"""
        )
    )
}
