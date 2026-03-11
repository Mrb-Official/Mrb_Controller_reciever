package com.tiltgamepad.receiver

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvData   = findViewById(R.id.tvData)
        btnStart = findViewById(R.id.btnStart)
        btnStop  = findViewById(R.id.btnStop)

        btnStart.setOnClickListener {
            startForegroundService(Intent(this, GamepadService::class.java))
            tvStatus.text = "✅ Gamepad Active!"
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, GamepadService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        // UI updater
        handler.post(object : Runnable {
            override fun run() {
                tvData.text = """
                    Packets: ${GamepadService.packetCount}
                    Tilt: ${GamepadService.lastTilt}
                    Gas: ${if (GamepadService.gasOn) "ON 🔥" else "OFF"}
                    Brake: ${if (GamepadService.brakeOn) "ON 🛑" else "OFF"}
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
