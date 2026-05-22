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

    fun fetchSpeech(text: String, apiKey: String, voiceName: String, modelName: String = "gemini-2.5-flash-preview-tts"): ByteArray? {
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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API HTTP Error ${response.code}: $errBody")
                    var friendlyError = "Erro HTTP ${response.code}"
                    try {
                        val jsonObj = JSONObject(errBody)
                        val errObj = jsonObj.optJSONObject("error")
                        if (errObj != null) {
                            val msg = errObj.optString("message")
                            if (!msg.isNullOrBlank()) {
                                friendlyError = msg
                            }
                        }
                    } catch (e: Exception) {
                        if (errBody.isNotEmpty()) friendlyError = errBody
                    }
                    throw Exception(friendlyError)
                }
                val bodyString = response.body?.string() ?: throw Exception("Corpo da resposta vazio da API")
                Log.d(TAG, "Response JSON: $bodyString")
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    val promptFeedback = responseJson.optJSONObject("promptFeedback")
                    val blockReason = promptFeedback?.optString("blockReason")
                    if (!blockReason.isNullOrEmpty()) {
                        throw Exception("Resposta bloqueada por segurança: $blockReason")
                    }
                    throw Exception("Nenhum candidato retornado pela API. Resposta: $bodyString")
                }
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content") ?: throw Exception("Objeto 'content' ausente no candidato")
                val parts = content.optJSONArray("parts") ?: throw Exception("Objeto 'parts' ausente no conteúdo")
 
                var returnedTextReason = ""
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
                    } else {
                        val txt = part.optString("text")
                        if (!txt.isNullOrEmpty()) {
                            returnedTextReason += txt
                        }
                    }
                }

                if (returnedTextReason.isNotEmpty()) {
                    throw Exception("Modelo respondeu com texto em vez de áudio: $returnedTextReason")
                } else {
                    throw Exception("Nenhum dado de áudio retornado na parte inlineData")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API network request failed: ${e.message}", e)
            throw e
        }
    }
}
