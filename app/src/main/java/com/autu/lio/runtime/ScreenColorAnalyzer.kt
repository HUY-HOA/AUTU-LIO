package com.autu.lio.runtime

import kotlin.math.abs

/**
 * Bounded, dependency-free color analysis for sampled Android screen pixels.
 * Pixel arrays are expected in Android ARGB_8888 layout.
 */
object ScreenColorAnalyzer {
    fun describe(argb: Int): Map<String, Any?> {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        val alpha = (argb ushr 24) and 0xFF
        return linkedMapOf(
            "ĐỎ" to red.toDouble(),
            "XANH_LÁ" to green.toDouble(),
            "XANH_DƯƠNG" to blue.toDouble(),
            "ĐỘ_TRONG_SUỐT" to alpha.toDouble(),
            "HEX" to "#" + red.toString(16).padStart(2, '0').uppercase() +
                green.toString(16).padStart(2, '0').uppercase() +
                blue.toString(16).padStart(2, '0').uppercase()
        )
    }

    fun isNear(argb: Int, red: Int, green: Int, blue: Int, tolerance: Int): Boolean {
        validateTarget(red, green, blue, tolerance)
        return isNearUnchecked(argb, red, green, blue, tolerance)
    }

    fun findFirstNear(
        pixels: IntArray,
        sampleWidth: Int,
        sampleHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        red: Int,
        green: Int,
        blue: Int,
        tolerance: Int
    ): List<Int>? {
        validatePixels(pixels, sampleWidth, sampleHeight)
        require(screenWidth > 0 && screenHeight > 0) { "Kích thước màn hình không hợp lệ." }
        validateTarget(red, green, blue, tolerance)
        for (y in 0 until sampleHeight) {
            for (x in 0 until sampleWidth) {
                if (isNearUnchecked(pixels[y * sampleWidth + x], red, green, blue, tolerance)) {
                    val screenX = (((x + 0.5) * screenWidth) / sampleWidth).toInt()
                        .coerceIn(0, screenWidth - 1)
                    val screenY = (((y + 0.5) * screenHeight) / sampleHeight).toInt()
                        .coerceIn(0, screenHeight - 1)
                    return listOf(screenX, screenY)
                }
            }
        }
        return null
    }

    fun matchingPercentage(
        pixels: IntArray,
        sampleWidth: Int,
        sampleHeight: Int,
        red: Int,
        green: Int,
        blue: Int,
        tolerance: Int
    ): Double {
        validatePixels(pixels, sampleWidth, sampleHeight)
        validateTarget(red, green, blue, tolerance)
        if (pixels.isEmpty()) return 0.0
        var matches = 0
        for (pixel in pixels) {
            if (isNearUnchecked(pixel, red, green, blue, tolerance)) matches++
        }
        return matches * 100.0 / pixels.size
    }

    private fun isNearUnchecked(argb: Int, red: Int, green: Int, blue: Int, tolerance: Int): Boolean {
        val actualRed = (argb shr 16) and 0xFF
        val actualGreen = (argb shr 8) and 0xFF
        val actualBlue = argb and 0xFF
        return abs(actualRed - red) <= tolerance &&
            abs(actualGreen - green) <= tolerance &&
            abs(actualBlue - blue) <= tolerance
    }

    private fun validatePixels(pixels: IntArray, width: Int, height: Int) {
        require(width > 0 && height > 0) { "Kích thước ảnh mẫu không hợp lệ." }
        require(width.toLong() * height.toLong() == pixels.size.toLong()) {
            "Số pixel không khớp kích thước ảnh mẫu."
        }
    }

    private fun validateTarget(red: Int, green: Int, blue: Int, tolerance: Int) {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "Giá trị RGB phải nằm trong khoảng 0 đến 255."
        }
        require(tolerance in 0..255) { "Sai số màu phải nằm trong khoảng 0 đến 255." }
    }
}
