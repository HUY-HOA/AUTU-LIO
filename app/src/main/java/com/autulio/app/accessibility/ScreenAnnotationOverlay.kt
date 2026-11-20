package com.autulio.app.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

sealed interface ScreenAnnotation {
    val color: Int
    val strokeWidthPx: Float

    data class Text(
        val value: String,
        val x: Float,
        val baselineY: Float,
        val textSizePx: Float,
        override val color: Int
    ) : ScreenAnnotation {
        override val strokeWidthPx: Float = 0f
    }

    data class Rectangle(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        override val color: Int,
        override val strokeWidthPx: Float
    ) : ScreenAnnotation

    data class Circle(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        override val color: Int,
        override val strokeWidthPx: Float
    ) : ScreenAnnotation

    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val color: Int,
        override val strokeWidthPx: Float
    ) : ScreenAnnotation

    data class Arrow(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val color: Int,
        override val strokeWidthPx: Float
    ) : ScreenAnnotation
}

/**
 * Full-screen, transparent and non-touchable annotation layer.
 * Touch input passes through to the application below this window.
 */
class ScreenAnnotationOverlay(
    context: Context,
    private val windowManager: WindowManager
) {
    private val view = ScreenAnnotationView(context)
    private var attached = false
    private val annotations = mutableListOf<ScreenAnnotation>()

    fun add(annotation: ScreenAnnotation): Boolean = runCatching {
        if (!attached) attach()
        require(annotations.size < MAX_ANNOTATIONS) {
            "Đã đạt giới hạn đánh dấu trên màn hình. Hãy xóa bớt rồi thử lại."
        }
        annotations += annotation
        view.setAnnotations(annotations.toList())
        true
    }.getOrDefault(false)

    fun clear() {
        annotations.clear()
        if (attached) view.setAnnotations(emptyList())
    }

    /** Temporarily hides annotations while AUTU-LIO opens its own editing screens. */
    fun hide() {
        if (!attached) return
        val removed = runCatching {
            windowManager.removeView(view)
            true
        }.getOrDefault(false)
        if (removed || view.parent == null) attached = false
    }

    /** Restores annotations after returning to another application. */
    fun show(): Boolean = runCatching {
        if (annotations.isEmpty()) {
            true
        } else {
            if (!attached) attach()
            view.setAnnotations(annotations.toList())
            true
        }
    }.getOrDefault(false)

    fun remove() {
        hide()
        annotations.clear()
    }

    private fun attach() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(view, params)
        attached = true
    }

    private companion object {
        const val MAX_ANNOTATIONS = 200
    }
}

private class ScreenAnnotationView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var items: List<ScreenAnnotation> = emptyList()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
    }

    fun setAnnotations(annotations: List<ScreenAnnotation>) {
        items = annotations
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        items.forEach { item ->
            when (item) {
                is ScreenAnnotation.Text -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.FILL
                    paint.color = item.color
                    paint.textSize = item.textSizePx
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(item.value, item.x, item.baselineY, paint)
                }
                is ScreenAnnotation.Rectangle -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = item.strokeWidthPx
                    paint.color = item.color
                    canvas.drawRect(
                        item.x,
                        item.y,
                        item.x + item.width,
                        item.y + item.height,
                        paint
                    )
                }
                is ScreenAnnotation.Circle -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = item.strokeWidthPx
                    paint.color = item.color
                    canvas.drawCircle(
                        item.centerX,
                        item.centerY,
                        item.radius,
                        paint
                    )
                }
                is ScreenAnnotation.Line -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = item.strokeWidthPx
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = item.color
                    canvas.drawLine(
                        item.startX,
                        item.startY,
                        item.endX,
                        item.endY,
                        paint
                    )
                }
                is ScreenAnnotation.Arrow -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = item.strokeWidthPx
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = item.color
                    canvas.drawLine(
                        item.startX,
                        item.startY,
                        item.endX,
                        item.endY,
                        paint
                    )
                    val angle = atan2(
                        (item.endY - item.startY).toDouble(),
                        (item.endX - item.startX).toDouble()
                    )
                    val headLength = max(item.strokeWidthPx * 4f, 12f)
                    val backAngle = angle + PI
                    val leftX = item.endX + headLength * cos(backAngle - PI / 6.0).toFloat()
                    val leftY = item.endY + headLength * sin(backAngle - PI / 6.0).toFloat()
                    val rightX = item.endX + headLength * cos(backAngle + PI / 6.0).toFloat()
                    val rightY = item.endY + headLength * sin(backAngle + PI / 6.0).toFloat()
                    canvas.drawLine(item.endX, item.endY, leftX, leftY, paint)
                    canvas.drawLine(item.endX, item.endY, rightX, rightY, paint)
                }
            }
        }
    }
}
