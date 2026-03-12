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

        // var rakha hai taaki app se change ho sake
        var LEFT_X  = 580f
        var LEFT_Y  = 700f
        var RIGHT_X = 229f
        var RIGHT_Y = 703f
        var ACCEL_X = 2192f
        var ACCEL_Y = 850f
        const val DEADZONE = 1.0f

        // Sender se accessibility band karne ke liye
        fun disableFromSender() {
            instance?.disableSelf()
            instance = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastDir = 0
    private var accelHeld = false
    private var steeringActive = false
    private var currentSteerDir = 0
    private var accelActive = false

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
             0 -> stopSteering()
        }
    }

    fun handleAccelerator(on: Boolean) {
        if (accelHeld == on) return
        accelHeld = on
        if (on) startAccel() else stopAccel()
    }

    private fun startSteering(dir: Int) {
        steeringActive = true
        currentSteerDir = dir
        doSteer(dir)
    }

    private fun stopSteering() {
        steeringActive = false
        currentSteerDir = 0
    }

    private fun startAccel() {
        accelActive = true
        doAccel()
    }

    private fun stopAccel() {
        accelActive = false
    }

    private fun doSteer(dir: Int) {
        if (!steeringActive || currentSteerDir != dir) return
        val x = if (dir == -1) LEFT_X else RIGHT_X
        val y = if (dir == -1) LEFT_Y else RIGHT_Y
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1000L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                if (steeringActive && currentSteerDir == dir) {
                    handler.post { doSteer(dir) }
                }
            }
            override fun onCancelled(g: GestureDescription) {
                if (steeringActive && currentSteerDir == dir) {
                    handler.postDelayed({ doSteer(dir) }, 16L)
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
