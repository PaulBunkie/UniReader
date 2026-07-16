package com.example.unireader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class UniReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Загружаем настройки и применяем тему ДО создания любого Activity
        val settings = ReaderSettings.load(this)
        val mode = if (settings.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
                   else AppCompatDelegate.MODE_NIGHT_NO
        
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
