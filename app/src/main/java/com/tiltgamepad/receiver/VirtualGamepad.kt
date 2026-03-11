package com.tiltgamepad.receiver

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent

class VirtualGamepad(private val context: Context) {
    private var deviceId = -1
    private val handler = Handler(Looper.getMainLooper())

    // Current state
    private var steerAxis = 0f
    private var gasAxis = 0f
    private var brakeAxis = 0f

    fun create() {
        Log.d("GAMEPAD", "Virtual gamepad created")
    }

    fun setSteer(value: Float) {
        steerAxis = value
        Log.d("GAMEPAD", "Steer: $value")
        injectAxis()
    }

    fun setGas(on: Boolean) {
        gasAxis = if (on) 1f else 0f
        Log.d("GAMEPAD", "Gas: $on")
        injectAxis()
    }

    fun setBrake(on: Boolean) {
        brakeAxis = if (on) 1f else 0f
        Log.d("GAMEPAD", "Brake: $on")
        injectAxis()
    }

    private fun injectAxis() {
        // Android 13+ VirtualDevice API
        try {
            val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            Log.d("GAMEPAD", "Injecting: steer=$steerAxis gas=$gasAxis brake=$brakeAxis")
        } catch (e: Exception) {
            Log.e("GAMEPAD", "Inject failed: ${e.message}")
        }
    }

    fun destroy() {
        Log.d("GAMEPAD", "Virtual gamepad destroyed")
    }
}
