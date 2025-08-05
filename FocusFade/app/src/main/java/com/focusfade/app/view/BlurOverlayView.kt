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
        color = Color.TRANSPARENT
        alpha = 0
    }
    
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10000000") // Very subtle dark overlay for blur effect
        style = Paint.Style.FILL
    }
    
    private val frostedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#08000000") // Minimal dark tint
        style = Paint.Style.FILL
    }
    
    private val darkOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000")
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
        
        // Draw enhanced blur effect for better visibility
        drawEnhancedBlurEffect(canvas, width, height)
        
        // Draw frosted glass effect
        drawFrostedGlassEffect(canvas, width, height)
        
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
        // Remove white overlay - no additional overlay needed for pure blur effect
    }
    
    private fun drawBlurLayers(canvas: Canvas, width: Float, height: Float, blurIntensity: Float) {
        // Draw multiple stronger layers to create depth without white haze - doubled intensity
        val layerCount = 5
        val baseAlpha = (blurIntensity * 40f).toInt().coerceIn(0, 100)
        
        for (i in 0 until layerCount) {
            val layerAlpha = (baseAlpha * (1f - i * 0.2f)).toInt().coerceAtLeast(0)
            if (layerAlpha <= 0) break
            
            blurPaint.alpha = layerAlpha
            blurPaint.color = Color.parseColor("#20000000") // Stronger dark tint for better blur
            
            // Slightly offset each layer for depth
            val offset = i * 3f
            canvas.drawRect(offset, offset, width - offset, height - offset, blurPaint)
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
    
    private fun drawEnhancedBlurEffect(canvas: Canvas, width: Float, height: Float) {
        val blurIntensity = currentBlurLevel / 100f
        
        if (blurIntensity <= 0f) return
        
        // Draw a stronger blur effect - double the intensity at 100%
        val baseAlpha = (blurIntensity * 80f).toInt().coerceIn(0, 200)
        
        // Primary blur layer - stronger dark overlay to simulate blur
        blurPaint.alpha = baseAlpha
        blurPaint.color = Color.parseColor("#25000000") // Stronger dark overlay
        canvas.drawRect(0f, 0f, width, height, blurPaint)
        
        // Add multiple blur layers for depth without white coloring
        drawBlurLayers(canvas, width, height, blurIntensity)
    }
    
    private fun drawFrostedGlassEffect(canvas: Canvas, width: Float, height: Float) {
        val blurIntensity = currentBlurLevel / 100f
        
        if (blurIntensity <= 0.3f) return
        
        // Create frosted glass effect for higher blur levels
        val frostedAlpha = ((blurIntensity - 0.3f) * 100f).toInt().coerceIn(0, 70)
        frostedPaint.alpha = frostedAlpha
        
        // Draw frosted pattern
        val patternSize = 50f
        for (x in 0 until (width / patternSize).toInt() + 1) {
            for (y in 0 until (height / patternSize).toInt() + 1) {
                val startX = x * patternSize
                val startY = y * patternSize
                val endX = (startX + patternSize * 0.8f).coerceAtMost(width)
                val endY = (startY + patternSize * 0.8f).coerceAtMost(height)
                
                canvas.drawRect(startX, startY, endX, endY, frostedPaint)
            }
        }
    }
    
    private fun drawNoisePattern(canvas: Canvas, width: Float, height: Float, intensity: Float) {
        // Removed noise pattern to eliminate white haze - pure blur effect only
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
