package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true
        // Discovery service bhi start karo
        DiscoveryService.start()
        scope.launch {
            try {
                socket = DatagramSocket(9876)
                val buf = ByteArray(64)
                Log.d("UDP", "Listening on port 9876")
                while (running) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        socket?.receive(pkt)
                        val msg = String(pkt.data, 0, pkt.length).trim()
                        dispatch(msg)
                    } catch (e: Exception) {
                        if (running) Log.w("UDP", e.message ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e("UDP", "Socket error: ${e.message}")
            }
        }
        return START_STICKY
    }

    private fun dispatch(msg: String) {
        val svc = SteeringAccessibilityService.instance ?: return
        when {
            msg.startsWith("STEER:") -> {
                val tilt = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                svc.handleTilt(tilt)
            }
            msg == "RACE:ON" -> svc.handleAccelerator(true)
            msg == "RACE:OFF" -> svc.handleAccelerator(false)
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        DiscoveryService.stop()
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
            .setContentText("UDP listening on port 9876")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
}
