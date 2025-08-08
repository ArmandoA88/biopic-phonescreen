package com.focusfade.app.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.focusfade.app.R
import kotlin.math.min

/**
 * Custom view showing an animated hourglass that fills up according to blur percentage.
 */
class HourglassProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress: Float = 0f // 0 to 100
    private val paintOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.WHITE
    }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.CYAN // Changed to direct color to avoid missing resource error
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val pathOutline = Path()

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h) * 0.8f
        val cx = w / 2
        val cy = h / 2

        // Draw hourglass outline
        pathOutline.reset()
        pathOutline.moveTo(cx - size / 2, cy - size / 2)
        pathOutline.lineTo(cx + size / 2, cy - size / 2)
        pathOutline.lineTo(cx - size / 2, cy + size / 2)
        pathOutline.lineTo(cx + size / 2, cy + size / 2)
        pathOutline.close()
        canvas.drawPath(pathOutline, paintOutline)

        // Calculate fill area for the hourglass
        val fillHeight = (progress / 100f) * size
        val fillTop = cy + size / 2 - fillHeight
        canvas.drawRect(cx - size / 2 + 8, fillTop, cx + size / 2 - 8, cy + size / 2 - 8, paintFill)

        // Draw percentage text
        val textY = cy - (paintText.descent() + paintText.ascent()) / 2
        canvas.drawText("${progress.toInt()}%", cx, textY, paintText)
    }
}
