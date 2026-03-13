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
        
        const val BRAKE_X = 1943f
        const val BRAKE_Y = 975f
        
        const val DEADZONE = 0.1f 

        private var currentTilt = 0f
        private var gasActive   = false
        private var brakeActive = false 
        
        private var isGesturing = false
        
        private var activeSteerState = 0 
        private var lastGestureState = 0 // Multi-touch sync karne ka naya hathiyar!
        
        private var steerStroke: GestureDescription.StrokeDescription? = null
        private var gasStroke: GestureDescription.StrokeDescription? = null
        private var brakeStroke: GestureDescription.StrokeDescription? = null
        
        private var currentSteerX = 0f
        private var currentGasY = GAS_Y
        private var currentBrakeY = BRAKE_Y

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
        
        private fun checkAndStart() {
            val active = Math.abs(currentTilt) > DEADZONE || gasActive || brakeActive
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
        val needsBrake = brakeActive
        
        // Ek unique number banate hain jisse pata chale ki kitne aur kaunse buttons dabe hain
        val currentGestureState = (if (desiredSteerState != 0) 1 else 0) + 
                                  (if (needsGas) 2 else 0) + 
                                  (if (needsBrake) 4 else 0)
        
        if (currentGestureState == 0) {
            isGesturing = false
            resetStates()
            return
        }

        // AGAR BUTTONS KA COMBINATION CHANGE HUA HAI (e.g., Gas dabi thi, ab Steer bhi dab gaya)
        // Toh saare touches ko reset karke ek sath fresh dabao taaki Android overlap na kare!
        if (currentGestureState != lastGestureState || activeSteerState != desiredSteerState) {
            steerStroke = null
            gasStroke = null
            brakeStroke = null
            lastGestureState = currentGestureState
            activeSteerState = desiredSteerState
        }

        var hasStroke = false

        // --- STEERING (Left/Right) ---
        if (desiredSteerState != 0) {
            val baseTargetX = if (desiredSteerState == -1) LEFT_BTN_X else RIGHT_BTN_X
            val baseTargetY = if (desiredSteerState == -1) LEFT_BTN_Y else RIGHT_BTN_Y

            val sPath = Path()
            
            if (steerStroke == null) {
                currentSteerX = baseTargetX
                sPath.moveTo(currentSteerX, baseTargetY)
                currentSteerX += 1f 
                sPath.lineTo(currentSteerX, baseTargetY)
                steerStroke = GestureDescription.StrokeDescription(sPath, 0L, duration, true)
            } else {
                sPath.moveTo(currentSteerX, baseTargetY)
                currentSteerX = if (currentSteerX == baseTargetX) baseTargetX + 1f else baseTargetX
                sPath.lineTo(currentSteerX, baseTargetY)
                steerStroke = steerStroke!!.continueStroke(sPath, 0L, duration, true)
            }
            builder.addStroke(steerStroke!!)
            hasStroke = true
        }

        // --- GAS ---
        if (needsGas) {
            val gPath = Path()
            if (gasStroke == null) {
                currentGasY = GAS_Y
                gPath.moveTo(GAS_X, currentGasY)
                currentGasY += 1f 
                gPath.lineTo(GAS_X, currentGasY)
                gasStroke = GestureDescription.StrokeDescription(gPath, 0L, duration, true)
            } else {
                gPath.moveTo(GAS_X, currentGasY)
                currentGasY = if (currentGasY == GAS_Y) GAS_Y + 1f else GAS_Y
                gPath.lineTo(GAS_X, currentGasY)
                gasStroke = gasStroke!!.continueStroke(gPath, 0L, duration, true)
            }
            builder.addStroke(gasStroke!!)
            hasStroke = true
        }

        // --- BRAKE ---
        if (needsBrake) {
            val bPath = Path()
            if (brakeStroke == null) {
                currentBrakeY = BRAKE_Y
                bPath.moveTo(BRAKE_X, currentBrakeY)
                currentBrakeY += 1f 
                bPath.lineTo(BRAKE_X, currentBrakeY)
                brakeStroke = GestureDescription.StrokeDescription(bPath, 0L, duration, true)
            } else {
                bPath.moveTo(BRAKE_X, currentBrakeY)
                currentBrakeY = if (currentBrakeY == BRAKE_Y) BRAKE_Y + 1f else BRAKE_Y
                bPath.lineTo(BRAKE_X, currentBrakeY)
                brakeStroke = brakeStroke!!.continueStroke(bPath, 0L, duration, true)
            }
            builder.addStroke(brakeStroke!!)
            hasStroke = true
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
        steerStroke = null; gasStroke = null; brakeStroke = null
        activeSteerState = 0
        lastGestureState = 0
        currentSteerX = 0f
        currentGasY = GAS_Y
        currentBrakeY = BRAKE_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
