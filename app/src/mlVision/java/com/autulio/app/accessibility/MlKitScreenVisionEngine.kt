package com.autulio.app.accessibility

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Kept outside src/main so the development APK does not package ML Kit native
 * runtimes or bundled vision models. This class is compiled only for public builds.
 */
internal class MlKitScreenVisionEngine : ScreenVisionEngine {
    companion object {
        private const val MAX_OCR_STRUCTURED_BLOCKS = 32
        private const val MAX_OCR_STRUCTURED_LINES = 40
        private const val MAX_SCREEN_BARCODE_RESULTS = 56
        private const val MAX_OCR_TEXT_VALUE_CHARS = 4_096
        private const val TASK_TIMEOUT_SECONDS = 15L
    }

    @Volatile private var textRecognizer: TextRecognizer? = null
    @Volatile private var objectDetector: ObjectDetector? = null
    @Volatile private var imageLabeler: ImageLabeler? = null
    @Volatile private var qrCodeScanner: BarcodeScanner? = null
    @Volatile private var barcodeScanner: BarcodeScanner? = null

    override fun close() {
        textRecognizer?.close()
        textRecognizer = null
        imageLabeler?.close()
        imageLabeler = null
        qrCodeScanner?.close()
        qrCodeScanner = null
        barcodeScanner?.close()
        barcodeScanner = null
        objectDetector?.close()
        objectDetector = null
    }

