package com.example.appsuper

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat

class MainAppView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), AppState.StateListener {

    interface AppViewListener {
        fun onSendCommand(action: String, number: Int = -1)
        fun onSendCommandWithList(action: String, visibleList: ArrayList<Int>)
        fun onHideRequest()
    }

    private var listener: AppViewListener? = null

    // --- UI ---
    private val statusText: TextView
    private val counterText: TextView // НОВЫЙ ЭЛЕМЕНТ: Счетчик
    private val combinedLineLayout: LinearLayout
    private val keypadContainer: FrameLayout
    private val inputDisplay: TextView
    private val lineScrollView: HorizontalScrollView
    private val toggleKeyboardButton: Button
    private val freezeButton: Button
    // hideButton и showLinesCheckbox УДАЛЕНЫ

    // Локальное состояние ввода, не синхронизируется
    private val currentInput = StringBuilder()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_main_app, this, true)
        statusText = findViewById(R.id.statusText)
        counterText = findViewById(R.id.counter_text) // ИНИЦИАЛИЗАЦИЯ СЧЕТЧИКА
        combinedLineLayout = findViewById(R.id.combinedLine)
        keypadContainer = findViewById(R.id.keypad_container)
        inputDisplay = findViewById(R.id.input_display)
        lineScrollView = findViewById(R.id.line_scrollview)
        toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button)
        freezeButton = findViewById(R.id.freeze_button)
        // Инициализация удаленных элементов убрана

        setupMainUI()
    }

    // Подписываемся на обновления при появлении View
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AppState.registerListener(this)
        // Сразу обновляем UI, чтобы показать актуальное состояние
        onStateChanged()
    }

    // Отписываемся, чтобы избежать утечек памяти
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppState.unregisterListener(this)
    }

    // Этот метод вызывается, когда AppState сообщает об изменениях
    override fun onStateChanged() {
        updateAllUI()
    }

    fun setListener(listener: AppViewListener) {
        this.listener = listener
    }

    fun setOverlayMode() {
        // Убираем фон для режима оверлея
        this.setBackgroundColor(Color.TRANSPARENT)
        // Фон линии делаем полупрозрачным
        lineScrollView.setBackgroundColor(Color.parseColor("#992C2C2E"))
    }

    private fun setupMainUI() {
        // Логика для showLinesCheckbox УДАЛЕНА

        toggleKeyboardButton.setOnClickListener {
            if (keypadContainer.visibility == View.VISIBLE) {
                keypadContainer.visibility = View.GONE
            } else {
                keypadContainer.visibility = View.VISIBLE
            }
        }

        freezeButton.setOnClickListener {
            listener?.onSendCommand(AppConstants.ACTION_FREEZE_ALL)
            listener?.onSendCommand(AppConstants.ACTION_HIDE_ALL_SYMBOLS)
            toast("Символы заморожены и скрыты")
        }

        // Логика для hideButton УДАЛЕНА

        createPhoneKeypad()

        // Клавиатура и линия теперь всегда видимы по умолчанию
        keypadContainer.visibility = View.VISIBLE
        lineScrollView.visibility = View.VISIBLE
    }

    private fun createPhoneKeypad() {
        // ... (код этой функции не меняется)
        keypadContainer.removeAllViews()
        val keypadView = LayoutInflater.from(context).inflate(R.layout.keypad_layout, keypadContainer, false)
        val tableLayout = keypadView.findViewById<TableLayout>(R.id.keypad_table)
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✔"))
        val inflater = LayoutInflater.from(context)
        keys.forEach { rowKeys ->
            val tableRow = TableRow(context).apply { layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
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
        // ... (код этой функции не меняется)
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
            if (numberToSend != null && numberToSend in 0..36) {
                handleNumberInput(numberToSend)
            } else {
                toast("Введите число от 0 до 36")
            }
            currentInput.clear()
            inputDisplay.text = ""
            // keypadContainer.visibility = View.GONE // ЭТА СТРОКА УДАЛЕНА, чтобы клавиатура не скрывалась
        }
    }

    // Все функции ниже теперь работают с данными из AppState
    private fun handleNumberInput(number: Int) {
        if (!AppState.isGameStarted) handleInitialFill(number) else handleGamePlay(number)
    }

    private fun handleInitialFill(number: Int) {
        if (AppState.red.contains(number) || AppState.green.contains(number)) {
            toast("Число $number уже введено!")
            return
        }
        if (AppState.red.size + AppState.green.size >= AppState.TOTAL_NUMBERS) {
            toast("Все 37 чисел уже введены.")
            return
        }
        AppState.red.add(0, number)
        if (AppState.red.size > AppState.RED_LINE_CAPACITY) {
            val numberToMove = AppState.red.removeAt(AppState.red.lastIndex)
            if (AppState.green.size < AppState.GREEN_LINE_CAPACITY) {
                AppState.green.add(0, numberToMove)
            }
        }
        listener?.onSendCommand(AppConstants.ACTION_SHOW_SYMBOL, number)
        if (AppState.red.size + AppState.green.size == AppState.TOTAL_NUMBERS) {
            AppState.isGameStarted = true
            AppState.movesMadeAfterStart = 0
            toast("Все числа введены. Начали!")
        }
        // Оповещаем всех об изменении состояния
        AppState.notifyListeners()
    }

    private fun handleDeleteClick(number: Int) {
        if (AppState.isGameStarted) return
        val wasRemoved = AppState.red.remove(number) || AppState.green.remove(number)
        if (wasRemoved) {
            while (AppState.red.size < AppState.RED_LINE_CAPACITY && AppState.green.isNotEmpty()) {
                AppState.red.add(AppState.green.removeAt(0))
            }
            listener?.onSendCommand(AppConstants.ACTION_DELETE_SYMBOL, number)
            toast("Число $number удалено")
            // Оповещаем всех об изменении состояния
            AppState.notifyListeners()
        }
    }

    private fun handleGamePlay(number: Int) {
        val isNumberInRed = AppState.red.contains(number)
        val isNumberInGreen = AppState.green.contains(number)
        if (!isNumberInRed && !isNumberInGreen) {
            toast("Ошибка: число $number не найдено.")
            return
        }
        var isAlreadyPlayed = false
        if (isNumberInRed) {
            if (AppState.movesMadeAfterStart > 0 && AppState.red.indexOf(number) < AppState.movesMadeAfterStart) isAlreadyPlayed = true
        } else if (isNumberInGreen) {
            if (AppState.movesMadeAfterStart > AppState.RED_LINE_CAPACITY) {
                val playedCountOnGreen = AppState.movesMadeAfterStart - AppState.RED_LINE_CAPACITY
                if (playedCountOnGreen > 0 && AppState.green.indexOf(number) < playedCountOnGreen) isAlreadyPlayed = true
            }
        }
        if (!isAlreadyPlayed) AppState.movesMadeAfterStart++
        if (isNumberInRed) AppState.red.remove(number) else AppState.green.remove(number)
        AppState.red.add(0, number)
        if (AppState.red.size > AppState.RED_LINE_CAPACITY) {
            AppState.green.add(0, AppState.red.removeAt(AppState.red.lastIndex))
            if (AppState.green.size > AppState.GREEN_LINE_CAPACITY) {
                AppState.green.removeAt(AppState.green.lastIndex)
            }
        }
        val numbersToShow = if (AppState.lastInputWasInRed == isNumberInRed) (if (isNumberInRed) AppState.red else AppState.green) else emptyList()
        listener?.onSendCommandWithList(AppConstants.ACTION_SET_VISIBILITY, ArrayList(numbersToShow))
        AppState.lastInputWasInRed = isNumberInRed
        // Оповещаем всех об изменении состояния
        AppState.notifyListeners()
    }

    private fun updateAllUI() {
        // Обновляем текст статуса
        if (AppState.isGameStarted) {
            statusText.text = "Началось" // Изменено на "Началось" для соответствия скриншоту
        } else {
            val remaining = AppState.TOTAL_NUMBERS - (AppState.red.size + AppState.green.size)
            if (remaining == AppState.TOTAL_NUMBERS) {
                statusText.text = "Введите числа от 0 до 36."
            } else {
                statusText.text = "Осталось ввести: $remaining"
            }
        }
        updateCombinedLineUI()
    }

    private fun createNumberView(number: Int, color: Int): TextView {
        // ... (код этой функции не меняется)
        return TextView(context).apply {
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
        val redColor = ContextCompat.getColor(context, R.color.red_line_color)
        val greenColor = ContextCompat.getColor(context, R.color.green_line_color)
        val addNumberViews = { list: List<Int>, color: Int -> list.forEach { num -> combinedLineLayout.addView(createNumberView(num, color)) } }
        if (AppState.isGameStarted) {
            // --- НОВАЯ ЛОГИКА ДЛЯ СЧЕТЧИКА ---
            val remainingCount = (AppState.red.size + AppState.green.size) - AppState.movesMadeAfterStart
            counterText.text = remainingCount.toString()
            counterText.visibility = View.VISIBLE
            // --- КОНЕЦ НОВОЙ ЛОГИКИ ---

            val barrier = TextView(context).apply {
                text = "|"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
            }
            if (AppState.movesMadeAfterStart in 1..AppState.RED_LINE_CAPACITY) {
                AppState.red.take(AppState.movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor)) }
                combinedLineLayout.addView(barrier)
                AppState.red.drop(AppState.movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor)) }
                addNumberViews(AppState.green, greenColor)
            } else if (AppState.movesMadeAfterStart > AppState.RED_LINE_CAPACITY) {
                addNumberViews(AppState.red, redColor)
                val playedOnGreen = AppState.movesMadeAfterStart - AppState.RED_LINE_CAPACITY
                AppState.green.take(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor)) }
                combinedLineLayout.addView(barrier)
                AppState.green.drop(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor)) }
            } else {
                addNumberViews(AppState.red, redColor); addNumberViews(AppState.green, greenColor)
            }
        } else {
            // --- НОВАЯ ЛОГИКА ДЛЯ СЧЕТЧИКА ---
            counterText.visibility = View.GONE // Скрываем счетчик, если игра не началась
            // --- КОНЕЦ НОВОЙ ЛОГИКИ ---
            addNumberViews(AppState.red, redColor); addNumberViews(AppState.green, greenColor)
        }
        lineScrollView.post { lineScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT) }
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}