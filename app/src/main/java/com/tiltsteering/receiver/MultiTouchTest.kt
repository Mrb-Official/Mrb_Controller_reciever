package com.tiltsteering.receiver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
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
        private var running       = false
        private val handler       = Handler(Looper.getMainLooper())

        fun updateTilt(tilt: Float) { currentTilt = tilt; ensureLoop() }
        fun setGas(on: Boolean)     { gasActive   = on;   ensureLoop() }
        fun setButton(name: String, on: Boolean) {
            activeButtons[name] = on
            ensureLoop()
        }

        private fun ensureLoop() {
            if (running) return
            running = true
            handler.post(object : Runnable {
                override fun run() {
                    if (!running) return
                    instance?.doGesture()
                    handler.postDelayed(this, 100L)
                }
            })
        }

        fun stop() {
            running = false
            gasActive = false
            activeButtons.clear()
        }
    }

    private fun doGesture() {
        // Steering aur buttons alag alag dispatch karo
        // Kyunki GestureDescription max 2 strokes = simultaneous touches
        // Solution: Steering = apna gesture, Buttons = apna alag gesture

        dispatchSteering()
        dispatchButtons()
    }

    private fun dispatchSteering() {
        val tilt = currentTilt
        if (tilt <= DEADZONE_val && tilt >= -DEADZONE_val) return

        val factor = (tilt / 10f).coerceIn(-1f, 1f)
        val sx = if (tilt > 0) RIGHT_X_val else LEFT_X_val
        val sy = if (tilt > 0) RIGHT_Y_val else LEFT_Y_val
        val ex = sx + factor * SLIDE_val

        val path = Path().apply { moveTo(sx, sy); lineTo(ex, sy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 200L, true)

        try {
            dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                null, handler)
        } catch (e: Exception) {
            android.util.Log.e("STEER", e.message ?: "")
        }
    }

    private fun dispatchButtons() {
        // Gas + active buttons ko ek saath dispatch karo (max 2 per gesture)
        val toDispatch = mutableListOf<Pair<Float, Float>>()

        // Gas
        if (gasActive) {
            val cfg = UdpListenerService.buttonConfig["GAS"]
            toDispatch.add(Pair(cfg?.first ?: GAS_X_val, cfg?.second ?: GAS_Y_val))
        }

        // Active buttons
        for ((name, active) in activeButtons) {
            if (!active) continue
            val cfg = UdpListenerService.buttonConfig[name]
            if (cfg != null) {
                toDispatch.add(Pair(cfg.first, cfg.second))
                android.util.Log.d("BTN", "Touch: $name = ${cfg.first},${cfg.second}")
            } else {
                android.util.Log.w("BTN", "No coords for: $name")
            }
        }

        if (toDispatch.isEmpty()) return

        // 2-2 karke dispatch karo
        toDispatch.chunked(2).forEach { chunk ->
            val builder = GestureDescription.Builder()
            chunk.forEach { (x, y) ->
                val path = Path().apply { moveTo(x, y); lineTo(x, y) }
                builder.addStroke(
                    GestureDescription.StrokeDescription(path, 0L, 200L, true))
            }
            try {
                dispatchGesture(builder.build(), null, handler)
            } catch (e: Exception) {
                android.util.Log.e("BTN", e.message ?: "")
            }
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

    override fun onAccessibilityEvent(e: AccessibilityEvent) {}
    override fun onInterrupt() { stop() }
    override fun onDestroy()   { stop(); instance = null; super.onDestroy() }
}
