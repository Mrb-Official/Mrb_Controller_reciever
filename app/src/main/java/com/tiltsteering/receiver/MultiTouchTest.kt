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
        var steerX = 300f
        var steerY = 750f
        var gasX   = 2192f
        var gasY   = 850f
        const val MAX_OFFSET = 150f
        const val DEADZONE   = 0.3f

        private var currentTilt = 0f
        private var gasActive   = false
        private var running     = false
        private val handler     = Handler(Looper.getMainLooper())

        fun updateTilt(tilt: Float) {
            currentTilt = tilt
            if (!running) {
                running = true
                startLoop()
            }
        }

        fun setGas(on: Boolean) {
            gasActive = on
            if (!running) {
                running = true
                startLoop()
            }
        }

        fun testMultiTouch() {
            currentTilt = 5f
            gasActive = true
            running = true
            startLoop()
        }

        private fun startLoop() {
            handler.post(object : Runnable {
                override fun run() {
                    if (!running) return
                    instance?.doGesture()
                    handler.postDelayed(this, 16L)
                }
            })
        }
    }

    private fun doGesture() {
        val tilt   = currentTilt
        val offset = when {
            tilt > DEADZONE  -> -(tilt / 10f * MAX_OFFSET)
            tilt < -DEADZONE ->  (-tilt / 10f * MAX_OFFSET)
            else             -> 0f
        }

        val tx = steerX + offset
        val ty = steerY

        val builder = GestureDescription.Builder()

        // Steering stroke
        val steerPath = Path().apply {
            moveTo(tx, ty)
            lineTo(tx, ty)
        }
        builder.addStroke(
            GestureDescription.StrokeDescription(steerPath, 0L, 80L, true)
        )

        // Gas stroke — same startTime = 0L = Multitouch!
        if (gasActive) {
            val gasPath = Path().apply {
                moveTo(gasX, gasY)
                lineTo(gasX, gasY)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(gasPath, 0L, 80L, true)
            )
        }

        dispatchGesture(builder.build(), null, handler)
    }

    override fun onServiceConnected() {
        instance = this
        android.util.Log.d("MULTI", "Service connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        running = false
        instance = null
        super.onDestroy()
    }
}
