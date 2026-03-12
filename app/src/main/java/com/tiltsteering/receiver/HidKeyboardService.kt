package com.tiltsteering.receiver

import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.*

class HidKeyboardService : Service() {

    companion object {
        var btConnected = false
        var packetCount = 0
        var lastTilt    = "0.0"
        var gasOn       = false
        var brakeOn     = false
    }

    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket : DatagramSocket? = null
    private var running = false
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    // Keys currently pressed
    private var keyW = false  // Gas
    private var keyA = false  // Left
    private var keyD = false  // Right
    private var keyS = false  // Brake

    // HID Keyboard Descriptor
    private val hidDescriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        // Modifier keys
        0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), // Report Size (1)
        0x95.toByte(), 0x08.toByte(), // Report Count (8)
        0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute)
        // Reserved byte
        0x95.toByte(), 0x01.toByte(), // Report Count (1)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x81.toByte(), 0x01.toByte(), // Input (Constant)
        // Key array (6 keys)
        0x95.toByte(), 0x06.toByte(), // Report Count (6)
        0x75.toByte(), 0x08.toByte(), // Report Size (8)
        0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(), // Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(), // Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(), // Input (Data, Array)
        0xC0.toByte()                  // End Collection
    )

    // HID Keycodes
    private val KEY_A    = 0x04.toByte()
    private val KEY_D    = 0x07.toByte()
    private val KEY_S    = 0x16.toByte()
    private val KEY_W    = 0x1A.toByte()
    private val KEY_NONE = 0x00.toByte()

    private val sdpRecord = BluetoothHidDeviceAppSdpSettings(
        "Tilt Keyboard",
        "Tilt Game Controller",
        "Mrb Controller",
        BluetoothHidDevice.SUBCLASS1_NONE,
        hidDescriptor
    )

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d("HID", "Registered: $registered")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification())
        running = true

        // BT HID setup
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
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
                val newA = v > 0.3f   // LEFT
                val newD = v < -0.3f  // RIGHT
                if (newA != keyA || newD != keyD) {
                    keyA = newA
                    keyD = newD
                    sendKeys()
                }
            }
            msg == "GAS:ON"  -> { gasOn = true;  keyW = true;  sendKeys() }
            msg == "GAS:OFF" -> { gasOn = false; keyW = false; sendKeys() }
            msg == "BRK:ON"  -> { brakeOn = true;  keyS = true;  sendKeys() }
            msg == "BRK:OFF" -> { brakeOn = false; keyS = false; sendKeys() }
            msg == "RACE:ON"  -> { gasOn = true;  keyW = true;  sendKeys() }
            msg == "RACE:OFF" -> { gasOn = false; keyW = false; sendKeys() }
        }
    }

    private fun sendKeys() {
        val device = connectedDevice ?: return
        // Standard HID keyboard report: [modifier, reserved, key1, key2, key3, key4, key5, key6]
        val keys = mutableListOf<Byte>()
        if (keyW) keys.add(KEY_W)
        if (keyA) keys.add(KEY_A)
        if (keyD) keys.add(KEY_D)
        if (keyS) keys.add(KEY_S)

        val report = ByteArray(8)
        report[0] = 0x00 // No modifier
        report[1] = 0x00 // Reserved
        for (i in 0 until minOf(keys.size, 6)) {
            report[2 + i] = keys[i]
        }

        try {
            hidDevice?.sendReport(device, 0, report)
            Log.d("HID", "Keys sent: W=$keyW A=$keyA D=$keyD S=$keyS")
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
        val ch = NotificationChannel("hid", "HID Keyboard", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() =
        Notification.Builder(this, "hid")
            .setContentTitle("Tilt Keyboard Active")
            .setContentText("BT HID Keyboard running")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .build()
}
