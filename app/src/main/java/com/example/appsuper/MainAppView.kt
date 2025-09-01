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


    private val statusText: TextView
    private val counterText: TextView
    private val combinedLineLayout: LinearLayout
    private val keypadContainer: FrameLayout
    private val inputDisplay: TextView
    private val lineScrollView: HorizontalScrollView
    private val toggleKeyboardButton: Button
    private val freezeButton: Button

    private val currentInput = StringBuilder()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_main_app, this, true)
        statusText = findViewById(R.id.statusText)
        counterText = findViewById(R.id.counter_text)
        combinedLineLayout = findViewById(R.id.combinedLine)
        keypadContainer = findViewById(R.id.keypad_container)
        inputDisplay = findViewById(R.id.input_display)
        lineScrollView = findViewById(R.id.line_scrollview)
        toggleKeyboardButton = findViewById(R.id.toggle_keyboard_button)
        freezeButton = findViewById(R.id.freeze_button)

        setupMainUI()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AppState.registerListener(this)
        onStateChanged()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AppState.unregisterListener(this)
    }

    override fun onStateChanged() {
        updateAllUI()
    }

    fun setListener(listener: AppViewListener) {
        this.listener = listener
    }

    fun setOverlayMode() {
        this.setBackgroundColor(Color.TRANSPARENT)
        lineScrollView.setBackgroundColor(Color.parseColor("#992C2C2E"))
    }

    private fun setupMainUI() {
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

        createPhoneKeypad()
        keypadContainer.visibility = View.VISIBLE
        lineScrollView.visibility = View.VISIBLE
    }

    private fun createPhoneKeypad() {
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
        }
    }

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
            AppState.notifyListeners()
        }
    }


    private fun handleRollbackClick() {
        if (AppState.rollbackLastState()) {
            listener?.onSendCommandWithList(AppConstants.ACTION_SET_VISIBILITY, ArrayList(AppState.visibleSymbols))

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

        AppState.saveStateSnapshot()

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

        val numbersToShow = if (AppState.lastInputWasInRed == isNumberInRed) {
            if (isNumberInRed) AppState.red else AppState.green
        } else {
            emptyList()
        }

        AppState.visibleSymbols.clear()
        AppState.visibleSymbols.addAll(numbersToShow)

        listener?.onSendCommandWithList(AppConstants.ACTION_SET_VISIBILITY, ArrayList(AppState.visibleSymbols))

        AppState.lastInputWasInRed = isNumberInRed
        AppState.notifyListeners()
    }

    private fun updateAllUI() {
        if (AppState.isGameStarted) {
            statusText.text = "Началось"
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

    private fun createNumberView(number: Int, color: Int, clickListener: ((Int) -> Unit)?): TextView {
        return TextView(context).apply {
            text = number.toString()
            setTextColor(color)
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(12, 4, 12, 4)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4, 0, 4, 0) }
            if (clickListener != null) {
                setOnClickListener { clickListener.invoke(number) }
            }
        }
    }

    private fun updateCombinedLineUI() {
        combinedLineLayout.removeAllViews()
        val redColor = ContextCompat.getColor(context, R.color.red_line_color)
        val greenColor = ContextCompat.getColor(context, R.color.green_line_color)

        if (AppState.isGameStarted) {
            val remainingCount = (AppState.red.size + AppState.green.size) - AppState.movesMadeAfterStart
            counterText.text = remainingCount.toString()
            counterText.visibility = View.VISIBLE

            val barrier = TextView(context).apply {
                text = "|"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 0, 8, 0) }
            }

            val rollbackListener: (Int) -> Unit = { handleRollbackClick() }
            val noOpListener: ((Int) -> Unit)? = null

            if (AppState.movesMadeAfterStart in 1..AppState.RED_LINE_CAPACITY) {
                AppState.red.take(AppState.movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor, rollbackListener)) }
                combinedLineLayout.addView(barrier)
                AppState.red.drop(AppState.movesMadeAfterStart).forEach { combinedLineLayout.addView(createNumberView(it, redColor, noOpListener)) }
                AppState.green.forEach { combinedLineLayout.addView(createNumberView(it, greenColor, noOpListener)) }
            } else if (AppState.movesMadeAfterStart > AppState.RED_LINE_CAPACITY) {
                AppState.red.forEach { combinedLineLayout.addView(createNumberView(it, redColor, rollbackListener)) }
                val playedOnGreen = AppState.movesMadeAfterStart - AppState.RED_LINE_CAPACITY
                AppState.green.take(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor, rollbackListener)) }
                combinedLineLayout.addView(barrier)
                AppState.green.drop(playedOnGreen).forEach { combinedLineLayout.addView(createNumberView(it, greenColor, noOpListener)) }
            } else { // movesMadeAfterStart == 0
                AppState.red.forEach { combinedLineLayout.addView(createNumberView(it, redColor, noOpListener)) }
                AppState.green.forEach { combinedLineLayout.addView(createNumberView(it, greenColor, noOpListener)) }
            }
        } else {
            counterText.visibility = View.GONE
            val deleteListener: (Int) -> Unit = { handleDeleteClick(it) }
            AppState.red.forEach { combinedLineLayout.addView(createNumberView(it, redColor, deleteListener)) }
            AppState.green.forEach { combinedLineLayout.addView(createNumberView(it, greenColor, deleteListener)) }
        }
        lineScrollView.post { lineScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT) }
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}