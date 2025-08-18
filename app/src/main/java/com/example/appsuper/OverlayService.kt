package com.example.appsuper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlays = mutableMapOf<Int, View>()
    private val uiHandler = Handler(Looper.getMainLooper())

    private val NOTIFICATION_CHANNEL_ID = "com.example.appsuper.OverlayServiceChannel"
    private val NOTIFICATION_ID = 101

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForegroundService()
        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }

        when (intent?.action) {
            AppConstants.ACTION_SHOW_SYMBOL -> {
                val number = intent.getIntExtra(AppConstants.EXTRA_NUMBER, -1)
                if (number != -1) uiHandler.post { showOverlay(number) }
            }
            AppConstants.ACTION_DELETE_SYMBOL -> {
                val number = intent.getIntExtra(AppConstants.EXTRA_NUMBER, -1)
                if (number != -1) uiHandler.post { deleteOverlay(number) }
            }
            AppConstants.ACTION_REMOVE_ALL_SYMBOLS -> uiHandler.post { removeAllOverlays() }
            AppConstants.ACTION_HIDE_ALL_SYMBOLS -> uiHandler.post { hideAllOverlays() }
            AppConstants.ACTION_SET_VISIBILITY -> {
                val visibleList = intent.getIntegerArrayListExtra(AppConstants.EXTRA_VISIBLE_LIST)
                visibleList?.let { uiHandler.post { setOverlaysVisibility(it) } }
            }
            // НОВЫЙ ОБРАБОТЧИК
            AppConstants.ACTION_FREEZE_ALL -> uiHandler.post { freezeAllOverlays() }
        }

        return START_STICKY
    }

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "AppSuper Service"
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AppSuper Активен")
            .setContentText("Оверлей работает в фоновом режиме.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setOverlaysVisibility(visibleNumbers: List<Int>) {
        overlays.forEach { (number, view) ->
            view.visibility = if (visibleNumbers.contains(number)) View.VISIBLE else View.INVISIBLE
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay(number: Int) {
        if (overlays.containsKey(number)) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val symbolView = inflater.inflate(R.layout.overlay_symbol, null) as TextView
        symbolView.text = "🔹"

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0; y = 50
        }

        symbolView.setOnTouchListener(MovableTouchListener(params, symbolView))
        windowManager.addView(symbolView, params)
        overlays[number] = symbolView
    }

    private inner class MovableTouchListener(private val params: WindowManager.LayoutParams, private val view: View) : View.OnTouchListener {
        private var initialX = 0; private var initialY = 0
        private var initialTouchX = 0f; private var initialTouchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
                    return true
                }
            }
            return false
        }
    }

    // НОВАЯ ФУНКЦИЯ ДЛЯ ЗАМОРОЗКИ
    private fun freezeAllOverlays() {
        overlays.values.forEach { view ->
            view.setOnTouchListener(null) // Убираем возможность двигать
            val params = view.layoutParams as WindowManager.LayoutParams
            // Добавляем флаг, чтобы нажатия проходили "сквозь" символ
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun deleteOverlay(number: Int) {
        overlays.remove(number)?.let { viewToRemove ->
            if (viewToRemove.isAttachedToWindow) windowManager.removeView(viewToRemove)
        }
    }

    private fun removeAllOverlays() {
        overlays.values.forEach { view -> if (view.isAttachedToWindow) windowManager.removeView(view) }
        overlays.clear()
    }

    private fun hideAllOverlays() {
        overlays.values.forEach { it.visibility = View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.post { removeAllOverlays() }
        stopForeground(true)
        Log.d("OverlayService", "Служба уничтожена.")
    }



    override fun onBind(intent: Intent?): IBinder? = null
}