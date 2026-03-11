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

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvData: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvRace: TextView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvData   = findViewById(R.id.tvData)
        tvTilt   = findViewById(R.id.tvTilt)
        tvRace   = findViewById(R.id.tvRace)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startForegroundService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "✅ UDP Listening on Port 9876"
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        // Har 100ms pe UI update karo
        startUIUpdater()
    }

    private fun startUIUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                // UdpListenerService se live data lo
                val tilt = UdpListenerService.lastTilt
                val race = UdpListenerService.lastRace
                val count = UdpListenerService.packetCount

                if (count > 0) {
                    tvData.text = "✅ Data Aa Raha Hai! ($count packets)"
                    tvData.setTextColor(0xFF00AA00.toInt())
                } else {
                    tvData.text = "❌ Koi Data Nahi!"
                    tvData.setTextColor(0xFFFF0000.toInt())
                }

                tvTilt.text = "Tilt: $tilt"
                tvRace.text = "Gas: ${if (race) "ON 🔥" else "OFF"}"

                handler.postDelayed(this, 100)
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
