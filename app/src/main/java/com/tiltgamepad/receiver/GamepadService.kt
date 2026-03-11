package com.tiltgamepad.receiver

import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class GamepadService : Service() {

    companion object {
        var packetCount = 0
        var lastTilt = "0.0"
        var gasOn = false
        var brakeOn = false
        var btConnected = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var running = false
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d("HID", "App registered: $registered")
            if (registered) {
                Log.d("HID", "HID registered! Waiting for connection...")
            }
        }
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    btConnected = true
                    Log.d("HID", "Connected: ${device?.name}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    btConnected = false
                    Log.d("HID", "Disconnected")
                }
            }
        }
    }

    // HID Descriptor — Standard Gamepad
    private val hidDescriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(), // Usage (Game Pad)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        // Left Stick X (Steering)
        0x09.toByte(), 0x30.toByte(), // Usage (X)
        0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), // Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
        // Buttons (Gas=1, Brake=2)
        0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), // Usage Minimum (1)
        0x29.toByte(), 0x08.toByte(), // Usage Maximum (8)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), // Report Size (1)
        0x95.toByte(), 0x08.toByte(), // Report Count (8)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
        0xC0.toByte()                  // End Collection
    )

    private val sdpRecord = BluetoothHidDeviceAppSdpSettings(
        "Tilt Gamepad",
        "Tilt Steering Controller",
        "Mrb Controller",
        BluetoothHidDevice.SUBCLASS1_GAMEPAD,
        hidDescriptor
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true

        // BT HID setup
        val btManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val btAdapter = btManager.adapter
        btAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidDevice = proxy as BluetoothHidDevice
                hidDevice?.registerApp(
                    sdpRecord, null, null,
                    mainExecutor, hidCallback
                )
                Log.d("HID", "HID proxy connected")
            }
            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
                Log.d("HID", "HID proxy disconnected")
            }
        }, BluetoothProfile.HID_DEVICE)

        // UDP listener
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
                Log.e("GAMEPAD", e.message ?: "")
            }
        }
        return START_STICKY
    }

    private var steerAxis = 0
    private var buttons = 0

    private fun handleMessage(msg: String) {
        when {
            msg.startsWith("STEER:") -> {
                val v = msg.removePrefix("STEER:").toFloatOrNull() ?: return
                lastTilt = v.toString()
                // -1.0 to 1.0 = -127 to 127
                steerAxis = (v * 127).toInt().coerceIn(-127, 127)
                sendReport()
            }
            msg == "GAS:ON"  -> { gasOn = true;  buttons = buttons or 0x01; sendReport() }
            msg == "GAS:OFF" -> { gasOn = false; buttons = buttons and 0x01.inv(); sendReport() }
            msg == "BRK:ON"  -> { brakeOn = true;  buttons = buttons or 0x02; sendReport() }
            msg == "BRK:OFF" -> { brakeOn = false; buttons = buttons and 0x02.inv(); sendReport() }
        }
    }

    private fun sendReport() {
        val device = connectedDevice ?: return
        val report = byteArrayOf(
            steerAxis.toByte(),
            buttons.toByte()
        )
        try {
            hidDevice?.sendReport(device, 0, report)
            Log.d("HID", "Report sent: steer=$steerAxis buttons=$buttons")
        } catch (e: Exception) {
            Log.e("HID", "Send failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        hidDevice?.unregisterApp()
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
            .setContentText("Bluetooth HID Gamepad running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
}
