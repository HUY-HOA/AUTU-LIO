package com.autulio.app.runtime

/**
 * Display measurements and safe-area information in physical pixels.
 *
 * Cutout detection is nullable on Android versions or window contexts where Android
 * cannot provide reliable DisplayCutout data. An unknown value is never treated as
 * a confirmed display without a cutout.
 */
data class ScreenInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "Thông số vùng an toàn không được âm."
        }
    }
}

data class ScreenCutoutBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    init {
        require(right >= left && bottom >= top) {
            "Tọa độ vùng khuyết màn hình không hợp lệ."
        }
    }
}

data class ScreenDisplayGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val systemInsets: ScreenInsets = ScreenInsets(),
    val cutoutSafeInsets: ScreenInsets = ScreenInsets(),
    val cutoutDetectionKnown: Boolean,
    val cutoutBounds: List<ScreenCutoutBounds> = emptyList(),
    /** Android display rotation: 0, 1, 2, or 3 (90-degree increments). */
    val rotation: Int? = null
) {
    init {
        require(widthPx > 0 && heightPx > 0) {
            "Kích thước màn hình phải lớn hơn 0."
        }
        require(rotation == null || rotation in 0..3) {
            "Mã xoay màn hình phải từ 0 đến 3."
        }
    }

    /** Combined insets that keep content clear of system bars and display cutouts. */
    val safeInsets: ScreenInsets
        get() = ScreenInsets(
            left = maxOf(systemInsets.left, cutoutSafeInsets.left),
            top = maxOf(systemInsets.top, cutoutSafeInsets.top),
            right = maxOf(systemInsets.right, cutoutSafeInsets.right),
            bottom = maxOf(systemInsets.bottom, cutoutSafeInsets.bottom)
        )

    val safeWidthPx: Int
        get() = (widthPx - safeInsets.left - safeInsets.right).coerceAtLeast(0)

    val safeHeightPx: Int
        get() = (heightPx - safeInsets.top - safeInsets.bottom).coerceAtLeast(0)

    /** Null means that this Android version/window cannot confirm cutout presence. */
    val hasCutout: Boolean?
        get() = if (!cutoutDetectionKnown) {
            null
        } else {
            cutoutBounds.isNotEmpty() ||
                cutoutSafeInsets.left > 0 ||
                cutoutSafeInsets.top > 0 ||
                cutoutSafeInsets.right > 0 ||
                cutoutSafeInsets.bottom > 0
        }

    val cutoutCount: Int?
        get() = if (cutoutDetectionKnown) cutoutBounds.size else null

    /** Rectangles are flattened as left, top, right, bottom for each cutout. */
    fun flattenedCutoutBounds(): List<Int> = cutoutBounds.flatMap { bounds ->
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    companion object {
        fun unknown(
            widthPx: Int,
            heightPx: Int,
            systemInsets: ScreenInsets = ScreenInsets(),
            rotation: Int? = null
        ) = ScreenDisplayGeometry(
            widthPx = widthPx,
            heightPx = heightPx,
            systemInsets = systemInsets,
            cutoutDetectionKnown = false,
            rotation = rotation
        )
    }
}


/** Shared in-process cache populated by the app window and the accessibility overlay. */
object ScreenDisplayGeometryStore {
    @Volatile
    private var latest: ScreenDisplayGeometry? = null

    fun publish(geometry: ScreenDisplayGeometry) {
        latest = geometry
    }

    fun matches(
        geometry: ScreenDisplayGeometry,
        widthPx: Int,
        heightPx: Int,
        rotation: Int? = null
    ): Boolean {
        if (geometry.widthPx != widthPx || geometry.heightPx != heightPx) return false
        // A 180-degree turn can preserve dimensions while moving system bars.
        return rotation == null || geometry.rotation == rotation
    }

    fun current(
        widthPx: Int,
        heightPx: Int,
        rotation: Int? = null
    ): ScreenDisplayGeometry? = latest?.takeIf { matches(it, widthPx, heightPx, rotation) }

    fun clear() {
        latest = null
    }
}
