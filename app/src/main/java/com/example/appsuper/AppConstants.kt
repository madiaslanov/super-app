package com.example.appsuper

/**
 * Объект для хранения констант (действий Intent) для связи между Activity и Service.
 */
object AppConstants {
    // --- Действия для управления OverlayService ---
    const val ACTION_SHOW_SYMBOL = "com.example.appsuper.ACTION_SHOW_SYMBOL"
    const val ACTION_DELETE_SYMBOL = "com.example.appsuper.ACTION_DELETE_SYMBOL"
    const val ACTION_REMOVE_ALL_SYMBOLS = "com.example.appsuper.ACTION_REMOVE_ALL_SYMBOLS"
    const val ACTION_HIDE_ALL_SYMBOLS = "com.example.appsuper.ACTION_HIDE_ALL_SYMBOLS"
    const val ACTION_SET_VISIBILITY = "com.example.appsuper.ACTION_SET_VISIBILITY"
    const val ACTION_FREEZE_ALL = "com.example.appsuper.ACTION_FREEZE_ALL" // НОВАЯ КОНСТАНТА

    // --- Ключи для передачи данных в Intent ---
    const val EXTRA_NUMBER = "com.example.appsuper.EXTRA_NUMBER"
    const val EXTRA_VISIBLE_LIST = "com.example.appsuper.EXTRA_VISIBLE_LIST"
}