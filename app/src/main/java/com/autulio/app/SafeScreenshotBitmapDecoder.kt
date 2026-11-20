package com.autulio.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

internal data class DecodedScreenshotBitmap(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sampleSize: Int
) {
    val wasDownsampled: Boolean
        get() = sampleSize > 1
}

internal data class ExifOrientationTransform(
    val matrixValues: FloatArray,
    val outputWidth: Int,
    val outputHeight: Int
)

/**
 * Bounds memory use when opening imported photos while leaving source files untouched.
 * The returned bitmap is intended for on-device editing and preview.
 */
internal object SafeScreenshotBitmapDecoder {
    const val MAX_DECODED_PIXELS = 12_000_000L
    const val MAX_DECODED_SIDE = 8_192

    fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxPixels: Long = MAX_DECODED_PIXELS,
        maxSide: Int = MAX_DECODED_SIDE
    ): Int {
        require(width > 0 && height > 0) { "Kích thước ảnh không hợp lệ." }
        require(maxPixels > 0L && maxSide > 0) { "Giới hạn ảnh không hợp lệ." }

        var sampleSize = 1
        while (true) {
            val divisor = sampleSize.toLong()
            val sampledWidth = (width.toLong() + divisor - 1L) / divisor
            val sampledHeight = (height.toLong() + divisor - 1L) / divisor
            val sampledPixels = sampledWidth * sampledHeight
            if (
                sampledWidth <= maxSide.toLong() &&
                sampledHeight <= maxSide.toLong() &&
                sampledPixels <= maxPixels
            ) {
                return sampleSize
            }
            check(sampleSize <= Int.MAX_VALUE / 2) {
                "Không thể tính kích thước giải mã ảnh an toàn."
            }
            sampleSize *= 2
        }
    }

    internal fun orientationTransform(
        orientation: Int,
        width: Int,
        height: Int
    ): ExifOrientationTransform? {
        if (width <= 0 || height <= 0) return null
        val w = width.toFloat()
        val h = height.toFloat()
        val swapped = orientation in ExifInterface.ORIENTATION_TRANSPOSE..
            ExifInterface.ORIENTATION_ROTATE_270 &&
            orientation != ExifInterface.ORIENTATION_NORMAL

        val values = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> floatArrayOf(
                -1f, 0f, w,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_ROTATE_180 -> floatArrayOf(
                -1f, 0f, w,
                0f, -1f, h,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> floatArrayOf(
                1f, 0f, 0f,
                0f, -1f, h,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_TRANSPOSE -> floatArrayOf(
                0f, 1f, 0f,
                1f, 0f, 0f,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_ROTATE_90 -> floatArrayOf(
                0f, -1f, h,
                1f, 0f, 0f,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_TRANSVERSE -> floatArrayOf(
                0f, -1f, h,
                -1f, 0f, w,
                0f, 0f, 1f
            )
            ExifInterface.ORIENTATION_ROTATE_270 -> floatArrayOf(
                0f, 1f, 0f,
                -1f, 0f, w,
                0f, 0f, 1f
            )
            else -> return null
        }

        return ExifOrientationTransform(
            matrixValues = values,
            outputWidth = if (swapped) height else width,
            outputHeight = if (swapped) width else height
        )
    }

    fun decode(path: String): DecodedScreenshotBitmap? = runCatching {
        if (path.isBlank()) return@runCatching null
        val file = File(path)
        if (!file.isFile) return@runCatching null

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return@runCatching null

        val sampleSize = calculateInSampleSize(sourceWidth, sourceHeight)
        val decodedBitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@runCatching null

        var displayBitmap = decodedBitmap
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val transform = orientationTransform(
            orientation,
            decodedBitmap.width,
            decodedBitmap.height
        )
        if (transform != null) {
            val matrix = Matrix().apply { setValues(transform.matrixValues) }
            val orientedBitmap = runCatching {
                Bitmap.createBitmap(
                    decodedBitmap,
                    0,
                    0,
                    decodedBitmap.width,
                    decodedBitmap.height,
                    matrix,
                    true
                )
            }.getOrNull()
            if (orientedBitmap != null && orientedBitmap !== decodedBitmap) {
                displayBitmap = orientedBitmap
                decodedBitmap.recycle()
            }
        }

        DecodedScreenshotBitmap(
            bitmap = displayBitmap,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sampleSize = sampleSize
        )
    }.getOrNull()
}
