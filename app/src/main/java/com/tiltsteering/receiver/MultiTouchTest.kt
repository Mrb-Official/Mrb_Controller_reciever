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

        private var currentTilt      = 0f
        private var gasActive        = false
        private val activeButtons    = mutableMapOf<String, Boolean>()
        private var running          = false
        private val handler          = Handler(Looper.getMainLooper())

        // Track last state to avoid unnecessary re-dispatch
        private var lastTouchKey     = ""
        private var gestureActive    = false

        fun updateTilt(tilt: Float) { currentTilt = tilt; ensureLoop() }
        fun setGas(on: Boolean)     { gasActive = on; ensureLoop() }
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
                    handler.postDelayed(this, 80L)
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
                for (i in 1..10) {
                    val p = i.toFloat() / 10f
                    path.lineTo(x + (endX - x) * p, y + (endY - y) * p)
                }
                val stroke = GestureDescription.StrokeDescription(
                    path, 0L, 500L, false)
                try {
                    instance?.dispatchGesture(
                        GestureDescription.Builder().addStroke(stroke).build(),
                        null, h)
                    gestureActive = false
                    lastTouchKey = ""
                } catch (e: Exception) {
                    android.util.Log.e("SWIPE", e.message ?: "")
                }
            }
        }

        fun stop() {
            running = false
            gasActive = false
            activeButtons.clear()
            lastTouchKey = ""
            gestureActive = false
        }
    }

    private fun doGesture() {
        val touches = mutableListOf<Triple<Float, Float, String>>() // x, y, id

        // Steering
        val tilt = currentTilt
        if (tilt > DEADZONE_val || tilt < -DEADZONE_val) {
            val factor = (tilt / 10f).coerceIn(-1f, 1f)
            val sx = if (tilt > 0) RIGHT_X_val else LEFT_X_val
            val sy = if (tilt > 0) RIGHT_Y_val else LEFT_Y_val
            touches.add(Triple(sx + factor * SLIDE_val, sy, "steer"))
        }

        // Gas
        if (gasActive) {
            val cfg = UdpListenerService.buttonConfig["GAS"]
            touches.add(Triple(cfg?.first ?: GAS_X_val, cfg?.second ?: GAS_Y_val, "gas"))
        }

        // Active buttons
        for ((name, active) in activeButtons) {
            if (!active) continue
            val cfg = UdpListenerService.buttonConfig[name] ?: continue
            touches.add(Triple(cfg.first, cfg.second, name))
        }

        // Build state key
        val stateKey = touches.joinToString("|") { "${it.third}:${it.first.toInt()},${it.second.toInt()}" }

        // Nothing active = release all
        if (touches.isEmpty()) {
            if (gestureActive) {
                gestureActive = false
                lastTouchKey = ""
            }
            return
        }

        // Same state = don't redispatch = no drop!
        if (stateKey == lastTouchKey && gestureActive) return

        lastTouchKey = stateKey
        gestureActive = true

        // Dispatch chunked (max 2 per gesture)
        touches.chunked(2).forEachIndexed { index, chunk ->
            val builder = GestureDescription.Builder()
            chunk.forEach { (x, y, _) ->
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + 1f, y)
                }
                builder.addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        (index * 10).toLong(),
                        2000L,  // 2 second duration
                        true    // willContinue
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
