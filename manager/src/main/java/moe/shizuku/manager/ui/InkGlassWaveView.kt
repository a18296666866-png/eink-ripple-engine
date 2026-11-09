package moe.shizuku.manager.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Static monochrome contours inspired by INK GLASS.
 *
 * Rendering uses three opaque 16-grayscale-safe fills, no blurs, shadows,
 * translucent overlays or continuously running animations.
 * This background never takes touch events.
 */
class InkGlassWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val contour = Path()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // High white region; gently curved folds behind the title.
        fill.color = 0xFFEEEEEE.toInt()
        contour.reset()
        contour.moveTo(0f, h * 0.31f)
        contour.cubicTo(w * 0.17f, h * 0.32f, w * 0.37f, h * 0.29f,
            w * 0.53f, h * 0.22f)
        contour.cubicTo(w * 0.75f, h * 0.12f, w * 0.91f, h * 0.11f,
            w, h * 0.14f)
        contour.lineTo(w, h * 0.39f)
        contour.cubicTo(w * 0.69f, h * 0.37f, w * 0.48f, h * 0.39f,
            w * 0.25f, h * 0.35f)
        contour.close()
        canvas.drawPath(contour, fill)

        fill.color = 0xFFDDDDDD.toInt()
        contour.reset()
        contour.moveTo(w * 0.24f, h * 0.36f)
        contour.cubicTo(w * 0.43f, h * 0.30f, w * 0.61f, h * 0.24f,
            w * 0.79f, h * 0.20f)
        contour.cubicTo(w * 0.90f, h * 0.17f, w * 0.96f, h * 0.17f,
            w, h * 0.18f)
        contour.lineTo(w, h * 0.36f)
        contour.cubicTo(w * 0.70f, h * 0.35f, w * 0.48f, h * 0.43f,
            w * 0.24f, h * 0.36f)
        contour.close()
        canvas.drawPath(contour, fill)

        // A second wave anchors the lower area, without a shadow or gradient.
        fill.color = 0xFFEEEEEE.toInt()
        contour.reset()
        contour.moveTo(0f, h * 0.84f)
        contour.cubicTo(w * 0.27f, h * 0.79f, w * 0.42f, h * 0.91f,
            w * 0.62f, h * 0.87f)
        contour.cubicTo(w * 0.81f, h * 0.83f, w * 0.92f, h * 0.84f,
            w, h * 0.87f)
        contour.lineTo(w, h)
        contour.lineTo(0f, h)
        contour.close()
        canvas.drawPath(contour, fill)
    }
}
