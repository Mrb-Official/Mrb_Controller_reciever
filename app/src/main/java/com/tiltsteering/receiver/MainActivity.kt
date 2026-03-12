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
    private lateinit var etLeftX: EditText
    private lateinit var etLeftY: EditText
    private lateinit var etRightX: EditText
    private lateinit var etRightY: EditText
    private lateinit var etAccelX: EditText
    private lateinit var etAccelY: EditText
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs     = getSharedPreferences("coords", MODE_PRIVATE)
        tvStatus  = findViewById(R.id.tvStatus)
        tvData    = findViewById(R.id.tvData)
        etLeftX   = findViewById(R.id.etLeftX)
        etLeftY   = findViewById(R.id.etLeftY)
        etRightX  = findViewById(R.id.etRightX)
        etRightY  = findViewById(R.id.etRightY)
        etAccelX  = findViewById(R.id.etAccelX)
        etAccelY  = findViewById(R.id.etAccelY)

        etLeftX.setText(prefs.getFloat("leftX",   150f).toString())
        etLeftY.setText(prefs.getFloat("leftY",   750f).toString())
        etRightX.setText(prefs.getFloat("rightX", 450f).toString())
        etRightY.setText(prefs.getFloat("rightY", 750f).toString())
        etAccelX.setText(prefs.getFloat("accelX", 2192f).toString())
        etAccelY.setText(prefs.getFloat("accelY", 850f).toString())

        findViewById<Button>(R.id.btnSaveCoords).setOnClickListener {
            val lx = etLeftX.text.toString().toFloatOrNull()  ?: 150f
            val ly = etLeftY.text.toString().toFloatOrNull()  ?: 750f
            val rx = etRightX.text.toString().toFloatOrNull() ?: 450f
            val ry = etRightY.text.toString().toFloatOrNull() ?: 750f
            val ax = etAccelX.text.toString().toFloatOrNull() ?: 2192f
            val ay = etAccelY.text.toString().toFloatOrNull() ?: 850f

            prefs.edit()
                .putFloat("leftX", lx).putFloat("leftY", ly)
                .putFloat("rightX", rx).putFloat("rightY", ry)
                .putFloat("accelX", ax).putFloat("accelY", ay)
                .apply()

            SteeringAccessibilityService.LEFT_X  = lx
            SteeringAccessibilityService.LEFT_Y  = ly
            SteeringAccessibilityService.RIGHT_X = rx
            SteeringAccessibilityService.RIGHT_Y = ry
            SteeringAccessibilityService.ACCEL_X = ax
            SteeringAccessibilityService.ACCEL_Y = ay

            tvStatus.text = "✅ Saved!"
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (SteeringAccessibilityService.instance == null) {
                tvStatus.text = "❌ Pehle Accessibility ON karo!"
            } else {
                SteeringAccessibilityService.LEFT_X  = prefs.getFloat("leftX",  150f)
                SteeringAccessibilityService.LEFT_Y  = prefs.getFloat("leftY",  750f)
                SteeringAccessibilityService.RIGHT_X = prefs.getFloat("rightX", 450f)
                SteeringAccessibilityService.RIGHT_Y = prefs.getFloat("rightY", 750f)
                SteeringAccessibilityService.ACCEL_X = prefs.getFloat("accelX", 2192f)
                SteeringAccessibilityService.ACCEL_Y = prefs.getFloat("accelY", 850f)
                startForegroundService(Intent(this, UdpListenerService::class.java))
                tvStatus.text = "✅ UDP Active!"
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        handler.post(object : Runnable {
            override fun run() {
                tvData.text = """
                    Accessibility: ${if (SteeringAccessibilityService.instance != null) "✅ ON" else "❌ OFF"}
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
