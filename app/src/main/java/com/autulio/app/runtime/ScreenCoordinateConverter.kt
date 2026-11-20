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

    /**
     * Maps a 0..1 ratio onto the inclusive pixel range of a safe-area axis.
     * [safeStart] is the inset from the logical screen origin.
     */
    fun safeRatioToPixel(ratio: Double, safeStart: Int, safeSize: Int): Double {
        require(safeStart >= 0 && safeSize > 0) {
            "Vùng an toàn phải có kích thước dương và lề không âm."
        }
        require(ratio.isFinite() && ratio in 0.0..1.0) {
            "Tỷ lệ tọa độ vùng an toàn phải nằm trong khoảng 0 đến 1."
        }
        return safeStart + (ratio * (safeSize - 1).toDouble()).roundToInt().toDouble()
    }

    /** Converts a logical-screen pixel into a normalized 0..1 safe-area coordinate. */
    fun pixelToSafeRatio(pixel: Double, safeStart: Int, safeSize: Int): Double {
        require(safeStart >= 0 && safeSize > 0) {
            "Vùng an toàn phải có kích thước dương và lề không âm."
        }
        val safeEnd = safeStart.toLong() + safeSize.toLong() - 1L
        require(
            pixel.isFinite() &&
                pixel >= safeStart.toDouble() &&
                pixel <= safeEnd.toDouble()
        ) {
            "Tọa độ pixel nằm ngoài vùng an toàn."
        }
        return if (safeSize == 1) 0.0 else
            (pixel - safeStart.toDouble()) / (safeSize - 1).toDouble()
    }

    /**
     * Converts coordinates from the display's natural (ROTATION_0) coordinate
     * space into the current logical display coordinate space.
     */
    fun naturalToCurrent(
        x: Double,
        y: Double,
        currentWidthPx: Int,
        currentHeightPx: Int,
        rotation: Int
    ): Pair<Double, Double> {
        validateRotation(rotation)
        require(currentWidthPx > 0 && currentHeightPx > 0) {
            "Kích thước màn hình phải lớn hơn 0."
        }
        val naturalWidth = if (rotation % 2 == 0) currentWidthPx else currentHeightPx
        val naturalHeight = if (rotation % 2 == 0) currentHeightPx else currentWidthPx
        validatePoint(x, y, naturalWidth, naturalHeight, "tọa độ hướng gốc")
        return when (rotation) {
            0 -> x to y
            1 -> y to (naturalWidth - 1.0 - x)
            2 -> (naturalWidth - 1.0 - x) to (naturalHeight - 1.0 - y)
            else -> (naturalHeight - 1.0 - y) to x
        }
    }

    /** Converts current logical display coordinates back to natural orientation. */
    fun currentToNatural(
        x: Double,
        y: Double,
        currentWidthPx: Int,
        currentHeightPx: Int,
        rotation: Int
    ): Pair<Double, Double> {
        validateRotation(rotation)
        require(currentWidthPx > 0 && currentHeightPx > 0) {
            "Kích thước màn hình phải lớn hơn 0."
        }
        validatePoint(x, y, currentWidthPx, currentHeightPx, "tọa độ màn hình hiện tại")
        val naturalWidth = if (rotation % 2 == 0) currentWidthPx else currentHeightPx
        val naturalHeight = if (rotation % 2 == 0) currentHeightPx else currentWidthPx
        return when (rotation) {
            0 -> x to y
            1 -> (naturalWidth - 1.0 - y) to x
            2 -> (naturalWidth - 1.0 - x) to (naturalHeight - 1.0 - y)
            else -> y to (naturalHeight - 1.0 - x)
        }
    }

    private fun validateRotation(rotation: Int) {
        require(rotation in 0..3) { "Mã xoay màn hình phải từ 0 đến 3." }
    }

    private fun validatePoint(
        x: Double,
        y: Double,
        width: Int,
        height: Int,
        description: String
    ) {
        require(
            x.isFinite() && y.isFinite() &&
                x >= 0.0 && y >= 0.0 &&
                x <= (width - 1).toDouble() &&
                y <= (height - 1).toDouble()
        ) {
            "$description nằm ngoài kích thước ${width}x$height."
        }
    }

    private fun validateDensity(density: Double) {
        require(density.isFinite() && density > 0.0) {
            "Mật độ màn hình phải là số hữu hạn lớn hơn 0."
        }
    }
}
