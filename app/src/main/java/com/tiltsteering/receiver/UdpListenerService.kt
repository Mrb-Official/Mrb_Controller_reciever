package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {

    companion object {
        var lastTilt = "0.0"
        var gasOn = false
        var brakeOn = false
        var packetCount = 0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false

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
                    dispatch(msg)
                }
            } catch (e: Exception) {
                Log.e("UDP", e.message ?: "")
            }
        }
        return START_STICKY
    }

    private fun dispatch(msg: String) {
        val svc = SteeringAccessibilityService.instance
        when {
            msg.startsWith("STEER:") -> {
                val v = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                // -1.0 to 1.0 wapas -10 to 10 mein convert
                val tilt = v * 10f
                lastTilt = tilt.toString()
                svc?.handleTilt(tilt)
            }
            msg == "GAS:ON"  -> { gasOn = true;  svc?.handleAccelerator(true) }
            msg == "GAS:OFF" -> { gasOn = false; svc?.handleAccelerator(false) }
            msg == "BRK:ON"  -> { brakeOn = true }
            msg == "BRK:OFF" -> { brakeOn = false }
            // Purane messages bhi handle karo
            msg == "RACE:ON"  -> { gasOn = true;  svc?.handleAccelerator(true) }
            msg == "RACE:OFF" -> { gasOn = false; svc?.handleAccelerator(false) }
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
        val ch = NotificationChannel(
            "tilt", "Tilt Steering",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "tilt")
            .setContentTitle("Tilt Steering Active")
            .setContentText("Listening on port 9876")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
}
