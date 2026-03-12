package com.tiltsteering.receiver

import android.util.Log
import rikka.shizuku.Shizuku

object TouchInjector {

    private val TAG = "TouchInjector"
    private var shellProcess: java.lang.Process? = null
    private var outputStream: java.io.OutputStream? = null

    var centerX = 300f
    var centerY = 750f
    var gasX    = 2192f
    var gasY    = 850f
    const val MAX_OFFSET = 150f

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                return
            }
            Shizuku.requestPermission(100)
        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed: ${e.message}")
        }
    }

    private fun ensureShell() {
        if (shellProcess == null || outputStream == null) {
            try {
                // Shizuku se shell process start karo
                val process = Shizuku.newProcess(
                    arrayOf("sh"), null, null
                )
                shellProcess = process as java.lang.Process
                outputStream = process.outputStream
                Log.d(TAG, "Shizuku shell started!")
            } catch (e: Exception) {
                Log.e(TAG, "Shell failed: ${e.message}")
                shellProcess = null
                outputStream = null
            }
        }
    }

    private fun sendCmd(cmd: String) {
        try {
            ensureShell()
            outputStream?.write("$cmd\n".toByteArray())
            outputStream?.flush()
            Log.d(TAG, "CMD: $cmd")
        } catch (e: Exception) {
            Log.e(TAG, "CMD failed: ${e.message}")
            shellProcess = null
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
            shellProcess?.destroy()
        } catch (e: Exception) {}
        shellProcess = null
        outputStream = null
    }
}
