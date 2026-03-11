package com.tiltsteering.receiver

import android.util.Log
import kotlinx.coroutines.*
import java.net.*

/**
 * Sender ke TILT_DISCOVER message ka reply karta hai
 * Taaki sender auto-find kar sake receiver ko
 */
object DiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch {
            try {
                socket = DatagramSocket(9877)
                val buf = ByteArray(32)
                Log.d("Discovery", "Listening for discovery on port 9877")
                while (running) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        socket?.receive(pkt)
                        val msg = String(pkt.data, 0, pkt.length).trim()
                        if (msg == "TILT_DISCOVER") {
                            // Reply bhejo
                            val reply = "TILT_RECEIVER".toByteArray()
                            val response = DatagramPacket(
                                reply, reply.size,
                                pkt.address, pkt.port
                            )
                            socket?.send(response)
                            Log.d("Discovery", "Replied to ${pkt.address.hostAddress}")
                        }
                    } catch (e: Exception) {
                        if (running) Log.w("Discovery", e.message ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e("Discovery", "Error: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        scope.cancel()
        try { socket?.close() } catch (e: Exception) {}
    }
}
