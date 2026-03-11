package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class SteeringAccessibilityService : AccessibilityService() {
    companion object {
        var instance: SteeringAccessibilityService? = null
        const val LEFT_X  = 580f
        const val LEFT_Y  = 700f
        const val RIGHT_X = 229f
        const val RIGHT_Y = 703f
        const val ACCEL_X = 2192f
        const val ACCEL_Y = 850f
        const val DEADZONE = 1.0f  // Kam deadzone = zyada sensitive
        const val THRESHOLD = 0.1f
        const val HOLD_MS = 50L    // Chota chunk - continuously chain hoga
    }

    private var lastDir = 0
    private var lastTilt = 0f
    private var accelHeld = false
    private var busyLeft = false
    private var busyRight = false
    private var busyAccel = false

    fun testTouch() { hold(LEFT_X, LEFT_Y, "left") }

    fun handleTilt(tilt: Float) {
        val dir = when {
            tilt > DEADZONE -> -1   // Left
            tilt < -DEADZONE -> 1  // Right
            else -> 0
        }
        if (dir == lastDir) return
        lastDir = dir
        lastTilt = tilt
        when (dir) {
            -1 -> hold(LEFT_X, LEFT_Y, "left")
            1  -> hold(RIGHT_X, RIGHT_Y, "right")
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) hold(ACCEL_X, ACCEL_Y, "accel")
    }

    private fun hold(x: Float, y: Float, key: String) {
        val busy = when(key) {
            "left"  -> busyLeft
            "right" -> busyRight
            else    -> busyAccel
        }
        if (busy) return

        when(key) {
            "left"  -> busyLeft  = true
            "right" -> busyRight = true
            else    -> busyAccel = true
        }

        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, y) },
            0, HOLD_MS, true  // willContinue = true = hold!
        )

        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    when(key) {
                        "left"  -> busyLeft  = false
                        "right" -> busyRight = false
                        else    -> busyAccel = false
                    }
                    // Continuously hold karo jab tak direction same hai
                    val shouldContinue = when(key) {
                        "left"  -> lastDir == -1
                        "right" -> lastDir == 1
                        else    -> accelHeld
                    }
                    if (shouldContinue) hold(x, y, key)
                }
                override fun onCancelled(g: GestureDescription) {
                    when(key) {
                        "left"  -> busyLeft  = false
                        "right" -> busyRight = false
                        else    -> busyAccel = false
                    }
                }
            }, null
        )
    }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onDestroy() { instance = null; super.onDestroy() }
}
