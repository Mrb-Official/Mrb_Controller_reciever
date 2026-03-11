package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class SteeringAccessibilityService : AccessibilityService() {
    companion object {
        var instance: SteeringAccessibilityService? = null
        const val LEFT_X  = 580f
        const val LEFT_Y  = 700f
        const val RIGHT_X = 229f
        const val RIGHT_Y = 703f
        const val ACCEL_X = 2192f
        const val ACCEL_Y = 850f
        const val DEADZONE = 1.0f
    }

    private var lastDir = 0
    private var steeringActive = false
    private var accelHeld = false

    fun testTouch() { startSteering(-1) }

    fun handleTilt(tilt: Float) {
        val dir = when {
            tilt > DEADZONE  -> -1
            tilt < -DEADZONE ->  1
            else             ->  0
        }
        if (dir == lastDir) return
        lastDir = dir
        when (dir) {
            -1 -> startSteering(-1)
             1 -> startSteering(1)
             0 -> steeringActive = false
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) tapAccel()
    }

    private fun startSteering(dir: Int) {
        steeringActive = true
        val x = if (dir == -1) LEFT_X else RIGHT_X
        val y = if (dir == -1) LEFT_Y else RIGHT_Y
        continuousHold(x, y, dir)
    }

    private fun continuousHold(x: Float, y: Float, dir: Int) {
        if (!steeringActive || lastDir != dir) return
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, y) },
            0L, 2000L
        )
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (steeringActive && lastDir == dir) {
                        continuousHold(x, y, dir)
                    }
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (steeringActive && lastDir == dir) {
                        continuousHold(x, y, dir)
                    }
                }
            },
            null
        )
    }

    private fun tapAccel() {
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(ACCEL_X, ACCEL_Y) },
            0L, 100L
        )
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null
        )
    }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onDestroy() { instance = null; super.onDestroy() }
}
