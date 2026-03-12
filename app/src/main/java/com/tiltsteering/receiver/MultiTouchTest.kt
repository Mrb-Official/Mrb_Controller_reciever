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

        fun testMultiTouch() {
            val svc = instance ?: return
            val handler = Handler(Looper.getMainLooper())
            handler.post { svc.doMultiTouch() }
        }
    }

    private fun doMultiTouch() {
        // Path 1 - Left finger
        val path1 = Path().apply {
            moveTo(300f, 750f)
            lineTo(300f, 750f)
        }

        // Path 2 - Right finger  
        val path2 = Path().apply {
            moveTo(900f, 750f)
            lineTo(900f, 750f)
        }

        // SAME startTime = 0L = Simultaneous ✅
        val stroke1 = GestureDescription.StrokeDescription(
            path1, 0L, 2000L
        )
        val stroke2 = GestureDescription.StrokeDescription(
            path2, 0L, 2000L
        )

        // EK HI BUILDER = Multitouch ✅
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                android.util.Log.d("MULTI", "✅ Gesture completed!")
            }
            override fun onCancelled(g: GestureDescription) {
                android.util.Log.d("MULTI", "❌ Gesture cancelled!")
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onServiceConnected() {
        instance = this
        android.util.Log.d("MULTI", "Service connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
