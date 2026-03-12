package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.*

class SteeringAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SteeringAccessibilityService? = null
        var LEFT_X  = 235f
        var LEFT_Y  = 720f
        var RIGHT_X = 587f
        var RIGHT_Y = 738f
        var ACCEL_X = 2192f
        var ACCEL_Y = 850f
        const val DEADZONE  = 0.5f
        const val MAX_ANGLE = 45f

        fun disableFromSender() {
            instance?.disableSelf()
            instance = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentTilt  = 0f
    private var prevAngle    = 0f
    private var accelActive  = false
    private var accelHeld    = false
    private var steerRunning = false
    private var accelRunning = false
    private var steerToken   = 0
    private var accelToken   = 0

    private val cx get() = (LEFT_X + RIGHT_X) / 2f
    private val cy get() = (LEFT_Y + RIGHT_Y) / 2f
    private val r  get() = (RIGHT_X - LEFT_X) / 2f

    fun handleTilt(tilt: Float) {
        currentTilt = tilt
        if (!steerRunning) {
            steerToken++
            steerRunning = true
            prevAngle = 0f
            doSteerFrame(steerToken)
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld   = on
        accelActive = on
        if (on && !accelRunning) {
            accelToken++
            accelRunning = true
            doAccelFrame(accelToken)
        }
    }

    private fun doSteerFrame(token: Int) {
        if (token != steerToken) { steerRunning = false; return }

        val tilt     = currentTilt
        val target   = if (abs(tilt) > DEADZONE) (tilt / 10f * MAX_ANGLE) else 0f

        // Smooth interpolation — jerky nahi hoga
        val angle    = prevAngle + (target - prevAngle) * 0.3f
        prevAngle    = angle
        val rad      = Math.toRadians(angle.toDouble())

        // ARC path — round rotation feel
        val lx = cx + r * cos(PI + rad).toFloat()
        val ly = cy + r * sin(PI + rad).toFloat()
        val rx = cx + r * cos(rad).toFloat()
        val ry = cy + r * sin(rad).toFloat()

        // Single continuous arc path
        val path = Path().apply {
            moveTo(lx, ly)
            // Arc through center to right
            quadTo(cx, cy - r * 0.3f * sin(rad).toFloat(), rx, ry)
        }

        val stroke = GestureDescription.StrokeDescription(
            path, 0L, 80L, true
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (token == steerToken) {
                    handler.postDelayed({ doSteerFrame(token) }, 8L)
                } else {
                    steerRunning = false
                }
            }
            override fun onCancelled(g: GestureDescription) {
                if (token == steerToken) {
                    handler.postDelayed({ doSteerFrame(token) }, 16L)
                } else {
                    steerRunning = false
                }
            }
        }, handler)
    }

    private fun doAccelFrame(token: Int) {
        if (token != accelToken || !accelActive) {
            accelRunning = false; return
        }

        val path = Path().apply { moveTo(ACCEL_X, ACCEL_Y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 500L, true))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (token == accelToken && accelActive) {
                    handler.postDelayed({ doAccelFrame(token) }, 8L)
                } else { accelRunning = false }
            }
            override fun onCancelled(g: GestureDescription) {
                if (token == accelToken && accelActive) {
                    handler.postDelayed({ doAccelFrame(token) }, 16L)
                } else { accelRunning = false }
            }
        }, handler)
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        steerRunning = false
        accelRunning = false
        instance = null
        super.onDestroy()
    }
}
