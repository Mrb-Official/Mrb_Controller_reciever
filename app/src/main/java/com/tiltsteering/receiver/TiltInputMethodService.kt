package com.tiltsteering.receiver

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View

class TiltInputMethodService : InputMethodService() {

    companion object {
        var instance: TiltInputMethodService? = null

        // Key states
        var keyW = false
        var keyA = false
        var keyD = false
        var keyS = false

        fun pressKey(keyCode: Int) {
            instance?.currentInputConnection?.let { ic ->
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }

        fun holdKey(keyCode: Int, pressed: Boolean) {
            instance?.currentInputConnection?.let { ic ->
                if (pressed) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onCreateInputView(): View? = null

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
