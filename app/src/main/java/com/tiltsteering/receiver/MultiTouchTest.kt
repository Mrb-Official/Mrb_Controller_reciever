package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        var LEFT_X_val   = 235f;  var LEFT_Y_val   = 720f
        var RIGHT_X_val  = 587f;  var RIGHT_Y_val  = 738f
        var GAS_X_val    = 2192f; var GAS_Y_val    = 850f
        var SLIDE_val    = 60f
        var DEADZONE_val = 1.5f

        private var currentTilt   = 0f
        private var gasActive     = false
        private val activeButtons = mutableMapOf<String, Boolean>()
        
        private var isGesturing = false
        private var lastGestureStateHash = 0 
        
        // Active strokes ko track karne ke liye
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

        fun setButton(name: String, on: Boolean) {
            activeButtons[name] = on
            checkAndStart()
        }

        private fun checkAndStart() {
            val hasActiveTouches = (currentTilt > DEADZONE_val || currentTilt < -DEADZONE_val) || 
                                   gasActive || 
                                   activeButtons.values.any { it }
            
            if (!isGesturing && hasActiveTouches) {
                isGesturing = true
                instance?.dispatchNextGesture(true)
            }
        }
        
        fun doSwipe(x: Float, y: Float, dir: String, dist: Float) {
            // Swipe ke liye Claude ka logic theek tha, use waise hi rakha hai
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

        fun stop() {
            isGesturing = false
            gasActive = false
            currentTilt = 0f
            activeButtons.clear()
            currentStrokes.clear()
            currentPositions.clear()
        }
    }

    private fun dispatchNextGesture(isFirst: Boolean) {
        if (!isGesturing || instance == null) return

        // 1. Collect all required touches
        val touchesToMake = mutableListOf<Pair<Float, Float>>()
        
        val tilt = currentTilt
        if (tilt > DEADZONE_val || tilt < -DEADZONE_val) {
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            val sx = if (tilt > 0) RIGHT_X_val else LEFT_X_val
            val sy = if (tilt > 0) RIGHT_Y_val else LEFT_Y_val
            touchesToMake.add(Pair(sx + factor * SLIDE_val, sy))
        }

        if (gasActive) {
            touchesToMake.add(Pair(GAS_X_val, GAS_Y_val))
        }

        for ((name, active) in activeButtons) {
            if (!active) continue
            // Assuming UdpListenerService.buttonConfig exists as per Claude's code
            val cfg = UdpListenerService.buttonConfig?.get(name) ?: continue
            touchesToMake.add(Pair(cfg.first, cfg.second))
        }

        if (touchesToMake.isEmpty()) {
            stop()
            return
        }

        // 2. Hash check: Agar naya button daba ya chhuta hai, toh reset karo (Overlap bachane ke liye)
        val currentHash = touchesToMake.hashCode()
        if (currentHash != lastGestureStateHash) {
            currentStrokes.clear()
            currentPositions.clear()
            lastGestureStateHash = currentHash
        }

        // 3. Ek single Builder me saare touches daalo (Android 10 touches tak support karta hai)
        val builder = GestureDescription.Builder()
        var hasStroke = false
        val duration = 40L 

        for (i in touchesToMake.indices) {
            val baseTargetX = touchesToMake[i].first
            val baseTargetY = touchesToMake[i].second
            val path = Path()

            val previousStroke = currentStrokes[i]
            var currX = currentPositions[i]?.first ?: baseTargetX

            if (isFirst || previousStroke == null) {
                currX = baseTargetX
                path.moveTo(currX, baseTargetY)
                currX += 1f
                path.lineTo(currX, baseTargetY)
                currentStrokes[i] = GestureDescription.StrokeDescription(path, 0L, duration, true)
            } else {
                path.moveTo(currX, baseTargetY)
                currX = if (currX == baseTargetX) baseTargetX + 1f else baseTargetX
                path.lineTo(currX, baseTargetY)
                currentStrokes[i] = previousStroke.continueStroke(path, 0L, duration, true)
            }
            
            currentPositions[i] = Pair(currX, baseTargetY)
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
        SLIDE_val    = prefs.getFloat("slide",     60f)
        DEADZONE_val = prefs.getFloat("deadzone",  1.5f)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
}
