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
    private lateinit var tvData: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvData = findViewById(R.id.tvData)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val svc = SteeringAccessibilityService.instance
            if (svc == null) {
                tvStatus.text = "❌ Pehle Accessibility ON karo!"
            } else {
                startForegroundService(Intent(this, UdpListenerService::class.java))
                tvStatus.text = "✅ UDP Listening on Port 9876"
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        // Har 100ms pe UI update
        handler.post(object : Runnable {
            override fun run() {
                val accStatus = if (SteeringAccessibilityService.instance != null)
                    "✅ ON" else "❌ OFF"

                tvData.text = """
                    Accessibility: $accStatus
                    Packets: ${UdpListenerService.packetCount}
                    Tilt: ${UdpListenerService.lastTilt}
                    Gas: ${if (UdpListenerService.gasOn) "ON 🔥" else "OFF"}
                    Brake: ${if (UdpListenerService.brakeOn) "ON 🛑" else "OFF"}
                """.trimIndent()

                handler.postDelayed(this, 100)
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
