package com.focusfade.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.focusfade.app.databinding.ActivityDelayedLaunchBinding
import com.focusfade.app.manager.SettingsManager
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Activity that shows a delay screen before launching a distracting app.
 * Includes layered visual feedback effects to interrupt autopilot behavior.
 */
class DelayedLaunchActivity : Activity() {

    private lateinit var binding: ActivityDelayedLaunchBinding
    private lateinit var settingsManager: SettingsManager

    private var targetPackage: String? = null
    private var timer: CountDownTimer? = null
    private var breathingAnimatorSet: AnimatorSet? = null
    private var countdownPulseAnimator: ObjectAnimator? = null
    private var invertedFlashBitmap: Bitmap? = null

    private var totalDelaySeconds: Int = 0
    private var remainingTimeMs: Long = 0
    private var startTime: Long = 0

    companion object {
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_TOTAL_DELAY = "total_delay"
        private const val KEY_START_TIME = "start_time"

        private const val STREAK_PREFS = "delay_visual_streak"
        private const val STREAK_LAST_DATE = "last_date"
        private const val STREAK_CURRENT = "current_streak"
        private const val STREAK_TOTAL_PAUSES = "total_pauses"

        private const val EXTRA_GOAL_TEXT = "GOAL_TEXT"

        private const val DEFAULT_GOAL_TEXT = "Use this app only for what matters right now."
        private val QUOTES = listOf(
            "Small pauses build strong focus.",
            "Attention is your most valuable resource.",
            "Choose intention over impulse.",
            "A short wait can save a long distraction.",
            "Protect your priorities first."
        )

        private val INVERT_COLOR_MATRIX = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        binding = ActivityDelayedLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            targetPackage = savedInstanceState.getString(KEY_TARGET_PACKAGE)
            totalDelaySeconds = savedInstanceState.getInt(KEY_TOTAL_DELAY)
            startTime = savedInstanceState.getLong(KEY_START_TIME)

            val elapsedTime = System.currentTimeMillis() - startTime
            remainingTimeMs = (totalDelaySeconds * 1000L) - elapsedTime
            if (remainingTimeMs <= 0) {
                launchTargetApp()
                return
            }
        } else {
            targetPackage = intent.getStringExtra("TARGET_PACKAGE")
            totalDelaySeconds = intent.getIntExtra("DELAY_SECONDS", settingsManager.getLaunchDelaySeconds())
            startTime = System.currentTimeMillis()
            remainingTimeMs = totalDelaySeconds * 1000L
        }

