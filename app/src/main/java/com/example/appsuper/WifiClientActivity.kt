package com.example.appsuper

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

@SuppressLint("MissingPermission")
class WifiClientActivity : AppCompatActivity() {

    // --- UI для подключения ---
    private lateinit var ipAddressInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusTextClient: TextView

    // --- UI для игры (остается без изменений) ---
    private lateinit var statusText: TextView
    private lateinit var redLayout: LinearLayout
    private lateinit var greenLayout: LinearLayout
    private lateinit var keypadContainer: TableLayout
    private lateinit var inputDisplay: TextView
    private lateinit var redLineScrollView: HorizontalScrollView
    private lateinit var greenLineScrollView: HorizontalScrollView

    // --- Логика (остается без изменений) ---
    private val green = mutableListOf<Int>()
    private val red = mutableListOf<Int>()
    private val GREEN_LINE_CAPACITY = 18
    private val RED_LINE_CAPACITY = 19
    private val TOTAL_NUMBERS = 37
    private val currentInput = StringBuilder()
    private var isGameStarted = false
    private enum class Line { RED, GREEN }
    private var lastInputLine: Line? = null

    // --- Сеть ---
    private var clientSocket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // НОВАЯ ЛОГИКА: Показываем экран подключения
        setupConnectionUI()
    }

    private fun setupConnectionUI() {
        setContentView(R.layout.activity_wifi_client_hotspot)
        ipAddressInput = findViewById(R.id.ipAddressInput)
        connectButton = findViewById(R.id.connectButton)
        statusTextClient = findViewById(R.id.statusTextClient)

        connectButton.setOnClickListener {
            val hostAddress = ipAddressInput.text.toString()
            if (hostAddress.isBlank()) {
                toast("Пожалуйста, введите IP адрес сервера")
                return@setOnClickListener
            }
            statusTextClient.text = "Подключение к $hostAddress..."
            connectButton.isEnabled = false
            // Запускаем подключение в фоновом потоке
            Thread(ClientConnectionThread(hostAddress)).start()
        }
    }

    inner class ClientConnectionThread(private val hostAddress: String) : Runnable {
        override fun run() {
            try {
                val socket = Socket()
                // Подключаемся с таймаутом в 5 секунд
                socket.connect(InetSocketAddress(hostAddress, 8888), 5000)
                socket.tcpNoDelay = true // Важная оптимизация для низкой задержки
                clientSocket = socket

                // Если подключение успешно, переключаемся на основной UI игры
                runOnUiThread { setupMainUI() }
            } catch (e: IOException) {
                Log.e("Client", "Не удалось подключиться к серверу: ${e.message}")
                runOnUiThread {
                    toast("Ошибка подключения. Проверьте IP адрес и Wi-Fi соединение.")
                    statusTextClient.text = "Ошибка подключения."
                    connectButton.isEnabled = true
                }
            }
        }
    }

    // ==========================================================================================
    // ВЕСЬ КОД НИЖЕ ОСТАЕТСЯ БЕЗ ИЗМЕНЕНИЙ - ЭТО ВАША СУЩЕСТВУЮЩАЯ ИГРОВАЯ ЛОГИКА
    // ==========================================================================================

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
        if (red.contains(number) || green.contains(number)) {
            toast("Число $number уже введено!")
            return
        }
        red.add(0, number)
        if (red.size > RED_LINE_CAPACITY) {
            if (green.size < GREEN_LINE_CAPACITY) {
                green.add(0, red.removeAt(red.lastIndex))
            }
        }
        sendData(byteArrayOf(number.toByte()))
        updateAllUI()
        if (red.size + green.size == TOTAL_NUMBERS) {
            isGameStarted = true
            statusText.text = "Started"
            toast("Все числа введены. Начали!")
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
        if (red.size > RED_LINE_CAPACITY) {
            if (green.size < GREEN_LINE_CAPACITY) {
                green.add(0, red.removeAt(red.lastIndex))
            }
        }
        if (green.size > GREEN_LINE_CAPACITY) {
            green.removeAt(green.lastIndex)
        }
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
                } catch (e: IOException) {
                    runOnUiThread {
                        toast("Ошибка отправки: соединение потеряно.")
                        // Опционально: можно вернуться на экран подключения
                        // setupConnectionUI()
                    }
                }
            }.start()
        } else toast("Соединение не установлено")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { clientSocket?.close() } catch (e: IOException) { Log.e("WifiClientActivity", "Error closing client socket", e) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}