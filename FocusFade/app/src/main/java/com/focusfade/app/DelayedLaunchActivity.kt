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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_delayed_launch)

        targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        val delaySeconds = settingsManager.getLaunchDelaySeconds()
        
        progressBar = findViewById(R.id.delayProgressBar)
        countdownText = findViewById(R.id.delayCountdownView)
        progressPercentage = findViewById(R.id.progressPercentage)
        
        // Set up progress bar
        progressBar.max = delaySeconds
        progressBar.progress = 0
        
        // Animate progress bar
        val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, delaySeconds)
        progressAnimator.duration = (delaySeconds * 1000).toLong()
        progressAnimator.interpolator = AccelerateDecelerateInterpolator()
        progressAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            val percentage = (progress * 100) / delaySeconds
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

        timer = object : CountDownTimer((delaySeconds * 1000).toLong(), 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                countdownText.text = "Launching in $secondsLeft seconds..."
            }
            override fun onFinish() {
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
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
