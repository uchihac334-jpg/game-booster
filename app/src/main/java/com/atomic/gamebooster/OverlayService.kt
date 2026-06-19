package com.atomic.gamebooster

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var labelView: TextView? = null
    private var statsView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var lastFpsPackage: String? = null
    private var lastFrameCount: Long? = null
    private var lastFrameTimeMs: Long = 0

    private val ROG_RED = Color.parseColor("#FF1B2C")
    private val ROG_BG = Color.parseColor("#E60A0A0C")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        showOverlay()
        running = true
        startUpdating()
    }

    private fun startForegroundWithNotification() {
        val channelId = "game_booster_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Game Booster Overlay", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = buildNotification(channelId)
        startForeground(1, notification)
    }

    private fun buildNotification(channelId: String): Notification {
        val builder = Notification.Builder(this, channelId)
        builder.setContentTitle("Game Booster aktif")
        builder.setContentText("Monitor jalan di background")
        builder.setSmallIcon(android.R.drawable.ic_menu_compass)
        builder.setOngoing(true)
        return builder.build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 22f
            setColor(ROG_BG)
            setStroke(3, ROG_RED)
        }

        labelView = TextView(this).apply {
            text = "⚡ BOOSTER"
            setTextColor(ROG_RED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(4, 0, 4, 2)
        }

        statsView = TextView(this).apply {
            text = "..."
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(4, 0, 4, 0)
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(22, 12, 22, 12)
            addView(labelView)
            addView(statsView)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchStartX).toInt()
                    params.y = initialY + (event.rawY - touchStartY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(overlayView, params)
    }

    private fun computeFps(): String {
        val pkg = BoosterUtils.getForegroundPackage(applicationContext) ?: return "N/A"
        if (pkg == applicationContext.packageName) return "-"

        val now = System.currentTimeMillis()
        val count = BoosterUtils.readCumulativeFrameCount(pkg) ?: return "no-perm"

        if (pkg != lastFpsPackage || lastFrameCount == null) {
            lastFpsPackage = pkg
            lastFrameCount = count
            lastFrameTimeMs = now
            return "..."
        }

        val deltaFrames = count - (lastFrameCount ?: count)
        val deltaTimeSec = (now - lastFrameTimeMs) / 1000f

        lastFrameCount = count
        lastFrameTimeMs = now

        if (deltaTimeSec <= 0 || deltaFrames < 0) return "..."
        val fps = deltaFrames / deltaTimeSec
        return "${fps.toInt()}"
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                if (!running) return

                val prefs = getSharedPreferences("gb_prefs", Context.MODE_PRIVATE)
                val showFps = prefs.getBoolean("show_fps", true)
                val showCpu = prefs.getBoolean("show_cpu", true)
                val showTemp = prefs.getBoolean("show_temp", true)
                val showRam = prefs.getBoolean("show_ram", true)

                val parts = mutableListOf<String>()

                if (showFps) {
                    parts.add("FPS ${computeFps()}")
                }
                if (showCpu) {
                    val cpu = BoosterUtils.readCpuUsagePercent()
                    parts.add(if (cpu >= 0) "CPU ${cpu.toInt()}%" else "CPU N/A")
                }
                if (showTemp) {
                    val temp = BoosterUtils.readTemperatureCelsius()
                    parts.add(if (temp >= 0) "${temp.toInt()}°C" else "N/A°C")
                }
                if (showRam) {
                    val avail = BoosterUtils.getAvailableMemoryMB(applicationContext)
                    val total = BoosterUtils.getTotalMemoryMB(applicationContext)
                    parts.add("RAM ${avail}/${total}MB")
                }

                statsView?.text = if (parts.isEmpty()) "(semua off)" else parts.joinToString("  |  ")
                handler.postDelayed(this, 1000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        overlayView?.let { windowManager?.removeView(it) }
    }
}
