package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        // Default coordinates
        var LEFT_X_val   = 235f;  var LEFT_Y_val   = 720f
        var RIGHT_X_val  = 587f;  var RIGHT_Y_val  = 738f
        var GAS_X_val    = 2192f; var GAS_Y_val    = 850f
        var BRAKE_X_val  = 1943f; var BRAKE_Y_val  = 975f
        var SLIDE_val    = 60f
        var DEADZONE_val = 1.5f

        private var currentTilt   = 0f
        private var gasActive     = false
        private var brakeActive   = false
        
        private var isGesturing = false
        private var lastGestureStateStr = "" 
        
        private val currentStrokes = mutableMapOf<Int, GestureDescription.StrokeDescription>()
        private val currentPositions = mutableMapOf<Int, Pair<Float, Float>>()

        fun updateTilt(tilt: Float) {
            currentTilt = tilt
            checkAndStart()
        }

        fun setGas(on: Boolean) {
            gasActive = on
            checkAndStart()
        }

        fun setBrake(on: Boolean) {
            brakeActive = on
            checkAndStart()
        }

        // ADDED BACK: UdpListenerService ka error fix karne ke liye
        fun setButton(name: String, on: Boolean) {
            // Agar game me aage chal kar extra buttons add karne honge toh yahan logic daalenge.
            // Abhi ke liye isko khali rakha hai taaki Steer/Gas/Brake overlap na ho.
        }

        // ADDED BACK: Camera/Screen swipe feature compile hone ke liye
        fun doSwipe(x: Float, y: Float, dir: String, dist: Float) {
            val endX = when(dir) { "left" -> x-dist; "right" -> x+dist; else -> x }
            val endY = when(dir) { "up"   -> y-dist; "down"  -> y+dist; else -> y }
            val path = Path().apply { moveTo(x, y); lineTo(endX, endY) }
            try {
                instance?.dispatchGesture(
                    GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0L, 200L, false))
                        .build(), null, null)
            } catch (e: Exception) {
                Log.e("SWIPE", e.message ?: "")
            }
        }

        private fun checkAndStart() {
            val hasActiveTouches = (currentTilt > DEADZONE_val || currentTilt < -DEADZONE_val) || gasActive || brakeActive
            if (!isGesturing && hasActiveTouches) {
                isGesturing = true
                instance?.dispatchNextGesture(true)
            }
        }

        fun stop() {
            isGesturing = false
            gasActive = false
            brakeActive = false
            currentTilt = 0f
            currentStrokes.clear()
            currentPositions.clear()
        }
    }

    private fun dispatchNextGesture(isFirst: Boolean) {
        if (!isGesturing || instance == null) return

        val tilt = currentTilt
        val steerActive = if (tilt > DEADZONE_val) 1 else if (tilt < -DEADZONE_val) -1 else 0
        
        val currentStateStr = "S:$steerActive|G:$gasActive|B:$brakeActive"
        
        if (currentStateStr == "S:0|G:false|B:false") {
            stop()
            return
        }

        if (currentStateStr != lastGestureStateStr) {
            currentStrokes.clear()
            currentPositions.clear()
            lastGestureStateStr = currentStateStr
        }

        val touchesToMake = mutableListOf<Pair<Float, Float>>()

        if (steerActive != 0) {
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            val sx = if (tilt > 0) RIGHT_X_val else LEFT_X_val
            val sy = if (tilt > 0) RIGHT_Y_val else LEFT_Y_val
            touchesToMake.add(Pair(sx + factor * SLIDE_val, sy))
        }

        if (gasActive) {
            touchesToMake.add(Pair(GAS_X_val, GAS_Y_val))
        }

        if (brakeActive) {
            touchesToMake.add(Pair(BRAKE_X_val, BRAKE_Y_val))
        }

        val builder = GestureDescription.Builder()
        var hasStroke = false
        val duration = 40L 

        for (i in touchesToMake.indices) {
            val targetX = touchesToMake[i].first
            val targetY = touchesToMake[i].second
            val path = Path()

            val previousStroke = currentStrokes[i]
            var currX = currentPositions[i]?.first ?: targetX
            var currY = currentPositions[i]?.second ?: targetY

            if (isFirst || previousStroke == null) {
                currX = targetX
                currY = targetY
                path.moveTo(currX, currY)
                currX += 1f 
                path.lineTo(currX, currY)
                currentStrokes[i] = GestureDescription.StrokeDescription(path, 0L, duration, true)
            } else {
                path.moveTo(currX, currY)
                currX = if (currX == targetX) targetX + 1f else targetX
                currY = targetY
                path.lineTo(currX, currY)
                currentStrokes[i] = previousStroke.continueStroke(path, 0L, duration, true)
            }
            
            currentPositions[i] = Pair(currX, currY)
            builder.addStroke(currentStrokes[i]!!)
            hasStroke = true
        }

        if (!hasStroke) {
            stop()
            return
        }

        try {
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (isGesturing) dispatchNextGesture(false)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    stop()
                }
            }, null)
        } catch (e: Exception) {
            Log.e("MULTI", "Gesture error: ${e.message}")
            stop()
        }
    }

    override fun onServiceConnected() { 
        instance = this 
        val prefs = applicationContext.getSharedPreferences("tilt_prefs", MODE_PRIVATE)
        LEFT_X_val   = prefs.getFloat("left_x",   235f)
        LEFT_Y_val   = prefs.getFloat("left_y",   720f)
        RIGHT_X_val  = prefs.getFloat("right_x",  587f)
        RIGHT_Y_val  = prefs.getFloat("right_y",  738f)
        GAS_X_val    = prefs.getFloat("gas_x",   2192f)
        GAS_Y_val    = prefs.getFloat("gas_y",    850f)
        BRAKE_X_val  = prefs.getFloat("brake_x", 1943f)
        BRAKE_Y_val  = prefs.getFloat("brake_y",  975f)
        SLIDE_val    = prefs.getFloat("slide",     60f)
        DEADZONE_val = prefs.getFloat("deadzone",  1.5f)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
}
