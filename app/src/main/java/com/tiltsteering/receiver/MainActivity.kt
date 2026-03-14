package com.tiltsteering.receiver

import android.animation.*
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AirplaneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tiltValue: Float = 0f
        set(value) { field = value; invalidate() }

    private val paintBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val paintWing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200,255,255,255); style = Paint.Style.FILL }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40,255,255,255); style = Paint.Style.STROKE
        strokeWidth = 20f; maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    private val paintTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60,100,200,255); style = Paint.Style.STROKE; strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width/2f; val cy = height/2f
        val angle = (tiltValue/10f*35f).coerceIn(-35f,35f)
        canvas.save(); canvas.rotate(angle,cx,cy)
        val s = minOf(width,height)*0.35f
        canvas.drawCircle(cx,cy,s*0.6f,paintGlow)
        for(i in 1..4){ paintTrail.alpha=60-i*12
            canvas.drawLine(cx-s*0.1f,cy+s*0.3f+i*s*0.15f,cx+s*0.1f,cy+s*0.3f+i*s*0.15f,paintTrail) }
        val body=Path().apply{ moveTo(cx,cy-s*0.8f)
            cubicTo(cx+s*0.12f,cy-s*0.4f,cx+s*0.1f,cy+s*0.1f,cx,cy+s*0.5f)
            cubicTo(cx-s*0.1f,cy+s*0.1f,cx-s*0.12f,cy-s*0.4f,cx,cy-s*0.8f) }
        canvas.drawPath(body,paintBody)
        val lw=Path().apply{ moveTo(cx-s*0.08f,cy-s*0.05f); lineTo(cx-s*0.9f,cy+s*0.25f)
            lineTo(cx-s*0.6f,cy+s*0.35f); lineTo(cx-s*0.05f,cy+s*0.15f); close() }
        canvas.drawPath(lw,paintWing)
        val rw=Path().apply{ moveTo(cx+s*0.08f,cy-s*0.05f); lineTo(cx+s*0.9f,cy+s*0.25f)
            lineTo(cx+s*0.6f,cy+s*0.35f); lineTo(cx+s*0.05f,cy+s*0.15f); close() }
        canvas.drawPath(rw,paintWing)
        val lt=Path().apply{ moveTo(cx-s*0.05f,cy+s*0.35f); lineTo(cx-s*0.35f,cy+s*0.6f)
            lineTo(cx-s*0.2f,cy+s*0.65f); lineTo(cx,cy+s*0.5f); close() }
        canvas.drawPath(lt,paintBody)
        val rt=Path().apply{ moveTo(cx+s*0.05f,cy+s*0.35f); lineTo(cx+s*0.35f,cy+s*0.6f)
            lineTo(cx+s*0.2f,cy+s*0.65f); lineTo(cx,cy+s*0.5f); close() }
        canvas.drawPath(rt,paintBody)
        canvas.restore()
    }
}

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPacketCount = 0
    private var onConnectedPage = false

    private lateinit var page0: View  // Accessibility page
    private lateinit var page1: View  // Waiting page
    private lateinit var page2: View  // Connected page

    private lateinit var tvMyIp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvPkt: TextView
    private lateinit var tvConnectedIp: TextView
    private lateinit var airplaneView: AirplaneView
    private lateinit var tiltBar: ProgressBar

    private val monitor = object : Runnable {
        override fun run() {
            val pkt  = UdpListenerService.packetCount
            val tilt = UdpListenerService.lastTilt.toFloatOrNull() ?: 0f

            // Check accessibility every tick
            if (!isAccessibilityEnabled()) {
                if (page0.visibility != View.VISIBLE) showPage(0)
            } else if (!onConnectedPage && pkt > lastPacketCount) {
                switchToConnected()
            } else if (!onConnectedPage && page1.visibility != View.VISIBLE
                && page0.visibility != View.VISIBLE) {
                showPage(1)
            }

            lastPacketCount = pkt

            if (onConnectedPage) {
                airplaneView.tiltValue = tilt
                tvTilt.text = "%.1f°".format(tilt)
                tvPkt.text  = "PKT $pkt"
                tvStatus.text = if (MultiTouchTest.instance != null) "● ACTIVE" else "○ WAITING"
                tvStatus.setTextColor(
                    if (MultiTouchTest.instance != null)
                        Color.argb(255,0,255,120)
                    else Color.argb(255,150,150,150))
            }

            tiltBar.progress = (100 + (tilt/10f*100).toInt()).coerceIn(0,200)
            handler.postDelayed(this, 100)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return list.any { it.id.contains(packageName) }
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

        page0 = buildAccessibilityPage()
        page1 = buildWaitingPage()
        page2 = buildConnectedPage()

        page0.visibility = View.GONE
        page1.visibility = View.GONE
        page2.visibility = View.GONE
        page2.alpha = 0f

        root.addView(page2, matchParent())
        root.addView(page1, matchParent())
        root.addView(page0, matchParent())

        setContentView(root)

        // Show correct page on start
        if (!isAccessibilityEnabled()) {
            page0.visibility = View.VISIBLE
        } else {
            page1.visibility = View.VISIBLE
        }
    }

    // PAGE 0 — Accessibility permission
    private fun buildAccessibilityPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050510"))
            setPadding(60, 40, 60, 40)
        }

        val tvIcon = TextView(this).apply {
            text = "🔐"
            textSize = 56f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120)
        }

        val tvTitle = TextView(this).apply {
            text = "Permission Required"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8
            layoutParams = lp
        }

        val tvDesc = TextView(this).apply {
            text = "MRB Controller needs Accessibility permission to inject touch inputs into games."
            textSize = 13f
            setTextColor(Color.argb(180,255,255,255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 32
            layoutParams = lp
        }

        // Steps card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F25"))
            setPadding(32, 24, 32, 24)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0,0,v.width,v.height,20f)
                }
            }
            clipToOutline = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 24
            layoutParams = lp
        }

        val steps = listOf(
            "1. Tap the button below",
            "2. Find 'MRB Controller' in the list",
            "3. Enable the toggle",
            "4. Come back here"
        )
        steps.forEach { step ->
            card.addView(TextView(this).apply {
                text = step
                textSize = 13f
                setTextColor(Color.argb(200,255,255,255))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 8
                layoutParams = lp
            })
        }

        val btnGo = Button(this).apply {
            text = "Open Accessibility Settings →"
            textSize = 14f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.argb(255,0,255,120))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0,0,v.width,v.height,16f)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(tvIcon)
        root.addView(tvTitle)
        root.addView(tvDesc)
        root.addView(card)
        root.addView(btnGo)
        return root
    }

    // PAGE 1 — Waiting for connection
    private fun buildWaitingPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050510"))
            setPadding(60, 40, 60, 40)
        }

        val tvPlane = TextView(this).apply {
            text = "✈"
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120)
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Controller"
            textSize = 26f
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
            setTextColor(Color.argb(150,255,255,255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 32
            layoutParams = lp
        }

        // IP card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F0F25"))
            setPadding(32,24,32,24)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0,0,v.width,v.height,20f)
                }
            }
            clipToOutline = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 24
            layoutParams = lp
        }

        val tvHow = TextView(this).apply {
            text = "HOW TO CONNECT"
            textSize = 10f
            setTextColor(Color.argb(100,255,255,255))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.2f
        }
        val tvSteps = TextView(this).apply {
            text = "1. Connect sender phone to this hotspot\n2. Enter IP below in sender app\n3. Press Connect"
            textSize = 13f
            setTextColor(Color.argb(180,255,255,255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 8; lp.bottomMargin = 16
            layoutParams = lp
        }
        val tvIpLabel = TextView(this).apply {
            text = "YOUR HOTSPOT IP"
            textSize = 10f
            setTextColor(Color.argb(100,255,255,255))
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.2f
        }
        tvMyIp = TextView(this).apply {
            text = "Loading..."
            textSize = 30f
            setTextColor(Color.argb(255,0,255,140))
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4
            layoutParams = lp
        }

        card.addView(tvHow)
        card.addView(tvSteps)
        card.addView(tvIpLabel)
        card.addView(tvMyIp)

        val tvWait = TextView(this).apply {
            text = "⏳ Waiting for sender..."
            textSize = 12f
            setTextColor(Color.argb(100,255,255,255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 16
            layoutParams = lp
        }

        val btnSettings = buildBtn("⚙ Settings") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        root.addView(tvPlane)
        root.addView(tvTitle)
        root.addView(tvSub)
        root.addView(card)
        root.addView(tvWait)
        root.addView(btnSettings)

        handler.postDelayed({ tvMyIp.text = getRealIp() }, 500)
        return root
    }

    // PAGE 2 — Connected
    private fun buildConnectedPage(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#050510"))
        }

        airplaneView = AirplaneView(this).apply {
            layoutParams = FrameLayout.LayoutParams(280,280).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20,16,20,16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        tvStatus = TextView(this).apply {
            text = "● ACTIVE"
            textSize = 12f
            setTextColor(Color.argb(255,0,255,120))
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvConnectedIp = TextView(this).apply {
            text = getRealIp()
            textSize = 11f
            setTextColor(Color.argb(100,255,255,255))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
        tvPkt = TextView(this).apply {
            text = "PKT 0"
            textSize = 10f
            setTextColor(Color.argb(60,255,255,255))
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

        val bottomArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(40,0,40,20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            }
        }

        val tvReady = TextView(this).apply {
            text = "🎮  READY TO RACE"
            textSize = 14f
            setTextColor(Color.argb(200,0,255,120))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.1f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8
            layoutParams = lp
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
            max = 200; progress = 100
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 8)
        }

        val tvLR = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4
            layoutParams = lp
        }
        tvLR.addView(TextView(this).apply {
            text = "◀ LEFT"; textSize = 10f
            setTextColor(Color.argb(60,255,255,255))
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        tvLR.addView(TextView(this).apply {
            text = "RIGHT ▶"; textSize = 10f
            setTextColor(Color.argb(60,255,255,255))
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        bottomArea.addView(tvReady)
        bottomArea.addView(tvTilt)
        bottomArea.addView(tiltBar)
        bottomArea.addView(tvLR)

        root.addView(airplaneView)
        root.addView(topBar)
        root.addView(bottomArea)
        return root
    }

    private fun showPage(page: Int) {
        page0.visibility = if (page == 0) View.VISIBLE else View.GONE
        page1.visibility = if (page == 1) View.VISIBLE else View.GONE
        if (page != 2) {
            page2.visibility = View.GONE
            onConnectedPage = false
        }
    }

    private fun switchToConnected() {
        onConnectedPage = true
        tvConnectedIp.text = getRealIp()
        page2.visibility = View.VISIBLE
        page2.animate().alpha(1f).setDuration(600).start()
        page1.animate().alpha(0f).setDuration(400).withEndAction {
            page1.visibility = View.GONE
        }.start()
        page0.visibility = View.GONE
    }

    private fun getRealIp(): String {
        return try {
            var hotspotIp: String? = null
            var anyIp: String? = null
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return "No Interface"
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                for (addr in iface.inetAddresses) {
                    val a = addr.hostAddress ?: continue
                    if (addr.isLoopbackAddress || !a.contains('.')) continue
                    val isHotspot = name == "ap0" || name.startsWith("swlan") ||
                        name.startsWith("softap") || name.startsWith("wlan")
                    if (isHotspot) hotspotIp = a
                    else if (a.startsWith("192.168.") || a.startsWith("10.") ||
                             a.startsWith("172.")) { if (anyIp == null) anyIp = a }
                }
            }
            hotspotIp ?: anyIp ?: "Enable Hotspot"
        } catch (e: Exception) { "Error" }
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT)

    private fun buildBtn(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text; textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0,0,v.width,v.height,16f)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(200, 52)
            setOnClickListener { onClick() }
        }
    }

    private fun buildSmallBtn(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 16f
            setTextColor(Color.argb(100,255,255,255))
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(40,40)
            lp.leftMargin = 8; layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheck accessibility when user comes back from settings
        if (isAccessibilityEnabled() && !onConnectedPage) {
            showPage(1)
            tvMyIp.text = getRealIp()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
