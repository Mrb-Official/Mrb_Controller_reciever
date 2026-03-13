package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MultiTouchTest : AccessibilityService() {

    companion object {
        var instance: MultiTouchTest? = null

        const val LEFT_X  = 235f
        const val LEFT_Y  = 720f
        const val RIGHT_X = 587f
        const val RIGHT_Y = 738f
        const val GAS_X   = 2192f
        const val GAS_Y   = 850f
        
        const val DEADZONE = 0.1f 
        const val MAX_RADIUS = 120f // Steering kitna aage tak ghumegi

        private var currentTilt = 0f
        private var gasActive   = false
        
        // State tracking (Ungli kahan par hai abhi)
        private var isGesturing = false
        private var currentLeftX = LEFT_X
        private var currentLeftY = LEFT_Y
        private var currentRightX = RIGHT_X
        private var currentRightY = RIGHT_Y
        private var currentGasY = GAS_Y
        
        // Purane strokes ko yaad rakhne ke liye taaki unhe 'continue' kar sakein
        private var leftStroke: GestureDescription.StrokeDescription? = null
        private var rightStroke: GestureDescription.StrokeDescription? = null
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
        val duration = 40L // 40ms ka fast loop taaki drag ekdum smooth ho
        
        val needsSteering = Math.abs(tilt) > DEADZONE
        val needsGas = gasActive
        
        if (!needsSteering && !needsGas) {
            isGesturing = false
            resetStates()
            return
        }

        var hasStroke = false

        // --- STEERING LOGIC (Circular Hold & Drag) ---
        if (needsSteering) {
            // Circular Rotation Logic
            val targetLeftX = LEFT_X + (tilt * MAX_RADIUS)
            val targetLeftY = LEFT_Y - (tilt * MAX_RADIUS)
            
            val targetRightX = RIGHT_X - (tilt * MAX_RADIUS)
            val targetRightY = RIGHT_Y + (tilt * MAX_RADIUS)

            // Left Ungli ko hold karke khiskana
            val lPath = Path().apply { moveTo(currentLeftX, currentLeftY); lineTo(targetLeftX, targetLeftY) }
            leftStroke = if (isFirst || leftStroke == null) {
                GestureDescription.StrokeDescription(lPath, 0L, duration, true)
            } else {
                leftStroke!!.continueStroke(lPath)
            }
            builder.addStroke(leftStroke!!)
            currentLeftX = targetLeftX
            currentLeftY = targetLeftY

            // Right Ungli ko hold karke khiskana
            val rPath = Path().apply { moveTo(currentRightX, currentRightY); lineTo(targetRightX, targetRightY) }
            rightStroke = if (isFirst || rightStroke == null) {
                GestureDescription.StrokeDescription(rPath, 0L, duration, true)
            } else {
                rightStroke!!.continueStroke(rPath)
            }
            builder.addStroke(rightStroke!!)
            currentRightX = targetRightX
            currentRightY = targetRightY
            
            hasStroke = true
        } else {
            leftStroke = null
            rightStroke = null
            currentLeftX = LEFT_X
            currentLeftY = LEFT_Y
            currentRightX = RIGHT_X
            currentRightY = RIGHT_Y
        }

        // --- GAS BUTTON LOGIC (Hold) ---
        if (needsGas) {
            val targetGasY = if (currentGasY == GAS_Y) GAS_Y + 1f else GAS_Y
            val gPath = Path().apply { moveTo(GAS_X, currentGasY); lineTo(GAS_X, targetGasY) }
            gasStroke = if (isFirst || gasStroke == null) {
                GestureDescription.StrokeDescription(gPath, 0L, duration, true)
            } else {
                gasStroke!!.continueStroke(gPath)
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
            // Callback ke zariye agla frame trigger karna (No more handler overlap)
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
        leftStroke = null; rightStroke = null; gasStroke = null
        currentLeftX = LEFT_X; currentLeftY = LEFT_Y
        currentRightX = RIGHT_X; currentRightY = RIGHT_Y; currentGasY = GAS_Y
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() { isGesturing = false; resetStates() }
    override fun onDestroy() { isGesturing = false; instance = null; super.onDestroy() }
}
