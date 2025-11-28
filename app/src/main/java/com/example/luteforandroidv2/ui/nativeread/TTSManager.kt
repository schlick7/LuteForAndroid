package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentUtteranceId: String? = null

    // TTS configuration properties
    private var speechRate: Float = 1.0f // 1.0 = normal speed
    private var pitch: Float = 1.0f // 1.0 = normal pitch
    private var voiceName: String? = null // Voice name to use

    // Callback interface for TTS state changes
    interface TTSListener {
        fun onTTSStateChanged(isPlaying: Boolean)
    }

    private var ttsListener: TTSListener? = null

    fun setTTSListener(listener: TTSListener?) {
        this.ttsListener = listener
    }

    fun initializeTTS(initCallback: (Boolean) -> Unit) {
        tts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        // Set language to system default initially
                        val result = tts?.setLanguage(Locale.getDefault())
                        isInitialized =
                                (result != TextToSpeech.LANG_MISSING_DATA &&
                                        result != TextToSpeech.LANG_NOT_SUPPORTED)

                        if (isInitialized) {
                            // Apply current settings after initialization
                            applyCurrentSettings()
                        }

                        // Set up progress listener to handle state changes
                        tts?.setOnUtteranceProgressListener(
                                object : UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {
                                        ttsListener?.onTTSStateChanged(true)
                                    }

                                    override fun onDone(utteranceId: String?) {
                                        ttsListener?.onTTSStateChanged(false)
                                    }

                                    override fun onError(utteranceId: String?) {
                                        ttsListener?.onTTSStateChanged(false)
                                    }
                                }
                        )

                        initCallback(isInitialized)
                    } else {
                        isInitialized = false
                        initCallback(false)
                    }
                }
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) return

        // Apply current settings before speaking
        applyCurrentSettings()

        currentUtteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentUtteranceId)
    }

    fun pause() {
        if (!isInitialized || tts == null) return

        // Note: TextToSpeech doesn't have a true pause functionality
        // We'll stop the current utterance instead
        tts?.stop()
        ttsListener?.onTTSStateChanged(false)
    }

    fun stop() {
        if (!isInitialized || tts == null) return

        tts?.stop()
        ttsListener?.onTTSStateChanged(false)
    }

    fun isSpeaking(): Boolean {
        return isInitialized && tts != null && tts?.isSpeaking == true
    }

    fun setLanguage(languageCode: String): Boolean {
        if (!isInitialized || tts == null) return false

        val locale = getLocaleForLanguageCode(languageCode)
        val result = tts?.setLanguage(locale)

        // Preserve voice selection if possible after setting language
        if (voiceName != null) {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            }
        }

        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun setLanguageForBook(languageName: String): Boolean {
        if (!isInitialized || tts == null) return false

        val locale = mapLanguageNameToLocale(languageName)
        val result = tts?.setLanguage(locale)

        // Preserve voice selection if possible after setting language
        if (voiceName != null) {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            }
        }

        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun mapLanguageNameToLocale(languageName: String): Locale {
        // First try to convert common language names to standard language codes
        val standardCode =
                when (languageName.lowercase().trim()) {
                    "english", "eng", "en_us", "en-gb", "en-au", "en-ca" -> "en"
                    "spanish", "español", "spa" -> "es"
                    "french", "français", "fra" -> "fr"
                    "german", "deutsch", "deu" -> "de"
                    "italian", "italiano", "ita" -> "it"
                    "portuguese", "português", "por" -> "pt"
                    "russian", "русский", "rus" -> "ru"
                    "chinese", "中文", "chinese simplified", "simplified chinese", "zho", "chi" ->
                            "zh"
                    "chinese traditional", "zh-hant", "繁體中文" -> "zh-Hant"
                    "japanese", "日本語", "jpn" -> "ja"
                    "korean", "한국어", "kor" -> "ko"
                    "arabic", "العربية", "ara" -> "ar"
                    "hindi", "हिन्दी", "hin" -> "hi"
                    "bengali", "বাংলা", "ben" -> "bn"
                    "punjabi", "ਪੰਜਾਬੀ", "pan" -> "pa"
                    "urdu", "اردو", "urd" -> "ur"
                    "turkish", "türkçe", "tur" -> "tr"
                    "dutch", "nederlands", "nld" -> "nl"
                    "swedish", "svenska", "swe" -> "sv"
                    "norwegian", "norsk", "nor" -> "no"
                    "danish", "dansk", "dan" -> "da"
                    "finnish", "suomi", "fin" -> "fi"
                    "polish", "polski", "pol" -> "pl"
                    "czech", "čeština", "ces" -> "cs"
                    "greek", "ελληνικά", "ell" -> "el"
                    "hebrew", "עברית", "heb" -> "he"
                    "thai", "ไทย", "tha" -> "th"
                    "vietnamese", "tiếng việt", "vie" -> "vi"
                    "indonesian", "bahasa indonesia", "ind" -> "id"
                    "malay", "bahasa melayu", "msa" -> "ms"
                    else -> languageName.lowercase().trim()
                }

        // Use Locale.forLanguageTag to convert the standard language code to a Locale
        val locale = Locale.forLanguageTag(standardCode)

        // If the locale has a valid language, use it; otherwise, return the default locale
        return if (!locale.language.isNullOrEmpty() && locale.language != "und") {
            locale
        } else {
            // Fallback to the original code-based mapping if language tag doesn't work
            fallbackLanguageMapping(standardCode)
        }
    }

    private fun fallbackLanguageMapping(languageCode: String): Locale {
        return when (languageCode.lowercase().trim()) {
            "en" -> Locale.ENGLISH
            "es" -> Locale("es")
            "fr" -> Locale("fr")
            "de" -> Locale("de")
            "it" -> Locale("it")
            "pt" -> Locale("pt")
            "ru" -> Locale("ru")
            "ja" -> Locale("ja")
            "ko" -> Locale("ko")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "zh-hant" -> Locale.TRADITIONAL_CHINESE
            "ar" -> Locale("ar")
            "hi" -> Locale("hi")
            "bn" -> Locale("bn")
            "pa" -> Locale("pa")
            "ur" -> Locale("ur")
            "tr" -> Locale("tr")
            "nl" -> Locale("nl")
            "sv" -> Locale("sv")
            "no" -> Locale("no")
            "da" -> Locale("da")
            "fi" -> Locale("fi")
            "pl" -> Locale("pl")
            "cs" -> Locale("cs")
            "el" -> Locale("el")
            "he" -> Locale("he")
            "th" -> Locale("th")
            "vi" -> Locale("vi")
            "id" -> Locale("id")
            "ms" -> Locale("ms")
            else -> Locale.getDefault()
        }
    }

    private fun getLocaleForLanguageCode(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "en" -> Locale.ENGLISH
            "es" -> Locale("es")
            "fr" -> Locale("fr")
            "de" -> Locale("de")
            "it" -> Locale("it")
            "ja" -> Locale("ja")
            "ko" -> Locale("ko")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ru" -> Locale("ru")
            "pt" -> Locale("pt")
            "ar" -> Locale("ar")
            else -> Locale.getDefault()
        }
    }

    fun shutdown() {
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        isInitialized = false
    }

    // Get and set speech rate (0.5 to 2.0)
    fun setSpeechRate(rate: Float): Boolean {
        if (!isInitialized || tts == null) return false

        speechRate = rate.coerceIn(0.5f, 2.0f) // Limit between 0.5 and 2.0
        tts?.setSpeechRate(speechRate)
        return true
    }

    fun getSpeechRate(): Float {
        return speechRate
    }

    // Get and set pitch (0.8 to 1.2)
    fun setPitch(pitchValue: Float): Boolean {
        if (!isInitialized || tts == null) return false

        pitch = pitchValue.coerceIn(0.8f, 1.2f) // Limit between 0.8 and 1.2
        tts?.setPitch(pitch)
        return true
    }

    fun getPitch(): Float {
        return pitch
    }

    /** Get available voices from TTS engine */
    fun getAvailableVoices(): List<String> {
        if (!isInitialized || tts == null) return emptyList()

        val voices = mutableListOf<String>()
        tts?.voices?.forEach { voice -> voices.add(voice.name) }
        return voices
    }

    /** Set voice by name */
    fun setVoice(voiceName: String): Boolean {
        if (!isInitialized || tts == null) return false

        this.voiceName = voiceName
        val selectedVoice = tts?.voices?.find { it.name == voiceName }
        return if (selectedVoice != null) {
            tts?.voice = selectedVoice
            true
        } else {
            false
        }
    }

    fun getVoice(): String? {
        return if (isInitialized && tts != null) {
            tts?.voice?.name
        } else {
            voiceName
        }
    }

    // Method to apply all settings to the current TTS instance
    fun applyCurrentSettings() {
        if (!isInitialized || tts == null) return

        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        if (voiceName != null) {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            }
        }
    }

    companion object {
        @Volatile private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }
}
