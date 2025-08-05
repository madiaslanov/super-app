package com.example.appsuper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

@SuppressLint("MissingPermission")
class WifiClientActivity : AppCompatActivity(), WifiDirectBroadcastReceiver.YourActivityInterface {

    // --- UI ---
    private lateinit var statusText: TextView
    private lateinit var redLayout: LinearLayout
    private lateinit var greenLayout: LinearLayout
    private lateinit var keypadContainer: TableLayout
    private lateinit var inputDisplay: TextView
    private lateinit var deviceList: ListView
    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var redLineScrollView: HorizontalScrollView
    private lateinit var greenLineScrollView: HorizontalScrollView

    // --- Логика ---
    private val green = mutableListOf<Int>() // 18 элементов
    private val red = mutableListOf<Int>()   // 19 элементов
    private val GREEN_LINE_CAPACITY = 18
    private val RED_LINE_CAPACITY = 19
    private val TOTAL_NUMBERS = 37
    private val currentInput = StringBuilder()
    private var isGameStarted = false
    private enum class Line { RED, GREEN }
    private var lastInputLine: Line? = null
    private var isDiscovering = false

    // --- Wi-Fi Direct ---
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver
    private var clientSocket: Socket? = null
    private val peers = mutableListOf<WifiP2pDevice>()
    private val peerNames = mutableListOf<String>()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) startWifiDirect()
            else { toast("Нужны все разрешения"); finish() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_client_hybrid)
        deviceList = findViewById(R.id.deviceListClient)
        val discoverButton: Button = findViewById(R.id.discoverButtonClient)
        val selectionStatusText: TextView = findViewById(R.id.statusTextClient)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, peerNames)
        deviceList.adapter = listAdapter
        discoverButton.setOnClickListener { discoverPeers() }
        deviceList.setOnItemClickListener { _, _, position, _ ->
            val device = peers[position]
            val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { toast("Подключение к ${device.deviceName}...") }
                override fun onFailure(reason: Int) { toast("Ошибка подключения: $reason") }
            })
        }
        selectionStatusText.text = "Инициализация клиента..."
        checkAndRequestPermissions()
    }

    private fun setupMainUI() {
        setContentView(R.layout.activity_device_a)
        redLayout = findViewById(R.id.redLine)
        greenLayout = findViewById(R.id.greenLine)
        statusText = findViewById(R.id.statusText)
        keypadContainer = findViewById(R.id.keypad_container)
        inputDisplay = findViewById(R.id.input_display)
        val resetButton: Button = findViewById(R.id.reset_button)
        redLineScrollView = findViewById(R.id.red_line_scrollview)
        greenLineScrollView = findViewById(R.id.green_line_scrollview)
        val redToggle: CheckBox = findViewById(R.id.red_line_toggle)
        val greenToggle: CheckBox = findViewById(R.id.green_line_toggle)
        redToggle.setOnCheckedChangeListener { _, isChecked -> redLineScrollView.visibility = if (isChecked) View.VISIBLE else View.GONE }
        greenToggle.setOnCheckedChangeListener { _, isChecked -> greenLineScrollView.visibility = if (isChecked) View.VISIBLE else View.GONE }
        statusText.text = "✅ Соединение! Введите числа от 0 до 36."
        createPhoneKeypad()
        resetButton.setOnClickListener { resetAll() }
    }

    private fun createPhoneKeypad() {
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✔"))
        val inflater = LayoutInflater.from(this)
        keys.forEach { rowKeys ->
            val tableRow = TableRow(this).apply { layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
            rowKeys.forEach { key ->
                val keyView = inflater.inflate(R.layout.keypad_button_view, tableRow, false)
                keyView.findViewById<TextView>(R.id.keypad_number).text = key
                keyView.findViewById<TextView>(R.id.keypad_letters).visibility = View.GONE
                keyView.setOnClickListener { onKeypadClick(key) }
                keyView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f)
                tableRow.addView(keyView)
            }
            keypadContainer.addView(tableRow)
        }
    }

    private fun onKeypadClick(key: String) {
        when (key) {
            "✔" -> sendCurrentInput()
            "⌫" -> {
                if (currentInput.isNotEmpty()) {
                    currentInput.deleteCharAt(currentInput.length - 1)
                    inputDisplay.text = currentInput.toString()
                }
            }
            else -> {
                if (currentInput.length < 2) {
                    currentInput.append(key)
                    inputDisplay.text = currentInput.toString()
                }
            }
        }
    }

    private fun sendCurrentInput() {
        if (currentInput.isNotEmpty()) {
            val numberToSend = currentInput.toString().toIntOrNull()
            if (numberToSend != null && numberToSend in 0..36) handleNumberInput(numberToSend)
            else toast("Введите число от 0 до 36")
            currentInput.clear()
            inputDisplay.text = ""
        }
    }

    private fun resetAll() {
        red.clear(); green.clear(); currentInput.clear()
        inputDisplay.text = ""; isGameStarted = false; lastInputLine = null
        updateAllUI()
        statusText.text = "Сброшено. Введите числа от 0 до 36."
        toast("Все линии и состояния очищены")
        sendData(byteArrayOf(201.toByte()))
    }

    private fun handleNumberInput(number: Int) {
        if (!isGameStarted) handleInitialFill(number)
        else handleGamePlay(number)
    }

    private fun handleInitialFill(number: Int) {
        if (red.contains(number) || green.contains(number)) { toast("Число $number уже введено!"); return }
        if (red.size < RED_LINE_CAPACITY) red.add(number)
        else if (green.size < GREEN_LINE_CAPACITY) green.add(number)
        sendData(byteArrayOf(number.toByte()))
        updateAllUI()
        if (red.size + green.size == TOTAL_NUMBERS) {
            isGameStarted = true
            statusText.text = "Started"
            toast("Все числа введены. Начали!")
            red.reverse(); green.reverse()
            updateAllUI()
        } else {
            statusText.text = "Осталось ввести: ${TOTAL_NUMBERS - (red.size + green.size)}"
        }
    }

    private fun handleGamePlay(number: Int) {
        val currentLine: Line
        when {
            red.contains(number) -> { currentLine = Line.RED; red.remove(number) }
            green.contains(number) -> { currentLine = Line.GREEN; green.remove(number) }
            else -> { toast("Ошибка: число $number не найдено в линиях."); return }
        }
        red.add(0, number)
        // ИСПРАВЛЕНИЕ СБОЯ: Используем removeAt вместо removeLast
        if (red.size > RED_LINE_CAPACITY) green.add(0, red.removeAt(red.lastIndex))
        if (green.size > GREEN_LINE_CAPACITY) green.removeAt(green.lastIndex)

        val numbersToShow = if (lastInputLine == currentLine) {
            if (currentLine == Line.GREEN) green else red
        } else emptyList()

        sendVisibilityCommand(numbersToShow)
        lastInputLine = currentLine
        updateAllUI()
    }

    private fun sendVisibilityCommand(visibleNumbers: List<Int>) {
        if (visibleNumbers.size > 255) { Log.e("WifiClientActivity", "Слишком много номеров для отправки"); return }
        val data = ByteArray(2 + visibleNumbers.size)
        data[0] = 102.toByte()
        data[1] = visibleNumbers.size.toByte()
        visibleNumbers.forEachIndexed { index, number -> data[index + 2] = number.toByte() }
        sendData(data)
    }

    private fun updateAllUI() {
        updateLineUI(redLayout, red, true)
        updateLineUI(greenLayout, green, false)
    }

    private fun updateLineUI(layout: LinearLayout, numbers: List<Int>, isRed: Boolean) {
        layout.removeAllViews()
        numbers.forEach { num ->
            val button = Button(this).apply {
                text = num.toString(); minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setPadding(12, 4, 12, 4); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4, 0, 4, 0) }
                isClickable = false
            }
            layout.addView(button)
        }
    }

    private fun sendData(data: ByteArray) {
        if (clientSocket?.isConnected == true) {
            Thread {
                try {
                    clientSocket?.outputStream?.write(data)
                    clientSocket?.outputStream?.flush()
                } catch (e: IOException) { runOnUiThread { toast("Ошибка отправки: ${e.message}") } }
            }.start()
        } else toast("Соединение не установлено")
    }

    // ... Остальная часть класса без изменений ...
    private fun checkAndRequestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startWifiDirect()
        else requestPermissionsLauncher.launch(perms)
    }

    private fun startWifiDirect() {
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, Looper.getMainLooper(), null)
        receiver = WifiDirectBroadcastReceiver(manager, channel, this)
        discoverPeers()
    }

    private fun discoverPeers() {
        if (isDiscovering) { toast("Поиск уже запущен..."); return }
        val selectionStatusText: TextView = findViewById(R.id.statusTextClient)
        val discoverButton: Button = findViewById(R.id.discoverButtonClient)
        discoverButton.isEnabled = false; discoverButton.alpha = 0.5f; isDiscovering = true
        selectionStatusText.text = "Поиск устройств..."
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { toast("Поиск запущен успешно") }
            override fun onFailure(reason: Int) {
                val reasonText = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct не поддерживается."
                    WifiP2pManager.ERROR -> "Внутренняя ошибка. Убедитесь, что Wi-Fi и Геолокация (GPS) включены."
                    WifiP2pManager.BUSY -> "Система занята, попробуйте через минуту."
                    else -> "Неизвестная ошибка: код $reason"
                }
                toast("Ошибка запуска поиска: $reasonText")
                isDiscovering = false; discoverButton.isEnabled = true; discoverButton.alpha = 1.0f
                selectionStatusText.text = "Ошибка. Попробуйте снова."
            }
        })
    }

    override fun onPeersAvailable(peerList: List<WifiP2pDevice>) {
        val discoverButton: Button? = findViewById(R.id.discoverButtonClient)
        isDiscovering = false
        discoverButton?.isEnabled = true; discoverButton?.alpha = 1.0f
        val selectionStatusText: TextView? = findViewById(R.id.statusTextClient)
        peers.clear(); peers.addAll(peerList); peerNames.clear()
        peerList.forEach { peerNames.add(it.deviceName) }
        listAdapter.notifyDataSetChanged()
        selectionStatusText?.text = if (peerList.isEmpty()) "Устройства не найдены." else "Выберите устройство."
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (info.groupFormed) Thread(ClientConnectionThread(info.groupOwnerAddress.hostAddress)).start()
    }

    inner class ClientConnectionThread(private val hostAddress: String) : Runnable {
        override fun run() {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, 8888), 5000)
                clientSocket = socket
                runOnUiThread { setupMainUI() }
            } catch (e: IOException) { runOnUiThread { toast("Не удалось подключиться к серверу") } }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::receiver.isInitialized) registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        if (::receiver.isInitialized) unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.removeGroup(channel, null)
        try { clientSocket?.close() } catch (e: IOException) { Log.e("WifiClientActivity", "Error closing client socket", e) }
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}