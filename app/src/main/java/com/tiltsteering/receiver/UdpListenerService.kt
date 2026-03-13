package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {

    companion object {
        var packetCount = 0
        var lastTilt    = "0.0"
        var gasOn       = false
        var brakeOn     = false
    }

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket : DatagramSocket? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true
        scope.launch {
            try {
                socket = DatagramSocket(9876)
                val buf = ByteArray(64)
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
                MultiTouchTest.updateTilt(v)
            }
            msg == "GAS:ON"  -> { gasOn = true;  MultiTouchTest.setGas(true) }
            msg == "GAS:OFF" -> { gasOn = false; MultiTouchTest.setGas(false) }
            msg == "BRK:ON"  -> { brakeOn = true }
            msg == "BRK:OFF" -> { brakeOn = false }
            msg == "RACE:ON"  -> { gasOn = true;  MultiTouchTest.setGas(true) }
            msg == "RACE:OFF" -> { gasOn = false; MultiTouchTest.setGas(false) }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        try { socket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel("tilt", "Tilt Controller", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "tilt")
            .setContentTitle("Tilt Controller Active")
            .setContentText("Listening on port 9876")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
}
