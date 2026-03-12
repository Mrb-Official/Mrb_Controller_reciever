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

        // Sirf CENTER point — baki sab calculate hoga
        var CENTER_X = 400f
        var CENTER_Y = 700f
        var ACCEL_X  = 2192f
        var ACCEL_Y  = 850f
        const val DEADZONE = 1.0f
        const val MAX_SWIPE = 300f  // Maximum swipe length pixels

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
            tilt > DEADZONE  -> -1  // LEFT
            tilt < -DEADZONE ->  1  // RIGHT
            else             ->  0  // CENTER
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

        // Tilt se swipe length calculate karo
        val tiltAbs = Math.abs(lastTilt).coerceIn(DEADZONE, 10f)
        val ratio = (tiltAbs - DEADZONE) / (10f - DEADZONE)
        val swipeLen = ratio * MAX_SWIPE + 20f

        // Center se left ya right swipe
        val startX: Float
        val endX: Float

        if (currentDir == -1) {
            // LEFT — center se left
            startX = CENTER_X + swipeLen / 2
            endX   = CENTER_X - swipeLen / 2
        } else {
            // RIGHT — center se right
            startX = CENTER_X - swipeLen / 2
            endX   = CENTER_X + swipeLen / 2
        }

        // Tilt zyada = swipe tezi
        val duration = (300f - ratio * 250f).toLong().coerceIn(50L, 300L)

        val path = Path().apply {
            moveTo(startX, CENTER_Y)
            lineTo(endX, CENTER_Y)
        }

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
