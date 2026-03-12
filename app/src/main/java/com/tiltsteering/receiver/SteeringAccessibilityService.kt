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

        var LEFT_X  = 580f
        var LEFT_Y  = 700f
        var RIGHT_X = 229f
        var RIGHT_Y = 703f
        var ACCEL_X = 2192f
        var ACCEL_Y = 850f
        const val DEADZONE = 1.0f

        fun disableFromSender() {
            instance?.disableSelf()
            instance = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTilt = 0f
    private var accelHeld = false
    private var steeringActive = false
    private var accelActive = false
    private var currentDir = 0

    fun handleTilt(tilt: Float) {
        lastTilt = tilt
        val dir = when {
            tilt > DEADZONE  -> -1
            tilt < -DEADZONE ->  1
            else             ->  0
        }

        if (dir != currentDir) {
            currentDir = dir
            steeringActive = dir != 0
            if (steeringActive) doSteer()
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) { accelActive = true; doAccel() }
        else accelActive = false
    }

    private fun doSteer() {
        if (!steeringActive || currentDir == 0) return

        // Tilt value se swipe length calculate karo
        // Thoda tilt = choti swipe, zyada tilt = lambi swipe
        val tiltAbs = Math.abs(lastTilt).coerceIn(DEADZONE, 10f)
        val swipeLength = ((tiltAbs - DEADZONE) / (10f - DEADZONE) * 200f) + 20f

        // Center point se swipe karo
        val centerX = (LEFT_X + RIGHT_X) / 2f
        val centerY = (LEFT_Y + RIGHT_Y) / 2f

        val startX: Float
        val endX: Float

        if (currentDir == -1) {
            // LEFT swipe
            startX = centerX + swipeLength / 2
            endX   = centerX - swipeLength / 2
        } else {
            // RIGHT swipe
            startX = centerX - swipeLength / 2
            endX   = centerX + swipeLength / 2
        }

        val path = Path().apply {
            moveTo(startX, centerY)
            lineTo(endX, centerY)
        }

        // Swipe duration bhi tilt se — zyada tilt = tezi swipe
        val duration = (300f - (tiltAbs / 10f * 200f)).toLong().coerceIn(50L, 300L)

        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (steeringActive && currentDir != 0) {
                    handler.postDelayed({ doSteer() }, 16L)
                }
            }
            override fun onCancelled(g: GestureDescription) {
                if (steeringActive && currentDir != 0) {
                    handler.postDelayed({ doSteer() }, 16L)
                }
            }
        }, handler)
    }

    private fun doAccel() {
        if (!accelActive) return
        val path = Path().apply { moveTo(ACCEL_X, ACCEL_Y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1000L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (accelActive) handler.post { doAccel() }
            }
            override fun onCancelled(g: GestureDescription) {
                if (accelActive) handler.postDelayed({ doAccel() }, 16L)
            }
        }, handler)
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        steeringActive = false
        accelActive = false
        instance = null
        super.onDestroy()
    }
}
