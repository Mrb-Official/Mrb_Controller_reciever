package com.tiltsteering.receiver

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

// ── Steering Wheel View ──────────────────────────
class SteeringWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var tiltValue: Float = 0f
        set(value) { field = value; invalidate() }

    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    private val paintHub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val paintSpoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val paintArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r  = minOf(width, height) / 2f - 16f
        val angle = (tiltValue / 10f * 90f).coerceIn(-90f, 90f)

        // Outer ring
        canvas.drawCircle(cx, cy, r, paintRing)

        // Tilt arc
        val arcRect = RectF(cx-r*0.62f, cy-r*0.62f, cx+r*0.62f, cy+r*0.62f)
        canvas.drawArc(arcRect, -90f, -angle, false, paintArc)

        canvas.save()
        canvas.rotate(-angle, cx, cy)

        // Hub
        canvas.drawCircle(cx, cy, r * 0.24f, paintHub)

        // 3 Spokes
        for (i in 0..2) {
            val a = Math.toRadians((i * 120.0 - 90.0))
            canvas.drawLine(
                cx + (r*0.24f * Math.cos(a)).toFloat(),
                cy + (r*0.24f * Math.sin(a)).toFloat(),
                cx + (r * Math.cos(a)).toFloat(),
                cy + (r * Math.sin(a)).toFloat(),
                paintSpoke)
        }

        // Top dot
        canvas.drawCircle(cx, cy - r + 10f, 8f, paintDot)

        // Center dot
        canvas.drawCircle(cx, cy, 6f, paintDot)

        canvas.restore()
    }
}

