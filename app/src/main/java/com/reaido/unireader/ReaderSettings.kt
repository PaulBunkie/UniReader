package com.reaido.unireader

import android.content.Context
import androidx.core.content.edit

data class ReaderSettings(
    var fontFamily: String = "sans-serif",
    var fontSize: Int = 18,
    var isItalic: Boolean = false,
    var isBold: Boolean = false,
    var paragraphSpacing: Float = 0.4f,
    var lineHeight: Float = 1.4f,
    var firstLineIndent: Float = 0.5f,
    var columnGap: Int = 2,
    var paddingLeft: Int = 10,
    var paddingRight: Int = 9,
    var paddingTop: Int = 0,
    var paddingBottom: Int = 0,
    var isDarkMode: Boolean = false,
    var isPagedMode: Boolean = true,
    var isFullscreen: Boolean = false,
    var brightness: Float = -1.0f,
    var isProdApi: Boolean = true,
    var keepScreenOn: Boolean = false,
    var targetLanguage: String = "Russian"
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
                paragraphSpacing = prefs.getFloat("paragraphSpacing", 0.4f),
                lineHeight = prefs.getFloat("lineHeight", 1.4f),
                firstLineIndent = prefs.getFloat("firstLineIndent", 0.5f),
                columnGap = prefs.getInt("columnGap", 2),
                paddingLeft = prefs.getInt("paddingLeft", 10),
                paddingRight = prefs.getInt("paddingRight", 9),
                paddingTop = prefs.getInt("paddingTop", 0),
                paddingBottom = prefs.getInt("paddingBottom", 0),
                isDarkMode = prefs.getBoolean("isDarkMode", false),
                isPagedMode = prefs.getBoolean("isPagedMode", true),
                isFullscreen = prefs.getBoolean("isFullscreen", false),
                brightness = prefs.getFloat("brightness", -1.0f),
                isProdApi = prefs.getBoolean("isProdApi", true),
                keepScreenOn = prefs.getBoolean("keepScreenOn", false),
                targetLanguage = prefs.getString("targetLanguage", "Russian") ?: "Russian"
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
            putBoolean("isProdApi", isProdApi)
            putBoolean("keepScreenOn", keepScreenOn)
            putString("targetLanguage", targetLanguage)
        }
    }
}
