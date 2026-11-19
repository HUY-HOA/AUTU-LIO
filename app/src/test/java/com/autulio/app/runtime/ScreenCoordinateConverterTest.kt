package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCoordinateConverterTest {
    @Test
    fun convertsDpToPixelsUsingCurrentDensity() {
        assertEquals(30.0, ScreenCoordinateConverter.dpToPx(20.0, 1.5), 0.0)
        assertEquals(63.0, ScreenCoordinateConverter.dpToPx(25.0, 2.5), 0.0)
    }

    @Test
    fun convertsPixelsBackToDp() {
        assertEquals(20.0, ScreenCoordinateConverter.pxToDp(30.0, 1.5), 0.0001)
        assertEquals(25.2, ScreenCoordinateConverter.pxToDp(63.0, 2.5), 0.0001)
    }

    @Test
    fun mapsRelativeCoordinatesToTheFirstAndLastPixels() {
        assertEquals(0.0, ScreenCoordinateConverter.ratioToPixel(0.0, 1080), 0.0)
        assertEquals(1079.0, ScreenCoordinateConverter.ratioToPixel(1.0, 1080), 0.0)
        assertEquals(540.0, ScreenCoordinateConverter.ratioToPixel(0.5, 1081), 0.0)
    }

    @Test
    fun mapsPixelCoordinatesBackToRelativeRatios() {
        assertEquals(0.0, ScreenCoordinateConverter.pixelToRatio(0.0, 1080), 0.0)
        assertEquals(1.0, ScreenCoordinateConverter.pixelToRatio(1079.0, 1080), 0.0)
        assertEquals(0.5, ScreenCoordinateConverter.pixelToRatio(540.0, 1081), 0.0)
    }

    @Test
    fun handlesOnePixelDimensionWithoutDivisionByZero() {
        assertEquals(0.0, ScreenCoordinateConverter.ratioToPixel(1.0, 1), 0.0)
        assertEquals(0.0, ScreenCoordinateConverter.pixelToRatio(0.0, 1), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRelativeRatioOutsideZeroToOne() {
        ScreenCoordinateConverter.ratioToPixel(1.01, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelsOutsideDisplayBounds() {
        ScreenCoordinateConverter.pixelToRatio(100.0, 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDisplayDensity() {
        ScreenCoordinateConverter.dpToPx(10.0, 0.0)
    }
}
