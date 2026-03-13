package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        const val LEFT_X  = 235f
        const val LEFT_Y  = 720f
        const val RIGHT_X = 587f
        const val RIGHT_Y = 738f
        const val GAS_X   = 2192f
        const val GAS_Y   = 850f
        
        const val DEADZONE = 0.1f 
        const val SLIDE    = 60f // Diagonal slide kitna lamba hoga

        private var currentTilt = 0f
        private var gasActive   = false
        private var running     = false
        private val handler     = Handler(Looper.getMainLooper())

        fun updateTilt(tilt: Float) {
            Log.d("MULTI_TILT", "Asli Tilt Value: $tilt")
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
                    handler.postDelayed(this, 100L)
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
        val gestureDuration = 80L

        if (tilt > DEADZONE || tilt < -DEADZONE) {
            val factor = (tilt / 10f).coerceIn(-1f, 1f)

            // DIAGONAL SWIPE LOGIC
            // Left point (Swipe jaisa / chahiye)
            val leftStartX = LEFT_X
            val leftStartY = LEFT_Y
            val leftEndX   = LEFT_X + (factor * SLIDE)
            // Y me minus lagaya taaki diagonal cross bane
            val leftEndY   = LEFT_Y - (factor * SLIDE) 
            
            val leftPath = Path().apply {
                moveTo(leftStartX, leftStartY)
                lineTo(leftEndX, leftEndY)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(leftPath, 0L, gestureDuration, false)
            )

            // Right point (Swipe jaisa \ chahiye)
            val rightStartX = RIGHT_X
            val rightStartY = RIGHT_Y
            val rightEndX   = RIGHT_X + (factor * SLIDE)
            // Y me plus lagaya taaki opposite cross bane
            val rightEndY   = RIGHT_Y + (factor * SLIDE) 
            
            val rightPath = Path().apply {
                moveTo(rightStartX, rightStartY)
                lineTo(rightEndX, rightEndY)
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(rightPath, 0L, gestureDuration, false)
            )

            hasStroke = true
        }

        if (gasActive) {
            val gasPath = Path().apply {
                moveTo(GAS_X, GAS_Y)
                lineTo(GAS_X + 1f, GAS_Y) 
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(gasPath, 0L, gestureDuration, false)
            )
            hasStroke = true
        }

        if (!hasStroke) return

        try {
            dispatchGesture(builder.build(), null, handler)
        } catch (e: Exception) {
            Log.e("MULTI", e.message ?: "")
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
}
