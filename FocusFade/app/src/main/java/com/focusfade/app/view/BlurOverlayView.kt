package com.focusfade.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * Custom view that renders the blur overlay effect
 */
class BlurOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var currentBlurLevel = 0f
    private var targetBlurLevel = 0f
    
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 0
    }
    
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    
    private var blurAnimator: ValueAnimator? = null
    
    // Blur effect parameters
    private val maxBlurRadius = 25f
    private val blurSteps = 20
    private val blurRects = mutableListOf<RectF>()
    private val blurPath = Path()
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setupBlurEffect()
    }
    
    private fun setupBlurEffect() {
        // Pre-calculate blur rectangles for performance
        val stepSize = maxBlurRadius / blurSteps
        for (i in 0 until blurSteps) {
            blurRects.add(RectF())
        }
    }
    
    /**
     * Updates the blur level with smooth animation
     */
    fun updateBlurLevel(newBlurLevel: Float, animate: Boolean = true) {
        val clampedLevel = newBlurLevel.coerceIn(0f, 100f)
        
        if (clampedLevel == targetBlurLevel) return
        
        targetBlurLevel = clampedLevel
        
        if (animate) {
            animateToBlurLevel(clampedLevel)
        } else {
            currentBlurLevel = clampedLevel
            updateOverlayAlpha()
            invalidate()
        }
    }
    
    private fun animateToBlurLevel(targetLevel: Float) {
        blurAnimator?.cancel()
        
        val startLevel = currentBlurLevel
        val duration = calculateAnimationDuration(startLevel, targetLevel)
        
        blurAnimator = ValueAnimator.ofFloat(startLevel, targetLevel).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                currentBlurLevel = animator.animatedValue as Float
                updateOverlayAlpha()
                invalidate()
            }
            
            start()
        }
    }
    
    private fun calculateAnimationDuration(start: Float, target: Float): Long {
        val blurChange = kotlin.math.abs(target - start)
        return when {
            blurChange <= 10f -> 300L
            blurChange >= 90f -> 1500L
            else -> (300L + (blurChange / 10f) * 150L).toLong()
        }
    }
    
    private fun updateOverlayAlpha() {
        // Convert blur level (0-100) to alpha (0-255)
        val alpha = (currentBlurLevel * 2.55f).toInt().coerceIn(0, 255)
        overlayPaint.alpha = alpha
        blurPaint.alpha = alpha
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (currentBlurLevel <= 0f) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        if (width <= 0 || height <= 0) return
        
        // Draw blur effect using multiple overlapping rectangles
        drawBlurEffect(canvas, width, height)
        
        // Draw subtle overlay for additional blur simulation
        drawOverlay(canvas, width, height)
    }
    
    private fun drawBlurEffect(canvas: Canvas, width: Float, height: Float) {
        val blurIntensity = currentBlurLevel / 100f
        val effectiveBlurRadius = maxBlurRadius * blurIntensity
        
        if (effectiveBlurRadius <= 0f) return
        
        val stepSize = effectiveBlurRadius / blurSteps
        val baseAlpha = (blurIntensity * 15f).toInt().coerceIn(0, 30)
        
        // Draw multiple layers of slightly offset rectangles to simulate blur
        for (i in 0 until min(blurSteps, (effectiveBlurRadius / stepSize).toInt())) {
            val offset = i * stepSize
            val layerAlpha = baseAlpha - (i * 2).coerceAtLeast(0)
            
            if (layerAlpha <= 0) break
            
            blurPaint.alpha = layerAlpha
            
            // Create slightly inset rectangles for each layer
            val rect = blurRects[i]
            rect.set(
                offset,
                offset,
                width - offset,
                height - offset
            )
            
            canvas.drawRect(rect, blurPaint)
        }
    }
    
    private fun drawOverlay(canvas: Canvas, width: Float, height: Float) {
        if (currentBlurLevel > 20f) {
            // Add a subtle white overlay for higher blur levels
            val overlayAlpha = ((currentBlurLevel - 20f) * 1.5f).toInt().coerceIn(0, 120)
            overlayPaint.alpha = overlayAlpha
            canvas.drawRect(0f, 0f, width, height, overlayPaint)
        }
    }
    
    /**
     * Creates a more sophisticated blur effect using paths
     */
    private fun drawAdvancedBlurEffect(canvas: Canvas, width: Float, height: Float) {
        val blurIntensity = currentBlurLevel / 100f
        
        if (blurIntensity <= 0f) return
        
        blurPath.reset()
        
        // Create organic blur shapes
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = min(width, height) * 0.4f * blurIntensity
        
        // Draw multiple concentric blur circles
        for (i in 0 until 5) {
            val radius = maxRadius * (1f - i * 0.15f)
            val alpha = (blurIntensity * 25f * (1f - i * 0.2f)).toInt().coerceIn(0, 50)
            
            if (alpha <= 0 || radius <= 0) continue
            
            blurPaint.alpha = alpha
            canvas.drawCircle(centerX, centerY, radius, blurPaint)
        }
    }
    
    /**
     * Gets the current blur level
     */
    fun getCurrentBlurLevel(): Float = currentBlurLevel
    
    /**
     * Gets the target blur level
     */
    fun getTargetBlurLevel(): Float = targetBlurLevel
    
    /**
     * Immediately sets blur level without animation
     */
    fun setBlurLevelImmediate(blurLevel: Float) {
        updateBlurLevel(blurLevel, animate = false)
    }
    
    /**
     * Clears the blur effect
     */
    fun clearBlur() {
        updateBlurLevel(0f, animate = true)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurAnimator?.cancel()
    }
}
