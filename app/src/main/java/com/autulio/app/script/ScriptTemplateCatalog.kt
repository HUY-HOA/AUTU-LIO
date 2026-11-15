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
            descriptionVi = "Chạm vào một vị trí. Hãy sửa tọa độ X và Y trước khi chạy.",
            descriptionEn = "Tap one point. Change X and Y before running.",
            source = """
                // Sửa tọa độ X và Y theo màn hình của bạn
                tap(200, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "tap_sequence",
            nameVi = "Hai lần chạm có thời gian chờ",
            nameEn = "Two taps with a delay",
            descriptionVi = "Chạm vị trí thứ nhất, chờ 500 ms rồi chạm vị trí thứ hai.",
            descriptionEn = "Tap the first point, wait 500 ms, then tap the second point.",
            source = """
                // Sửa các tọa độ trước khi chạy
                tap(200, 400)
                wait(500)
                tap(500, 400)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "long_press",
            nameVi = "Nhấn giữ",
            nameEn = "Long press",
            descriptionVi = "Nhấn giữ tại một vị trí trong 800 ms.",
            descriptionEn = "Hold one point for 800 ms.",
            source = """
                // Sửa tọa độ và thời lượng giữ (ms)
                longPress(200, 400, 800)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "swipe_up",
            nameVi = "Vuốt lên",
            nameEn = "Swipe up",
            descriptionVi = "Vuốt từ dưới lên trên trong 450 ms.",
            descriptionEn = "Swipe from lower to upper screen in 450 ms.",
            source = """
                // Sửa điểm đầu, điểm cuối và thời lượng (ms)
                swipe(360, 850, 360, 350, 450)
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "repeat_tap",
            nameVi = "Lặp thao tác chạm 3 lần",
            nameEn = "Repeat a tap three times",
            descriptionVi = "Lặp thao tác chạm, mỗi lần cách nhau 500 ms.",
            descriptionEn = "Repeat a tap with a 500 ms delay between taps.",
            source = """
                // Sửa tọa độ, số lần lặp và thời gian chờ
                repeat(3) {
                    tap(200, 400)
                    wait(500)
                }
            """.trimIndent()
        ),
        ScriptTemplate(
            id = "tap_wait_swipe",
            nameVi = "Chạm, chờ rồi vuốt",
            nameEn = "Tap, wait, then swipe",
            descriptionVi = "Một chuỗi gồm chạm, chờ và vuốt.",
            descriptionEn = "A sequence containing a tap, a wait and a swipe.",
            source = """
                // Sửa tọa độ theo ứng dụng mục tiêu
                tap(200, 400)
                wait(700)
                swipe(360, 850, 360, 350, 450)
            """.trimIndent()
        )
    )
}
