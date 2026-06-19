package com.atomic.gamebooster

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
    private var textView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    // state buat hitung FPS (delta cumulative frame count / delta waktu)
    private var lastFpsPackage: String? = null
    private var lastFrameCount: Long? = null
    private var lastFrameTimeMs: Long = 0

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
        val notification = NotificationCompatBuilder(this, channelId)
        startForeground(1, notification)
    }

    private fun NotificationCompatBuilder(context: Context, channelId: String): Notification {
        val builder = Notification.Builder(context, channelId)
        builder.setContentTitle("Game Booster aktif")
        builder.setContentText("Monitor CPU & suhu jalan di background")
        builder.setSmallIcon(android.R.drawable.ic_menu_compass)
        builder.setOngoing(true)
        return builder.build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        textView = TextView(this).apply {
            text = "Booster ON"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(20, 12, 20, 12)
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            addView(textView)
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

        windowManager?.addView(overlayView, params)
    }

    private fun computeFps(): String {
        val pkg = BoosterUtils.getForegroundPackage(applicationContext) ?: return "N/A"
        if (pkg == applicationContext.packageName) return "-" // jangan ukur diri sendiri (overlay)

        val now = System.currentTimeMillis()
        val count = BoosterUtils.readCumulativeFrameCount(pkg) ?: return "no-perm"

        if (pkg != lastFpsPackage || lastFrameCount == null) {
            // app baru kebuka / baseline belum ada, tunggu sample berikutnya
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
                val cpu = BoosterUtils.readCpuUsagePercent()
                val temp = BoosterUtils.readTemperatureCelsius()
                val ramAvail = BoosterUtils.getAvailableMemoryMB(applicationContext)
                val ramTotal = BoosterUtils.getTotalMemoryMB(applicationContext)
                val fps = computeFps()

                val cpuText = if (cpu >= 0) "${cpu.toInt()}%" else "N/A"
                val tempText = if (temp >= 0) "${temp.toInt()}°C" else "N/A"

                textView?.text = "FPS $fps | CPU $cpuText | $tempText | RAM ${ramAvail}/${ramTotal}MB"
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
