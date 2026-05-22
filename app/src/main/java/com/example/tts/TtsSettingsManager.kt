package com.example.tts

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object TtsSettingsManager {
    private const val PREFS_NAME = "tts_settings_prefs"
    private const val KEY_USE_GEMINI = "use_gemini"
    private const val KEY_VOICE_NAME = "voice_name"
    private const val KEY_ROBOT_PITCH = "robot_pitch"
    private const val KEY_ROBOT_SPEED = "robot_speed"
    private const val KEY_ROBOT_STYLE = "robot_style"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_SPEECH_LOGS = "speech_logs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isUseGemini(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_GEMINI, false)
    }

    fun setUseGemini(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_GEMINI, value).apply()
    }

    fun getVoiceName(context: Context): String {
        return getPrefs(context).getString(KEY_VOICE_NAME, "Kore") ?: "Kore"
    }

    fun setVoiceName(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_VOICE_NAME, value).apply()
    }

    fun getRobotPitch(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ROBOT_PITCH, 1.0f)
    }

    fun setRobotPitch(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ROBOT_PITCH, value).apply()
    }

    fun getRobotSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ROBOT_SPEED, 1.0f)
    }

    fun setRobotSpeed(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ROBOT_SPEED, value).apply()
    }

    fun getRobotStyle(context: Context): String {
        return getPrefs(context).getString(KEY_ROBOT_STYLE, "Robô Clássico") ?: "Robô Clássico"
    }

    fun setRobotStyle(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_ROBOT_STYLE, value).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun setGeminiApiKey(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, value).apply()
    }

    fun addSpeechLog(context: Context, text: String, engine: String) {
        val list = getSpeechLogs(context).toMutableList()
        val timestamp = System.currentTimeMillis()
        list.add(0, SpeechLogItem(text, engine, timestamp))
        if (list.size > 30) {
            list.removeAt(list.size - 1)
        }
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        getPrefs(context).edit().putString(KEY_SPEECH_LOGS, array.toString()).apply()
    }

    fun getSpeechLogs(context: Context): List<SpeechLogItem> {
        val jsonStr = getPrefs(context).getString(KEY_SPEECH_LOGS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SpeechLogItem>()
            for (i in 0 until array.length()) {
                list.add(SpeechLogItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearSpeechLogs(context: Context) {
        getPrefs(context).edit().remove(KEY_SPEECH_LOGS).apply()
    }
}

data class SpeechLogItem(val text: String, val engine: String, val timestamp: Long) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("text", text)
        obj.put("engine", engine)
        obj.put("timestamp", timestamp)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): SpeechLogItem {
            return SpeechLogItem(
                obj.getString("text"),
                obj.getString("engine"),
                obj.getLong("timestamp")
            )
        }
    }
}
