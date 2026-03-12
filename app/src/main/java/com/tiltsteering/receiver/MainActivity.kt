package com.tiltsteering.receiver

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (MultiTouchTest.instance == null) {
                tvStatus.text = "❌ Accessibility ON karo!"
            } else {
                MultiTouchTest.testMultiTouch()
                tvStatus.text = "✅ MultiTouch Test Sent!"
            }
        }

        handler.post(object : Runnable {
            override fun run() {
                val status = if (MultiTouchTest.instance != null) "✅ ON" else "❌ OFF"
                tvStatus.text = "Accessibility: $status"
                handler.postDelayed(this, 500)
            }
        })
    }
}