        totalDelaySeconds = totalDelaySeconds.coerceAtLeast(1)
        setupUI()
        startCountdown()
    }

    private fun setupUI() {
        val riskLevel = intent.getFloatExtra("RISK_LEVEL", (totalDelaySeconds / 20f).coerceIn(0.35f, 1f))
        binding.visualFeedbackView.setRiskLevel(riskLevel)

        applyGrayscaleMode()
        loadTargetAppPresentation()
        updateStreakBadge()
        populateGoalCard()
        startBreathingAnimation()
        startCountdownPulseAnimation()
        updateVisualState(remainingTimeMs)
        triggerContrastInversionFlash()
    }

    private fun applyGrayscaleMode() {
        val grayscalePaint = Paint().apply {
            val grayscale = ColorMatrix().apply { setSaturation(0f) }
            colorFilter = ColorMatrixColorFilter(grayscale)
        }
        binding.contentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, grayscalePaint)
    }

    private fun loadTargetAppPresentation() {
        val packageName = targetPackage ?: return
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val appIcon = packageManager.getApplicationIcon(appInfo)

            binding.appIcon.setImageDrawable(appIcon)
            binding.delayTitle.text = "Pause before opening $appName"
        } catch (_: Exception) {
            binding.delayTitle.text = "Delayed Launch"
        }
    }

    private fun updateStreakBadge() {
        val stats = recordPauseAndGetStats()
        val pauseLabel = if (stats.totalPauses == 1) "pause" else "pauses"
        binding.streakBadge.text = "${stats.currentStreak}-day streak | ${stats.totalPauses} mindful $pauseLabel"
    }

    private fun populateGoalCard() {
        val goalFromIntent = intent.getStringExtra(EXTRA_GOAL_TEXT).orEmpty().trim()
        val savedGoalText = settingsManager.getDelayGoalText().trim()
        val resolvedGoal = when {
            goalFromIntent.isNotBlank() -> goalFromIntent
            savedGoalText.isNotBlank() -> savedGoalText
            else -> DEFAULT_GOAL_TEXT
        }

        val quoteIndexSeed = abs((targetPackage?.hashCode() ?: 0) + getDateKey(0).hashCode())
        val quoteIndex = quoteIndexSeed % QUOTES.size

        binding.goalText.text = resolvedGoal
        binding.quoteText.text = QUOTES[quoteIndex]
    }

    private fun startBreathingAnimation() {
        val scaleX = ObjectAnimator.ofFloat(binding.ringContainer, "scaleX", 1f, 1.05f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleY = ObjectAnimator.ofFloat(binding.ringContainer, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }

        breathingAnimatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun startCountdownPulseAnimation() {
        countdownPulseAnimator = ObjectAnimator.ofFloat(binding.delayCountdownView, "alpha", 1f, 0.7f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(remainingTimeMs, 100L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMs = millisUntilFinished
                updateVisualState(millisUntilFinished)
            }

            override fun onFinish() {
                remainingTimeMs = 0L
                updateVisualState(0L)
                launchTargetApp()
            }
        }.start()
    }

    private fun updateVisualState(currentRemainingMs: Long) {
        val totalMs = totalDelaySeconds * 1000L
        val clampedRemaining = currentRemainingMs.coerceIn(0L, totalMs)
        val elapsed = (totalMs - clampedRemaining).coerceAtLeast(0L)
        val progress = if (totalMs == 0L) 1f else elapsed.toFloat() / totalMs.toFloat()

        val progressPermille = (progress * 1000f).roundToInt().coerceIn(0, 1000)
        val progressPercent = (progress * 100f).roundToInt().coerceIn(0, 100)
        val secondsLeft = if (clampedRemaining <= 0L) 0 else ((clampedRemaining + 999L) / 1000L).toInt()

        binding.countdownRing.progress = progressPermille
        binding.progressPercentage.text = "$progressPercent%"
        binding.delayCountdownView.text = if (secondsLeft > 0) {
            "Launching in $secondsLeft seconds..."
        } else {
            "Launching now..."
        }

        binding.visualFeedbackView.setProgress(progress)
    }

    private fun triggerContrastInversionFlash() {
        binding.delayRoot.post {
            val width = binding.delayRoot.width
            val height = binding.delayRoot.height
            if (width <= 0 || height <= 0) return@post

            binding.inversionFlashOverlay.visibility = View.GONE

            val snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(snapshot).also { canvas ->
                binding.delayRoot.draw(canvas)
            }

            val inverted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val invertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(INVERT_COLOR_MATRIX)
            }
            Canvas(inverted).drawBitmap(snapshot, 0f, 0f, invertPaint)
            snapshot.recycle()

            invertedFlashBitmap?.recycle()
            invertedFlashBitmap = inverted
            binding.inversionFlashOverlay.setImageBitmap(invertedFlashBitmap)
            binding.inversionFlashOverlay.alpha = 0f
            binding.inversionFlashOverlay.visibility = View.VISIBLE

            binding.inversionFlashOverlay.animate()
                .alpha(0.95f)
                .setDuration(90L)
                .withEndAction {
                    binding.inversionFlashOverlay.animate()
                        .alpha(0f)
                        .setDuration(190L)
                        .withEndAction {
                            binding.inversionFlashOverlay.visibility = View.GONE
                        }
                        .start()
                }
                .start()
        }
    }

    private fun launchTargetApp() {
        binding.delayCountdownView.text = "Launching now..."
        binding.progressPercentage.text = "100%"
        targetPackage?.let { packageName ->
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        finish()
    }

    private data class StreakStats(val currentStreak: Int, val totalPauses: Int)

    private fun recordPauseAndGetStats(): StreakStats {
        val prefs = getSharedPreferences(STREAK_PREFS, Context.MODE_PRIVATE)
        val today = getDateKey(0)
        val yesterday = getDateKey(-1)

        val lastDate = prefs.getString(STREAK_LAST_DATE, "") ?: ""
        var currentStreak = prefs.getInt(STREAK_CURRENT, 0)
        var totalPauses = prefs.getInt(STREAK_TOTAL_PAUSES, 0)

        totalPauses += 1
        if (lastDate != today) {
            currentStreak = if (lastDate == yesterday) currentStreak + 1 else 1
            prefs.edit()
                .putString(STREAK_LAST_DATE, today)
                .putInt(STREAK_CURRENT, currentStreak)
                .putInt(STREAK_TOTAL_PAUSES, totalPauses)
                .apply()
        } else {
            prefs.edit().putInt(STREAK_TOTAL_PAUSES, totalPauses).apply()
        }

        return StreakStats(currentStreak = currentStreak.coerceAtLeast(1), totalPauses = totalPauses)
    }

    private fun getDateKey(dayOffset: Int): String {
        val calendar = Calendar.getInstance()
        if (dayOffset != 0) {
            calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TARGET_PACKAGE, targetPackage)
        outState.putInt(KEY_TOTAL_DELAY, totalDelaySeconds)
        outState.putLong(KEY_START_TIME, startTime)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        breathingAnimatorSet?.cancel()
        countdownPulseAnimator?.cancel()
        binding.contentContainer.setLayerType(View.LAYER_TYPE_NONE, null)
        invertedFlashBitmap?.recycle()
        invertedFlashBitmap = null
    }
}
