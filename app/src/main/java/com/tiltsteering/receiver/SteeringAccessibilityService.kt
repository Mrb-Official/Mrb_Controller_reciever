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
        const val DEADZONE = 1.0f
    }

    private var lastDir = 0
    private var steeringGestureActive = false
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
            1  -> startSteering(1)
            0  -> stopSteering()
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) tapAccel()
    }

    private fun startSteering(dir: Int) {
        steeringGestureActive = true
        val x = if (dir == -1) LEFT_X else RIGHT_X
        val y = if (dir == -1) LEFT_Y else RIGHT_Y
        continuousHold(x, y, dir)
    }

    private fun stopSteering() {
        steeringGestureActive = false
    }

    private fun continuousHold(x: Float, y: Float, dir: Int) {
        if (!steeringGestureActive || lastDir != dir) return

        // 2000ms lamba hold — game ke liye perfect
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, y) },
            0, 2000L
        )

        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    // Agar abhi bhi same direction — repeat karo
                    if (steeringGestureActive && lastDir == dir) {
                        continuousHold(x, y, dir)
                    }
                }
                override fun onCancelled(g: GestureDescription) {
                    if (steeringGestureActive && lastDir == dir) {
                        continuousHold(x, y, dir)
                    }
                }
            }, null
        )
    }

    private fun tapAccel() {
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(ACCEL_X, ACCEL_Y) },
            0, 100L
        )
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null
        )
    }

    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onDestroy() { instance = null; super.onDestroy() }
}
