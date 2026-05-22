package com.example.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

class CheckVoiceDataActivity : Activity() {
    private val tag = "CheckVoiceDataActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val action = intent?.action
        Log.i(tag, "Received intent action: $action")
        
        if (action == TextToSpeech.Engine.ACTION_CHECK_TTS_DATA) {
            val returnIntent = Intent()
            
            // Available languages
            val availableLanguages = ArrayList<String>().apply {
                add("pt-BR")
                add("por-BRA")
                add("en-US")
                add("eng-USA")
            }
            
            // Unavailable/uninstalled languages
            val unavailableLanguages = ArrayList<String>()
            
            // Key extras required by TextToSpeechSettings:
            returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableLanguages)
            returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableLanguages)
            
            // Return status pass
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnIntent)
        } else if (action == TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT) {
            val returnIntent = Intent()
            
            val language = intent?.getStringExtra("language") ?: ""
            Log.i(tag, "Requested sample text language: $language")
            
            val sampleText = if (language.lowercase().startsWith("pt") || language.lowercase().startsWith("por")) {
                "Isto é um exemplo de síntese de voz usando Voz Viva."
            } else {
                "This is an example of speech synthesis using Voz Viva."
            }
            
            returnIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
            setResult(RESULT_OK, returnIntent)
        } else {
            setResult(RESULT_CANCELED)
        }
        
        finish()
    }
}
