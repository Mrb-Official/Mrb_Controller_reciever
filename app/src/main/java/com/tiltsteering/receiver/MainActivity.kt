package com.tiltsteering.receiver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // Test touch - screen center pe tap karo
            val svc = SteeringAccessibilityService.instance
            if (svc != null) {
                svc.testTouch()
                findViewById<TextView>(R.id.tvStatus).text = 
                    "Touch test bheja! Kuch hua?"
            } else {
                findViewById<TextView>(R.id.tvStatus).text = 
                    "Accessibility Service OFF hai!"
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            findViewById<TextView>(R.id.tvStatus).text = "Stopped"
        }
    }
}
