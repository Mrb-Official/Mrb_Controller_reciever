package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class SteeringAccessibilityService : AccessibilityService() {
    companion object {
        var instance: SteeringAccessibilityService? = null
        var LEFT_X  = 150f
        var LEFT_Y  = 900f
        var RIGHT_X = 900f
        var RIGHT_Y = 900f
        var ACCEL_X = 1000f
        var ACCEL_Y = 800f
        const val DEADZONE = 2.0f
        const val THRESHOLD = 0.3f
        const val HOLD_MS = 500L
    }

    private var lastDir = 0
    private var lastTilt = 0f
    private var accelHeld = false
    private var busy = false

    fun testTouch() {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        hold(screenWidth / 2, screenHeight / 2)
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

        // Press karo
        val pressPath = Path().apply { moveTo(x, y) }
        val press = GestureDescription.StrokeDescription(
            pressPath, 0, HOLD_MS, true
        )

        busy = true
        dispatchGesture(
            GestureDescription.Builder().addStroke(press).build(),
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
