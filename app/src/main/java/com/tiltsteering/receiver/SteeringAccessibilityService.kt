package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class SteeringAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SteeringAccessibilityService? = null
        var CENTER_X = 300f
        var CENTER_Y = 750f
        var ACCEL_X  = 2192f
        var ACCEL_Y  = 850f
        const val DEADZONE = 1.0f
        const val MAX_SWIPE = 150f

        fun disableFromSender() {
            instance?.disableSelf()
            instance = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentTilt = 0f
    private var accelHeld = false
    private var accelActive = false
    private var gestureRunning = false

    fun handleTilt(tilt: Float) {
        currentTilt = tilt
        if (!gestureRunning) {
            startContinuousGesture()
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        accelActive = on
    }

    private fun startContinuousGesture() {
        gestureRunning = true
        sendFrame()
    }

    private fun sendFrame() {
        if (!gestureRunning) return

        val tilt = currentTilt
        val builder = GestureDescription.Builder()

        // Steering — center se tilt ke hisaab se position
        val offset = when {
            tilt > DEADZONE  -> -(tilt / 10f * MAX_SWIPE)   // LEFT
            tilt < -DEADZONE ->  (-tilt / 10f * MAX_SWIPE)  // RIGHT
            else             -> 0f                            // CENTER hold
        }

        val targetX = CENTER_X + offset

        val steerPath = Path().apply {
            moveTo(CENTER_X, CENTER_Y)
            lineTo(targetX, CENTER_Y)
        }

        val steerStroke = GestureDescription.StrokeDescription(
            steerPath, 0L, 100L, true  // willContinue = true = finger nahi uthegi
        )
        builder.addStroke(steerStroke)

        // Accel ek saath
        if (accelActive) {
            val accelPath = Path().apply { moveTo(ACCEL_X, ACCEL_Y) }
            val accelStroke = GestureDescription.StrokeDescription(
                accelPath, 0L, 100L, true
            )
            builder.addStroke(accelStroke)
        }

        dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                handler.postDelayed({ sendFrame() }, 16L)
            }
            override fun onCancelled(g: GestureDescription) {
                handler.postDelayed({ sendFrame() }, 16L)
            }
        }, handler)
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        gestureRunning = false
        instance = null
        super.onDestroy()
    }
}
