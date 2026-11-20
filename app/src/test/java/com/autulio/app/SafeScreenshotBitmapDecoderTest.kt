package com.autulio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeScreenshotBitmapDecoderTest {
    @Test
    fun keepsNormalPhoneScreenshotsAtOriginalResolution() {
        assertEquals(
            1,
            SafeScreenshotBitmapDecoder.calculateInSampleSize(1440, 3200)
        )
        assertEquals(
            1,
            SafeScreenshotBitmapDecoder.calculateInSampleSize(3840, 2160)
        )
    }

    @Test
    fun downsamplesLargePhotosWithinPixelAndSideLimits() {
        val width = 20_000
        val height = 12_000
        val sampleSize = SafeScreenshotBitmapDecoder.calculateInSampleSize(width, height)

        val sampledWidth = (width.toLong() + sampleSize - 1L) / sampleSize
        val sampledHeight = (height.toLong() + sampleSize - 1L) / sampleSize

        assertTrue(sampleSize > 1)
        assertTrue(sampledWidth <= SafeScreenshotBitmapDecoder.MAX_DECODED_SIDE)
        assertTrue(sampledHeight <= SafeScreenshotBitmapDecoder.MAX_DECODED_SIDE)
        assertTrue(
            sampledWidth * sampledHeight <=
                SafeScreenshotBitmapDecoder.MAX_DECODED_PIXELS
        )
    }

    @Test
    fun usesPowerOfTwoSamplingFactors() {
        val sampleSize = SafeScreenshotBitmapDecoder.calculateInSampleSize(
            width = 16_000,
            height = 2_000
        )
        assertTrue(sampleSize > 0)
        assertEquals(0, sampleSize and (sampleSize - 1))
    }

    @Test
    fun rejectsInvalidDimensionsAndLimits() {
        val invalidDimensions = runCatching {
            SafeScreenshotBitmapDecoder.calculateInSampleSize(0, 100)
        }
        val invalidLimits = runCatching {
            SafeScreenshotBitmapDecoder.calculateInSampleSize(100, 100, maxPixels = 0)
        }

        assertTrue(invalidDimensions.isFailure)
        assertTrue(invalidLimits.isFailure)
    }
}
