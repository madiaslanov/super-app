package com.example.appsuper

/**
 * An object to hold constants shared across different parts of the app,
 * like Activity and Service, to avoid direct dependencies.
 */
object AppConstants {
    // --- Broadcast Actions ---
    const val ACTION_LOCK_ALL = "com.example.appsuper.ACTION_LOCK_ALL_OVERLAYS"
    const val ACTION_ALL_NUMBERS_RECEIVED = "com.example.appsuper.ACTION_ALL_NUMBERS_RECEIVED"

    // --- Network Commands ---
    // A special byte sent periodically to keep the TCP connection alive.
    // Value is 255 (or -1 as a signed Byte).
    const val KEEP_ALIVE_BYTE: Byte = -1
}