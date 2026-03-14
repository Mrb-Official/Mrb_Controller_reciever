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

        val buttonConfig = mutableMapOf(
            "BRAKE"   to Pair(235f,  720f),
            "GAS"     to Pair(2192f, 850f),
            "GEAR+"   to Pair(2244f, 592f),
            "GEAR-"   to Pair(2244f, 592f),
        )
        val buttonHold = mutableMapOf(
            "BRAKE"   to true,
            "GAS"     to true,
            "GEAR+"   to false,
            "GEAR-"   to false,
        )
        val buttonSwipe = mutableMapOf(
            "GEAR+"   to Pair("up",   200f),
            "GEAR-"   to Pair("down", 200f),
        )
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
                val sdir = prefs.getString("btn_sdir_$name", "none") ?: "none"
                val sdist= prefs.getFloat("btn_sdist_$name", 100f)
                buttonConfig[name] = Pair(x, y)
                buttonHold[name]   = hold
                if (sdir != "none") buttonSwipe[name] = Pair(sdir, sdist)
                Log.d("UDP", "Loaded: $name=$x,$y swipe=$sdir$sdist")
            }
    }

    private fun saveConfig(name: String, x: Float, y: Float,
                           hold: Boolean, sdir: String, sdist: Float) {
        prefs.edit()
            .putFloat("btn_x_$name", x)
            .putFloat("btn_y_$name", y)
            .putBoolean("btn_hold_$name", hold)
            .putString("btn_sdir_$name", sdir)
            .putFloat("btn_sdist_$name", sdist)
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

            // CFG:NAME:X:Y:HOLD:SWIPEDIR:SWIPEDIST
            msg.startsWith("CFG:") -> {
                val p = msg.split(":")
                if (p.size >= 5) {
                    val name  = p[1]
                    val x     = p[2].toFloatOrNull() ?: return
                    val y     = p[3].toFloatOrNull() ?: return
                    val hold  = p[4] == "1"
                    val sdir  = if (p.size >= 6) p[5] else "none"
                    val sdist = if (p.size >= 7) p[6].toFloatOrNull() ?: 100f else 100f
                    buttonConfig[name] = Pair(x, y)
                    buttonHold[name]   = hold
                    if (sdir != "none") buttonSwipe[name] = Pair(sdir, sdist)
                    saveConfig(name, x, y, hold, sdir, sdist)
                    Log.d("UDP", "CFG: $name=$x,$y hold=$hold swipe=$sdir$sdist")
                }
            }

            // SWIPE:NAME:DIR:DIST — direct swipe command
            msg.startsWith("SWIPE:") -> {
                val p = msg.split(":")
                if (p.size >= 4) {
                    val name = p[1]
                    val dir  = p[2]
                    val dist = p[3].toFloatOrNull() ?: 200f
                    val cfg  = buttonConfig[name]
                    if (cfg != null) {
                        Log.d("UDP", "SWIPE: $name dir=$dir dist=$dist at ${cfg.first},${cfg.second}")
                        MultiTouchTest.doSwipe(cfg.first, cfg.second, dir, dist)
                    } else {
                        Log.w("UDP", "SWIPE: No coords for $name")
                    }
                }
            }

            // BTN:NAME:ON/OFF — hold buttons
            msg.startsWith("BTN:") -> {
                val p = msg.split(":")
                if (p.size >= 3) {
                    val name = p[1]
                    val on   = p[2] == "ON"
                    Log.d("UDP", "BTN: $name=$on")
                    MultiTouchTest.setButton(name, on)
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
            .setContentText("Listening :9876")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
}
