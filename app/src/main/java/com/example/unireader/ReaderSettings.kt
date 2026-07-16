package com.example.unireader

import android.content.Context
import androidx.core.content.edit

data class ReaderSettings(
    var fontFamily: String = "sans-serif",
    var fontSize: Int = 18,
    var isItalic: Boolean = false,
    var isBold: Boolean = false,
    var paragraphSpacing: Float = 1.0f,
    var lineHeight: Float = 1.6f,
    var firstLineIndent: Float = 1.5f,
    var columnGap: Int = 0,
    var paddingLeft: Int = 10,
    var paddingRight: Int = 10,
    var paddingTop: Int = 0,
    var paddingBottom: Int = 0,
    var isDarkMode: Boolean = false,
    var isPagedMode: Boolean = true,
    var isFullscreen: Boolean = false,
    var brightness: Float = -1.0f
) {
    companion object {
        private const val PREFS_NAME = "reader_settings"
        fun load(context: Context): ReaderSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ReaderSettings(
                fontFamily = prefs.getString("fontFamily", "sans-serif") ?: "sans-serif",
                fontSize = prefs.getInt("fontSize", 18),
                isItalic = prefs.getBoolean("isItalic", false),
                isBold = prefs.getBoolean("isBold", false),
                paragraphSpacing = prefs.getFloat("paragraphSpacing", 1.0f),
                lineHeight = prefs.getFloat("lineHeight", 1.6f),
                firstLineIndent = prefs.getFloat("firstLineIndent", 1.5f),
                columnGap = prefs.getInt("columnGap", 0),
                paddingLeft = prefs.getInt("paddingLeft", 10),
                paddingRight = prefs.getInt("paddingRight", 10),
                paddingTop = prefs.getInt("paddingTop", 0),
                paddingBottom = prefs.getInt("paddingBottom", 0),
                isDarkMode = prefs.getBoolean("isDarkMode", false),
                isPagedMode = prefs.getBoolean("isPagedMode", true),
                isFullscreen = prefs.getBoolean("isFullscreen", false),
                brightness = prefs.getFloat("brightness", -1.0f)
            )
        }
    }
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString("fontFamily", fontFamily)
            putInt("fontSize", fontSize)
            putBoolean("isItalic", isItalic)
            putBoolean("isBold", isBold)
            putFloat("paragraphSpacing", paragraphSpacing)
            putFloat("lineHeight", lineHeight)
            putFloat("firstLineIndent", firstLineIndent)
            putInt("columnGap", columnGap)
            putInt("paddingLeft", paddingLeft)
            putInt("paddingRight", paddingRight)
            putInt("paddingTop", paddingTop)
            putInt("paddingBottom", paddingBottom)
            putBoolean("isDarkMode", isDarkMode)
            putBoolean("isPagedMode", isPagedMode)
            putBoolean("isFullscreen", isFullscreen)
            putFloat("brightness", brightness)
        }
    }
}
