package com.example.appsuper

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service(), MainAppView.AppViewListener {

    private lateinit var windowManager: WindowManager
    private val overlays = mutableMapOf<Int, View>()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var floatingButtonView: View? = null
    private var mainOverlayView: MainAppView? = null

    private val NOTIFICATION_CHANNEL_ID = "com.example.appsuper.OverlayServiceChannel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForegroundService()
        uiHandler.post { showFloatingButton() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_SHOW_SYMBOL -> handleShowSymbol(intent)
            AppConstants.ACTION_DELETE_SYMBOL -> handleDeleteSymbol(intent)
            AppConstants.ACTION_REMOVE_ALL_SYMBOLS -> uiHandler.post { removeAllNumberOverlays() }
            AppConstants.ACTION_HIDE_ALL_SYMBOLS -> uiHandler.post { hideAllOverlays() }
            AppConstants.ACTION_SET_VISIBILITY -> handleSetVisibility(intent)
            AppConstants.ACTION_FREEZE_ALL -> uiHandler.post { freezeAllOverlays() }
        }
        return START_STICKY
    }

    private fun showFloatingButton() {
        if (floatingButtonView != null) return
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingButtonView = inflater.inflate(R.layout.floating_button_layout, null)
        val params = getLayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 100
        }

        val touchListener = FloatingButtonTouchListener(params, floatingButtonView!!, windowManager) {
            if (mainOverlayView == null) {
                showMainOverlay()
            } else {
                hideMainOverlay()
            }
        }
        floatingButtonView?.setOnTouchListener(touchListener)
        windowManager.addView(floatingButtonView, params)
        floatingButtonView?.post { touchListener.animateToPeekingState() }
    }


    private fun showMainOverlay() {
        if (mainOverlayView != null) return
        mainOverlayView = MainAppView(this).apply {
            setListener(this@OverlayService)
            setOverlayMode()
        }
        val params = getLayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
        windowManager.addView(mainOverlayView, params)
        bringFloatingButtonToFront()
    }

    private fun bringFloatingButtonToFront() {
        floatingButtonView?.let { button ->
            if (button.isAttachedToWindow) {
                val params = button.layoutParams as WindowManager.LayoutParams
                windowManager.removeView(button)
                windowManager.addView(button, params)
            }
        }
    }

    private fun hideMainOverlay() {
        mainOverlayView?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        mainOverlayView = null
    }

    override fun onSendCommand(action: String, number: Int) {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
            if (number != -1) putExtra(AppConstants.EXTRA_NUMBER, number)
        }
        startService(intent)
    }

    override fun onSendCommandWithList(action: String, visibleList: ArrayList<Int>) {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
            putIntegerArrayListExtra(AppConstants.EXTRA_VISIBLE_LIST, visibleList)
        }
        startService(intent)
    }

    override fun onHideRequest() {
        hideMainOverlay()
    }

    private fun handleShowSymbol(intent: Intent) {
        val number = intent.getIntExtra(AppConstants.EXTRA_NUMBER, -1)
        if (number != -1) uiHandler.post { showNumberOverlay(number) }
    }

    private fun handleDeleteSymbol(intent: Intent) {
        val number = intent.getIntExtra(AppConstants.EXTRA_NUMBER, -1)
        if (number != -1) uiHandler.post { deleteNumberOverlay(number) }
    }

    private fun handleSetVisibility(intent: Intent) {
        val visibleList = intent.getIntegerArrayListExtra(AppConstants.EXTRA_VISIBLE_LIST)
        visibleList?.let { uiHandler.post { setOverlaysVisibility(it) } }
    }

    @SuppressLint("InflateParams")
    private fun showNumberOverlay(number: Int) {
        if (overlays.containsKey(number)) return
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val symbolView = inflater.inflate(R.layout.overlay_symbol, null) as TextView
        symbolView.text = "🔹"
        val params = getLayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0; y = 50
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        symbolView.setOnTouchListener(MovableTouchListener(params, symbolView))
        windowManager.addView(symbolView, params)
        overlays[number] = symbolView
    }

    private fun deleteNumberOverlay(number: Int) {
        overlays.remove(number)?.let { viewToRemove ->
            if (viewToRemove.isAttachedToWindow) windowManager.removeView(viewToRemove)
        }
    }

    private fun removeAllNumberOverlays() {
        overlays.values.forEach { view -> if (view.isAttachedToWindow) windowManager.removeView(view) }
        overlays.clear()
    }

    private fun freezeAllOverlays() {
        overlays.values.forEach { view ->
            view.setOnTouchListener(null)
            val params = view.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun hideAllOverlays() {
        overlays.values.forEach { it.visibility = View.GONE }
    }

    private fun setOverlaysVisibility(visibleNumbers: List<Int>) {
        overlays.forEach { (number, view) ->
            view.visibility = if (visibleNumbers.contains(number)) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun getLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(width, height, layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideMainOverlay()
        floatingButtonView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        removeAllNumberOverlays()
        stopForeground(true)
    }

    private inner class FloatingButtonTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View,
        private val windowManager: WindowManager,
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private val clickThreshold = 10
        private var isDragging = false
        private var currentAnimator: ValueAnimator? = null

        private val screenWidth: Int by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width()
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.width
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentAnimator?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    animateToFullVisibility()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val xDiff = event.rawX - initialTouchX
                    val yDiff = event.rawY - initialTouchY
                    if (isDragging || Math.abs(xDiff) > clickThreshold || Math.abs(yDiff) > clickThreshold) {
                        isDragging = true
                        params.x = initialX + xDiff.toInt()
                        params.y = initialY + yDiff.toInt()
                        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onClick.invoke()
                    }
                    view.post { animateToPeekingState() }
                    return true
                }
            }
            return false
        }

        private fun animateToPosition(targetX: Int) {
            currentAnimator?.cancel()
            val startX = params.x
            currentAnimator = ValueAnimator.ofInt(startX, targetX).apply {
                duration = 200L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    params.x = animation.animatedValue as Int
                    if (view.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: IllegalArgumentException) {
                            currentAnimator?.cancel()
                        }
                    }
                }
                start()
            }
        }

        fun animateToPeekingState() {
            val peekingAmount = view.width / 3
            val targetX = if (params.x < screenWidth / 2) {
                -(view.width - peekingAmount)
            } else {
                screenWidth - peekingAmount
            }
            animateToPosition(targetX)
        }

        private fun animateToFullVisibility() {
            val targetX = if (params.x < screenWidth / 2) {
                0
            } else {
                screenWidth - view.width
            }
            animateToPosition(targetX)
        }
    }

    private inner class MovableTouchListener(
        private val params: WindowManager.LayoutParams,
        private val view: View,
        private val onClick: (() -> Unit)? = null
    ) : View.OnTouchListener {
        private var initialX = 0; private var initialY = 0
        private var initialTouchX = 0f; private var initialTouchY = 0f
        private val clickThreshold = 10

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val xDiff = (event.rawX - initialTouchX)
                    val yDiff = (event.rawY - initialTouchY)
                    if (Math.abs(xDiff) < clickThreshold && Math.abs(yDiff) < clickThreshold) {
                        onClick?.invoke()
                    }
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

    private fun startAsForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "AppSuper Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AppSuper Активен")
            .setContentText("Нажмите на плавающую кнопку для доступа.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun toast(message: String) {
        uiHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}