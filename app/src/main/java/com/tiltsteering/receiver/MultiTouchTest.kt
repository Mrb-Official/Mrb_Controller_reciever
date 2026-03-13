package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        const val LEFT_BTN_X  = 260f
        const val LEFT_BTN_Y  = 700f
        const val RIGHT_BTN_X = 600f
        const val RIGHT_BTN_Y = 700f
        
        const val GAS_X = 2192f
        const val GAS_Y = 850f
        
        const val DEADZONE = 0.1f 

        private var currentTilt = 0f
        private var gasActive   = false
        
        private var isGesturing = false
        private var activeSteerState = 0 
        
        private var steerStroke: GestureDescription.StrokeDescription? = null
        private var gasStroke: GestureDescription.StrokeDescription? = null
        
        // Exact last position track karne ke liye variables (Hold tutne se bachane ke liye)
        private var currentSteerX = 0f
        private var currentGasY = GAS_Y

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
        
        val desiredSteerState = if (tilt < -DEADZONE) -1 else if (tilt > DEADZONE) 1 else 0
        val needsGas = gasActive
        
        if (desiredSteerState == 0 && !needsGas) {
            isGesturing = false
            resetStates()
            return
        }

        var hasStroke = false

        // --- BUTTON HOLD LOGIC (LEFT ya RIGHT) ---
        if (desiredSteerState != 0) {
            val baseTargetX = if (desiredSteerState == -1) LEFT_BTN_X else RIGHT_BTN_X
            val baseTargetY = if (desiredSteerState == -1) LEFT_BTN_Y else RIGHT_BTN_Y

            val sPath = Path()
            
            if (isFirst || steerStroke == null || activeSteerState != desiredSteerState) {
                // Naya button press
                currentSteerX = baseTargetX
                sPath.moveTo(currentSteerX, baseTargetY)
                currentSteerX += 1f // 1 pixel aage badhao
                sPath.lineTo(currentSteerX, baseTargetY)
                steerStroke = GestureDescription.StrokeDescription(sPath, 0L, duration, true)
            } else {
                // Continue Hold: Purani jagah se start karo (Taaki ungli na uthe)
                sPath.moveTo(currentSteerX, baseTargetY)
                // Wapas base pe aao ya 1 pixel aage jao (Vibrate by 1 pixel)
                currentSteerX = if (currentSteerX == baseTargetX) baseTargetX + 1f else baseTargetX
                sPath.lineTo(currentSteerX, baseTargetY)
                steerStroke = steerStroke!!.continueStroke(sPath, 0L, duration, true)
            }
            
            builder.addStroke(steerStroke!!)
            activeSteerState = desiredSteerState 
            hasStroke = true
        } else {
            steerStroke = null
            activeSteerState = 0
        }

        // --- GAS LOGIC (Hold) ---
        if (needsGas) {
            val gPath = Path()
            if (isFirst || gasStroke == null) {
                currentGasY = GAS_Y
                gPath.moveTo(GAS_X, currentGasY)
                currentGasY += 1f 
                gPath.lineTo(GAS_X, currentGasY)
                gasStroke = GestureDescription.StrokeDescription(gPath, 0L, duration, true)
            } else {
                // Continue Hold
                gPath.moveTo(GAS_X, currentGasY)
                currentGasY = if (currentGasY == GAS_Y) GAS_Y + 1f else GAS_Y
                gPath.lineTo(GAS_X, currentGasY)
                gasStroke = gasStroke!!.continueStroke(gPath, 0L, duration, true)
            }
            builder.addStroke(gasStroke!!)
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
        activeSteerState = 0
        currentSteerX = 0f
        currentGasY = GAS_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
