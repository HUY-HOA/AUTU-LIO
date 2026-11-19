package com.autulio.app.accessibility

import android.graphics.Bitmap
import android.graphics.Rect

internal data class ScreenVisionTextItem(
    val text: String,
    val bounds: Rect
)

/**
 * Optional on-device ML vision adapter.
 *
 * The development build deliberately excludes its implementation and all ML Kit
 * dependencies. The public build adds the implementation through
 * -PAUTU_LIO_ENABLE_ML_VISION=true.
 */
internal interface ScreenVisionEngine {
    fun recognizeScreenObjects(bitmap: Bitmap): List<Any?>
    fun recognizeScreenImageLabels(bitmap: Bitmap): List<String>
    fun scanScreenBarcodes(bitmap: Bitmap, qrOnly: Boolean): List<String>
    fun scanScreenBarcodeDetails(bitmap: Bitmap, qrOnly: Boolean): List<Any?>
    fun readText(bitmap: Bitmap): String
    fun findText(bitmap: Bitmap, query: String): Rect?
    fun recognizeStructuredScreenText(bitmap: Bitmap): List<Any?>
    fun recognizeScreenTextItems(bitmap: Bitmap): List<ScreenVisionTextItem>
    fun close()
}