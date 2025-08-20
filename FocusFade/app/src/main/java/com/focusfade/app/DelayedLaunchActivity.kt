package com.focusfade.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import com.focusfade.app.manager.SettingsManager

/**
 * Activity that shows a delay screen before launching a distracting app
 */
class DelayedLaunchActivity : Activity() {

    private lateinit var settingsManager: SettingsManager
    private var targetPackage: String? = null
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_delayed_launch)

        targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        val delaySeconds = settingsManager.getLaunchDelaySeconds() // config 10–30
        val textView = findViewById<TextView>(R.id.delayCountdownView)

        timer = object : CountDownTimer((delaySeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                textView.text = "Launching in ${millisUntilFinished / 1000} sec..."
            }
            override fun onFinish() {
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
