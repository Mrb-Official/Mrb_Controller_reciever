package com.tiltsteering.receiver

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SteeringWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var tiltValue: Float = 0f
        set(value) { field = value; invalidate() }

    private val paintRing   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 8f }
    private val paintSpoke  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60,255,255,255); style = Paint.Style.STROKE; strokeWidth = 6f }
    private val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val paintArc    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80,255,255,255); style = Paint.Style.STROKE; strokeWidth = 6f }
    private val paintDot    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r  = (minOf(width, height) / 2f) - 8f
        val angle = (tiltValue / 10f * 90f).coerceIn(-90f, 90f)
        canvas.drawCircle(cx, cy, r, paintRing)
        canvas.drawCircle(cx, cy, r * 0.28f, paintCenter)
        val arcRect = RectF(cx - r*0.6f, cy - r*0.6f, cx + r*0.6f, cy + r*0.6f)
        canvas.drawArc(arcRect, -90f, -angle, false, paintArc)
        canvas.save()
        canvas.rotate(-angle, cx, cy)
        for (i in 0..2) {
            val a = Math.toRadians((i * 120.0 - 90.0))
            canvas.drawLine(
                cx + (r*0.28f* Math.cos(a)).toFloat(), cy + (r*0.28f*Math.sin(a)).toFloat(),
                cx + (r*Math.cos(a)).toFloat(),        cy + (r*Math.sin(a)).toFloat(), paintSpoke)
        }
        canvas.drawCircle(cx, cy - r + 8f, 5f, paintDot)
        canvas.restore()
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvPackets: TextView
    private lateinit var wheelView: SteeringWheelView
    private val handler = Handler(Looper.getMainLooper())

    private val update = object : Runnable {
        override fun run() {
            val tilt = UdpListenerService.lastTilt.toFloatOrNull() ?: 0f
            wheelView.tiltValue = tilt
            tvTilt.text    = "%.2f".format(tilt)
            tvPackets.text = "PKT: ${UdpListenerService.packetCount}"
            tvStatus.text  = if (MultiTouchTest.instance != null) "● ACTIVE" else "○ OFF"
            tvStatus.setTextColor(
                if (MultiTouchTest.instance != null)
                    Color.argb(255,0,255,100)
                else Color.argb(255,80,80,80))
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.parseColor("#0A0A0A"))
        setContentView(R.layout.activity_main)
        tvStatus  = findViewById(R.id.tvStatus)
        tvTilt    = findViewById(R.id.tvTilt)
        tvPackets = findViewById(R.id.tvPackets)
        wheelView = findViewById(R.id.wheelView)
        startForegroundService(Intent(this, UdpListenerService::class.java))
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        handler.post(update)
    }

    override fun onDestroy() { handler.removeCallbacks(update); super.onDestroy() }
}
