package com.example.appsuper

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.net.Socket

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlays = mutableMapOf<Int, View>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var listeningThread: Thread? = null

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiServerActivity.ACTION_LOCK_ALL) {
                uiHandler.post { lockAllUnfrozenOverlays() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(lockReceiver, IntentFilter(WifiServerActivity.ACTION_LOCK_ALL))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val socket = WifiSocketHolder.socket
        if (socket == null || socket.isClosed) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (listeningThread == null || !listeningThread!!.isAlive) {
            listeningThread = Thread { listenToSocket(socket) }.apply { start() }
        }
        return START_STICKY
    }

    // ИЗМЕНЕНО: Игнорируем команды 100 и 101
    private fun listenToSocket(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            while (!Thread.currentThread().isInterrupted) {
                val command = inputStream.read()
                if (command == -1) break

                when (command) {
                    in 0..36 -> uiHandler.post { showOverlay(command) }
                    // Команды 100, 101 (подсветка) теперь просто игнорируются
                    100, 101 -> {
                        // Читаем данные, чтобы не засорять поток, но ничего с ними не делаем
                        readNumberList(inputStream)
                    }
                    201 -> uiHandler.post { removeAllOverlays() }
                }
            }
        } catch (e: IOException) {
            Log.e("OverlayService", "Сокет закрыт: ${e.message}")
        } finally {
            uiHandler.post { stopSelf() }
        }
    }

    private fun readNumberList(inputStream: InputStream): List<Int> {
        val list = mutableListOf<Int>()
        try {
            val size = inputStream.read()
            if (size != -1) {
                repeat(size) {
                    val number = inputStream.read()
                    if (number != -1) list.add(number) else return list
                }
            }
        } catch (e: IOException) { /* ignore */ }
        return list
    }

    // ИЗМЕНЕНО: Добавлена логика "сброса" существующего оверлея
    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(number: Int) {
        val screenWidth: Int
        val screenHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }

        // Если оверлей для этого номера уже существует, сбрасываем его
        if (overlays.containsKey(number)) {
            val existingView = overlays[number]
            if (existingView is TextView) {
                val params = existingView.layoutParams as WindowManager.LayoutParams

                // Возвращаем в начальное состояние
                existingView.alpha = 1.0f
                existingView.tag = "unfrozen"
                existingView.setOnTouchListener(MovableTouchListener(params))

                // Возвращаем на стартовую позицию
                params.x = screenWidth - 200
                params.y = screenHeight / 2 - 100
                if (existingView.isAttachedToWindow) {
                    windowManager.updateViewLayout(existingView, params)
                }
                toast("Символ $number возвращен")
            }
            return
        }

        // Если оверлея нет, создаем новый
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
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 200
            y = screenHeight / 2 - 100
        }

        symbolView.tag = "unfrozen"
        symbolView.setOnTouchListener(MovableTouchListener(params))

        windowManager.addView(symbolView, params)
        overlays[number] = symbolView
    }

    private inner class MovableTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    if (v.isAttachedToWindow) {
                        windowManager.updateViewLayout(v, params)
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun lockAllUnfrozenOverlays() {
        var lockedCount = 0
        overlays.values.forEach { view ->
            if (view.tag == "unfrozen" && view is TextView) {
                view.setOnTouchListener(null)
                view.alpha = 0.6f
                view.tag = "frozen"
                lockedCount++
            }
        }
        if (lockedCount > 0) {
            toast("$lockedCount символ(ов) закреплено!")
        } else {
            toast("Нет новых символов для закрепления.")
        }
    }

    private fun removeAllOverlays() {
        overlays.values.forEach { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        overlays.clear()
    }

    // УДАЛЕНО: Метод highlightOverlays больше не нужен

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(lockReceiver)
        listeningThread?.interrupt()
        uiHandler.post { removeAllOverlays() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}