package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        // Naya Pointer Position jo tune bataya (X lock rahega, Y center hai)
        const val STEER_X = 570f 
        const val STEER_BASE_Y = 750f 
        
        // Pura screen cover karne ke liye lamba slide (400 pixels upar aur neeche)
        const val MAX_SLIDE_Y = 400f 
        
        const val GAS_X = 2192f
        const val GAS_Y = 850f
        
        const val DEADZONE = 0.1f 

        private var currentTilt = 0f
        private var gasActive   = false
        
        private var isGesturing = false
        
        // Tracking Y position taaki smooth slide ho sake
        private var currentSteerY = STEER_BASE_Y
        private var currentGasY = GAS_Y
        
        private var steerStroke: GestureDescription.StrokeDescription? = null
        private var gasStroke: GestureDescription.StrokeDescription? = null

        fun updateTilt(tilt: Float) {
            currentTilt = tilt
            checkAndStart()
        }

        fun setGas(on: Boolean) {
            gasActive = on
            checkAndStart()
        }
        
        private fun checkAndStart() {
            val active = Math.abs(currentTilt) > DEADZONE || gasActive
            if (!isGesturing && active) {
                isGesturing = true
                instance?.dispatchNextGesture(true)
            }
        }
    }

    private fun dispatchNextGesture(isFirst: Boolean) {
        if (!isGesturing || instance == null) return

        val tilt = currentTilt
        val builder = GestureDescription.Builder()
        val duration = 40L 
        
        val needsSteering = Math.abs(tilt) > DEADZONE
        val needsGas = gasActive
        
        if (!needsSteering && !needsGas) {
            isGesturing = false
            resetStates()
            return
        }

        var hasStroke = false

        // --- VERTICAL SLIDE (Straight Line Upar-Neeche) LOGIC ---
        if (needsSteering) {
            // Tilt ko limit kiya
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            
            // X constant (570) rahega, sirf Y change hoga
            // Factor ke hisaab se target Y nikalenge
            val targetY = STEER_BASE_Y + (factor * MAX_SLIDE_Y)

            val sPath = Path()
            
            if (isFirst || steerStroke == null) {
                // Pehla touch: Seedha exact position par start karo
                currentSteerY = targetY
                sPath.moveTo(STEER_X, currentSteerY)
                sPath.lineTo(STEER_X, currentSteerY + 1f) // 1 pixel validation ke liye
                steerStroke = GestureDescription.StrokeDescription(sPath, 0L, duration, true)
            } else {
                // Hold karke smooth khiskana purani Y se nayi Y tak
                sPath.moveTo(STEER_X, currentSteerY)
                sPath.lineTo(STEER_X, targetY)
                currentSteerY = targetY
                steerStroke = steerStroke!!.continueStroke(sPath, 0L, duration, true)
            }
            
            builder.addStroke(steerStroke!!)
            hasStroke = true
        } else {
            steerStroke = null
        }

        // --- GAS LOGIC ---
        if (needsGas) {
            val targetGasY = if (currentGasY == GAS_Y) GAS_Y + 1f else GAS_Y
            val gPath = Path()
            if (isFirst || gasStroke == null) {
                gPath.moveTo(GAS_X, GAS_Y)
                gPath.lineTo(GAS_X, targetGasY)
                gasStroke = GestureDescription.StrokeDescription(gPath, 0L, duration, true)
            } else {
                gPath.moveTo(GAS_X, currentGasY)
                gPath.lineTo(GAS_X, targetGasY)
                gasStroke = gasStroke!!.continueStroke(gPath, 0L, duration, true)
            }
            builder.addStroke(gasStroke!!)
            currentGasY = targetGasY
            hasStroke = true
        } else {
            gasStroke = null
            currentGasY = GAS_Y
        }

        if (!hasStroke) {
            isGesturing = false
            resetStates()
            return
        }

        try {
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (isGesturing) dispatchNextGesture(false)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    isGesturing = false
                    resetStates()
                }
            }, null)
        } catch (e: Exception) {
            Log.e("MULTI", "Gesture error: ${e.message}")
            isGesturing = false
            resetStates()
        }
    }
    
    private fun resetStates() {
        steerStroke = null; gasStroke = null
        currentSteerY = STEER_BASE_Y
        currentGasY = GAS_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