    private fun getObjectDetector(): ObjectDetector {
        objectDetector?.let { return it }
        return synchronized(this) {
            objectDetector ?: ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            ).also { objectDetector = it }
        }
    }

    override fun recognizeScreenObjects(bitmap: Bitmap): List<Any?> =
        Tasks.await(
            getObjectDetector().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        ).take(5).map { detected ->
            val bounds = detected.boundingBox
            val labels = detected.labels
                .sortedByDescending { it.confidence }
                .map { label ->
                    linkedMapOf<String, Any?>(
                        "TÊN" to label.text,
                        "CHỈ_SỐ" to label.index.toDouble(),
                        "ĐỘ_TIN_CẬY" to label.confidence.toDouble()
                    )
                }.toMutableList<Any?>()
            linkedMapOf<String, Any?>(
                "X" to bounds.left.toDouble(),
                "Y" to bounds.top.toDouble(),
                "RỘNG" to bounds.width().toDouble(),
                "CAO" to bounds.height().toDouble(),
                "NHÃN" to labels
            )
        }.toMutableList<Any?>()

    private fun getImageLabeler(): ImageLabeler {
        imageLabeler?.let { return it }
        return synchronized(this) {
            imageLabeler ?: ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                .also { imageLabeler = it }
        }
    }

    override fun recognizeScreenImageLabels(bitmap: Bitmap): List<String> =
        Tasks.await(
            getImageLabeler().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
            .asSequence()
            .sortedByDescending { it.confidence }
            .map { it.text }
            .distinct()
            .take(64)
            .toList()

    private fun getBarcodeScanner(qrOnly: Boolean): BarcodeScanner {
        if (qrOnly) {
            qrCodeScanner?.let { return it }
            return synchronized(this) {
                qrCodeScanner ?: BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                ).also { qrCodeScanner = it }
            }
        }
        barcodeScanner?.let { return it }
        return synchronized(this) {
            barcodeScanner ?: BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            ).also { barcodeScanner = it }
        }
    }

    override fun scanScreenBarcodes(bitmap: Bitmap, qrOnly: Boolean): List<String> =
        Tasks.await(
            getBarcodeScanner(qrOnly).process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
            .mapNotNull { it.rawValue?.takeIf(String::isNotBlank) }
            .distinct()
            .take(64)

    override fun scanScreenBarcodeDetails(bitmap: Bitmap, qrOnly: Boolean): List<Any?> =
        Tasks.await(
            getBarcodeScanner(qrOnly).process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
            .asSequence()
            .take(MAX_SCREEN_BARCODE_RESULTS)
            .map { barcode ->
                val bounds = barcode.boundingBox
                linkedMapOf<String, Any?>(
                    "GIÁ_TRỊ_GỐC" to barcode.rawValue?.take(MAX_OCR_TEXT_VALUE_CHARS),
                    "GIÁ_TRỊ_HIỂN_THỊ" to barcode.displayValue?.take(MAX_OCR_TEXT_VALUE_CHARS),
                    "ĐỊNH_DẠNG" to barcodeFormatName(barcode.format),
                    "LOẠI_NỘI_DUNG" to barcodeValueTypeName(barcode.valueType),
                    "X" to bounds?.left?.toDouble(),
                    "Y" to bounds?.top?.toDouble(),
                    "RỘNG" to bounds?.width()?.toDouble(),
                    "CAO" to bounds?.height()?.toDouble()
                )
            }
            .toList()
            .toMutableList<Any?>()

    private fun getTextRecognizer(): TextRecognizer {
        textRecognizer?.let { return it }
        return synchronized(this) {
            textRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .also { textRecognizer = it }
        }
    }

    override fun readText(bitmap: Bitmap): String =
        Tasks.await(
            getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        ).text

    override fun findText(bitmap: Bitmap, query: String): Rect? {
        val recognized = Tasks.await(
            getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        val bounds = recognized.textBlocks.firstOrNull {
            it.text.contains(query, ignoreCase = true) && it.boundingBox != null
        }?.boundingBox ?: return null
        return Rect(bounds)
    }

    override fun recognizeStructuredScreenText(bitmap: Bitmap): List<Any?> {
        val recognized = Tasks.await(
            getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        var remainingLines = MAX_OCR_STRUCTURED_LINES
        return recognized.textBlocks
            .asSequence()
            .take(MAX_OCR_STRUCTURED_BLOCKS)
            .mapNotNull { block ->
                val bounds = block.boundingBox
                val lines = block.lines
                    .asSequence()
                    .take(remainingLines)
                    .map { line ->
                        val lineBounds = line.boundingBox
                        linkedMapOf<String, Any?>(
                            "VĂN_BẢN" to line.text.take(MAX_OCR_TEXT_VALUE_CHARS),
                            "X" to lineBounds?.left?.toDouble(),
                            "Y" to lineBounds?.top?.toDouble(),
                            "RỘNG" to lineBounds?.width()?.toDouble(),
                            "CAO" to lineBounds?.height()?.toDouble()
                        )
                    }
                    .toList()
                    .toMutableList<Any?>()
                remainingLines -= lines.size
                linkedMapOf<String, Any?>(
                    "VĂN_BẢN" to block.text.take(MAX_OCR_TEXT_VALUE_CHARS),
                    "X" to bounds?.left?.toDouble(),
                    "Y" to bounds?.top?.toDouble(),
                    "RỘNG" to bounds?.width()?.toDouble(),
                    "CAO" to bounds?.height()?.toDouble(),
                    "CÁC_DÒNG" to lines
                )
            }
            .toList()
            .toMutableList<Any?>()
    }

    override fun recognizeScreenTextItems(bitmap: Bitmap): List<ScreenVisionTextItem> {
        val recognized = Tasks.await(
            getTextRecognizer().process(InputImage.fromBitmap(bitmap, 0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        return recognized.textBlocks.mapNotNull { block ->
            val text = block.text.trim()
            val bounds = block.boundingBox
            if (text.isEmpty() || bounds == null) null
            else ScreenVisionTextItem(text, Rect(bounds))
        }
    }

    private fun barcodeFormatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "MÃ_QR"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "KHÔNG_RÕ"
    }

    private fun barcodeValueTypeName(type: Int): String = when (type) {
        Barcode.TYPE_CONTACT_INFO -> "LIÊN_HỆ"
        Barcode.TYPE_EMAIL -> "EMAIL"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_PHONE -> "ĐIỆN_THOẠI"
        Barcode.TYPE_PRODUCT -> "SẢN_PHẨM"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_TEXT -> "VĂN_BẢN"
        Barcode.TYPE_URL -> "ĐƯỜNG_DẪN"
        Barcode.TYPE_WIFI -> "WI_FI"
        Barcode.TYPE_GEO -> "TỌA_ĐỘ_ĐỊA_LÝ"
        Barcode.TYPE_CALENDAR_EVENT -> "LỊCH"
        Barcode.TYPE_DRIVER_LICENSE -> "GIẤY_PHÉP_LÁI_XE"
        else -> "KHÔNG_RÕ"
    }
}