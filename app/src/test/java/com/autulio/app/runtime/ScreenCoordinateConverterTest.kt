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

    @Test
    fun mapsRatiosAcrossSafeAreaEdgesAndConvertsBack() {
        assertEquals(10.0, ScreenCoordinateConverter.safeRatioToPixel(0.0, 10, 11), 0.0)
        assertEquals(15.0, ScreenCoordinateConverter.safeRatioToPixel(0.5, 10, 11), 0.0)
        assertEquals(20.0, ScreenCoordinateConverter.safeRatioToPixel(1.0, 10, 11), 0.0)
        assertEquals(0.5, ScreenCoordinateConverter.pixelToSafeRatio(15.0, 10, 11), 0.0)
    }

    @Test
    fun handlesOnePixelSafeArea() {
        assertEquals(7.0, ScreenCoordinateConverter.safeRatioToPixel(1.0, 7, 1), 0.0)
        assertEquals(0.0, ScreenCoordinateConverter.pixelToSafeRatio(7.0, 7, 1), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelsOutsideSafeArea() {
        ScreenCoordinateConverter.pixelToSafeRatio(9.0, 10, 11)
    }

    @Test
    fun mapsNaturalCoordinatesToAllCurrentRotationsAndBack() {
        val naturalWidth = 1080
        val naturalHeight = 2400
        val x = 100.0
        val y = 200.0
        val cases = listOf(
            Triple(0, naturalWidth to naturalHeight, 100.0 to 200.0),
            Triple(1, naturalHeight to naturalWidth, 200.0 to 979.0),
            Triple(2, naturalWidth to naturalHeight, 979.0 to 2199.0),
            Triple(3, naturalHeight to naturalWidth, 2199.0 to 100.0)
        )

        cases.forEach { (rotation, dimensions, expected) ->
            val current = ScreenCoordinateConverter.naturalToCurrent(
                x, y, dimensions.first, dimensions.second, rotation
            )
            assertEquals(expected.first, current.first, 0.0)
            assertEquals(expected.second, current.second, 0.0)

            val natural = ScreenCoordinateConverter.currentToNatural(
                current.first, current.second, dimensions.first, dimensions.second, rotation
            )
            assertEquals(x, natural.first, 0.0)
            assertEquals(y, natural.second, 0.0)
        }
    }

    @Test
    fun supportsLandscapeNaturalOrientation() {
        val current = ScreenCoordinateConverter.naturalToCurrent(
            100.0, 200.0, currentWidthPx = 1080, currentHeightPx = 2400, rotation = 1
        )
        assertEquals(200.0, current.first, 0.0)
        assertEquals(2399.0, current.second, 0.0)
        val natural = ScreenCoordinateConverter.currentToNatural(
            current.first, current.second, 1080, 2400, 1
        )
        assertEquals(100.0, natural.first, 0.0)
        assertEquals(200.0, natural.second, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDisplayDensity() {
        ScreenCoordinateConverter.dpToPx(10.0, 0.0)
    }
}
