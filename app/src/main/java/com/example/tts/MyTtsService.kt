package com.example.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MyTtsService : TextToSpeechService() {
    private val tag = "MyTtsService"
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var currentLanguage = arrayOf("por", "BRA", "")

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Serviço TTS Criado")
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        Log.i(tag, "Serviço TTS Destruído")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrBlank()) return TextToSpeech.LANG_NOT_SUPPORTED
        val lowerLang = lang.trim().lowercase(Locale.ROOT)
        return if (lowerLang == "por" || lowerLang == "pt" || lowerLang == "eng" || lowerLang == "en") {
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        return currentLanguage
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE || result == TextToSpeech.LANG_AVAILABLE) {
            val normalLang = lang ?: "por"
            val normalCountry = country ?: "BRA"
            val normalVariant = variant ?: ""
            currentLanguage = arrayOf(normalLang, normalCountry, normalVariant)
        }
        return result
    }

    override fun onStop() {
        Log.i(tag, "Interrompendo síntese de áudio ativa")
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val textToSpeak = request.text ?: ""
        if (textToSpeak.trim().isEmpty()) {
            callback.start(16000, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val customApiKey = TtsSettingsManager.getGeminiApiKey(this)
        val voiceName = TtsSettingsManager.getVoiceName(this)

        runBlocking {
            try {
                val apiKey = customApiKey.trim()
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    Log.w(tag, "Chave de API do Gemini não configurada pelo usuário")
                    TtsSettingsManager.addSpeechLog(applicationContext, textToSpeak, "Erro: Chave API Ausente")
                    callback.error()
                } else {
                    TtsSettingsManager.addSpeechLog(applicationContext, textToSpeak, "Gemini AI ($voiceName)")
                    val audioBytes = GeminiApiClient.fetchSpeech(textToSpeak, apiKey, voiceName)
                    if (audioBytes != null) {
                        val decoded = AudioDecoder.decodeToPcm(audioBytes, cacheDir)
                        if (decoded != null) {
                            // Decoded successfully, feed it to the synthesis pipeline
                            callback.start(decoded.sampleRate, android.media.AudioFormat.ENCODING_PCM_16BIT, decoded.channelCount)
                            callback.audioAvailable(decoded.pcmData, 0, decoded.pcmData.size)
                            callback.done()
                        } else {
                            Log.e(tag, "Erro ao decodificar a voz do Gemini")
                            TtsSettingsManager.addSpeechLog(applicationContext, textToSpeak, "Erro: Falha na Decodificação")
                            callback.error()
                        }
                    } else {
                        Log.e(tag, "Retorno da API Gemini nulo")
                        TtsSettingsManager.addSpeechLog(applicationContext, textToSpeak, "Erro: Falha na API")
                        callback.error()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro na síntese Gemini: ${e.message}", e)
                TtsSettingsManager.addSpeechLog(applicationContext, textToSpeak, "Erro: ${e.message}")
                callback.error()
            }
        }
    }
}
