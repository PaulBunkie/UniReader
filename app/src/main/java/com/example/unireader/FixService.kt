package com.example.unireader

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class FixService {
    private val client = OkHttpClient()
    private val baseUrl = "http://136.109.52.87:8080/api"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun improveText(
        text: String,
        context: String? = null,
        hotpoints: List<String>? = null,
        onStatusUpdate: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 1. POST /api/improve
            val postJson = JSONObject().put("text", text)
            if (!context.isNullOrEmpty()) postJson.put("context", context)
            if (!hotpoints.isNullOrEmpty()) {
                val hpArray = org.json.JSONArray()
                hotpoints.forEach { hpArray.put(it) }
                postJson.put("hotpoints", hpArray)
            }
            
            val postBody = postJson.toString()
            val postRequest = Request.Builder()
                .url("$baseUrl/improve")
                .post(postBody.toRequestBody(jsonMediaType))
                .build()

            val postResponse = client.newCall(postRequest).execute()
            if (!postResponse.isSuccessful) {
                onError("Ошибка создания задачи: ${postResponse.code}")
                return
            }

            val taskData = JSONObject(postResponse.body?.string() ?: "")
            val taskId = taskData.getString("task_id")

            // 2. Polling GET /api/improve/<task_id>
            while (true) {
                val getRequest = Request.Builder()
                    .url("$baseUrl/improve/$taskId")
                    .get()
                    .build()

                val getResponse = client.newCall(getRequest).execute()
                if (!getResponse.isSuccessful) {
                    if (getResponse.code == 404) {
                        onError("Задача не найдена (404)")
                    } else {
                        onError("Ошибка сервера: ${getResponse.code}")
                    }
                    return
                }

                val statusData = JSONObject(getResponse.body?.string() ?: "")
                val status = statusData.getString("status")

                when (status) {
                    "completed" -> {
                        onSuccess(statusData.getString("result"))
                        return
                    }
                    "error" -> {
                        onError(statusData.optString("error", "Неизвестная ошибка модели"))
                        return
                    }
                    "processing" -> {
                        onStatusUpdate("Обработка...")
                    }
                }

                delay(1500) // Poll every 1.5 seconds
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Сетевая ошибка: ${e.message}")
        }
    }
}
