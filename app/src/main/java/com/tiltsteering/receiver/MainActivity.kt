package com.tiltsteering.receiver

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var etCenterX: EditText
    private lateinit var etCenterY: EditText
    private lateinit var etAccelX: EditText
    private lateinit var etAccelY: EditText
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("coords", MODE_PRIVATE)

        tvStatus  = findViewById(R.id.tvStatus)
        tvData    = findViewById(R.id.tvData)
        etCenterX = findViewById(R.id.etCenterX)
        etCenterY = findViewById(R.id.etCenterY)
        etAccelX  = findViewById(R.id.etAccelX)
        etAccelY  = findViewById(R.id.etAccelY)

        // Saved values load karo
        etCenterX.setText(prefs.getFloat("centerX", 400f).toString())
        etCenterY.setText(prefs.getFloat("centerY", 700f).toString())
        etAccelX.setText(prefs.getFloat("accelX", 2192f).toString())
        etAccelY.setText(prefs.getFloat("accelY", 850f).toString())

        // Save button
        findViewById<Button>(R.id.btnSaveCoords).setOnClickListener {
            val cx = etCenterX.text.toString().toFloatOrNull() ?: 400f
            val cy = etCenterY.text.toString().toFloatOrNull() ?: 700f
            val ax = etAccelX.text.toString().toFloatOrNull() ?: 2192f
            val ay = etAccelY.text.toString().toFloatOrNull() ?: 850f

            prefs.edit()
                .putFloat("centerX", cx).putFloat("centerY", cy)
                .putFloat("accelX", ax).putFloat("accelY", ay)
                .apply()

            SteeringAccessibilityService.CENTER_X = cx
            SteeringAccessibilityService.CENTER_Y = cy
            SteeringAccessibilityService.ACCEL_X  = ax
            SteeringAccessibilityService.ACCEL_Y  = ay

            tvStatus.text = "✅ Coordinates saved!"
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val svc = SteeringAccessibilityService.instance
            if (svc == null) {
                tvStatus.text = "❌ Pehle Accessibility ON karo!"
            } else {
                SteeringAccessibilityService.CENTER_X = prefs.getFloat("centerX", 400f)
                SteeringAccessibilityService.CENTER_Y = prefs.getFloat("centerY", 700f)
                SteeringAccessibilityService.ACCEL_X  = prefs.getFloat("accelX", 2192f)
                SteeringAccessibilityService.ACCEL_Y  = prefs.getFloat("accelY", 850f)
                startForegroundService(Intent(this, UdpListenerService::class.java))
                tvStatus.text = "✅ UDP Listening on Port 9876"
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        handler.post(object : Runnable {
            override fun run() {
                val accStatus = if (SteeringAccessibilityService.instance != null)
                    "✅ ON" else "❌ OFF"
                tvData.text = """
                    Accessibility: $accStatus
                    Packets: ${UdpListenerService.packetCount}
                    Tilt: ${UdpListenerService.lastTilt}
                    Gas: ${if (UdpListenerService.gasOn) "ON 🔥" else "OFF"}
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
