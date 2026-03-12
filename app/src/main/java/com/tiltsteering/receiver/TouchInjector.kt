package com.tiltsteering.receiver

import android.util.Log
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

object TouchInjector {

    private val TAG = "TouchInjector"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var centerX = 300f
    var centerY = 750f
    var gasX    = 2192f
    var gasY    = 850f
    const val MAX_OFFSET = 150f

    private var currentTilt = 0f
    private var steerActive = false
    private var accelActive = false
    private var steerJob: Job? = null
    private var accelJob: Job? = null

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    fun requestPermission() {
        try { Shizuku.requestPermission(100) }
        catch (e: Exception) { Log.e(TAG, "Permission failed: ${e.message}") }
    }

    private fun runCmd(cmd: String) {
        try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "CMD failed: ${e.message}")
        }
    }

    fun updateSteering(tilt: Float) {
        currentTilt = tilt
        if (steerJob == null || steerJob?.isActive == false) {
            steerActive = true
            steerJob = scope.launch {
                while (steerActive) {
                    val offset = when {
                        currentTilt > 0.3f  -> -(currentTilt / 10f * MAX_OFFSET)
                        currentTilt < -0.3f -> (-currentTilt / 10f * MAX_OFFSET)
                        else                -> 0f
                    }
                    val x = (centerX + offset).toInt()
                    val y = centerY.toInt()
                    runCmd("input touchscreen swipe $x $y $x $y 50")
                    delay(50)
                }
            }
        }
    }

    fun stopSteering() {
        steerActive = false
        steerJob?.cancel()
        steerJob = null
    }

    fun setAccel(on: Boolean) {
        accelActive = on
        if (on) {
            if (accelJob == null || accelJob?.isActive == false) {
                accelJob = scope.launch {
                    while (accelActive) {
                        runCmd("input touchscreen swipe ${gasX.toInt()} ${gasY.toInt()} ${gasX.toInt()} ${gasY.toInt()} 50")
                        delay(50)
                    }
                }
            }
        } else {
            accelJob?.cancel()
            accelJob = null
        }
    }

    fun release() {
        steerActive = false
        accelActive = false
        steerJob?.cancel()
        accelJob?.cancel()
        scope.cancel()
    }
}
