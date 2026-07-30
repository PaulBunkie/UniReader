package com.reaido.unireader

import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {
    private val logs = mutableListOf<String>()
    var onLogUpdate: ((String) -> Unit)? = null

    fun log(tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$time] $tag: $msg"
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > 200) logs.removeAt(0)
            val fullText = logs.joinToString("\n")
            onLogUpdate?.invoke(fullText)
        }
    }
}
