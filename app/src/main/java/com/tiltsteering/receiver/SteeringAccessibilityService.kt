package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class SteeringAccessibilityService : AccessibilityService() {
    companion object {
        var instance: SteeringAccessibilityService? = null
        const val LEFT_X  = 229f
        const val LEFT_Y  = 703f
        const val RIGHT_X = 390f
        const val RIGHT_Y = 749f
        const val ACCEL_X = 980f
        const val ACCEL_Y = 500f
        const val DEADZONE = 2.0f
        const val THRESHOLD = 0.3f
        const val HOLD_MS = 500L
    }

    private var lastDir = 0
    private var lastTilt = 0f
    private var accelHeld = false
    private var busy = false

    fun testTouch() {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        hold(w / 2, h / 2)
    }

    fun handleTilt(tilt: Float) {
        val dir = when {
            tilt > DEADZONE -> -1
            tilt < -DEADZONE -> 1
            else -> 0
        }
        if (dir == lastDir && abs(tilt - lastTilt) < THRESHOLD) return
        lastDir = dir
        lastTilt = tilt
        when (dir) {
            -1 -> hold(LEFT_X, LEFT_Y)
            1  -> hold(RIGHT_X, RIGHT_Y)
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) hold(ACCEL_X, ACCEL_Y)
    }

    private fun hold(x: Float, y: Float) {
        if (busy) return
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, y) },
            0, HOLD_MS, true
        )
        busy = true
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    busy = false
                    val cont = (lastDir == -1 && x == LEFT_X) ||
                               (lastDir == 1 && x == RIGHT_X) ||
                               (accelHeld && x == ACCEL_X)
                    if (cont) hold(x, y)
                }
                override fun onCancelled(g: GestureDescription) {
                    busy = false
                }
            }, null
        )
    }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onDestroy() { instance = null; super.onDestroy() }
}
