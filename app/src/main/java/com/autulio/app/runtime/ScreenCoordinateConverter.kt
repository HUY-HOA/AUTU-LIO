package com.autulio.app.runtime

import kotlin.math.roundToInt

/** Converts Android display coordinates without assuming a fixed device density. */
object ScreenCoordinateConverter {
    fun dpToPx(dp: Double, density: Double): Double {
        validateDensity(density)
        require(dp.isFinite() && dp >= 0.0) { "Giá trị DP phải là số hữu hạn không âm." }
        return (dp * density).roundToInt().toDouble()
    }

    fun pxToDp(px: Double, density: Double): Double {
        validateDensity(density)
        require(px.isFinite() && px >= 0.0) { "Giá trị pixel phải là số hữu hạn không âm." }
        return px / density
    }

    fun ratioToPixel(ratio: Double, pixelSize: Int): Double {
        require(pixelSize > 0) { "Kích thước màn hình phải lớn hơn 0." }
        require(ratio.isFinite() && ratio in 0.0..1.0) {
            "Tỷ lệ tọa độ phải nằm trong khoảng 0 đến 1."
        }
        return (ratio * (pixelSize - 1).toDouble()).roundToInt().toDouble()
    }

    fun pixelToRatio(pixel: Double, pixelSize: Int): Double {
        require(pixelSize > 0) { "Kích thước màn hình phải lớn hơn 0." }
        require(pixel.isFinite() && pixel >= 0.0 && pixel <= (pixelSize - 1).toDouble()) {
            "Tọa độ pixel nằm ngoài kích thước màn hình."
        }
        return if (pixelSize == 1) 0.0 else pixel / (pixelSize - 1).toDouble()
    }

    private fun validateDensity(density: Double) {
        require(density.isFinite() && density > 0.0) {
            "Mật độ màn hình phải là số hữu hạn lớn hơn 0."
        }
    }
}
