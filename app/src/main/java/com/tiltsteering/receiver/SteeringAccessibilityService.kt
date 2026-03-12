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
        var LEFT_X  = 150f
        var LEFT_Y  = 750f
        var RIGHT_X = 450f
        var RIGHT_Y = 750f
        var ACCEL_X = 2192f
        var ACCEL_Y = 850f
        const val DEADZONE  = 0.5f
        const val MAX_ANGLE = 60f

        fun disableFromSender() {
            instance?.disableSelf()
            instance = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentTilt = 0f
    private var accelActive = false
    private var accelHeld   = false

    // Steering gesture state
    private var steerRunning = false
    private var steerToken   = 0

    // Accel gesture state  
    private var accelRunning = false
    private var accelToken   = 0

    private val centerX get() = (LEFT_X + RIGHT_X) / 2f
    private val centerY get() = (LEFT_Y + RIGHT_Y) / 2f
    private val radius  get() = (RIGHT_X - LEFT_X) / 2f

    fun handleTilt(tilt: Float) {
        currentTilt = tilt
        if (!steerRunning) {
            steerToken++
            steerRunning = true
            doSteerFrame(steerToken)
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld  = on
        accelActive = on
        if (on && !accelRunning) {
            accelToken++
            accelRunning = true
            doAccelFrame(accelToken)
        }
    }

    // ─── STEERING LOOP ───────────────────────────────
    private fun doSteerFrame(token: Int) {
        if (token != steerToken) return

        val tilt  = currentTilt
        val angle = if (abs(tilt) > DEADZONE) tilt / 10f * MAX_ANGLE else 0f
        val rad   = Math.toRadians(angle.toDouble())

        val lx = centerX + radius * cos(Math.PI + rad).toFloat()
        val ly = centerY + radius * sin(Math.PI + rad).toFloat()
        val rx = centerX + radius * cos(rad).toFloat()
        val ry = centerY + radius * sin(rad).toFloat()

        val leftPath = Path().apply {
            moveTo(LEFT_X, LEFT_Y)
            lineTo(lx, ly)
        }
        val rightPath = Path().apply {
            moveTo(RIGHT_X, RIGHT_Y)
            lineTo(rx, ry)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(leftPath,  0L, 200L, true))
            .addStroke(GestureDescription.StrokeDescription(rightPath, 0L, 200L, true))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (token == steerToken) {
                    handler.postDelayed({ doSteerFrame(token) }, 16L)
                } else {
                    steerRunning = false
                }
            }
            override fun onCancelled(g: GestureDescription) {
                if (token == steerToken) {
                    handler.postDelayed({ doSteerFrame(token) }, 32L)
                } else {
                    steerRunning = false
                }
            }
        }, handler)
    }

    // ─── ACCEL LOOP ──────────────────────────────────
    private fun doAccelFrame(token: Int) {
        if (token != accelToken || !accelActive) {
            accelRunning = false
            return
        }

        val path = Path().apply { moveTo(ACCEL_X, ACCEL_Y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 500L, true))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (token == accelToken && accelActive) {
                    handler.postDelayed({ doAccelFrame(token) }, 16L)
                } else {
                    accelRunning = false
                }
            }
            override fun onCancelled(g: GestureDescription) {
                if (token == accelToken && accelActive) {
                    handler.postDelayed({ doAccelFrame(token) }, 32L)
                } else {
                    accelRunning = false
                }
            }
        }, handler)
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        steerRunning = false
        accelRunning = false
        instance     = null
        super.onDestroy()
    }
}
