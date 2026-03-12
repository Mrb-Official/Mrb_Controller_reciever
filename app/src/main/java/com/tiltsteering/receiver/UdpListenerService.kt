package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {

    companion object {
        var packetCount = 0
        var lastTilt    = "0.0"
        var gasOn       = false
        var brakeOn     = false
    }

    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket  : DatagramSocket? = null
    private var running = false

    // Key states
    private var keyW = false
    private var keyA = false
    private var keyD = false
    private var keyS = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true

        scope.launch {
            try {
                socket = DatagramSocket(9876)
                val buf = ByteArray(64)
                Log.d("UDP", "Listening on 9876")
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

    private fun handleMessage(msg: String) {
        when {
            msg.startsWith("STEER:") -> {
                val v = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                lastTilt = v.toString()

                val newA = v > 0.3f   // LEFT  = A
                val newD = v < -0.3f  // RIGHT = D

                if (newA != keyA) {
                    keyA = newA
                    TiltInputMethodService.holdKey(KeyEvent.KEYCODE_A, keyA)
                }
                if (newD != keyD) {
                    keyD = newD
                    TiltInputMethodService.holdKey(KeyEvent.KEYCODE_D, keyD)
                }
            }
            msg == "GAS:ON"  -> {
                gasOn = true
                keyW  = true
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_W, true)
            }
            msg == "GAS:OFF" -> {
                gasOn = false
                keyW  = false
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_W, false)
            }
            msg == "BRK:ON"  -> {
                brakeOn = true
                keyS    = true
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_S, true)
            }
            msg == "BRK:OFF" -> {
                brakeOn = false
                keyS    = false
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_S, false)
            }
            msg == "RACE:ON"  -> {
                gasOn = true
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_W, true)
            }
            msg == "RACE:OFF" -> {
                gasOn = false
                TiltInputMethodService.holdKey(KeyEvent.KEYCODE_W, false)
            }
        }
    }

    override fun onDestroy() {
        // Sab keys release karo
        TiltInputMethodService.holdKey(KeyEvent.KEYCODE_W, false)
        TiltInputMethodService.holdKey(KeyEvent.KEYCODE_A, false)
        TiltInputMethodService.holdKey(KeyEvent.KEYCODE_D, false)
        TiltInputMethodService.holdKey(KeyEvent.KEYCODE_S, false)
        running = false
        scope.cancel()
        try { socket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(
            "tilt", "Tilt Controller",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "tilt")
            .setContentTitle("Tilt Controller Active")
            .setContentText("Listening on port 9876")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
}
