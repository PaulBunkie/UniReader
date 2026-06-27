package com.example.unireader

import android.content.Context

data class ReaderSettings(
    var fontFamily: String = "sans-serif",
    var fontSize: Int = 18,
    var isItalic: Boolean = false,
    var isBold: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "reader_settings"
        fun load(context: Context): ReaderSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ReaderSettings(
                fontFamily = prefs.getString("fontFamily", "sans-serif") ?: "sans-serif",
                fontSize = prefs.getInt("fontSize", 18),
                isItalic = prefs.getBoolean("isItalic", false),
                isBold = prefs.getBoolean("isBold", false)
            )
        }
    }
    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("fontFamily", fontFamily)
            putInt("fontSize", fontSize)
            putBoolean("isItalic", isItalic)
            putBoolean("isBold", isBold)
            apply()
        }
    }
}
