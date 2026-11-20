package com.autulio.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@runCatching null

        DecodedScreenshotBitmap(
            bitmap = bitmap,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sampleSize = sampleSize
        )
    }.getOrNull()
}
