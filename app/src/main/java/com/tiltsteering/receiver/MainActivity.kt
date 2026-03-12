package com.tiltsteering.receiver

import android.content.Intent
import android.net.Uri
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
        tvData   = findViewById(R.id.tvData)

        findViewById<Button>(R.id.btnShizuku).setOnClickListener {
            try {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) startActivity(intent)
            } catch (e: Exception) {}
            // Permission request
            TouchInjector.requestPermission()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            // Shizuku check hataya — seedha start
            startForegroundService(Intent(this, UdpListenerService::class.java))
            tvStatus.text = "✅ Controller Active!"
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UdpListenerService::class.java))
            TouchInjector.release()
            tvStatus.text = "⛔ Stopped"
        }

        handler.post(object : Runnable {
            override fun run() {
                tvData.text = """
                    Shizuku: ${if (TouchInjector.isShizukuReady()) "✅ Ready" else "❌ Start karo"}
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
