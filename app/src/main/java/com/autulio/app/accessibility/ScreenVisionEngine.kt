package com.autulio.app.accessibility

import android.graphics.Bitmap
import android.graphics.Rect

internal data class ScreenVisionTextItem(
    val text: String,
    val bounds: Rect
)

/** On-device ML Kit adapter for Android screen vision features. */
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