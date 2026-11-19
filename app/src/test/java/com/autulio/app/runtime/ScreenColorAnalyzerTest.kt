package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenColorAnalyzerTest {
    @Test
    fun describesArgbPixelAsRgbAndHex() {
        val result = ScreenColorAnalyzer.describe(0xFF12AB34.toInt())

        assertEquals(18.0, result["ĐỎ"])
        assertEquals(171.0, result["XANH_LÁ"])
        assertEquals(52.0, result["XANH_DƯƠNG"])
        assertEquals(255.0, result["ĐỘ_TRONG_SUỐT"])
        assertEquals("#12AB34", result["HEX"])
    }

    @Test
    fun matchesEachRgbChannelWithinConfiguredTolerance() {
        val pixel = 0xFF203040.toInt()

        assertTrue(ScreenColorAnalyzer.isNear(pixel, 32, 48, 64, 0))
        assertTrue(ScreenColorAnalyzer.isNear(pixel, 34, 46, 66, 2))
        assertFalse(ScreenColorAnalyzer.isNear(pixel, 35, 48, 64, 2))
    }

    @Test
    fun findsApproximateScreenCoordinatesFromDownsampledPixels() {
        val pixels = intArrayOf(
            0xFF000000.toInt(), 0xFF000000.toInt(),
            0xFFFF0000.toInt(), 0xFF000000.toInt()
        )

        val point = ScreenColorAnalyzer.findFirstNear(
            pixels, 2, 2, 400, 800, 255, 0, 0, 0
        )

        assertEquals(listOf(300, 600), point)
    }

    @Test
    fun calculatesMatchedPixelPercentage() {
        val pixels = intArrayOf(
            0xFFFF0000.toInt(),
            0xFFEE0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt()
        )

        val result = ScreenColorAnalyzer.matchingPercentage(
            pixels, 2, 2, 255, 0, 0, 17
        )

        assertEquals(50.0, result, 0.0001)
    }

    @Test
    fun returnsNoPointWhenSampleHasNoMatchingColor() {
        val point = ScreenColorAnalyzer.findFirstNear(
            intArrayOf(0xFF000000.toInt()), 1, 1, 100, 200, 255, 255, 255, 0
        )

        assertNull(point)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRgbValuesOutsideSupportedRange() {
        ScreenColorAnalyzer.isNear(0xFFFFFFFF.toInt(), 256, 0, 0, 0)
    }
}
