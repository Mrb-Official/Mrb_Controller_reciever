package com.tiltsteering.receiver

import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object TouchInjector {

    private var process: ShizukuRemoteProcess? = null
    private var outputStream: java.io.OutputStream? = null
    private val TAG = "TouchInjector"

    // Touch slot IDs
    private val SLOT_STEER = 0
    private val SLOT_ACCEL = 1

    // Current positions
    private var steerX = 0f
    private var steerY = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var steerActive = false
    private var accelActive = false

    // Game coordinates - steering center
    var centerX = 300f
    var centerY = 750f
    var gasX    = 2192f
    var gasY    = 850f
    const val MAX_OFFSET = 150f

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(100)
        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed: ${e.message}")
        }
    }

    private fun ensureProcess() {
        if (process == null || outputStream == null) {
            try {
                process = Shizuku.newProcess(arrayOf("sh"), null, null)
                outputStream = process!!.outputStream
                Log.d(TAG, "Shizuku process started!")
            } catch (e: Exception) {
                Log.e(TAG, "Process failed: ${e.message}")
            }
        }
    }

    private fun sendCmd(cmd: String) {
        try {
            ensureProcess()
            outputStream?.write("$cmd\n".toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "CMD failed: ${e.message}")
            process = null
            outputStream = null
        }
    }

    fun updateSteering(tilt: Float) {
        val offset = when {
            tilt > 0.3f  -> -(tilt / 10f * MAX_OFFSET)
            tilt < -0.3f ->  (-tilt / 10f * MAX_OFFSET)
            else         -> 0f
        }
        steerX = centerX + offset
        steerY = centerY

        if (!steerActive) {
            steerActive = true
            sendCmd("input touchscreen swipe ${steerX.toInt()} ${steerY.toInt()} ${steerX.toInt()} ${steerY.toInt()} 100000")
        } else {
            sendCmd("input touchscreen swipe ${steerX.toInt()} ${steerY.toInt()} ${steerX.toInt()} ${steerY.toInt()} 100000")
        }
    }

    fun setAccel(on: Boolean) {
        accelActive = on
        if (on) {
            sendCmd("input touchscreen swipe ${gasX.toInt()} ${gasY.toInt()} ${gasX.toInt()} ${gasY.toInt()} 100000")
        }
    }

    fun release() {
        try {
            outputStream?.close()
            process?.destroy()
        } catch (e: Exception) {}
        process = null
        outputStream = null
        steerActive = false
        accelActive = false
    }
}
