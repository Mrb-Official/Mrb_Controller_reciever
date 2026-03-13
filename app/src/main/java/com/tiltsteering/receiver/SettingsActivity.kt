package com.tiltsteering.receiver

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.parseColor("#0A0A0A"))

        val prefs = getSharedPreferences("tilt_prefs", MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        fun addField(label: String, key: String, default: Float): EditText {
            val tv = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#888888"))
                textSize = 12f
            }
            val et = EditText(this).apply {
                setText(prefs.getFloat(key, default).toInt().toString())
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                textSize = 16f
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setPadding(20, 16, 20, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 4, 0, 16)
                layoutParams = lp
            }
            layout.addView(tv)
            layout.addView(et)
            return et
        }

        val etLeftX   = addField("Steer LEFT X",  "left_x",   235f)
        val etLeftY   = addField("Steer LEFT Y",  "left_y",   720f)
        val etRightX  = addField("Steer RIGHT X", "right_x",  587f)
        val etRightY  = addField("Steer RIGHT Y", "right_y",  738f)
        val etGasX    = addField("Gas X",         "gas_x",   2192f)
        val etGasY    = addField("Gas Y",         "gas_y",    850f)
        val etSlide   = addField("Slide Amount",  "slide",     60f)
        val etDead    = addField("Deadzone",      "deadzone",   1.5f)

        val btnSave = Button(this).apply {
            text = "SAVE"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            textSize = 14f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120)
            lp.setMargins(0, 16, 0, 0)
            layoutParams = lp
        }

        btnSave.setOnClickListener {
            prefs.edit()
                .putFloat("left_x",   etLeftX.text.toString().toFloatOrNull()  ?: 235f)
                .putFloat("left_y",   etLeftY.text.toString().toFloatOrNull()  ?: 720f)
                .putFloat("right_x",  etRightX.text.toString().toFloatOrNull() ?: 587f)
                .putFloat("right_y",  etRightY.text.toString().toFloatOrNull() ?: 738f)
                .putFloat("gas_x",    etGasX.text.toString().toFloatOrNull()   ?: 2192f)
                .putFloat("gas_y",    etGasY.text.toString().toFloatOrNull()   ?: 850f)
                .putFloat("slide",    etSlide.text.toString().toFloatOrNull()  ?: 60f)
                .putFloat("deadzone", etDead.text.toString().toFloatOrNull()   ?: 1.5f)
                .apply()

            // Update live values
            MultiTouchTest.LEFT_X_val   = etLeftX.text.toString().toFloatOrNull()  ?: 235f
            MultiTouchTest.LEFT_Y_val   = etLeftY.text.toString().toFloatOrNull()  ?: 720f
            MultiTouchTest.RIGHT_X_val  = etRightX.text.toString().toFloatOrNull() ?: 587f
            MultiTouchTest.RIGHT_Y_val  = etRightY.text.toString().toFloatOrNull() ?: 738f
            MultiTouchTest.GAS_X_val    = etGasX.text.toString().toFloatOrNull()   ?: 2192f
            MultiTouchTest.GAS_Y_val    = etGasY.text.toString().toFloatOrNull()   ?: 850f
            MultiTouchTest.SLIDE_val    = etSlide.text.toString().toFloatOrNull()  ?: 60f
            MultiTouchTest.DEADZONE_val = etDead.text.toString().toFloatOrNull()   ?: 1.5f

            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            finish()
        }

        layout.addView(btnSave)

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }
}
