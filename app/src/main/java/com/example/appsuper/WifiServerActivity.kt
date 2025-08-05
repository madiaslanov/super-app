package com.example.appsuper

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.ServerSocket

@SuppressLint("MissingPermission")
class WifiServerActivity : AppCompatActivity(), WifiDirectBroadcastReceiver.YourActivityInterface {

    private lateinit var statusText: TextView
    private lateinit var stopButton: Button
    private lateinit var lockButton: Button
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver

    private var isServerRunning = false
    private var serverThread: Thread? = null

    companion object {
        const val ACTION_LOCK_ALL = "com.example.appsuper.ACTION_LOCK_ALL_OVERLAYS"
        const val ACTION_ALL_NUMBERS_RECEIVED = "com.example.appsuper.ACTION_ALL_NUMBERS_RECEIVED"
    }

    private val buttonEnablerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ALL_NUMBERS_RECEIVED) {
                lockButton.isEnabled = true
                toast("Все символы расставлены. Можно заморозить.")
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canDrawOverlays()) startWifiDirect() else { toast("Разрешение на оверлей необходимо для работы"); finish() }
    }

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) checkOverlayPermission() else { toast("Нужны все разрешения для работы"); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_b_simple)
        statusText = findViewById(R.id.statusTextB)
        stopButton = findViewById(R.id.stopButtonB)
        lockButton = findViewById(R.id.lockButtonB)

        registerReceiver(buttonEnablerReceiver, IntentFilter(ACTION_ALL_NUMBERS_RECEIVED), RECEIVER_EXPORTED)

        stopButton.setOnClickListener {
            cleanUp()
            toast("Сервис остановлен")
            statusText.text = "Остановлено."
            it.visibility = View.GONE
            lockButton.visibility = View.GONE
        }

        lockButton.setOnClickListener {
            sendBroadcast(Intent(ACTION_LOCK_ALL))
            // ИЗМЕНЕНИЕ ЗДЕСЬ: Программная блокировка удалена, так как теперь она в манифесте
            toast("Символы заморожены.")
        }

        statusText.text = "Инициализация сервера..."
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            checkOverlayPermission()
        } else {
            requestPermissionsLauncher.launch(perms)
        }
    }

    private fun checkOverlayPermission() {
        if (canDrawOverlays()) {
            startWifiDirect()
        } else {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun canDrawOverlays(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(this)
    } else {
        true
    }

    private fun startWifiDirect() {
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, Looper.getMainLooper(), null)
        receiver = WifiDirectBroadcastReceiver(manager, channel, this)

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                statusText.text = "Группа создана.\nОжидание клиента..."
            }
            override fun onFailure(reason: Int) {
                statusText.text = "Ошибка создания группы: $reason"
            }
        })
    }

    override fun onPeersAvailable(peers: List<WifiP2pDevice>) {}

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed && info.isGroupOwner && !isServerRunning) {
            isServerRunning = true
            statusText.text = "✅ Клиент подключился! Запуск сервиса..."
            stopButton.visibility = View.VISIBLE
            lockButton.visibility = View.VISIBLE
            serverThread = Thread(ServerThread()).apply { start() }
        }
    }

    inner class ServerThread : Runnable {
        override fun run() {
            try {
                ServerSocket(8888).use { serverSocket ->
                    while (!Thread.currentThread().isInterrupted) {
                        Log.d("WifiDirect", "Сервер ожидает клиента на accept()...")
                        val clientSocket = serverSocket.accept()
                        Log.d("WifiDirect", "Клиент принят! Запуск OverlayService.")
                        WifiSocketHolder.socket = clientSocket
                        startService(Intent(this@WifiServerActivity, OverlayService::class.java))
                    }
                }
            } catch (e: IOException) {
                Log.e("WifiDirect", "Ошибка сервера (возможно, сокет был закрыт): ${e.message}")
            } finally {
                isServerRunning = false
            }
        }
    }

    private fun cleanUp() {
        // ИЗМЕНЕНИЕ ЗДЕСЬ: Разблокировка ориентации больше не нужна
        stopService(Intent(this, OverlayService::class.java))
        serverThread?.interrupt()
        serverThread = null
        try {
            WifiSocketHolder.socket?.close()
            WifiSocketHolder.socket = null
        } catch (e: IOException) { /* ignore */ }
        isServerRunning = false
        runOnUiThread {
            stopButton.visibility = View.GONE
            lockButton.visibility = View.GONE
            lockButton.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::receiver.isInitialized) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::receiver.isInitialized) {
            unregisterReceiver(receiver)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(buttonEnablerReceiver)
        cleanUp()
        if (::manager.isInitialized) {
            manager.removeGroup(channel, null)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}