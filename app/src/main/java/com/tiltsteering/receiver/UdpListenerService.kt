package com.tiltsteering.receiver

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class UdpListenerService : Service() {

    companion object {
        // UI ke liye live data
        var lastTilt: String = "0.0"
        var lastRace: Boolean = false
        var packetCount: Int = 0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true
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
                        packetCount++
                        Log.d("UDP", "Got: $msg")
                        dispatch(msg)
                    } catch (e: Exception) {
                        if (running) Log.w("UDP", e.message ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e("UDP", "Error: ${e.message}")
            }
        }
        return START_STICKY
    }

    private fun dispatch(msg: String) {
        when {
            msg.startsWith("STEER:") -> {
                val tilt = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                lastTilt = tilt.toString()
                SteeringAccessibilityService.instance?.handleTilt(tilt)
            }
            msg == "RACE:ON" -> {
                lastRace = true
                SteeringAccessibilityService.instance?.handleAccelerator(true)
            }
            msg == "RACE:OFF" -> {
                lastRace = false
                SteeringAccessibilityService.instance?.handleAccelerator(false)
            }
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
            .setContentText("Listening on port 9876")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
}
