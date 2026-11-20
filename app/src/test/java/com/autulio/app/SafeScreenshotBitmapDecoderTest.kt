package com.autulio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeScreenshotBitmapDecoderTest {
    @Test
    fun mapsExifOrientationsToExpectedOutputDimensions() {
        val normal = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_NORMAL, 400, 300
        )
        val rotate90 = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_ROTATE_90, 400, 300
        )
        val rotate270 = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_ROTATE_270, 400, 300
        )
        val transpose = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_TRANSPOSE, 400, 300
        )
        val transverse = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_TRANSVERSE, 400, 300
        )

        assertEquals(null, normal)
        assertEquals(300, rotate90?.outputWidth)
        assertEquals(400, rotate90?.outputHeight)
        assertEquals(300, rotate270?.outputWidth)
        assertEquals(400, rotate270?.outputHeight)
        assertEquals(300, transpose?.outputWidth)
        assertEquals(400, transpose?.outputHeight)
        assertEquals(300, transverse?.outputWidth)
        assertEquals(400, transverse?.outputHeight)
    }

    @Test
    fun mapsExifMirroringAndRotationsToExpectedAffineMatrices() {
        val horizontal = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL, 400, 300
        )
        val vertical = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL, 400, 300
        )
        val rotate180 = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_ROTATE_180, 400, 300
        )
        val rotate90 = SafeScreenshotBitmapDecoder.orientationTransform(
            android.media.ExifInterface.ORIENTATION_ROTATE_90, 400, 300
        )

        org.junit.Assert.assertArrayEquals(
            floatArrayOf(-1f, 0f, 400f, 0f, 1f, 0f, 0f, 0f, 1f),
            horizontal?.matrixValues
        )
        org.junit.Assert.assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, -1f, 300f, 0f, 0f, 1f),
            vertical?.matrixValues
        )
        org.junit.Assert.assertArrayEquals(
            floatArrayOf(-1f, 0f, 400f, 0f, -1f, 300f, 0f, 0f, 1f),
            rotate180?.matrixValues
        )
        org.junit.Assert.assertArrayEquals(
            floatArrayOf(0f, -1f, 300f, 1f, 0f, 0f, 0f, 0f, 1f),
            rotate90?.matrixValues
        )
    }

    @Test
    fun ignoresUnsupportedExifOrientationAndInvalidDimensions() {
        assertEquals(
            null,
            SafeScreenshotBitmapDecoder.orientationTransform(0, 400, 300)
        )
        assertEquals(
            null,
            SafeScreenshotBitmapDecoder.orientationTransform(
                android.media.ExifInterface.ORIENTATION_ROTATE_90, 0, 300
            )
        )
    }

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
