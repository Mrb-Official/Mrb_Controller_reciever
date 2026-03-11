package com.tiltgamepad.receiver

import android.app.*
import android.content.Intent
import android.hardware.input.InputManager
import android.os.IBinder
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import kotlinx.coroutines.*
import java.net.*

class GamepadService : Service() {

    companion object {
        var packetCount = 0
        var lastTilt = "0.0"
        var gasOn = false
        var brakeOn = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false
    private var virtualGamepad: VirtualGamepad? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        virtualGamepad = VirtualGamepad(this)
        virtualGamepad?.create()
        running = true

        scope.launch {
            try {
                socket = DatagramSocket(9876)
                val buf = ByteArray(64)
                Log.d("GAMEPAD", "Listening on 9876")
                while (running) {
                    val pkt = DatagramPacket(buf, buf.size)
                    socket?.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length).trim()
                    packetCount++
                    handleMessage(msg)
                }
            } catch (e: Exception) {
                Log.e("GAMEPAD", e.message ?: "")
            }
        }
        return START_STICKY
    }

    private fun handleMessage(msg: String) {
        when {
            msg.startsWith("STEER:") -> {
                val tilt = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                lastTilt = tilt.toString()
                // -10 to 10 range ko -1.0 to 1.0 mein convert
                val axis = (tilt / 10f).coerceIn(-1f, 1f)
                virtualGamepad?.setSteer(axis)
            }
            msg == "GAS:ON"   -> { gasOn = true;  virtualGamepad?.setGas(true) }
            msg == "GAS:OFF"  -> { gasOn = false; virtualGamepad?.setGas(false) }
            msg == "BRK:ON"   -> { brakeOn = true;  virtualGamepad?.setBrake(true) }
            msg == "BRK:OFF"  -> { brakeOn = false; virtualGamepad?.setBrake(false) }
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        virtualGamepad?.destroy()
        try { socket?.close() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel("gamepad", "Tilt Gamepad", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "gamepad")
            .setContentTitle("Tilt Gamepad Active")
            .setContentText("Listening for controller input")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
}
