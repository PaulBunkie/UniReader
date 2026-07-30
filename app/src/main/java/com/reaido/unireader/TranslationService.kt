package com.reaido.unireader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChapterTranslationResponse(
    val xhtml: String,
    val glossaryJson: String,
    val model: String? = null
)

class TranslationService(private val context: Context, private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translateTOC(text: String, targetLanguage: String = "russian"): String? {
        try {
            val body = JSONObject()
                .put("text", text)
                .put("target_language", targetLanguage)
            
            val request = Request.Builder()
                .url("$baseUrl/translate/toc")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                DebugLogger.log("API", "TOC Error: ${response.code} -> $responseBody")
                throw Exception("${context.getString(R.string.error_toc_request_failed, response.code)}: $responseBody")
            }

            val taskData = JSONObject(responseBody)
            val taskId = taskData.getString("task_id")
            DebugLogger.log("API", "TOC Task: $taskId")

            val result = pollTask(taskId, "/translate") ?: return null
            DebugLogger.log("API", "TOC Success")
            return result.optString("result")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun translateChapter(
        text: String,
        glossary: JSONObject?,
        userCorrections: JSONArray?,
        bookId: String,
        sectionId: Int,
        targetLanguage: String = "russian"
    ): ChapterTranslationResponse? { 
        try {
            val body = JSONObject()
                .put("text", text)
                .put("target_language", targetLanguage)
                .put("book_id", bookId)
                .put("section_id", sectionId)

            if (glossary != null && glossary.length() > 0) {
                body.put("glossary", glossary)
            }
            if (userCorrections != null && userCorrections.length() > 0) {
                body.put("user_corrections", userCorrections)
            }

            val request = Request.Builder()
                .url("$baseUrl/translate")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            Log.d("TranslationService", "Sending request to /translate with book_id: $bookId, section: $sectionId")
            DebugLogger.log("API", "REQ ch $sectionId: ${body.toString().take(200)}...")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("TranslationService", "Request failed with code: ${response.code}, body: $responseBody")
                DebugLogger.log("API", "ERR ch $sectionId: ${response.code} -> $responseBody")
                throw Exception("${context.getString(R.string.error_api_request_failed, response.code)}: $responseBody")
            }

            val taskData = JSONObject(responseBody)
            val taskId = taskData.getString("task_id")
            DebugLogger.log("API", "TASK ch $sectionId: $taskId")

            val result = pollTask(taskId, "/translate") ?: run {
                // If pollTask returns null, it means it already logged a status error or network fail
                return null
            }
            
            val model = result.optString("model")
            val newGlossaryObj = result.optJSONObject("glossary") ?: glossary ?: JSONObject()
            
            DebugLogger.log("API", "OK ch $sectionId [${model ?: "unknown"}]")
            
            return ChapterTranslationResponse(
                xhtml = result.getString("result"),
                glossaryJson = newGlossaryObj.toString(),
                model = model
            )
        } catch (e: Exception) {
            Log.e("TranslationService", "Error in translateChapter", e)
            DebugLogger.log("API", "EXCEPTION ch $sectionId: ${e.message}")
            return null
        }
    }

    private suspend fun pollTask(taskId: String, endpoint: String): JSONObject? {
        while (true) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$endpoint/$taskId")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    DebugLogger.log("API", "POLL HTTP ERR: ${response.code} -> $responseBody")
                    throw Exception("${context.getString(R.string.error_polling_failed, response.code)}: $responseBody")
                }

                val data = JSONObject(responseBody)
                val status = data.getString("status")

                if (status == "completed") return data
                if (status == "error") {
                    val errMsg = data.optString("error", context.getString(R.string.unknown_server_error))
                    DebugLogger.log("API", "STATUS ERR: $errMsg")
                    throw Exception(errMsg)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DebugLogger.log("API", "POLL FAIL: ${e.message}")
                throw e
            }

            delay(1000)
        }
    }
}
