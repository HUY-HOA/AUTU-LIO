package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenMetricsInfoTest {
    @Test
    fun convertsPixelsToDpForCurrentDensity() {
        assertEquals(360.0, ScreenMetricsInfo.widthDp(1080, 3.0), 0.0001)
        assertEquals(800.0, ScreenMetricsInfo.heightDp(2400, 3.0), 0.0001)
    }

    @Test
    fun calculatesAspectRatioUsingRealPixelDimensions() {
        assertEquals(0.45, ScreenMetricsInfo.aspectRatio(1080, 2400), 0.0001)
        assertEquals(1.777777, ScreenMetricsInfo.aspectRatio(1920, 1080), 0.0001)
    }

    @Test
    fun identifiesPortraitLandscapeAndSquareDisplays() {
        assertEquals("DỌC", ScreenMetricsInfo.orientation(1080, 2400))
        assertEquals("NGANG", ScreenMetricsInfo.orientation(2400, 1080))
        assertEquals("VUÔNG", ScreenMetricsInfo.orientation(1200, 1200))
    }

    @Test
    fun convertsDisplayRotationToDegrees() {
        assertEquals(0, ScreenMetricsInfo.rotationDegrees(0))
        assertEquals(90, ScreenMetricsInfo.rotationDegrees(1))
        assertEquals(180, ScreenMetricsInfo.rotationDegrees(2))
        assertEquals(270, ScreenMetricsInfo.rotationDegrees(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDisplayRotation() {
        ScreenMetricsInfo.rotationDegrees(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroDisplayDimensions() {
        ScreenMetricsInfo.aspectRatio(0, 100)
    }
}
