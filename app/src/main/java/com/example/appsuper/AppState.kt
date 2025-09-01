package com.example.appsuper

import java.util.Collections


object AppState {

    interface StateListener {
        fun onStateChanged()
    }

    private val listeners = Collections.synchronizedSet(mutableSetOf<StateListener>())

    fun registerListener(listener: StateListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: StateListener) {
        listeners.remove(listener)
    }

    fun notifyListeners() {
        listeners.forEach { it.onStateChanged() }
    }

    val green = mutableListOf<Int>()
    val red = mutableListOf<Int>()
    val visibleSymbols = mutableListOf<Int>()

    const val GREEN_LINE_CAPACITY = 18
    const val RED_LINE_CAPACITY = 19
    const val TOTAL_NUMBERS = 37

    var isGameStarted = false
        set(value) {
            field = value
            if (value) {
                clearHistory()
                visibleSymbols.clear()
            }
        }

    var lastInputWasInRed: Boolean? = null
    var movesMadeAfterStart = 0

    data class GameStateSnapshot(
        val red: List<Int>,
        val green: List<Int>,
        val movesMadeAfterStart: Int,
        val lastInputWasInRed: Boolean?,
        val visibleSymbols: List<Int>
    )

    private val stateHistory = mutableListOf<GameStateSnapshot>()


    fun saveStateSnapshot() {
        val snapshot = GameStateSnapshot(
            red = red.toList(),
            green = green.toList(),
            movesMadeAfterStart = movesMadeAfterStart,
            lastInputWasInRed = lastInputWasInRed,
            visibleSymbols = visibleSymbols.toList()
        )
        stateHistory.add(snapshot)
    }

    fun rollbackLastState(): Boolean {
        if (stateHistory.isNotEmpty()) {
            val lastState = stateHistory.removeAt(stateHistory.lastIndex)
            red.clear()
            red.addAll(lastState.red)
            green.clear()
            green.addAll(lastState.green)
            movesMadeAfterStart = lastState.movesMadeAfterStart
            lastInputWasInRed = lastState.lastInputWasInRed
            visibleSymbols.clear()
            visibleSymbols.addAll(lastState.visibleSymbols)
            return true
        }
        return false
    }

    fun clearHistory() {
        stateHistory.clear()
    }
}