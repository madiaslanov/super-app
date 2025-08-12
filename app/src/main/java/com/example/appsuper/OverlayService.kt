package com.example.appsuper

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private var allNumbersReceivedBroadcastSent = false

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiServerActivity.ACTION_LOCK_ALL) {
                uiHandler.post { freezeAndHideAll() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(lockReceiver, IntentFilter(WifiServerActivity.ACTION_LOCK_ALL), RECEIVER_EXPORTED)
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

    private fun listenToSocket(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            while (!Thread.currentThread().isInterrupted) {
                val command = inputStream.read()
                if (command == -1) break

                when (command) {
                    in 0..36 -> uiHandler.post { showOverlay(command) }
                    102 -> {
                        val visibleList = readNumberList(inputStream)
                        uiHandler.post { setOverlaysVisibility(visibleList) }
                    }
                    201 -> uiHandler.post { removeAllOverlays() }
                    202 -> {
                        val numberToDelete = inputStream.read()
                        if (numberToDelete != -1) {
                            uiHandler.post { deleteOverlay(numberToDelete) }
                        }
                    }
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
            if (size != -1) repeat(size) { val number = inputStream.read(); if (number != -1) list.add(number) else return list }
        } catch (e: IOException) { Log.e("OverlayService", "Ошибка чтения списка номеров: ${e.message}") }
        return list
    }

    private fun deleteOverlay(number: Int) {
        overlays[number]?.let { viewToRemove ->
            if (viewToRemove.isAttachedToWindow) {
                windowManager.removeView(viewToRemove)
            }
            overlays.remove(number)
            Log.d("OverlayService", "Удален оверлей для числа $number")
        }
    }

    private fun setOverlaysVisibility(visibleNumbers: List<Int>) {
        overlays.forEach { (number, view) ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (visibleNumbers.contains(number)) {
                view.visibility = View.VISIBLE
                if (view.tag != "frozen") {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            } else {
                view.visibility = View.INVISIBLE
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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
            x = 0; y = 100
        }

        symbolView.setOnTouchListener(MovableTouchListener(params))
        windowManager.addView(symbolView, params)
        overlays[number] = symbolView

        if (!allNumbersReceivedBroadcastSent && overlays.size == 37) {
            sendBroadcast(Intent(WifiServerActivity.ACTION_ALL_NUMBERS_RECEIVED))
            allNumbersReceivedBroadcastSent = true
        }
    }

    private inner class MovableTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var initialX = 0; private var initialY = 0; private var initialTouchX = 0f; private var initialTouchY = 0f
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; return true }
                MotionEvent.ACTION_MOVE -> { params.x = initialX + (event.rawX - initialTouchX).toInt(); params.y = initialY + (event.rawY - initialTouchY).toInt(); if (v.isAttachedToWindow) windowManager.updateViewLayout(v, params); return true }
            }
            return false
        }
    }

    private fun freezeAndHideAll() {
        overlays.values.forEach { view ->
            view.visibility = View.INVISIBLE
            view.setOnTouchListener(null)
            view.tag = "frozen"
            val params = view.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        }
        toast("Все символы заморожены и скрыты.")
    }

    private fun removeAllOverlays() {
        overlays.values.forEach { view -> if (view.isAttachedToWindow) windowManager.removeView(view) }
        overlays.clear()
        allNumbersReceivedBroadcastSent = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(lockReceiver)
        listeningThread?.interrupt()
        uiHandler.post { removeAllOverlays() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}