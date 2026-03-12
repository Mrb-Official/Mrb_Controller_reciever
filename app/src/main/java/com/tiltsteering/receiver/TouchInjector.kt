package com.tiltsteering.receiver

import android.util.Log
import rikka.shizuku.Shizuku

object TouchInjector {

    private var process: java.lang.Process? = null
    private var outputStream: java.io.OutputStream? = null
    private val TAG = "TouchInjector"

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
                process = Runtime.getRuntime().exec("su")
                outputStream = process!!.outputStream
                Log.d(TAG, "Process started!")
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
        val x = (centerX + offset).toInt()
        val y = centerY.toInt()
        sendCmd("input touchscreen swipe $x $y $x $y 100000")
    }

    fun setAccel(on: Boolean) {
        if (on) {
            val x = gasX.toInt()
            val y = gasY.toInt()
            sendCmd("input touchscreen swipe $x $y $x $y 100000")
        }
    }

    fun release() {
        try {
            outputStream?.close()
            process?.destroy()
        } catch (e: Exception) {}
        process = null
        outputStream = null
    }
}
