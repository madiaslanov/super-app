package com.example.appsuper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), MainAppView.AppViewListener {

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            checkOverlayPermissionAndStart()
        } else {
            toast("Приложение не может работать без разрешения на отправку уведомлений.")
            finish()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canDrawOverlays()) {
            startOverlayService()
        } else {
            toast("Приложение не может работать без разрешения на отображение поверх других окон.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Создаем наш основной View и добавляем его в Activity
        val mainAppView = MainAppView(this)
        mainAppView.setListener(this)
        findViewById<FrameLayout>(R.id.root_container).addView(mainAppView)

        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                checkOverlayPermissionAndStart()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            checkOverlayPermissionAndStart()
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (canDrawOverlays()) {
            startOverlayService()
        } else {
            toast("Пожалуйста, предоставьте разрешение на отображение поверх других окон.")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
        // В Activity эта кнопка ничего не делает, так как мы в основном приложении
    }

    private fun canDrawOverlays(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}