package com.appvolume

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import kotlin.math.abs

class OverlayService : Service() {
    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val blue = 0xFF007AFF.toInt()

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("v", "Volume", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "v")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Volume per aplikasi aktif").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        addBubble()
        scope.launch { while (isActive) { VolumeEngine.tick(); delay(1000) } }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)

    private fun addBubble() {
        val b = TextView(this).apply {
            text = "🔊"; textSize = 20f; gravity = Gravity.CENTER
            background = bg(0xEEFFFFFF.toInt(), 24); elevation = 8f
        }
        val lp = params(dp(48), dp(48)).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(200) }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt(); val my = (e.rawY - dy).toInt()
                    if (abs(mx) > 10 || abs(my) > 10) moved = true
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(b, lp); bubble = b
    }

    private fun togglePanel() {
        panel?.let { wm.removeView(it); panel = null; return }
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = bg(0xF5F2F2F7.toInt(), 18)
        }
        val apps = VolumeEngine.apps.value
        if (apps.isEmpty()) p.addView(TextView(this).apply {
            text = "Belum ada aplikasi bersuara"; setTextColor(0xFF8E8E93.toInt())
        })
        apps.forEach { a ->
            p.addView(TextView(this).apply { text = a.label; setTextColor(Color.BLACK); textSize = 15f })
            p.addView(SeekBar(this).apply {
                max = 100; progress = (a.volume * 100).toInt()
                progressTintList = ColorStateList.valueOf(blue)
                thumbTintList = ColorStateList.valueOf(blue)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) {
                        if (u) VolumeEngine.setVolume(a.pkg, pr / 100f)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }
        val lp = params(dp(300), WindowManager.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(100) }
        wm.addView(p, lp); panel = p
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        bubble?.let { wm.removeView(it) }
        panel?.let { wm.removeView(it) }
        super.onDestroy()
    }
}
