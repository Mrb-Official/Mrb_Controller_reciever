package com.tiltgamepad.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter = btManager.adapter

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (btAdapter == null || !btAdapter.isEnabled) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                tvStatus.text = "⚠️ Bluetooth ON karo!"
                return@setOnClickListener
            }
            startForegroundService(Intent(this, GamepadService::class.java))
            tvStatus.text = "✅ Gamepad Active! Pair karo Samsung se"
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, GamepadService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        handler.post(object : Runnable {
            override fun run() {
                tvData.text = """
                    Packets: ${GamepadService.packetCount}
                    Tilt: ${GamepadService.lastTilt}
                    Gas: ${if (GamepadService.gasOn) "ON 🔥" else "OFF"}
                    Brake: ${if (GamepadService.brakeOn) "ON 🛑" else "OFF"}
                    BT: ${if (GamepadService.btConnected) "Connected ✅" else "Waiting..."}
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
