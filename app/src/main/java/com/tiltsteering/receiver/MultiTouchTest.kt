package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {
    companion object {
        var instance: MultiTouchTest? = null
        var steerCenterX = 411f
        var steerCenterY = 729f
        var steerRadius  = 176f
        var gasX = 2192f
        var gasY = 850f
        const val DEADZONE  = 0.3f
        const val MAX_ANGLE = 90f
        private var currentTilt  = 0f
        private var gasActive    = false
        private var running      = false
        private var currentAngle = 0f
        private var targetAngle  = 0f
        private val handler = Handler(Looper.getMainLooper())

        fun updateTilt(tilt: Float) {
            targetAngle = when {
                tilt > DEADZONE  -> -(tilt / 10f * MAX_ANGLE)
                tilt < -DEADZONE ->  (-tilt / 10f * MAX_ANGLE)
                else             -> 0f
            }
            currentTilt = tilt
            if (!running) startLoop()
        }

        fun setGas(on: Boolean) {
            gasActive = on
            if (!running) startLoop()
        }

        private fun startLoop() {
            running = true
            handler.post(object : Runnable {
                override fun run() {
                    if (!running) return
                    currentAngle += (targetAngle - currentAngle) * 0.25f
                    instance?.doGesture(currentAngle)
                    handler.postDelayed(this, 16L)
                }
            })
        }

        fun stop() {
            running = false
            currentAngle = 0f
            targetAngle  = 0f
            gasActive    = false
        }
    }

    private fun doGesture(angle: Float) {
        val rad     = Math.toRadians(angle.toDouble())
        val offsetX = (steerRadius * Math.sin(rad)).toFloat()
        val offsetY = (steerRadius * (1 - Math.cos(rad))).toFloat()
        val tx = steerCenterX + offsetX
        val ty = steerCenterY + offsetY
        val builder = GestureDescription.Builder()
        val steerPath = Path().apply { moveTo(tx, ty); lineTo(tx, ty) }
        builder.addStroke(GestureDescription.StrokeDescription(steerPath, 0L, 80L, true))
        if (gasActive) {
            val gasPath = Path().apply { moveTo(gasX, gasY); lineTo(gasX, gasY) }
            builder.addStroke(GestureDescription.StrokeDescription(gasPath, 0L, 80L, true))
        }
        try { dispatchGesture(builder.build(), null, handler) }
        catch (e: Exception) { android.util.Log.e("MULTI", e.message ?: "") }
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
}
