package com.autulio.app.runtime

/** Platform-independent display measurements used by Android script functions. */
object ScreenMetricsInfo {
    fun widthDp(widthPx: Int, density: Double): Double {
        validateDensity(density)
        require(widthPx > 0) { "Chiều rộng màn hình phải lớn hơn 0." }
        return widthPx.toDouble() / density
    }

    fun heightDp(heightPx: Int, density: Double): Double {
        validateDensity(density)
        require(heightPx > 0) { "Chiều cao màn hình phải lớn hơn 0." }
        return heightPx.toDouble() / density
    }

    fun aspectRatio(widthPx: Int, heightPx: Int): Double {
        require(widthPx > 0 && heightPx > 0) { "Kích thước màn hình phải lớn hơn 0." }
        return widthPx.toDouble() / heightPx.toDouble()
    }

    fun orientation(widthPx: Int, heightPx: Int): String {
        require(widthPx > 0 && heightPx > 0) { "Kích thước màn hình phải lớn hơn 0." }
        return when {
            widthPx > heightPx -> "NGANG"
            heightPx > widthPx -> "DỌC"
            else -> "VUÔNG"
        }
    }

    fun rotationDegrees(rotation: Int): Int {
        require(rotation in 0..3) { "Mã xoay màn hình phải từ 0 đến 3." }
        return rotation * 90
    }

    private fun validateDensity(density: Double) {
        require(density.isFinite() && density > 0.0) {
            "Mật độ màn hình phải là số hữu hạn lớn hơn 0."
        }
    }
}
