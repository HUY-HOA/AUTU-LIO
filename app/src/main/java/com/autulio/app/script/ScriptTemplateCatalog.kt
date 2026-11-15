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
                CHẠM(200, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "tap_sequence",
            nameVi = "Chuỗi chạm có thời gian chờ",
            nameEn = "Tap sequence with delay",
            descriptionVi = "Chạm vị trí thứ nhất, chờ 500 ms rồi chạm vị trí thứ hai.",
            descriptionEn = "Tap the first point, wait 500 ms, then tap the second point.",
            source = """
                CHẠM(200, 400)
                CHỜ(500)
                CHẠM(500, 400)
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
                ĐẶT soLan = 3
                LẶP(soLan) {
                    CHẠM(200, 400)
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
                NẾU (PIN() > 20) {
                    CHẠM(200, 400)
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
                ĐẶT dem = 0
                LẶP_KHI (dem < 5) {
                    dem = dem + 1
                    CHẠM(200, 400)
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
                CHỜ_ĐẾN_KHI(ỨNG_DỤNG_ĐANG_MỞ("com.example.game"), 15000, 250)
                CHẠM(200, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "until_loop",
            nameVi = "Lặp đến khi đạt mục tiêu",
            nameEn = "Loop until a goal is reached",
            descriptionVi = "Thực hiện thao tác cho đến khi biến đạt điều kiện dừng.",
            descriptionEn = "Repeat actions until a variable reaches the stop condition.",
            source = """
                ĐẶT dem = 0
                LẶP_ĐẾN_KHI(dem >= 5) {
                    dem = dem + 1
                    CHẠM(200, 400)
                    CHỜ(300)
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
        )
    )
}
