package com.focusfade.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Renders layered fullscreen visual feedback during delayed app launch.
 */
class DelayVisualFeedbackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var riskLevel = 0.6f
    private var frameTick = 0
    private var vignetteShader: RadialGradient? = null

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val mosaicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val slatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }

    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(160, 255, 255, 255)
    }

    fun setProgress(progressFraction: Float) {
        progress = progressFraction.coerceIn(0f, 1f)
        frameTick++
        invalidate()
    }

    fun setRiskLevel(risk: Float) {
        riskLevel = risk.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateVignetteShader(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        drawDimLayer(canvas, width, height)
        drawColorWash(canvas, width, height)
        drawMosaic(canvas, width, height)
        drawVignette(canvas, width, height)
        drawWallBarrier(canvas, width, height)
    }

    private fun drawDimLayer(canvas: Canvas, width: Float, height: Float) {
        val alpha = (170f - (progress * 60f)).toInt().coerceIn(80, 210)
        dimPaint.color = Color.argb(alpha, 0, 0, 0)
        canvas.drawRect(0f, 0f, width, height, dimPaint)
    }

    private fun drawColorWash(canvas: Canvas, width: Float, height: Float) {
        val amber = Color.rgb(194, 130, 36)
        val red = Color.rgb(194, 49, 45)
        val washColor = lerpColor(amber, red, riskLevel)
        val alpha = (68f + ((1f - progress) * 55f)).toInt().coerceIn(40, 125)
        washPaint.color = Color.argb(alpha, Color.red(washColor), Color.green(washColor), Color.blue(washColor))
        canvas.drawRect(0f, 0f, width, height, washPaint)
    }

    private fun drawMosaic(canvas: Canvas, width: Float, height: Float) {
        val tileSize = (44f - (progress * 18f)).coerceAtLeast(20f)
        val columns = (width / tileSize).toInt() + 1
        val rows = (height / tileSize).toInt() + 1
        val leftOffset = (frameTick % 3) * 2f
        val topOffset = ((frameTick + 1) % 3) * 2f

        for (x in 0..columns) {
            for (y in 0..rows) {
                val noise = ((x * 73856093) xor (y * 19349663) xor (frameTick * 83492791))
                val alpha = 15 + (noise and 0x1F)
                mosaicPaint.color = Color.argb(alpha, 20, 20, 20)
                val left = (x * tileSize) - leftOffset
                val top = (y * tileSize) - topOffset
                canvas.drawRect(left, top, left + tileSize, top + tileSize, mosaicPaint)
            }
        }
    }

    private fun drawVignette(canvas: Canvas, width: Float, height: Float) {
        vignettePaint.shader = vignetteShader
        vignettePaint.alpha = (150f - (progress * 40f)).toInt().coerceIn(95, 180)
        canvas.drawRect(0f, 0f, width, height, vignettePaint)
    }

    private fun drawWallBarrier(canvas: Canvas, width: Float, height: Float) {
        val openingProgress = progress.coerceIn(0f, 1f)
        val panelWidth = (width / 2f) * (1f - (openingProgress * 0.92f))
        val panelAlpha = (205f - (openingProgress * 130f)).toInt().coerceIn(70, 220)
        wallPaint.color = Color.argb(panelAlpha, 14, 14, 14)

        canvas.drawRect(0f, 0f, panelWidth, height, wallPaint)
        canvas.drawRect(width - panelWidth, 0f, width, height, wallPaint)

        val slatSpacing = 34f
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, panelWidth, y, slatPaint)
            canvas.drawLine(width - panelWidth, y, width, y, slatPaint)
            y += slatSpacing
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val crackLength = min(width, height) * 0.22f * (1f - openingProgress)
        if (crackLength <= 1f) return

        canvas.drawLine(centerX - 10f, centerY, centerX - crackLength, centerY - crackLength * 0.45f, crackPaint)
        canvas.drawLine(centerX - 12f, centerY + 8f, centerX - crackLength * 0.9f, centerY + crackLength * 0.35f, crackPaint)
        canvas.drawLine(centerX + 10f, centerY, centerX + crackLength, centerY - crackLength * 0.42f, crackPaint)
        canvas.drawLine(centerX + 8f, centerY + 10f, centerX + crackLength * 0.87f, centerY + crackLength * 0.4f, crackPaint)
    }

    private fun updateVignetteShader(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val radius = min(width, height) * 0.92f
        vignetteShader = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(
                Color.argb(0, 0, 0, 0),
                Color.argb(150, 0, 0, 0)
            ),
            floatArrayOf(0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun lerpColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val r = (Color.red(startColor) + ((Color.red(endColor) - Color.red(startColor)) * clamped)).toInt()
        val g = (Color.green(startColor) + ((Color.green(endColor) - Color.green(startColor)) * clamped)).toInt()
        val b = (Color.blue(startColor) + ((Color.blue(endColor) - Color.blue(startColor)) * clamped)).toInt()
        return Color.rgb(r, g, b)
    }
}
