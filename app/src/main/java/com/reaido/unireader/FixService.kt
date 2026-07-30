package com.reaido.unireader

import android.content.Context
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class FixService(private val context: Context, private val baseUrl: String) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun improveText(
        text: String,
        contextText: String? = null,
        hotpoints: List<String>? = null,
        onStatusUpdate: (String) -> Unit,
        onSuccess: (String, String?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 1. POST /api/improve
            val postJson = JSONObject().put("text", text)
            if (!contextText.isNullOrEmpty()) postJson.put("context", contextText)
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

            DebugLogger.log("FIX", "REQ: ${text.take(50)}...")
            val postResponse = client.newCall(postRequest).execute()
            if (!postResponse.isSuccessful) {
                DebugLogger.log("FIX", "ERR: ${postResponse.code}")
                onError(context.getString(R.string.error_creating_task, postResponse.code))
                return
            }

            val taskData = JSONObject(postResponse.body?.string() ?: "")
            val taskId = taskData.getString("task_id")
            DebugLogger.log("FIX", "TASK: $taskId")

            // 2. Polling GET /api/improve/<task_id>
            while (true) {
                val getRequest = Request.Builder()
                    .url("$baseUrl/improve/$taskId")
                    .get()
                    .build()

                val getResponse = client.newCall(getRequest).execute()
                if (!getResponse.isSuccessful) {
                    if (getResponse.code == 404) {
                        onError(context.getString(R.string.task_not_found))
                    } else {
                        onError(context.getString(R.string.server_error, getResponse.code))
                    }
                    return
                }

                val statusData = JSONObject(getResponse.body?.string() ?: "")
                val status = statusData.getString("status")

                when (status) {
                    "completed" -> {
                        DebugLogger.log("FIX", "OK: ${statusData.getString("result").take(50)}...")
                        onSuccess(statusData.getString("result"), statusData.optString("model"))
                        return
                    }
                    "error" -> {
                        DebugLogger.log("FIX", "ERR: ${statusData.optString("error")}")
                        onError(statusData.optString("error", context.getString(R.string.unknown_model_error)))
                        return
                    }
                    "processing" -> {
                        onStatusUpdate(context.getString(R.string.processing))
                    }
                }

                delay(1500) // Poll every 1.5 seconds
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onError(context.getString(R.string.network_error, e.message ?: ""))
        }
    }
}
