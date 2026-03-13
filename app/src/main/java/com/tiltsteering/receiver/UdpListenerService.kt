package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {

    companion object {
        var packetCount = 0
        var lastTilt    = "0.0"

        // Button config: name -> (x, y)
        val buttonConfig = mutableMapOf<String, Pair<Float, Float>>()
        val buttonHold   = mutableMapOf<String, Boolean>()
    }

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket : DatagramSocket? = null
    private var running = false
    private lateinit var prefs: SharedPreferences

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("btn_config", MODE_PRIVATE)
        loadSavedConfig()
        createChannel()
        startForeground(1, buildNotification())
        running = true
        scope.launch {
            try {
                socket = DatagramSocket(9876)
                val buf = ByteArray(256)
                while (running) {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket?.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length).trim()
                    packetCount++
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                Log.e("UDP", e.message ?: "")
            }
        }
        return START_STICKY
    }

    private fun loadSavedConfig() {
        prefs.all.keys
            .filter { it.startsWith("btn_x_") }
            .forEach { key ->
                val name = key.removePrefix("btn_x_")
                val x    = prefs.getFloat("btn_x_$name", 0f)
                val y    = prefs.getFloat("btn_y_$name", 0f)
                val hold = prefs.getBoolean("btn_hold_$name", true)
                buttonConfig[name] = Pair(x, y)
                buttonHold[name]   = hold
                Log.d("UDP", "Loaded: $name = $x,$y hold=$hold")
            }
    }

    private fun saveConfig(name: String, x: Float, y: Float, hold: Boolean) {
        prefs.edit()
            .putFloat("btn_x_$name", x)
            .putFloat("btn_y_$name", y)
            .putBoolean("btn_hold_$name", hold)
            .apply()
    }

    private fun handleMessage(msg: String) {
        when {
            msg.startsWith("STEER:") -> {
                val v = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                lastTilt = v.toString()
                MultiTouchTest.updateTilt(v)
            }
            msg == "RACE:ON"  -> MultiTouchTest.setGas(true)
            msg == "RACE:OFF" -> MultiTouchTest.setGas(false)
            msg == "BRK:ON"   -> MultiTouchTest.setButton("BRAKE", true)
            msg == "BRK:OFF"  -> MultiTouchTest.setButton("BRAKE", false)

            msg.startsWith("CFG:") -> {
                val p = msg.split(":")
                if (p.size >= 5) {
                    val name = p[1]
                    val x    = p[2].toFloatOrNull() ?: return
                    val y    = p[3].toFloatOrNull() ?: return
                    val hold = p[4] == "1"
                    buttonConfig[name] = Pair(x, y)
                    buttonHold[name]   = hold
                    saveConfig(name, x, y, hold)
                    Log.d("UDP", "CFG: $name=$x,$y hold=$hold")
                }
            }

            msg.startsWith("BTN:") -> {
                val p = msg.split(":")
                if (p.size >= 3) {
                    MultiTouchTest.setButton(p[1], p[2] == "ON")
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        try { socket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel("tilt", "Tilt Controller",
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "tilt")
            .setContentTitle("TiltController Active")
            .setContentText("Listening on :9876")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
}
