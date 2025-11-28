package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.content.SharedPreferences

class AiSettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var INSTANCE: AiSettingsManager? = null

        fun getInstance(context: Context): AiSettingsManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: AiSettingsManager(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }
    }

    // AI endpoint URL
    var aiEndpoint: String
        get() = prefs.getString("ai_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("ai_endpoint", value).apply()

    // AI prompt for term form
    var aiPromptTerm: String
        get() =
                prefs.getString(
                        "ai_prompt_term",
                        "Using the sentence '{sentence}' Translate only the following term from {language} to English: {term}. Respond with the 2 most common translations"
                )
                        ?: "Using the sentence '{sentence}' Translate only the following term from {language} to English: {term}. Respond with the 2 most common translations"
        set(value) = prefs.edit().putString("ai_prompt_term", value).apply()

    // AI model name
    var aiModel: String
        get() = prefs.getString("ai_model", "") ?: ""
        set(value) = prefs.edit().putString("ai_model", value).apply()

    // AI prompt for sentence reader
    var aiPromptSentence: String
        get() =
                prefs.getString(
                        "ai_prompt_sentence",
                        "Translate the following sentence from {language} to English: {sentence}"
                )
                        ?: "Translate the following sentence from {language} to English: {sentence}"
        set(value) = prefs.edit().putString("ai_prompt_sentence", value).apply()

    // Show AI button in term form
    var showAiButtonTerm: Boolean
        get() = prefs.getBoolean("show_ai_button_term", true)
        set(value) = prefs.edit().putBoolean("show_ai_button_term", value).apply()

    // Show AI button in sentence reader
    var showAiButtonSentence: Boolean
        get() = prefs.getBoolean("show_ai_button_sentence", true)
        set(value) = prefs.edit().putBoolean("show_ai_button_sentence", value).apply()

    // AI API key
    var aiApiKey: String
        get() = prefs.getString("ai_api_key", "") ?: ""
        set(value) = prefs.edit().putString("ai_api_key", value).apply()

    // Check if AI settings are configured
    fun isAiConfigured(): Boolean {
        return aiEndpoint.isNotEmpty()
    }

    // Check if AI button should be shown in term form
    fun shouldShowAiButtonInTermForm(): Boolean {
        return showAiButtonTerm && isAiConfigured()
    }

    // Check if AI button should be shown in sentence reader
    fun shouldShowAiButtonInSentenceReader(): Boolean {
        return showAiButtonSentence && isAiConfigured()
    }
}
