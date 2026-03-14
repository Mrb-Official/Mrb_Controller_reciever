package com.tiltsteering.receiver

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

// Airplane steering wheel view
class AirplaneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tiltValue: Float = 0f
        set(value) { field = value; invalidate() }

    private val paintBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val paintWing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255); style = Paint.Style.FILL
    }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE; strokeWidth = 20f
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 100, 200, 255)
        style = Paint.Style.STROKE; strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val angle = (tiltValue / 10f * 35f).coerceIn(-35f, 35f)

        canvas.save()
        canvas.rotate(angle, cx, cy)

        val s = minOf(width, height) * 0.35f

        // Glow
        canvas.drawCircle(cx, cy, s * 0.6f, paintGlow)

        // Trail lines
        for (i in 1..4) {
            paintTrail.alpha = 60 - i * 12
            canvas.drawLine(cx - s*0.1f, cy + s*0.3f + i*s*0.15f,
                           cx + s*0.1f, cy + s*0.3f + i*s*0.15f, paintTrail)
        }

        // Fuselage (body)
        val bodyPath = Path().apply {
            moveTo(cx, cy - s * 0.8f)
            cubicTo(cx + s*0.12f, cy - s*0.4f, cx + s*0.1f, cy + s*0.1f, cx, cy + s*0.5f)
            cubicTo(cx - s*0.1f, cy + s*0.1f, cx - s*0.12f, cy - s*0.4f, cx, cy - s*0.8f)
        }
        canvas.drawPath(bodyPath, paintBody)

        // Left wing
        val leftWing = Path().apply {
            moveTo(cx - s*0.08f, cy - s*0.05f)
            lineTo(cx - s*0.9f, cy + s*0.25f)
            lineTo(cx - s*0.6f, cy + s*0.35f)
            lineTo(cx - s*0.05f, cy + s*0.15f)
            close()
        }
        canvas.drawPath(leftWing, paintWing)

        // Right wing
        val rightWing = Path().apply {
            moveTo(cx + s*0.08f, cy - s*0.05f)
            lineTo(cx + s*0.9f, cy + s*0.25f)
            lineTo(cx + s*0.6f, cy + s*0.35f)
            lineTo(cx + s*0.05f, cy + s*0.15f)
            close()
        }
        canvas.drawPath(rightWing, paintWing)

        // Tail wings
        val leftTail = Path().apply {
            moveTo(cx - s*0.05f, cy + s*0.35f)
            lineTo(cx - s*0.35f, cy + s*0.6f)
            lineTo(cx - s*0.2f, cy + s*0.65f)
            lineTo(cx, cy + s*0.5f)
            close()
        }
        canvas.drawPath(leftTail, paintBody)

        val rightTail = Path().apply {
            moveTo(cx + s*0.05f, cy + s*0.35f)
            lineTo(cx + s*0.35f, cy + s*0.6f)
            lineTo(cx + s*0.2f, cy + s*0.65f)
            lineTo(cx, cy + s*0.5f)
            close()
        }
        canvas.drawPath(rightTail, paintBody)

        canvas.restore()
    }
}

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPacketCount = 0
    private var onConnectedPage = false

    // Page 1 views
    private lateinit var page1: View
    private lateinit var tvMyIp: TextView

    // Page 2 views
    private lateinit var page2: View
    private lateinit var tvStatus: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvPkt: TextView
    private lateinit var tvConnectedIp: TextView
    private lateinit var airplaneView: AirplaneView
    private lateinit var tiltBar: ProgressBar

    private val monitor = object : Runnable {
        override fun run() {
            val pkt = UdpListenerService.packetCount
            val tilt = UdpListenerService.lastTilt.toFloatOrNull() ?: 0f

            // Auto switch to page 2 when packets arrive
            if (!onConnectedPage && pkt > lastPacketCount) {
                switchToConnected()
            }
            lastPacketCount = pkt

            if (onConnectedPage) {
                airplaneView.tiltValue = tilt
                tvTilt.text = "%.1f°".format(tilt)
                tvPkt.text = "PKT $pkt"
                tvStatus.text = if (MultiTouchTest.instance != null) "● ACTIVE" else "○ WAITING"
                tvStatus.setTextColor(
                    if (MultiTouchTest.instance != null)
                        Color.argb(255, 0, 255, 120)
                    else Color.argb(255, 100, 100, 100))
            }

            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.decorView.setBackgroundColor(Color.parseColor("#050510"))

        startForegroundService(Intent(this, UdpListenerService::class.java))

        buildUI()
        handler.post(monitor)
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050510"))
        }

        // PAGE 1 - Waiting screen
        page1 = buildPage1()
        root.addView(page1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        // PAGE 2 - Connected screen
        page2 = buildPage2()
        page2.alpha = 0f
        page2.visibility = View.GONE
        root.addView(page2, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    private fun buildPage1(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050510"))
            setPadding(60, 40, 60, 40)
        }

        // Airplane icon big
        val ivPlane = TextView(this).apply {
            text = "✈"
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120)
            lp.bottomMargin = 8
            layoutParams = lp
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Controller"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 4
            layoutParams = lp
        }

        val tvSub = TextView(this).apply {
            text = "Tilt Steering Controller"
            textSize = 13f
            setTextColor(Color.argb(150, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 40
            layoutParams = lp
        }

        // IP card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F0F20"))
            setPadding(32, 24, 32, 24)
            // rounded via outline
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 20f)
                }
            }
            clipToOutline = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 32
            layoutParams = lp
        }

        val tvHowLabel = TextView(this).apply {
            text = "HOW TO CONNECT"
            textSize = 10f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.2f
        }

        val tvHow = TextView(this).apply {
            text = "1. Connect sender phone to this hotspot\n2. Enter the IP below in sender app\n3. Press Connect in sender app"
            textSize = 13f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 8
            lp.bottomMargin = 16
            layoutParams = lp
        }

        val tvIpLabel = TextView(this).apply {
            text = "YOUR IP ADDRESS"
            textSize = 10f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.2f
        }

        tvMyIp = TextView(this).apply {
            text = "Loading..."
            textSize = 32f
            setTextColor(Color.argb(255, 0, 255, 140))
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4
            layoutParams = lp
        }

        card.addView(tvHowLabel)
        card.addView(tvHow)
        card.addView(tvIpLabel)
        card.addView(tvMyIp)

        val tvWaiting = TextView(this).apply {
            text = "⏳ Waiting for sender to connect..."
            textSize = 12f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 16
            layoutParams = lp
        }

        // Buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnAccess = buildBtn("Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val btnSettings = buildBtn("⚙ Settings") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        btnRow.addView(btnAccess)
        btnRow.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 1)
        })
        btnRow.addView(btnSettings)

        root.addView(ivPlane)
        root.addView(tvTitle)
        root.addView(tvSub)
        root.addView(card)
        root.addView(tvWaiting)
        root.addView(btnRow)

        // Get real IP
        handler.postDelayed({
            tvMyIp.text = getRealIp()
        }, 500)

        return root
    }

    private fun buildPage2(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050510"))
        }

        // Center airplane
        airplaneView = AirplaneView(this).apply {
            layoutParams = FrameLayout.LayoutParams(280, 280).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        tvStatus = TextView(this).apply {
            text = "● ACTIVE"
            textSize = 12f
            setTextColor(Color.argb(255, 0, 255, 120))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvConnectedIp = TextView(this).apply {
            text = getRealIp()
            textSize = 11f
            setTextColor(Color.argb(100, 255, 255, 255))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }

        tvPkt = TextView(this).apply {
            text = "PKT 0"
            textSize = 10f
            setTextColor(Color.argb(60, 255, 255, 255))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = 12
            layoutParams = lp
        }

        val btnSet = buildSmallBtn("⚙") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        topBar.addView(tvStatus)
        topBar.addView(tvConnectedIp)
        topBar.addView(tvPkt)
        topBar.addView(btnSet)

        // Bottom tilt area
        val bottomArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(40, 0, 40, 20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }

        tvTilt = TextView(this).apply {
            text = "0.0°"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8
            layoutParams = lp
        }

        tiltBar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 200
            progress = 100
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 8)
        }

        val tvLeft = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4
            layoutParams = lp
        }
        val tvL = TextView(this).apply {
            text = "◀ LEFT"
            textSize = 10f
            setTextColor(Color.argb(60, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvR = TextView(this).apply {
            text = "RIGHT ▶"
            textSize = 10f
            setTextColor(Color.argb(60, 255, 255, 255))
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvLeft.addView(tvL); tvLeft.addView(tvR)

        // READY label
        val tvReady = TextView(this).apply {
            text = "🎮  READY TO RACE"
            textSize = 14f
            setTextColor(Color.argb(200, 0, 255, 120))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 12
            layoutParams = lp
        }

        bottomArea.addView(tvReady)
        bottomArea.addView(tvTilt)
        bottomArea.addView(tiltBar)
        bottomArea.addView(tvLeft)

        root.addView(airplaneView)
        root.addView(topBar)
        root.addView(bottomArea)

        // Monitor tilt for progress bar
        handler.post(object : Runnable {
            override fun run() {
                val t = UdpListenerService.lastTilt.toFloatOrNull() ?: 0f
                tiltBar.progress = (100 + (t / 10f * 100).toInt()).coerceIn(0, 200)
                handler.postDelayed(this, 50)
            }
        })

        return root
    }

    private fun switchToConnected() {
        onConnectedPage = true
        tvConnectedIp.text = getRealIp()
        page2.visibility = View.VISIBLE
        page2.animate().alpha(1f).setDuration(600).start()
        page1.animate().alpha(0f).setDuration(400).withEndAction {
            page1.visibility = View.GONE
        }.start()
    }

    private fun getRealIp(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) {
                // Try hotspot IP via network interfaces
                val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
                for (iface in ifaces) {
                    for (addr in iface.inetAddresses) {
                        val a = addr.hostAddress ?: continue
                        if (!addr.isLoopbackAddress && a.contains('.') &&
                            (a.startsWith("192.168") || a.startsWith("10."))) {
                            return a
                        }
                    }
                }
                "Check WiFi/Hotspot"
            } else {
                Formatter.formatIpAddress(ip)
            }
        } catch (e: Exception) { "---" }
    }

    private fun buildBtn(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 16f)
                }
            }
            clipToOutline = true
            val lp = LinearLayout.LayoutParams(160, 48)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun buildSmallBtn(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(40, 40)
            lp.leftMargin = 8
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
