package com.example.appsuper

import java.util.Collections

/**
 * Синглтон для хранения состояния всего приложения.
 * Это "единый источник правды" для MainActivity и OverlayService.
 */
object AppState {

    // --- Интерфейс для оповещения об изменениях ---
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

    // --- Состояние приложения ---
    val green = mutableListOf<Int>()
    val red = mutableListOf<Int>()
    const val GREEN_LINE_CAPACITY = 18
    const val RED_LINE_CAPACITY = 19
    const val TOTAL_NUMBERS = 37
    var isGameStarted = false
    var lastInputWasInRed: Boolean? = null
    var movesMadeAfterStart = 0
}