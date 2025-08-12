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

    // --- UI ---
    private lateinit var ipAddressInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusTextClient: TextView
    private lateinit var statusText: TextView
    private lateinit var redLayout: LinearLayout
    private lateinit var greenLayout: LinearLayout
    private lateinit var keypadContainer: TableLayout
    private lateinit var inputDisplay: TextView
    private lateinit var redLineScrollView: HorizontalScrollView
    private lateinit var greenLineScrollView: HorizontalScrollView

    // --- Logic ---
    private val green = mutableListOf<Int>()
    private val red = mutableListOf<Int>()
    private val GREEN_LINE_CAPACITY = 18
    private val RED_LINE_CAPACITY = 19
    private val TOTAL_NUMBERS = 37
    private val currentInput = StringBuilder()
    private var isGameStarted = false
    private enum class Line { RED, GREEN }
    private var lastInputLine: Line? = null
    private var movesMadeAfterStart = 0

    // --- Network ---
    private var clientSocket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            Thread(ClientConnectionThread(hostAddress)).start()
        }
    }

    inner class ClientConnectionThread(private val hostAddress: String) : Runnable {
        override fun run() {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, 8888), 5000)
                socket.tcpNoDelay = true
                clientSocket = socket
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
        updateAllUI()
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
            "⌫" -> { if (currentInput.isNotEmpty()) { currentInput.deleteCharAt(currentInput.length - 1); inputDisplay.text = currentInput.toString() } }
            else -> { if (currentInput.length < 2) { currentInput.append(key); inputDisplay.text = currentInput.toString() } }
        }
    }

    private fun sendCurrentInput() {
        if (currentInput.isNotEmpty()) {
            val numberToSend = currentInput.toString().toIntOrNull()
            if (numberToSend != null && numberToSend in 0..36) handleNumberInput(numberToSend)
            else toast("Введите число от 0 до 36")
            currentInput.clear(); inputDisplay.text = ""
        }
    }

    private fun resetAll() {
        red.clear(); green.clear(); currentInput.clear()
        inputDisplay.text = ""; isGameStarted = false; lastInputLine = null
        movesMadeAfterStart = 0
        updateAllUI()
        statusText.text = "Сброшено. Введите числа от 0 до 36."
        toast("Все линии и состояния очищены")
        sendData(byteArrayOf(201.toByte()))
    }

    private fun handleNumberInput(number: Int) {
        if (!isGameStarted) handleInitialFill(number) else handleGamePlay(number)
    }

    private fun handleInitialFill(number: Int) {
        if (red.contains(number) || green.contains(number)) { toast("Число $number уже введено!"); return }
        red.add(0, number)
        if (red.size > RED_LINE_CAPACITY) { if (green.size < GREEN_LINE_CAPACITY) { green.add(0, red.removeAt(red.lastIndex)) } }
        sendData(byteArrayOf(number.toByte()))
        if (red.size + green.size == TOTAL_NUMBERS) {
            isGameStarted = true
            movesMadeAfterStart = 0
            statusText.text = "Start"
            toast("Все числа введены. Начали!")
        } else {
            statusText.text = "Осталось ввести: ${TOTAL_NUMBERS - (red.size + green.size)}"
        }
        updateAllUI() // Crucial call to re-render buttons as non-clickable
    }

    private fun handleGamePlay(number: Int) {
        movesMadeAfterStart++
        val currentLine: Line
        when {
            red.contains(number) -> { currentLine = Line.RED; red.remove(number) }
            green.contains(number) -> { currentLine = Line.GREEN; green.remove(number) }
            else -> { toast("Ошибка: число $number не найдено."); movesMadeAfterStart--; return }
        }
        red.add(0, number)
        if (red.size > RED_LINE_CAPACITY) { if (green.size < GREEN_LINE_CAPACITY) { green.add(0, red.removeAt(red.lastIndex)) } }
        if (green.size > GREEN_LINE_CAPACITY) { green.removeAt(green.lastIndex) }
        val numbersToShow = if (lastInputLine == currentLine) { if (currentLine == Line.GREEN) green else red } else emptyList()
        sendVisibilityCommand(numbersToShow)
        lastInputLine = currentLine
        updateAllUI()
    }

    // --- LOGIC CHANGE HERE ---
    private fun handleDeleteClick(numberToDelete: Int) {
        // Guard clause: if game has started, do nothing. This reverses the logic.
        if (isGameStarted) return

        // Simple removal logic for pre-start phase
        if (!red.remove(numberToDelete)) {
            green.remove(numberToDelete)
        }

        updateAllUI()
        sendDeleteCommand(numberToDelete)
        toast("Число $numberToDelete удалено")
    }

    private fun sendDeleteCommand(number: Int) = sendData(byteArrayOf(202.toByte(), number.toByte()))

    private fun sendVisibilityCommand(visibleNumbers: List<Int>) {
        if (visibleNumbers.size > 255) return
        val data = ByteArray(2 + visibleNumbers.size).apply {
            this[0] = 102.toByte()
            this[1] = visibleNumbers.size.toByte()
            visibleNumbers.forEachIndexed { index, number -> this[index + 2] = number.toByte() }
        }
        sendData(data)
    }

    private fun updateAllUI() {
        updateLineUI(redLayout, red, true)
        updateLineUI(greenLayout, green, false)
    }

    // --- LOGIC CHANGE HERE ---
    private fun createNumberButton(number: Int): Button {
        return Button(this).apply {
            text = number.toString()
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(12, 4, 12, 4); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4, 0, 4, 0) }

            // This is the core logic change:
            // Buttons are clickable for deletion ONLY if the game has NOT started.
            if (!isGameStarted) {
                isClickable = true
                setOnClickListener { handleDeleteClick(number) }
            } else {
                isClickable = false
                setOnClickListener(null)
            }
        }
    }

    private fun updateLineUI(layout: LinearLayout, numbers: List<Int>, isRed: Boolean) {
        layout.removeAllViews()

        // The barrier logic is now contained and only runs if the game has started.
        if (isGameStarted) {
            val barrier = TextView(this).apply {
                text = "|"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(resources.getColor(android.R.color.white, null))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
            }

            val barrierOnRed = movesMadeAfterStart <= RED_LINE_CAPACITY
            val barrierOnGreen = movesMadeAfterStart > RED_LINE_CAPACITY && movesMadeAfterStart <= RED_LINE_CAPACITY + GREEN_LINE_CAPACITY

            if (isRed) {
                if (barrierOnRed) {
                    val playedCount = movesMadeAfterStart
                    numbers.take(playedCount).forEach { layout.addView(createNumberButton(it)) }
                    layout.addView(barrier)
                    numbers.drop(playedCount).forEach { layout.addView(createNumberButton(it)) }
                } else {
                    numbers.forEach { layout.addView(createNumberButton(it)) }
                }
            } else { // Green line
                if (barrierOnGreen) {
                    val playedCountOnGreen = movesMadeAfterStart - RED_LINE_CAPACITY
                    numbers.take(playedCountOnGreen).forEach { layout.addView(createNumberButton(it)) }
                    layout.addView(barrier)
                    numbers.drop(playedCountOnGreen).forEach { layout.addView(createNumberButton(it)) }
                } else {
                    numbers.forEach { layout.addView(createNumberButton(it)) }
                }
            }
        } else {
            // Pre-start: just render the buttons (they will be deletable)
            numbers.forEach { num -> layout.addView(createNumberButton(num)) }
        }
    }

    private fun sendData(data: ByteArray) {
        if (clientSocket?.isConnected == true) {
            Thread {
                try {
                    clientSocket?.outputStream?.write(data)
                    clientSocket?.outputStream?.flush()
                } catch (e: IOException) { runOnUiThread { toast("Ошибка отправки: соединение потеряно.") } }
            }.start()
        } else toast("Соединение не установлено")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { clientSocket?.close() } catch (e: IOException) { Log.e("WifiClientActivity", "Error closing client socket", e) }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}