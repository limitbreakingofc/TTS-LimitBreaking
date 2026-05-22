package com.example.tts

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"

    fun fetchSpeech(text: String, apiKey: String, voiceName: String): ByteArray? {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Diga exatamente em português: $text")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API HTTP Error ${response.code}: ${response.message}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
                val parts = content.optJSONArray("parts") ?: return null

                for (p in 0 until parts.length()) {
                    val part = parts.getJSONObject(p)
                    val inlineData = part.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val mimeType = inlineData.optString("mimeType", "")
                        if (mimeType.contains("audio", ignoreCase = true)) {
                            val base64Data = inlineData.optString("data", "")
                            if (base64Data.isNotEmpty()) {
                                return Base64.decode(base64Data, Base64.NO_WRAP)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API network request failed: ${e.message}", e)
        }
        return null
    }
}
