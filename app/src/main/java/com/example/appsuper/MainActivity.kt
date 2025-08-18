package com.example.appsuper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // --- UI ---
    private lateinit var statusText: TextView
    private lateinit var combinedLineLayout: LinearLayout
    private lateinit var keypadContainer: FrameLayout
    private lateinit var inputDisplay: TextView
    private lateinit var lineScrollView: HorizontalScrollView
    private lateinit var showLinesCheckbox: CheckBox
    private lateinit var toggleKeyboardButton: Button
    private lateinit var freezeButton: Button

    // --- Логика ---
    private val green = mutableListOf<Int>()
    private val red = mutableListOf<Int>()
    private val GREEN_LINE_CAPACITY = 18
    private val RED_LINE_CAPACITY = 19
    private val TOTAL_NUMBERS = 37
    private val currentInput = StringBuilder()
    private var isGameStarted = false
    private var lastInputWasInRed: Boolean? = null
    private var movesMadeAfterStart = 0

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (canDrawOverlays()) startOverlayService()
        else {
            toast("Приложение не может работать без разрешения на отображение поверх других окон.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupMainUI()
        checkOverlayPermissionAndStart()
    }

    private fun checkOverlayPermissionAndStart() {
        if (canDrawOverlays()) startOverlayService()
        else {
            toast("Пожалуйста, предоставьте разрешение на отображение поверх других окон.")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun canDrawOverlays(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun setupMainUI() {
        statusText = findViewById(R.id.statusText)
        combinedLineLayout = findViewById(R.id.combinedLine)
        keypadContainer = findViewById(R.id.keypad_container)
        inputDisplay = findViewById(R.id.input_display)
        lineScrollView = findViewById(R.id.line_scrollview)
        showLinesCheckbox = findViewById(R.id.line_toggle)
        toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button)
        freezeButton = findViewById(R.id.freeze_button)

        findViewById<Button>(R.id.reset_button).setOnClickListener { resetAll() }
        showLinesCheckbox.setOnCheckedChangeListener { _, isChecked -> lineScrollView.visibility = if (isChecked) View.VISIBLE else View.GONE }
        toggleKeyboardButton.setOnClickListener { keypadContainer.visibility = if (keypadContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE }

        freezeButton.setOnClickListener {
            sendCommandToService(AppConstants.ACTION_FREEZE_ALL)
            sendCommandToService(AppConstants.ACTION_HIDE_ALL_SYMBOLS)
            toast("Символы заморожены и скрыты")
            it.isEnabled = false
        }

        statusText.text = "Введите числа от 0 до 36."
        createPhoneKeypad()
        updateAllUI()
    }

    private fun createPhoneKeypad() {
        val keypadView = layoutInflater.inflate(R.layout.keypad_layout, keypadContainer, false)
        val tableLayout = keypadView.findViewById<TableLayout>(R.id.keypad_table)
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✔"))
        val inflater = LayoutInflater.from(this)
        keys.forEach { rowKeys ->
            val tableRow = TableRow(this).apply { layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
            rowKeys.forEach { key ->
                val keyView = inflater.inflate(R.layout.keypad_button_view, tableRow, false)
                keyView.findViewById<TextView>(R.id.keypad_number).text = key
                keyView.setOnClickListener { onKeypadClick(key) }
                keyView.layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f)
                tableRow.addView(keyView)
            }
            tableLayout.addView(tableRow)
        }
        keypadContainer.addView(keypadView)
    }

    private fun onKeypadClick(key: String) {
        when (key) {
            "✔" -> sendCurrentInput()
            "⌫" -> if (currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                inputDisplay.text = currentInput.toString()
            }
            else -> if (currentInput.length < 2) {
                currentInput.append(key)
                inputDisplay.text = currentInput.toString()
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
        inputDisplay.text = ""
        isGameStarted = false
        lastInputWasInRed = null
        movesMadeAfterStart = 0
        freezeButton.isEnabled = false
        updateAllUI()
        statusText.text = "Сброшено. Введите числа от 0 до 36."
        toast("Все линии и состояния очищены")
        sendCommandToService(AppConstants.ACTION_REMOVE_ALL_SYMBOLS)
    }

    private fun handleNumberInput(number: Int) {
        if (!isGameStarted) handleInitialFill(number)
        else handleGamePlay(number)
    }

    // ИЗМЕНЕНИЕ ЗДЕСЬ: Полностью переписана логика добавления
    private fun handleInitialFill(number: Int) {
        if (red.contains(number) || green.contains(number)) {
            toast("Число $number уже введено!")
            return
        }

        if (red.size + green.size >= TOTAL_NUMBERS) {
            toast("Все 37 чисел уже введены.")
            return
        }

        // Шаг 1: Новое число ВСЕГДА добавляется в начало красной линии.
        red.add(0, number)

        // Шаг 2: Если красная линия переполнилась, перемещаем последнее число в зеленую.
        if (red.size > RED_LINE_CAPACITY) {
            val numberToMove = red.removeAt(red.lastIndex) // Берем последнее число из красной
            if (green.size < GREEN_LINE_CAPACITY) {
                green.add(0, numberToMove) // Добавляем его в начало зеленой
            }
        }

        // Остальная логика остается прежней
        sendCommandToService(AppConstants.ACTION_SHOW_SYMBOL, number)

        if (red.size + green.size == TOTAL_NUMBERS) {
            isGameStarted = true
            movesMadeAfterStart = 0
            statusText.text = "Start"
            freezeButton.isEnabled = true
            toast("Все числа введены. Начали!")
        } else {
            statusText.text = "Осталось ввести: ${TOTAL_NUMBERS - (red.size + green.size)}"
        }
        updateAllUI()
    }


    private fun handleDeleteClick(number: Int) {
        if (isGameStarted) return

        // Сначала пытаемся удалить из зеленой линии
        if (green.remove(number)) {
            // Если удалили из зеленой, нужно "вернуть" число из красной
            if (red.isNotEmpty()) {
                green.add(red.removeAt(0))
            }
        } else {
            // Если в зеленой не было, удаляем из красной
            red.remove(number)
        }

        // Эта логика удаления была сложной и могла нарушить порядок,
        // давайте упростим до простого удаления с перерисовкой.
        // Простое удаление:
        val wasRemoved = red.remove(number) || green.remove(number)

        // При простом удалении может нарушиться правило 19/18.
        // Поэтому нужна перебалансировка.
        if (wasRemoved) {
            // Перебалансировка: если в красной линии не хватает, а в зеленой есть, переносим
            while(red.size < RED_LINE_CAPACITY && green.isNotEmpty()) {
                red.add(green.removeAt(0))
            }

            sendCommandToService(AppConstants.ACTION_DELETE_SYMBOL, number)
            updateAllUI()
            statusText.text = "Осталось ввести: ${TOTAL_NUMBERS - (red.size + green.size)}"
            if (freezeButton.isEnabled) {
                freezeButton.isEnabled = false
            }
            toast("Число $number удалено")
        }
    }


    private fun handleGamePlay(number: Int) {
        val isNumberInRed = red.contains(number)
        val isNumberInGreen = green.contains(number)
        if (!isNumberInRed && !isNumberInGreen) {
            toast("Ошибка: число $number не найдено.")
            return
        }
        var isAlreadyPlayed = false
        if (isNumberInRed) {
            if (movesMadeAfterStart > 0 && red.indexOf(number) < movesMadeAfterStart) isAlreadyPlayed = true
        } else if (isNumberInGreen) {
            if (movesMadeAfterStart > RED_LINE_CAPACITY) {
                val playedCountOnGreen = movesMadeAfterStart - RED_LINE_CAPACITY
                if (playedCountOnGreen > 0 && green.indexOf(number) < playedCountOnGreen) isAlreadyPlayed = true
            }
        }
        if (!isAlreadyPlayed) movesMadeAfterStart++

        if (isNumberInRed) red.remove(number) else green.remove(number)

        red.add(0, number)
        if (red.size > RED_LINE_CAPACITY) {
            green.add(0, red.removeAt(red.lastIndex))
            if (green.size > GREEN_LINE_CAPACITY) {
                green.removeAt(green.lastIndex)
            }
        }

        val numbersToShow = if (lastInputWasInRed == isNumberInRed) (if (isNumberInRed) red else green) else emptyList()
        sendVisibilityCommand(numbersToShow)
        lastInputWasInRed = isNumberInRed
        updateAllUI()
    }

    private fun sendVisibilityCommand(visibleNumbers: List<Int>) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = AppConstants.ACTION_SET_VISIBILITY
            putIntegerArrayListExtra(AppConstants.EXTRA_VISIBLE_LIST, ArrayList(visibleNumbers))
        }
        startService(intent)
    }

    private fun updateAllUI() {
        updateCombinedLineUI()
    }

    private fun createNumberView(number: Int, color: Int): TextView {
        return TextView(this).apply {
            text = number.toString()
            setTextColor(color)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(12, 4, 12, 4)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4, 0, 4, 0) }
            setOnClickListener { handleDeleteClick(number) }
        }
    }

    private fun updateCombinedLineUI() {
        combinedLineLayout.removeAllViews()
        val redColor = ContextCompat.getColor(this, R.color.red_line_color)
        val greenColor = ContextCompat.getColor(this, R.color.green_line_color)
        val addNumberViews = { list: List<Int>, color: Int -> list.forEach { num -> combinedLineLayout.addView(createNumberView(num, color)) } }

        if (isGameStarted) {
            val barrier = TextView(this).apply {
                text = "|"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
            }
            if (movesMadeAfterStart in 1..RED_LINE_CAPACITY) {
                red.take(movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor)) }
                combinedLineLayout.addView(barrier)
                red.drop(movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor)) }
                addNumberViews(green, greenColor)
            } else if (movesMadeAfterStart > RED_LINE_CAPACITY) {
                addNumberViews(red, redColor)
                val playedOnGreen = movesMadeAfterStart - RED_LINE_CAPACITY
                green.take(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor)) }
                combinedLineLayout.addView(barrier)
                green.drop(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor)) }
            } else {
                addNumberViews(red, redColor); addNumberViews(green, greenColor)
            }
        } else {
            addNumberViews(red, redColor); addNumberViews(green, greenColor)
        }
        lineScrollView.post { lineScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT) }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun sendCommandToService(action: String, number: Int = -1) {
        val intent = Intent(this, OverlayService::class.java).apply {
            this.action = action
            if (number != -1) putExtra(AppConstants.EXTRA_NUMBER, number)
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}