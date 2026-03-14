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

        // Gesture restart timer
        private var gestureStartTime = 0L
        private const val MAX_GESTURE_MS = 2800L // Android limit 3000ms

        fun updateTilt(tilt: Float) { currentTilt = tilt; ensureLoop() }
        fun setGas(on: Boolean)     { gasActive   = on;   ensureLoop() }
        fun setButton(name: String, on: Boolean) {
            activeButtons[name] = on
            ensureLoop()
        }

        private fun ensureLoop() {
            if (running) return
            running = true
            gestureStartTime = System.currentTimeMillis()
            handler.post(object : Runnable {
                override fun run() {
                    if (!running) return
                    instance?.doGesture()
                    handler.postDelayed(this, 100L)
                }
            })
        }

        fun doSwipe(x: Float, y: Float, dir: String, dist: Float) {
            val h = Handler(Looper.getMainLooper())
            h.post {
                val endX = when(dir) {
                    "left"  -> x - dist
                    "right" -> x + dist
                    else    -> x
                }
                val endY = when(dir) {
                    "up"   -> y - dist
                    "down" -> y + dist
                    else   -> y
                }
                val path = Path()
                path.moveTo(x, y)
                val steps = 10
                for (i in 1..steps) {
                    val p = i.toFloat() / steps
                    path.lineTo(x + (endX - x) * p, y + (endY - y) * p)
                }
                val stroke = GestureDescription.StrokeDescription(
                    path, 0L, 500L, false)
                try {
                    instance?.dispatchGesture(
                        GestureDescription.Builder().addStroke(stroke).build(),
                        null, h)
                } catch (e: Exception) {
                    android.util.Log.e("SWIPE", e.message ?: "")
                }
            }
        }

        fun stop() {
            running = false
            gasActive = false
            activeButtons.clear()
            gestureStartTime = 0L
        }
    }

    private fun doGesture() {
        val touches = mutableListOf<Pair<Float, Float>>()

        // Steering
        val tilt = currentTilt
        if (tilt > DEADZONE_val || tilt < -DEADZONE_val) {
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            val sx = if (tilt > 0) RIGHT_X_val else LEFT_X_val
            val sy = if (tilt > 0) RIGHT_Y_val else LEFT_Y_val
            touches.add(Pair(sx + factor * SLIDE_val, sy))
        }

        // Gas
        if (gasActive) {
            val cfg = UdpListenerService.buttonConfig["GAS"]
            touches.add(Pair(cfg?.first ?: GAS_X_val, cfg?.second ?: GAS_Y_val))
        }

        // Active buttons
        for ((name, active) in activeButtons) {
            if (!active) continue
            val cfg = UdpListenerService.buttonConfig[name]
            if (cfg != null) {
                touches.add(Pair(cfg.first, cfg.second))
            }
        }

        if (touches.isEmpty()) {
            gestureStartTime = System.currentTimeMillis()
            return
        }

        // Check if gesture needs restart (before 3s Android limit)
        val elapsed = System.currentTimeMillis() - gestureStartTime
        val needsRestart = elapsed >= MAX_GESTURE_MS

        if (needsRestart) {
            // Reset timer
            gestureStartTime = System.currentTimeMillis()
            android.util.Log.d("TOUCH", "Gesture restart at ${elapsed}ms")
        }

        touches.chunked(2).forEachIndexed { index, chunk ->
            val builder = GestureDescription.Builder()
            chunk.forEach { (x, y) ->
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + 1f, y)
                }
                builder.addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        (index * 10).toLong(),
                        // Agar restart = short duration, warna normal
                        if (needsRestart) 50L else 200L,
                        true // willContinue = always true for hold
                    )
                )
            }
            try {
                dispatchGesture(builder.build(), null, handler)
            } catch (e: Exception) {
                android.util.Log.e("TOUCH", e.message ?: "")
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
