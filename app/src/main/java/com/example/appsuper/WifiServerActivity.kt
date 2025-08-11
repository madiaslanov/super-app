package com.example.appsuper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException

@SuppressLint("SetTextI18n")
class WifiServerActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var stopButton: Button
    private lateinit var lockButton: Button

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var isServerRunning = false

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

    // ИЗМЕНЕНИЕ: Улучшенная логика обработки результата запроса разрешений
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Этот код выполнится, когда пользователь вернется с экрана настроек
        if (canDrawOverlays()) {
            // Если разрешение предоставлено, запускаем сервер
            startServer()
        } else {
            // Если пользователь отказал, показываем критическое сообщение и не даем работать дальше
            statusText.text = "Критическая ошибка:\nПриложение не может работать без разрешения на отображение поверх других окон.\n\nПожалуйста, перезапустите и предоставьте разрешение."
            toast("Разрешение не предоставлено. Работа невозможна.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_b_simple)

        statusText = findViewById(R.id.statusTextB)
        stopButton = findViewById(R.id.stopButtonB)
        lockButton = findViewById(R.id.lockButtonB)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buttonEnablerReceiver, IntentFilter(ACTION_ALL_NUMBERS_RECEIVED), RECEIVER_EXPORTED)
        } else {
            registerReceiver(buttonEnablerReceiver, IntentFilter(ACTION_ALL_NUMBERS_RECEIVED))
        }

        stopButton.setOnClickListener {
            cleanUp()
            statusText.text = "Остановлено. Включите точку доступа и перезапустите это окно для новой игры."
            it.visibility = View.GONE
            lockButton.visibility = View.GONE
        }

        lockButton.setOnClickListener {
            sendBroadcast(Intent(ACTION_LOCK_ALL))
            toast("Символы заморожены.")
        }

        // Запускаем проверку при старте
        checkOverlayPermissionAndStart()
    }

    // ИЗМЕНЕНИЕ: Метод с более понятным названием
    private fun checkOverlayPermissionAndStart() {
        if (canDrawOverlays()) {
            // Если разрешение уже есть, сразу запускаем сервер
            startServer()
        } else {
            // Если разрешения нет, информируем пользователя и отправляем его в настройки
            statusText.text = "Требуется разрешение:\nДля работы оверлеев необходимо разрешить приложению отображаться поверх других окон."
            toast("Пожалуйста, предоставьте разрешение на следующем экране")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun startServer() {
        if (isServerRunning) return

        val serverIp = getHotspotIpAddress()
        val instructionText = if (serverIp != null) {
            "Инструкция:\n1. Убедитесь, что точка доступа включена.\n2. Ваш IP для подключения:\n$serverIp\n3. Ожидайте подключения клиента..."
        } else {
            "Инструкция:\n1. Включите точку доступа (Wi-Fi Hotspot).\n2. IP-адрес не определен, попробуйте стандартный: 192.168.43.1\n3. Ожидайте подключения клиента..."
        }
        statusText.text = instructionText

        serverThread = Thread(ServerThread()).apply { start() }
        isServerRunning = true
    }

    // Остальной код остается без изменений...
    private fun getHotspotIpAddress(): String? {
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                if (networkInterface.name.contains("ap") || networkInterface.name.contains("wlan")) {
                    val inetAddresses = networkInterface.inetAddresses
                    while (inetAddresses.hasMoreElements()) {
                        val inetAddress = inetAddresses.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ServerIP", "Не удалось получить IP адрес", e)
        }
        return null
    }

    inner class ServerThread : Runnable {
        override fun run() {
            try {
                serverSocket = ServerSocket(8888)
                while (!Thread.currentThread().isInterrupted) {
                    runOnUiThread { Log.d("Server", "Сервер ожидает клиента на accept()...") }
                    val clientSocket = serverSocket!!.accept()
                    WifiSocketHolder.socket = clientSocket
                    startService(Intent(this@WifiServerActivity, OverlayService::class.java))
                    runOnUiThread {
                        statusText.text = "✅ Клиент подключен! Игра активна."
                        stopButton.visibility = View.VISIBLE
                        lockButton.visibility = View.VISIBLE
                    }
                    break
                }
            } catch (e: SocketException) {
                Log.i("Server", "Серверный сокет был закрыт, поток завершается.")
            } catch (e: IOException) {
                Log.e("Server", "Ошибка ввода/вывода в потоке сервера: ${e.message}")
            } finally {
                Log.i("Server", "Поток сервера окончательно завершен.")
                isServerRunning = false
            }
        }
    }

    private fun cleanUp() {
        stopService(Intent(this, OverlayService::class.java))
        try {
            WifiSocketHolder.socket?.close()
            WifiSocketHolder.socket = null
            serverSocket?.close()
            serverSocket = null
            serverThread?.interrupt()
            serverThread = null
        } catch (e: IOException) {
            Log.e("Cleanup", "Ошибка при закрытии сокетов", e)
        }
        isServerRunning = false
        runOnUiThread {
            stopButton.visibility = View.GONE
            lockButton.visibility = View.GONE
            lockButton.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(buttonEnablerReceiver)
        cleanUp()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}