// ── Main Activity ────────────────────────────────
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var onPage2 = false

    // Page 1
    private lateinit var page1: View
    private lateinit var tvIp: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnStart: Button

    // Page 2
    private lateinit var page2: View
    private lateinit var tvStatus: TextView
    private lateinit var tvTilt: TextView
    private lateinit var tvPkt: TextView
    private lateinit var wheelView: SteeringWheelView

    private val tick = object : Runnable {
        override fun run() {
            val pkt  = try { UdpListenerService.packetCount } catch(e: Exception) { 0 }
            val tilt = try { UdpListenerService.lastTilt.toFloatOrNull() ?: 0f } catch(e: Exception) { 0f }
            val serviceOn = try { MultiTouchTest.instance != null } catch(e: Exception) { false }

            tvIp.text = getIp()

            btnStart.isEnabled = serviceOn
            btnStart.alpha = if (serviceOn) 1f else 0.4f

            if (!onPage2 && pkt > 0) switchToPage2()

            if (onPage2) {
                wheelView.tiltValue = tilt
                tvTilt.text = "%.1f°".format(tilt)
                tvPkt.text  = "PKT $pkt"
                tvStatus.text = if (serviceOn) "● ACTIVE" else "○ WAITING"
                tvStatus.setTextColor(
                    if (serviceOn)
                        Color.argb(255, 0, 255, 120)
                    else Color.argb(255, 100, 100, 100))
            }

            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.windowInsetsController?.hide(
            WindowInsets.Type.statusBars() or
            WindowInsets.Type.navigationBars())
        window.decorView.setBackgroundColor(Color.parseColor("#0A0A0A"))

        try {
            startForegroundService(Intent(this, UdpListenerService::class.java))
        } catch(e: Exception) {}

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        page1 = buildPage1()
        page2 = buildPage2()
        page2.alpha = 0f
        page2.visibility = View.GONE

        root.addView(page1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(page2, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
        handler.post(tick)
    }

    private fun buildPage1(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(64, 48, 64, 48)
        }

        val ivIcon = ImageView(this).apply {
            val iconResId = resources.getIdentifier("icon", "drawable", packageName)
            if (iconResId != 0) {
                setImageResource(iconResId)
            } else {
                setImageResource(android.R.drawable.ic_menu_send)
            }
            layoutParams = LinearLayout.LayoutParams(80.dpToPx(), 80.dpToPx()).apply {
                gravity = Gravity.CENTER
                bottomMargin = 16.dpToPx()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val tvTitle = TextView(this).apply {
            text = "MRB Controller Host"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 6
            }
        }

       /*  val tvSub = TextView(this).apply {
            text = "Tilt Steering Receiver"
            textSize = 13f
            setTextColor(Color.argb(140, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 48
            }
        }*/

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#161616"))
            setPadding(32, 24, 32, 24)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 20f)
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 32
            }
        }

        val tvIpLabel = TextView(this).apply {
            text = "Your Pairing Code"
            textSize = 10f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        tvIp = TextView(this).apply {
            text = getIp()
            textSize = 28f
            setTextColor(Color.argb(255, 0, 255, 140))
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 6
                bottomMargin = 16
            }
        }

        val tvHow = TextView(this).apply {
            text = "Enter this Code in MRB Controller app"
            textSize = 12f
            setTextColor(Color.argb(100, 255, 255, 255))
            gravity = Gravity.CENTER
        }

        card.addView(tvIpLabel)
        card.addView(tvIp)
        card.addView(tvHow)

        

        btnAccessibility = buildBtn("Enable Accessibility Service",
            Color.parseColor("#1C1C1E")) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

        }
        btnAccessibility.layoutParams = LinearLayout.LayoutParams(0, 60.dpToPx(), 1f).apply{
            marginEnd = 10.dpToPx()
        }

        btnStart = buildBtn("Start Controller Host",
            Color.WHITE) {
            switchToPage2()

        }
        btnStart.layoutParams = LinearLayout.LayoutParams(0, 60.dpToPx(), 1f).apply{
            marginStart = 10.dpToPx()
        }
        btnStart.setTextColor(Color.BLACK)
        btnStart.isEnabled = false
        btnStart.alpha = 0.4f

        //horizontal boxLinearLayout.HORIZONTAL

        val buttonRow = LinearLayout(this)
        buttonRow.orientation = LinearLayout.HORIZONTAL
        buttonRow.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 
        LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonRow.weightSum = 2f
        buttonRow.addView(btnAccessibility)
        buttonRow.addView(btnStart)

        //end conntainer btn

        val tvHint = TextView(this).apply {
            text = "Enable MRB Controller in Accessibility first"
            textSize = 11f
            setTextColor(Color.argb(80, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
            }
        }

        root.addView(ivIcon)
        root.addView(tvTitle)
        root.addView(card)
        root.addView(buttonRow)
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 12.dpToPx())
        })
        root.addView(tvHint)

        return root
    }

    private fun buildPage2(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        wheelView = SteeringWheelView(this).apply {
            layoutParams = FrameLayout.LayoutParams(320.dpToPx(), 320.dpToPx()).apply {
                gravity = Gravity.CENTER
            }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24.dpToPx(), 20.dpToPx(), 24.dpToPx(), 20.dpToPx())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        tvStatus = TextView(this).apply {
            text = "○ WAITING"
            textSize = 13f
            setTextColor(Color.argb(255, 100, 100, 100))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvPkt = TextView(this).apply {
            text = "PKT 0"
            textSize = 11f
            setTextColor(Color.argb(60, 255, 255, 255))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 16.dpToPx()
            }
        }

        val btnSet = TextView(this).apply {
            text = "⚙"
            textSize = 20f
            setTextColor(Color.argb(80, 255, 255, 255))
            setOnClickListener {
                try {
                    startActivity(Intent(
                        this@MainActivity, Class.forName("${packageName}.SettingsActivity")))
                } catch(e: Exception) {
                    Toast.makeText(this@MainActivity, "Settings not implemented", Toast.LENGTH_SHORT).show()
                }
            }
        }

        topBar.addView(tvStatus)
        topBar.addView(tvPkt)
        topBar.addView(btnSet)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48.dpToPx(), 0, 48.dpToPx(), 32.dpToPx())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        }

        tvTilt = TextView(this).apply {
            text = "0.0°"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dpToPx()
            }
        }

        val tiltBar = ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = 200; progress = 100
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6.dpToPx())
        }

        val tvReady = TextView(this).apply {
            text = "READY TO RACE"
            textSize = 13f
            setTextColor(Color.argb(180, 0, 255, 120))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        bottom.addView(tvReady)
        bottom.addView(tvTilt)
        bottom.addView(tiltBar)

        handler.post(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    val t = try { UdpListenerService.lastTilt.toFloatOrNull() ?: 0f } catch(e: Exception) { 0f }
                    tiltBar.progress = (100 + (t/10f*100).toInt()).coerceIn(0, 200)
                    handler.postDelayed(this, 50)
                }
            }
        })

        root.addView(wheelView)
        root.addView(topBar)
        root.addView(bottom)

        return root
    }

    private fun switchToPage2() {
        onPage2 = true
        page2.visibility = View.VISIBLE
        page2.animate().alpha(1f).setDuration(500).start()
        page1.animate().alpha(0f).setDuration(300).withEndAction {
            page1.visibility = View.GONE
        }.start()
    }

    private fun getIp(): String {
        return try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                for (addr in iface.inetAddresses) {
                    val a = addr.hostAddress ?: continue
                    if (addr.isLoopbackAddress || !a.contains('.')) continue
                    if (name == "ap0" || name.startsWith("swlan") ||
                        name.startsWith("softap") || name.startsWith("wlan")) {
                        return a
                    }
                }
            }
            val wm = applicationContext.getSystemService(
                Context.WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
                .takeIf { it != "0.0.0.0" } ?: "Enable Hotspot"
        } catch (e: Exception) { "---" }
    }

    private fun buildBtn(
        text: String,
        bgColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(bgColor)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 18.dpToPx().toFloat())
                }
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 56.dpToPx()).apply {
                topMargin = 4.dpToPx()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
