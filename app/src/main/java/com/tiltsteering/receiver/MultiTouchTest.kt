package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        // Steering Wheel ka Center aur Radius (Tere purane points ke hisaab se set kiya hai)
        const val STEER_CENTER_X = 411f 
        const val STEER_CENTER_Y = 729f 
        const val WHEEL_RADIUS   = 175f 
        
        const val GAS_X = 2192f
        const val GAS_Y = 850f
        
        const val DEADZONE = 0.1f 

        private var currentTilt = 0f
        private var gasActive   = false
        
        private var isGesturing = false
        
        // Single pointer tracking variables
        private var currentSteerX = STEER_CENTER_X
        private var currentSteerY = STEER_CENTER_Y - WHEEL_RADIUS
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

        // --- SINGLE POINTER CIRCULAR STEERING LOGIC ---
        if (needsSteering) {
            // Tilt ko -1 se 1 ke beech me limit karte hain (assume max tilt is ~10)
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            
            // Angle calculate karna (Top = -90 degrees, Left = -180, Right = 0)
            // 100 degrees left aur 100 degrees right ghumne ki limit
            val angleDegrees = -90.0 + (factor * 100.0)
            val angleRad = Math.toRadians(angleDegrees)

            // Circle ke circumference (border) par naya X aur Y nikalna
            val targetX = STEER_CENTER_X + (WHEEL_RADIUS * Math.cos(angleRad)).toFloat()
            val targetY = STEER_CENTER_Y + (WHEEL_RADIUS * Math.sin(angleRad)).toFloat()

            val sPath = Path()
            
            if (isFirst || steerStroke == null) {
                // Pehli baar touch seedha target par rakho
                currentSteerX = targetX
                currentSteerY = targetY
                sPath.moveTo(currentSteerX, currentSteerY)
                sPath.lineTo(currentSteerX + 1f, currentSteerY) // 1 pixel ka jump valid gesture ke liye
                steerStroke = GestureDescription.StrokeDescription(sPath, 0L, duration, true)
            } else {
                // Ungli ko purani jagah se nayi jagah arc me khiskao
                sPath.moveTo(currentSteerX, currentSteerY)
                sPath.lineTo(targetX, targetY)
                currentSteerX = targetX
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
        currentGasY = GAS_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
