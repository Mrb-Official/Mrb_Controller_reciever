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

        const val LEFT_X  = 235f
        const val LEFT_Y  = 720f
        const val RIGHT_X = 587f
        const val RIGHT_Y = 738f
        const val SLIDE   = 80f
        const val GAS_X   = 2192f
        const val GAS_Y   = 850f

        // Deadzone = 1.5 = Real tilt tabhi count hoga
        const val DEADZONE = 1.5f

        private var currentTilt = 0f
        private var gasActive   = false
        private var running     = false
        private val handler     = Handler(Looper.getMainLooper())

        fun updateTilt(tilt: Float) {
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
                    instance?.doGesture()
                    handler.postDelayed(this, 80L)
                }
            })
        }

        fun stop() {
            running = false
            gasActive = false
        }
    }

    private fun doGesture() {
        val tilt = currentTilt
        val builder = GestureDescription.Builder()
        var hasStroke = false

        // Tilt outside deadzone = Dono points active
        if (tilt > DEADZONE || tilt < -DEADZONE) {
            val factor = tilt / 10f  // -1.0 to +1.0

            val leftX  = LEFT_X  - (factor * SLIDE)
            val rightX = RIGHT_X + (factor * SLIDE)

            val leftPath = Path().apply {
                moveTo(leftX, LEFT_Y)
                lineTo(leftX, LEFT_Y)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(leftPath, 0L, 100L, true)
            )

            val rightPath = Path().apply {
                moveTo(rightX, RIGHT_Y)
                lineTo(rightX, RIGHT_Y)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(rightPath, 0L, 100L, true)
            )

            hasStroke = true
        }

        // Gas always separate
        if (gasActive) {
            val gasPath = Path().apply {
                moveTo(GAS_X, GAS_Y)
                lineTo(GAS_X, GAS_Y)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(gasPath, 0L, 100L, true)
            )
            hasStroke = true
        }

        if (!hasStroke) return

        try {
            dispatchGesture(builder.build(), null, handler)
        } catch (e: Exception) {
            android.util.Log.e("MULTI", e.message ?: "")
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
}
