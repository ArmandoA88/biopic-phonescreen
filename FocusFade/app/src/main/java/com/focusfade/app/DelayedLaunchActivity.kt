package com.focusfade.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.focusfade.app.manager.SettingsManager

/**
 * Activity that shows a delay screen before launching a distracting app
 */
class DelayedLaunchActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private var targetPackage: String? = null
    private var timer: CountDownTimer? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var countdownText: TextView
    private lateinit var progressPercentage: TextView
    
    private var totalDelaySeconds: Int = 0
    private var remainingTimeMs: Long = 0
    private var startTime: Long = 0
    
    companion object {
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_TOTAL_DELAY = "total_delay"
        private const val KEY_START_TIME = "start_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_delayed_launch)

        progressBar = findViewById(R.id.delayProgressBar)
        countdownText = findViewById(R.id.delayCountdownView)
        progressPercentage = findViewById(R.id.progressPercentage)

        // Check if we're restoring from a saved state
        if (savedInstanceState != null) {
            // Restore state
            targetPackage = savedInstanceState.getString(KEY_TARGET_PACKAGE)
            totalDelaySeconds = savedInstanceState.getInt(KEY_TOTAL_DELAY)
            startTime = savedInstanceState.getLong(KEY_START_TIME)
            
            // Calculate remaining time
            val elapsedTime = System.currentTimeMillis() - startTime
            remainingTimeMs = (totalDelaySeconds * 1000L) - elapsedTime
            
            // If time has already expired, launch immediately
            if (remainingTimeMs <= 0) {
                launchTargetApp()
                return
            }
        } else {
            // Fresh start
            targetPackage = intent.getStringExtra("TARGET_PACKAGE")
            totalDelaySeconds = intent.getIntExtra("DELAY_SECONDS", settingsManager.getLaunchDelaySeconds())
            startTime = System.currentTimeMillis()
            remainingTimeMs = totalDelaySeconds * 1000L
        }
        
        setupUI()
        startCountdown()
    }
    
    private fun setupUI() {
        // Set up progress bar
        progressBar.max = totalDelaySeconds
        
        // Calculate current progress based on elapsed time
        val elapsedTime = System.currentTimeMillis() - startTime
        val currentProgress = ((elapsedTime / 1000).toInt()).coerceAtMost(totalDelaySeconds)
        progressBar.progress = currentProgress
        
        // Animate progress bar from current position
        val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", currentProgress, totalDelaySeconds)
        progressAnimator.duration = remainingTimeMs
        progressAnimator.interpolator = AccelerateDecelerateInterpolator()
        progressAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            val percentage = (progress * 100) / totalDelaySeconds
            progressPercentage.text = "$percentage%"
        }
        progressAnimator.start()

        // Pulse animation for countdown text
        val pulseAnimator = ObjectAnimator.ofFloat(countdownText, "alpha", 1f, 0.7f, 1f)
        pulseAnimator.duration = 1000
        pulseAnimator.repeatCount = ValueAnimator.INFINITE
        pulseAnimator.start()

        // Scale animation for progress percentage
        val scaleAnimator = ObjectAnimator.ofFloat(progressPercentage, "scaleX", 1f, 1.1f, 1f)
        scaleAnimator.duration = 500
        scaleAnimator.repeatCount = ValueAnimator.INFINITE
        scaleAnimator.start()
    }
    
    private fun startCountdown() {
        timer = object : CountDownTimer(remainingTimeMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMs = millisUntilFinished
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                countdownText.text = "Launching in $secondsLeft seconds..."
            }
            override fun onFinish() {
                launchTargetApp()
            }
        }.start()
    }
    
    private fun launchTargetApp() {
        countdownText.text = "Launching now..."
        progressPercentage.text = "100%"
        targetPackage?.let {
            val launchIntent = packageManager.getLaunchIntentForPackage(it)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TARGET_PACKAGE, targetPackage)
        outState.putInt(KEY_TOTAL_DELAY, totalDelaySeconds)
        outState.putLong(KEY_START_TIME, startTime)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity will handle configuration changes without recreating
        // The countdown will continue uninterrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
