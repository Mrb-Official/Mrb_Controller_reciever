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
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE; strokeWidth = 18f }
    private val paintSpoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888"); style = Paint.Style.STROKE; strokeWidth = 10f }
    private val paintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE; strokeWidth = 8f }
    private val paintArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6600"); style = Paint.Style.STROKE; strokeWidth = 14f
        alpha = 120 }
    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r  = (minOf(width, height) / 2f) - 20f
        val angle = (tiltValue / 10f * 90f).coerceIn(-90f, 90f)
        canvas.drawCircle(cx, cy, r, paintRing)
        canvas.drawCircle(cx, cy, r * 0.38f, paintCenter)
        val arcRect = RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f)
        canvas.drawArc(arcRect, -90f, -angle, false, paintArc)
        canvas.save()
        canvas.rotate(-angle, cx, cy)
        for (i in 0..2) {
            val a = Math.toRadians((i * 120.0 - 90.0))
            canvas.drawLine(
                cx + (r * 0.38f * Math.cos(a)).toFloat(),
                cy + (r * 0.38f * Math.sin(a)).toFloat(),
                cx + (r * Math.cos(a)).toFloat(),
                cy + (r * Math.sin(a)).toFloat(), paintSpoke)
        }
        canvas.drawCircle(cx, cy - r + 20f, 10f, paintDot)
        canvas.restore()
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvPackets: TextView
    private lateinit var wheelView: SteeringWheelView
    private lateinit var btnGas: Button
    private val handler = Handler(Looper.getMainLooper())

    private val update = object : Runnable {
        override fun run() {
            val tilt = UdpListenerService.lastTilt.toFloatOrNull() ?: 0f
            wheelView.tiltValue = tilt
            tvTilt.text    = "Tilt: ${"%.2f".format(tilt)}"
            tvPackets.text = "Packets: ${UdpListenerService.packetCount}"
            tvStatus.text  = if (MultiTouchTest.instance != null) "Accessibility: ✅ ON" else "Accessibility: ❌ OFF"
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus  = findViewById(R.id.tvStatus)
        tvTilt    = findViewById(R.id.tvTilt)
        tvPackets = findViewById(R.id.tvPackets)
        wheelView = findViewById(R.id.wheelView)
        btnGas    = findViewById(R.id.btnGas)
        startForegroundService(Intent(this, UdpListenerService::class.java))
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnGas.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { MultiTouchTest.setGas(true); btnGas.setBackgroundColor(Color.parseColor("#FF4444")) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { MultiTouchTest.setGas(false); btnGas.setBackgroundColor(Color.parseColor("#44AA44")) }
            }
            true
        }
        handler.post(update)
    }

    override fun onDestroy() { handler.removeCallbacks(update); super.onDestroy() }
}
