package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        // Tere naye Button Coordinates
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
        
        // State track karne ke liye: 0 = Koi button nahi, -1 = Left dabaya hai, 1 = Right dabaya hai
        private var activeSteerState = 0 
        
        private var steerStroke: GestureDescription.StrokeDescription? = null
        private var gasStroke: GestureDescription.StrokeDescription? = null
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
        
        // Faisla karte hain ki kaunsa button dabana hai (-1 left ke liye, 1 right ke liye, 0 dono nahi)
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
            val targetX = if (desiredSteerState == -1) LEFT_BTN_X else RIGHT_BTN_X
            val targetY = if (desiredSteerState == -1) LEFT_BTN_Y else RIGHT_BTN_Y

            val sPath = Path()
            
            // Agar pehla touch hai, YA FIR state change hui hai (jaise Left se direct Right tilt kiya)
            if (isFirst || steerStroke == null || activeSteerState != desiredSteerState) {
                sPath.moveTo(targetX, targetY)
                sPath.lineTo(targetX + 1f, targetY) // Valid touch ke liye 1 pixel ka jump
                steerStroke = GestureDescription.StrokeDescription(sPath, 0L, duration, true)
            } else {
                // Same button ko hold karke rakho
                sPath.moveTo(targetX, targetY)
                sPath.lineTo(targetX + 1f, targetY)
                steerStroke = steerStroke!!.continueStroke(sPath, 0L, duration, true)
            }
            
            builder.addStroke(steerStroke!!)
            activeSteerState = desiredSteerState // Nayi state save kar lo
            hasStroke = true
        } else {
            steerStroke = null
            activeSteerState = 0
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
        activeSteerState = 0
        currentGasY = GAS_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
