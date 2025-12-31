package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.FragmentAppSettingsBinding
import com.example.luteforandroidv2.theme.updateThemeFromServer
import com.example.luteforandroidv2.ui.nativeread.TTSManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup

class AppSettingsFragment : Fragment() {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    // Dynamic views for collapsible sections
    private lateinit var serverUrlEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var saveUrlButton: Button
    private lateinit var languageSpinner: Spinner
    private lateinit var visibilitySpinner: Spinner
    private lateinit var saveLanguageButton: Button
    private lateinit var themeModeSpinner: Spinner
    private lateinit var nativeReaderThemeModeSpinner: Spinner
    private lateinit var backgroundColorLabel: TextView
    private lateinit var backgroundColorEditText: EditText
    private lateinit var textColorLabel: TextView
    private lateinit var textColorEditText: EditText
    private lateinit var cssInjectionCheckbox: CheckBox
    private lateinit var saveThemeButton: Button
    private lateinit var defaultReaderSpinner: Spinner
    private lateinit var saveDefaultReaderButton: Button
    private lateinit var defaultBooksSpinner: Spinner
    private lateinit var saveDefaultBooksButton: Button
    private lateinit var aiEndpointEditText: EditText
    private lateinit var aiApiKeyEditText: EditText
    private lateinit var aiModelEditText: EditText
    private lateinit var aiPromptTermEditText: EditText
    private lateinit var aiPromptSentenceEditText: EditText
    private lateinit var showAiButtonTermCheckbox: CheckBox
    private lateinit var showAiButtonSentenceCheckbox: CheckBox
    private lateinit var saveAiButton: Button
    private lateinit var showAiInstructionsButton: Button
    private lateinit var ttsEngineSpinner: Spinner
    private lateinit var kokoroUrlLabel: TextView
    private lateinit var kokoroUrlEditText: EditText
    private lateinit var ttsLanguageSpinner: Spinner
    private lateinit var speechRateSeekBar: SeekBar
    private lateinit var speechRateValueTextView: TextView
    private lateinit var speechPitchSeekBar: SeekBar
    private lateinit var speechPitchValueTextView: TextView
    private lateinit var voiceEditText: EditText
    private lateinit var showVoicesButton: Button
    private lateinit var saveTtsButton: Button

    private lateinit var serverSettingsManager: ServerSettingsManager
    private lateinit var languageAdapter: ArrayAdapter<String>
    private lateinit var visibilityAdapter: ArrayAdapter<String>
    private lateinit var themeModeAdapter: ArrayAdapter<String>
    private lateinit var nativeReaderThemeModeAdapter: ArrayAdapter<String>
    private lateinit var defaultReaderAdapter: ArrayAdapter<String>
    private lateinit var defaultBooksAdapter: ArrayAdapter<String>
    private lateinit var aiSettingsManager: AiSettingsManager
    private lateinit var ttsEngineAdapter: ArrayAdapter<String>
    private lateinit var ttsLanguageAdapter: ArrayAdapter<String>

    companion object {
        // SharedPreferences keys for Android TTS settings
        private const val TTS_RATE_ANDROID = "tts_rate_android"
        private const val TTS_PITCH_ANDROID = "tts_pitch_android"
        private const val TTS_VOICE_ANDROID = "tts_voice_android"
        private const val TTS_LANGUAGE_ANDROID = "tts_language_android"

        // SharedPreferences keys for Kokoro TTS settings
        private const val TTS_RATE_KOKORO = "tts_rate_kokoro"
        private const val TTS_PITCH_KOKORO = "tts_pitch_kokoro"
        private const val TTS_VOICE_KOKORO = "tts_voice_kokoro"
        private const val TTS_LANGUAGE_KOKORO = "tts_language_kokoro"

        // Common keys that apply to both engines
        private const val TTS_ENGINE = "tts_engine"
        private const val KOKORO_SERVER_URL = "kokoro_server_url"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the settings manager
        serverSettingsManager = ServerSettingsManager.getInstance(requireContext())

        // Initialize AI settings manager
        aiSettingsManager = AiSettingsManager.getInstance(requireContext())

        // Initialize collapsible sections with dynamic views
        initializeCollapsibleSections()
    }

    private fun initializeCollapsibleSections() {
        // Create all the views that were previously in the layout
        createDynamicViews()

        // Create the collapsible section views dynamically
        val serverConfigSection = binding.root.findViewById<CollapsibleSectionView>(R.id.server_config_section)
        val headerDisplaySection = binding.root.findViewById<CollapsibleSectionView>(R.id.header_display_section)
        val themeSettingsSection = binding.root.findViewById<CollapsibleSectionView>(R.id.theme_settings_section)
        val defaultViewsSection = binding.root.findViewById<CollapsibleSectionView>(R.id.default_views_section)
        val aiSettingsSection = binding.root.findViewById<CollapsibleSectionView>(R.id.ai_settings_section)
        val ttsSettingsSection = binding.root.findViewById<CollapsibleSectionView>(R.id.tts_settings_section)

        // Set titles for each section
        serverConfigSection.setTitle("Server Configuration")
        headerDisplaySection.setTitle("Header Display Settings")
        themeSettingsSection.setTitle("Theme Settings")
        defaultViewsSection.setTitle("Default Views Settings")
        aiSettingsSection.setTitle("AI Settings")
        ttsSettingsSection.setTitle("TTS Settings")

        // Create content for each section
        val serverConfigContent = createServerConfigContent()
        val headerDisplayContent = createHeaderDisplayContent()
        val themeSettingsContent = createThemeSettingsContent()
        val defaultViewsContent = createDefaultViewsContent()
        val aiSettingsContent = createAiSettingsContent()
        val ttsSettingsContent = createTtsSettingsContent()

        // Set content for each section
        serverConfigSection.setContent(serverConfigContent)
        headerDisplaySection.setContent(headerDisplayContent)
        themeSettingsSection.setContent(themeSettingsContent)
        defaultViewsSection.setContent(defaultViewsContent)
        aiSettingsSection.setContent(aiSettingsContent)
        ttsSettingsSection.setContent(ttsSettingsContent)

        // Expand the first section by default
        serverConfigSection.expand()

        // Set up all the functionality after creating the views
        setupLanguageSelection()
        loadCurrentSettings()

        // Set up the save buttons
        saveUrlButton.setOnClickListener { saveUrlSettings() }

        // Combined button for header display settings (language + visibility)
        saveLanguageButton.setOnClickListener {
            saveLanguageSettings()
            saveVisibilitySettings()
            // Show combined success message
            statusTextView.text = "Header display settings saved successfully!"
            statusTextView.setTextColor(
                resources.getColor(android.R.color.holo_green_dark, null)
            )
            Toast.makeText(
                context,
                "Header display settings saved successfully",
                Toast.LENGTH_SHORT
            )
                .show()
        }

        saveThemeButton.setOnClickListener { saveThemeSettings() }

        // Set up the visibility selection spinner
        setupVisibilitySelection()

        // Set up the theme mode selection spinner
        setupThemeModeSelection()

        // Set up the native reader theme mode selection spinner
        setupNativeReaderThemeModeSelection()

        // Load CSS injection setting
        loadCssInjectionSetting()

        // Set up the default reader selection spinner
        setupDefaultReaderSelection()

        // Set up the default books selection spinner
        setupDefaultBooksSelection()

        // Set up the save default reader button
        saveDefaultReaderButton.setOnClickListener { saveDefaultReaderSettings() }

        // Set up the save default books button
        saveDefaultBooksButton.setOnClickListener { saveDefaultBooksSettings() }

        // Set up the save AI settings button
        saveAiButton.setOnClickListener { saveAiSettings() }

        // Set up the show AI instructions button
        showAiInstructionsButton.setOnClickListener { showAiInstructions() }

        // Set up the AI model name click listener to fetch available models
        aiModelEditText.setOnClickListener {
            fetchAvailableModels()
        }

        // Load AI settings
        loadAiSettings()

        // Set up TTS engine selection spinner
        setupTtsEngineSelection()

        // Set up the save TTS settings button
        saveTtsButton.setOnClickListener { saveTtsSettings() }

        // Set up speech rate SeekBar
        setupTtsRateSeekBar()

        // Set up speech pitch SeekBar
        setupTtsPitchSeekBar()

        // Set up TTS voice selection spinner
        setupTtsVoiceSelection()

        // Load TTS settings
        loadTtsSettings()
    }

    private fun createDynamicViews() {
        // Create all the views that were previously in the layout
        serverUrlEditText = EditText(requireContext()).apply {
            hint = "Enter server URL (e.g., http://192.168.1.100:5001)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        statusTextView = TextView(requireContext()).apply {
            textSize = 14f
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
        }

        saveUrlButton = Button(requireContext()).apply {
            text = "Save URL"
        }

        languageSpinner = Spinner(requireContext())

        visibilitySpinner = Spinner(requireContext())

        saveLanguageButton = Button(requireContext()).apply {
            text = "Save Header Display Settings"
        }

        themeModeSpinner = Spinner(requireContext())

        nativeReaderThemeModeSpinner = Spinner(requireContext())

        backgroundColorLabel = TextView(requireContext()).apply {
            text = "Background Color"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.NORMAL)
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            visibility = View.GONE
        }

        backgroundColorEditText = EditText(requireContext()).apply {
            hint = "Enter hex color (e.g., #FFFFFF)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
            visibility = View.GONE
        }

        textColorLabel = TextView(requireContext()).apply {
            text = "Text Color"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.NORMAL)
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            visibility = View.GONE
        }

        textColorEditText = EditText(requireContext()).apply {
            hint = "Enter hex color (e.g., #000000)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
            visibility = View.GONE
        }

        cssInjectionCheckbox = CheckBox(requireContext()).apply {
            text = "Disable CSS Injection"
            textSize = 16f
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
        }

        saveThemeButton = Button(requireContext()).apply {
            text = "Save Theme Settings"
        }

        defaultReaderSpinner = Spinner(requireContext())

        saveDefaultReaderButton = Button(requireContext()).apply {
            text = "Save Default Reader"
        }

        defaultBooksSpinner = Spinner(requireContext())

        saveDefaultBooksButton = Button(requireContext()).apply {
            text = "Save Default Books"
        }

        aiEndpointEditText = EditText(requireContext()).apply {
            hint = "http://192.168.1.100:11434/v1/chat/completions"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        aiApiKeyEditText = EditText(requireContext()).apply {
            hint = "Enter API key (e.g., for OpenAI)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        aiModelEditText = EditText(requireContext()).apply {
            hint = "Enter AI model name (e.g., llama2, mistral, phi)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        aiPromptTermEditText = EditText(requireContext()).apply {
            hint = "Enter AI prompt for term translation (use {term} as placeholder)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        aiPromptSentenceEditText = EditText(requireContext()).apply {
            hint = "Enter AI prompt for sentence processing (use {sentence} as placeholder)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        showAiButtonTermCheckbox = CheckBox(requireContext()).apply {
            text = "Show AI Button in Term Form"
            isChecked = true
        }

        showAiButtonSentenceCheckbox = CheckBox(requireContext()).apply {
            text = "Show AI Button in Sentence Reader"
            isChecked = true
        }

        saveAiButton = Button(requireContext()).apply {
            text = "Save AI Settings"
        }

        showAiInstructionsButton = Button(requireContext()).apply {
            text = "Show AI Integration Instructions"
        }

        ttsEngineSpinner = Spinner(requireContext())

        kokoroUrlLabel = TextView(requireContext()).apply {
            text = "Kokoro Server URL"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.NORMAL)
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            visibility = View.GONE
        }

        kokoroUrlEditText = EditText(requireContext()).apply {
            hint = "Enter Kokoro server URL (e.g., http://192.168.1.100:8880)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
            visibility = View.GONE
        }

        ttsLanguageSpinner = Spinner(requireContext())

        speechRateSeekBar = SeekBar(requireContext()).apply {
            max = 150
            progress = 50
        }

        speechRateValueTextView = TextView(requireContext()).apply {
            text = "Current rate: 1.0x"
            textSize = 14f
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            gravity = android.view.Gravity.CENTER
        }

        speechPitchSeekBar = SeekBar(requireContext()).apply {
            max = 40
            progress = 20
        }

        speechPitchValueTextView = TextView(requireContext()).apply {
            text = "Current pitch: 1.0x"
            textSize = 14f
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            gravity = android.view.Gravity.CENTER
        }

        voiceEditText = EditText(requireContext()).apply {
            hint = "Enter voice name(s) (e.g., af_bella or af_bella+af_heart)"
            inputType = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            setHintTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_surface, null))
        }

        showVoicesButton = Button(requireContext()).apply {
            text = "Show Voices"
        }

        saveTtsButton = Button(requireContext()).apply {
            text = "Save TTS Settings"
        }
    }

    private fun createServerConfigContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add server URL input
        val serverUrlLabel = createTextView("Server URL")
        layout.addView(serverUrlLabel)
        layout.addView(serverUrlEditText)
        layout.addView(saveUrlButton)
        layout.addView(statusTextView)

        // Add instructions
        val instructionsLabel = createTextView("Instructions", 18f, true)
        val instructionText1 =
            createTextView("Enter the full URL of your Lute server including the protocol (http:// or https://) and port number. For example:")
        val instructionText2 = createTextView("• http://192.168.1.100:5001")
        val instructionText3 = createTextView("• https://yourdomain.com:5001")
        val instructionText4 = createTextView("• http://localhost:5001")

        layout.addView(instructionsLabel)
        layout.addView(instructionText1)
        layout.addView(instructionText2)
        layout.addView(instructionText3)
        layout.addView(instructionText4)

        return layout
    }

    private fun createHeaderDisplayContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add language selection
        val languageLabel = createTextView("Language for Word Count Display")
        layout.addView(languageLabel)
        layout.addView(languageSpinner)

        // Add visibility selection
        val visibilityLabel = createTextView("Word Count Visibility")
        layout.addView(visibilityLabel)
        layout.addView(visibilitySpinner)

        // Add save button
        layout.addView(saveLanguageButton)

        return layout
    }

    private fun createThemeSettingsContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add theme mode selection
        val themeModeLabel = createTextView("Theme Mode")
        layout.addView(themeModeLabel)
        layout.addView(themeModeSpinner)

        // Add native reader theme section
        val nativeReaderThemeLabel = createTextView("Native Reader Theme", 18f, true)
        layout.addView(nativeReaderThemeLabel)

        val nativeReaderThemeModeLabel = createTextView("Theme Mode")
        layout.addView(nativeReaderThemeModeLabel)
        layout.addView(nativeReaderThemeModeSpinner)

        // Add background color fields (initially hidden, shown based on selection)
        layout.addView(backgroundColorLabel)
        layout.addView(backgroundColorEditText)
        layout.addView(textColorLabel)
        layout.addView(textColorEditText)

        // Add CSS injection checkbox
        layout.addView(cssInjectionCheckbox)

        // Add save button
        layout.addView(saveThemeButton)

        return layout
    }

    private fun createDefaultViewsContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add default reader settings
        val defaultReaderLabel = createTextView("Default Reader")
        layout.addView(defaultReaderLabel)
        layout.addView(defaultReaderSpinner)
        layout.addView(saveDefaultReaderButton)

        // Add default books settings
        val defaultBooksLabel = createTextView("Default Books View")
        layout.addView(defaultBooksLabel)
        layout.addView(defaultBooksSpinner)
        layout.addView(saveDefaultBooksButton)

        return layout
    }

    private fun createAiSettingsContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add AI endpoint
        val aiEndpointLabel = createTextView("OpenAI Endpoint URL - /v1/chat/completions")
        layout.addView(aiEndpointLabel)
        layout.addView(aiEndpointEditText)

        // Add AI API key
        val aiApiKeyLabel = createTextView("AI API Key (Leave blank for Local)")
        layout.addView(aiApiKeyLabel)
        layout.addView(aiApiKeyEditText)

        // Add AI model name
        val aiModelLabel = createTextView("AI Model Name")
        layout.addView(aiModelLabel)
        layout.addView(aiModelEditText)

        // Add AI prompt for term form
        val aiPromptTermLabel = createTextView("AI Prompt for Term Form")
        layout.addView(aiPromptTermLabel)
        layout.addView(aiPromptTermEditText)

        // Add AI prompt for sentence reader
        val aiPromptSentenceLabel = createTextView("AI Prompt for Sentence Reader")
        layout.addView(aiPromptSentenceLabel)
        layout.addView(aiPromptSentenceEditText)

        // Add checkboxes
        val checkboxLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        checkboxLayout.addView(showAiButtonTermCheckbox)
        checkboxLayout.addView(showAiButtonSentenceCheckbox)
        layout.addView(checkboxLayout)

        // Add save and instructions buttons
        layout.addView(saveAiButton)
        layout.addView(showAiInstructionsButton)

        return layout
    }

    private fun createTtsSettingsContent(): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add TTS engine selection
        val ttsEngineLabel = createTextView("TTS Engine")
        layout.addView(ttsEngineLabel)
        layout.addView(ttsEngineSpinner)

        // Add Kokoro server URL (initially hidden)
        layout.addView(kokoroUrlLabel)
        layout.addView(kokoroUrlEditText)

        // Add TTS language selection
        val ttsLanguageLabel = createTextView("TTS Language")
        layout.addView(ttsLanguageLabel)
        layout.addView(ttsLanguageSpinner)

        // Add speech rate
        val speechRateLabel = createTextView("Speech Rate")
        layout.addView(speechRateLabel)
        layout.addView(speechRateSeekBar)
        layout.addView(speechRateValueTextView)

        // Add speech pitch
        val speechPitchLabel = createTextView("Speech Pitch")
        layout.addView(speechPitchLabel)
        layout.addView(speechPitchSeekBar)
        layout.addView(speechPitchValueTextView)

        // Add voice selection
        val voiceSelectionLabel = createTextView("Voice Selection")
        val voiceSelectionLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add EditText with weight to take remaining space
        val editTextLayoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f // Weight to take remaining space
        )
        voiceEditText.layoutParams = editTextLayoutParams

        // Add button with wrap content
        val buttonLayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(16, 0, 0, 0) // Add left margin for spacing
        }
        showVoicesButton.layoutParams = buttonLayoutParams

        voiceSelectionLayout.addView(voiceEditText)
        voiceSelectionLayout.addView(showVoicesButton)
        layout.addView(voiceSelectionLabel)
        layout.addView(voiceSelectionLayout)

        // Add save button
        layout.addView(saveTtsButton)

        return layout
    }

    private fun createTextView(text: String, size: Float = 16f, isBold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = size
            setTypeface(null, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setTextColor(ResourcesCompat.getColor(resources, R.color.lute_on_background, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24) // 8dp in pixels
            }
        }
    }

    fun setupLanguageSelection() {
        // Fetch languages directly from the server
        fetchLanguagesFromServer { fetchedLanguages ->
            activity?.runOnUiThread {
                try {
                    // Create language array with "Auto" option for automatic language detection
                    val languageList =
                        if (fetchedLanguages.isNotEmpty()) {
                            val list = fetchedLanguages.toMutableList()
                            list.add(0, "Auto") // Add "Auto" option at the beginning
                            list
                        } else {
                            mutableListOf(
                                "Auto",
                                "Default Language"
                            ) // Fallback with Auto option
                        }
                    val languageArray = languageList.toTypedArray()

                    languageAdapter =
                        ArrayAdapter(requireContext(), R.layout.spinner_item, languageArray)
                    languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                    languageSpinner.adapter = languageAdapter

                    // Load previously selected language, defaulting to first available language
                    val sharedPref =
                        requireContext()
                            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val selectedLanguage =
                        sharedPref.getString(
                            "selected_language",
                            languageArray.firstOrNull() ?: "Default Language"
                        )
                    val position = languageArray.indexOf(selectedLanguage)
                    if (position >= 0) {
                        languageSpinner.setSelection(position)
                    } else {
                        // Default to first language in the list
                        languageSpinner.setSelection(0)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "AppSettingsFragment",
                        "Error setting up language selection",
                        e
                    )
                    // Fallback with Auto option
                    val defaultLanguages = arrayOf("Auto", "Default Language")
                    languageAdapter =
                        ArrayAdapter(requireContext(), R.layout.spinner_item, defaultLanguages)
                    languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                    languageSpinner.adapter = languageAdapter

                    // Load previously selected language
                    val sharedPref =
                        requireContext()
                            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val selectedLanguage = sharedPref.getString("selected_language", "Auto")
                    val position = defaultLanguages.indexOf(selectedLanguage)
                    if (position >= 0) {
                        languageSpinner.setSelection(position)
                    } else {
                        // Default to "Auto"
                        languageSpinner.setSelection(0)
                    }
                }
            }
        }
    }

    private fun fetchLanguagesFromServer(callback: (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val languages =
                withContext(Dispatchers.IO) {
                    try {
                        val serverSettingsManager =
                            ServerSettingsManager.getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            return@withContext emptyList<String>()
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()
                        val url = "$serverUrl/language/index"

                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                return@withContext emptyList<String>()
                            }

                            val html =
                                response.body?.string()
                                    ?: return@withContext emptyList<String>()

                            // Parse the HTML to extract language names
                            val document = Jsoup.parse(html)
                            val languageTable = document.selectFirst("table#languagetable")
                            if (languageTable == null) {
                                return@withContext emptyList<String>()
                            }

                            val languages = mutableListOf<String>()
                            val rows = languageTable.select("tbody tr")
                            for (row in rows) {
                                val cells = row.select("td")
                                if (cells.isNotEmpty()) {
                                    // First cell contains the language name in an anchor tag
                                    val languageName = cells[0].selectFirst("a")?.text()?.trim()
                                    if (!languageName.isNullOrEmpty()) {
                                        languages.add(languageName)
                                    }
                                }
                            }

                            return@withContext languages
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "AppSettingsFragment",
                            "Error fetching languages from server",
                            e
                        )
                        return@withContext emptyList<String>()
                    }
                }

            callback(languages)
        }
    }

    private fun loadCurrentSettings() {
        val currentUrl = serverSettingsManager.getServerUrl()
        if (currentUrl.isNotEmpty()) {
            serverUrlEditText.setText(currentUrl)
            statusTextView.text = "Current server URL loaded"
        } else {
            // Set placeholder text if no URL is configured
            serverUrlEditText.hint = "Enter server URL (e.g., http://192.168.1.100:5001)"
            statusTextView.text = "No server URL configured"
        }
    }

    private fun saveUrlSettings() {
        val serverUrl = serverUrlEditText.text.toString().trim()

        if (serverUrl.isEmpty()) {
            statusTextView.text = "Please enter a server URL"
            statusTextView.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, null)
            )
            return
        }

        if (!serverSettingsManager.isValidUrl(serverUrl)) {
            statusTextView.text =
                "Please enter a valid URL starting with http:// or https://"
            statusTextView.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, null)
            )
            return
        }

        // Save the server URL
        serverSettingsManager.saveServerUrl(serverUrl)

        // Send Android custom styles to the newly configured server
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            mainActivity?.sendAndroidCustomStyles()
        } catch (e: Exception) {
            android.util.Log.e(
                "AppSettingsFragment",
                "Error sending Android custom styles to new server",
                e
            )
        }

        // Clear the auto-navigation flags since URL has been saved
        val navSharedPref =
            requireContext()
                .getSharedPreferences(
                    "navigation_flags",
                    android.content.Context.MODE_PRIVATE
                )
        with(navSharedPref.edit()) {
            putBoolean("auto_switch_to_app_settings", false)
            putBoolean("first_launch_no_url", false)
            apply()
        }

        // Clear the word count cache since server URL has changed
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            mainActivity?.clearWordCountCache()
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error clearing word count cache", e)
        }

        // Update the status
        statusTextView.text = "URL saved successfully!"
        statusTextView.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Test server connectivity
        testServerConnectivity(serverUrl)

        // Show a toast message
        Toast.makeText(context, "URL saved successfully", Toast.LENGTH_SHORT).show()

        // Refresh the app to load pages with the correct server URL
        refreshApp()

        // Update MainActivity to reflect any visibility changes
        updateMainActivityVisibility()
    }

    private fun refreshApp() {
        // Refresh all WebViews in the app by triggering a reload
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            // Update word count with new server URL
            mainActivity?.updateWordCount()
            // Refresh the current WebView content
            mainActivity?.refreshCurrentWebView()
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error refreshing app", e)
        }
    }

    private fun saveLanguageSettings() {
        // Save the selected language
        val selectedLanguage = languageSpinner.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("selected_language", selectedLanguage)
            apply()
        }

        // Clear the word count cache since language has changed
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            mainActivity?.clearWordCountCache()
            // Also trigger an immediate update
            mainActivity?.updateWordCount()
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error clearing word count cache", e)
        }

        // Update MainActivity to reflect any visibility changes
        updateMainActivityVisibility()
    }

    private fun testServerConnectivity(serverUrl: String) {
        CoroutineScope(Dispatchers.Main).launch {
            statusTextView.append("\nTesting server connectivity...")

            val isReachable =
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL(serverUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000 // 5 seconds
                        connection.readTimeout = 5000 // 5 seconds
                        connection.connect()

                        val responseCode = connection.responseCode
                        connection.disconnect()

                        // Consider successful if we get a response (2xx or 3xx status codes)
                        responseCode in 200..399
                    } catch (e: Exception) {
                        false
                    }
                }

            if (isReachable) {
                statusTextView.append("\nServer is reachable")
                statusTextView.setTextColor(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )

                // Show a toast message to inform the user that the app will restart
                Toast.makeText(
                    context,
                    "Server is reachable! Restarting app to apply changes...",
                    Toast.LENGTH_LONG
                )
                    .show()

                // Restart the app to ensure proper theme application
                restartApp()
            } else {
                statusTextView.append("\nServer not available")
                statusTextView.setTextColor(
                    resources.getColor(android.R.color.holo_red_dark, null)
                )
            }
        }
    }

    private fun reloadSettingsFragment() {
        try {
            // Instead of navigating away and back, just refresh the content
            // Load the current server URL and settings
            loadCurrentSettings()

            // Set up language selection spinner by fetching languages from server
            setupLanguageSelection()
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error reloading settings fragment", e)
        }
    }

    private fun restartApp() {
        try {
            // Create an intent to restart the MainActivity
            val intent =
                android.content.Intent(
                    activity,
                    com.example.luteforandroidv2.MainActivity::class.java
                )
            intent.flags =
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION

            // Add a small delay to allow the toast to be shown
            android.os.Handler()
                .postDelayed(
                    {
                        // Start the new activity
                        startActivity(intent)
                        // Finish the current activity
                        activity?.finish()
                        // Kill the current process to ensure a clean restart
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    2000
                )
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error restarting app", e)
        }
    }

    private fun setupVisibilitySelection() {
        // Create visibility options array
        val visibilityOptions = arrayOf("Visible", "Hidden")
        visibilityAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, visibilityOptions)
        visibilityAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        visibilitySpinner.adapter = visibilityAdapter

        // Load previously selected visibility setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedVisibility = sharedPref.getString("word_count_visibility", "Visible")
        val position = visibilityOptions.indexOf(selectedVisibility)
        if (position >= 0) {
            visibilitySpinner.setSelection(position)
        } else {
            // Default to "Visible"
            visibilitySpinner.setSelection(0)
        }
    }

    private fun saveVisibilitySettings() {
        // Save the selected visibility
        val selectedVisibility = visibilitySpinner.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("word_count_visibility", selectedVisibility)
            apply()
        }

        // Update MainActivity to reflect the new setting
        updateMainActivityVisibility()
    }

    private fun updateMainActivityVisibility() {
        try {
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            mainActivity?.updateWordCountVisibility()
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error updating MainActivity visibility", e)
        }
    }

    private fun setupThemeModeSelection() {
        // Create theme mode options array
        val themeModeOptions = arrayOf("App Theme", "Auto Theme")
        themeModeAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, themeModeOptions)
        themeModeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        themeModeSpinner.adapter = themeModeAdapter

        // Load previously selected theme mode setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedThemeMode = sharedPref.getString("theme_mode", "App Theme")
        val position = themeModeOptions.indexOf(selectedThemeMode)
        if (position >= 0) {
            themeModeSpinner.setSelection(position)
        } else {
            // Default to "App Theme"
            themeModeSpinner.setSelection(0)
        }
    }

    /** Set up the native reader theme mode spinner */
    private fun setupNativeReaderThemeModeSelection() {
        // Create theme mode options array
        val themeModeOptions = arrayOf("App Settings", "Custom")
        nativeReaderThemeModeAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item, themeModeOptions)
        nativeReaderThemeModeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        nativeReaderThemeModeSpinner.adapter = nativeReaderThemeModeAdapter

        // Load previously selected native reader theme mode setting
        val sharedPref =
            requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        val selectedThemeMode = sharedPref.getString("native_reader_theme_mode", "App Settings")
        val position = themeModeOptions.indexOf(selectedThemeMode)
        if (position >= 0) {
            nativeReaderThemeModeSpinner.setSelection(position)
        } else {
            // Default to "App Settings"
            nativeReaderThemeModeSpinner.setSelection(0)
        }

        // Set up listener to show/hide textboxes based on selection
        nativeReaderThemeModeSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    when (position) {
                        0 -> { // App Settings
                            // Hide textboxes
                            backgroundColorLabel.visibility = android.view.View.GONE
                            backgroundColorEditText.visibility = android.view.View.GONE
                            textColorLabel.visibility = android.view.View.GONE
                            textColorEditText.visibility = android.view.View.GONE
                        }

                        1 -> { // Custom
                            // Show textboxes
                            backgroundColorLabel.visibility =
                                android.view.View.VISIBLE
                            backgroundColorEditText.visibility =
                                android.view.View.VISIBLE
                            textColorLabel.visibility =
                                android.view.View.VISIBLE
                            textColorEditText.visibility = android.view.View.VISIBLE
                        }
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                    // Hide textboxes by default
                    backgroundColorLabel.visibility = android.view.View.GONE
                    backgroundColorEditText.visibility = android.view.View.GONE
                    textColorLabel.visibility = android.view.View.GONE
                    textColorEditText.visibility = android.view.View.GONE
                }
            }

        // Trigger the listener to set initial visibility
        nativeReaderThemeModeSpinner.onItemSelectedListener?.onItemSelected(
            nativeReaderThemeModeSpinner,
            null,
            nativeReaderThemeModeSpinner.selectedItemPosition,
            0
        )

        // Load color values into EditTexts
        val colorsPref = requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        backgroundColorEditText.setText(colorsPref.getString("background_color", "#48484A"))
        textColorEditText.setText(colorsPref.getString("text_color", "#EBEBEB"))
    }

    /** Load Native Reader Theme settings from SharedPreferences */
    private fun loadNativeReaderThemeSettings() {
        val sharedPref =
            requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        val backgroundColor = sharedPref.getString("background_color", "#FFFFFF")
        val textColor = sharedPref.getString("text_color", "#000000")
        val themeMode = sharedPref.getString("native_reader_theme_mode", "App Settings")

        // Set the spinner selection
        val themeModeOptions = arrayOf("App Settings", "Custom")
        val position = themeModeOptions.indexOf(themeMode)
        if (position >= 0) {
            nativeReaderThemeModeSpinner.setSelection(position)
        } else {
            // Default to "App Settings"
            nativeReaderThemeModeSpinner.setSelection(0)
        }

        // Set text values
        backgroundColorEditText.setText(backgroundColor)
        textColorEditText.setText(textColor)

        // Trigger the listener to set initial visibility
        nativeReaderThemeModeSpinner.onItemSelectedListener?.onItemSelected(
            nativeReaderThemeModeSpinner,
            null,
            nativeReaderThemeModeSpinner.selectedItemPosition,
            0
        )
    }

    /** Save Native Reader Theme settings to SharedPreferences */
    private fun saveNativeReaderThemeSettings() {
        val selectedThemeMode = nativeReaderThemeModeSpinner.selectedItem.toString()

        val sharedPref =
            requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("native_reader_theme_mode", selectedThemeMode)

            // Only save custom colors if Custom mode is selected
            if (selectedThemeMode == "Custom") {
                val backgroundColor = backgroundColorEditText.text.toString().trim()
                val textColor = textColorEditText.text.toString().trim()

                // Validate hex colors
                if (backgroundColor.isNotEmpty() && !isValidHexColor(backgroundColor)) {
                    statusTextView.text = "Invalid background color format. Use #RRGGBB."
                    statusTextView.setTextColor(
                        resources.getColor(android.R.color.holo_red_dark, null)
                    )
                    return
                }

                if (textColor.isNotEmpty() && !isValidHexColor(textColor)) {
                    statusTextView.text = "Invalid text color format. Use #RRGGBB."
                    statusTextView.setTextColor(
                        resources.getColor(android.R.color.holo_red_dark, null)
                    )
                    return
                }

                if (backgroundColor.isNotEmpty()) {
                    putString("background_color", backgroundColor)
                }
                if (textColor.isNotEmpty()) {
                    putString("text_color", textColor)
                }
            } else {
                // For App Settings mode, save the default values
                putString("background_color", "#48484A")
                putString("text_color", "#EBEBEB")
            }
            apply()
        }
    }

    /** Validate hex color format */
    private fun isValidHexColor(color: String): Boolean {
        return color.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))
    }

    private fun setupDefaultReaderSelection() {
        // Create default reader options array
        val defaultReaderOptions = arrayOf("Webview Reader", "Native Reader")
        defaultReaderAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item, defaultReaderOptions)
        defaultReaderAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        defaultReaderSpinner.adapter = defaultReaderAdapter

        // Load previously selected default reader setting, defaulting to "Native Reader"
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedDefaultReader = sharedPref.getString("default_reader", "Native Reader")
        val position = defaultReaderOptions.indexOf(selectedDefaultReader)
        if (position >= 0) {
            defaultReaderSpinner.setSelection(position)
        } else {
            // Default to "Native Reader"
            defaultReaderSpinner.setSelection(1) // Index 1 is "Native Reader"
        }
    }

    private fun saveDefaultReaderSettings() {
        // Save the selected default reader
        val selectedDefaultReader = defaultReaderSpinner.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("default_reader", selectedDefaultReader)
            apply()
        }

        // Update the status
        statusTextView.text = "Default reader settings saved successfully!"
        statusTextView.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show a toast message
        Toast.makeText(context, "Default reader settings saved successfully", Toast.LENGTH_SHORT)
            .show()
    }

    private fun setupDefaultBooksSelection() {
        // Create default books options array
        val defaultBooksOptions = arrayOf("Books View", "Native Books")
        defaultBooksAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item, defaultBooksOptions)
        defaultBooksAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        defaultBooksSpinner.adapter = defaultBooksAdapter

        // Load previously selected default books setting, defaulting to "Native Books"
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedDefaultBooks = sharedPref.getString("default_books", "Native Books")
        val position = defaultBooksOptions.indexOf(selectedDefaultBooks)
        if (position >= 0) {
            defaultBooksSpinner.setSelection(position)
        } else {
            // Default to "Native Books"
            defaultBooksSpinner.setSelection(1) // Index 1 is "Native Books"
        }
    }

    private fun saveDefaultBooksSettings() {
        // Save the selected default books
        val selectedDefaultBooks = defaultBooksSpinner.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("default_books", selectedDefaultBooks)
            apply()
        }

        // Update the status
        statusTextView.text = "Default books settings saved successfully!"
        statusTextView.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show a toast message
        Toast.makeText(context, "Default books settings saved successfully", Toast.LENGTH_SHORT)
            .show()
    }

    private fun saveThemeSettings() {
        // Save the selected theme mode
        val selectedThemeMode = themeModeSpinner.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("theme_mode", selectedThemeMode)

            // Save the CSS injection setting
            putBoolean("disable_css_injection", cssInjectionCheckbox.isChecked)
            apply()
        }

        // Save Native Reader Theme settings
        saveNativeReaderThemeSettings()

        // Update the status
        statusTextView.text = "Theme settings saved successfully!"
        statusTextView.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show a toast message
        Toast.makeText(context, "Theme settings saved successfully", Toast.LENGTH_SHORT).show()

        // Show a toast message to inform the user that the app will restart
        Toast.makeText(context, "Restarting app to apply theme changes...", Toast.LENGTH_LONG)
            .show()

        // Restart the app to ensure proper theme application
        restartApp()
    }

    private fun updateThemeApplication() {
        try {
            // Get the selected theme mode
            val sharedPref =
                requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val selectedThemeMode = sharedPref.getString("theme_mode", "App Theme")

            // Apply theme based on selection
            val mainActivity = activity as? com.example.luteforandroidv2.MainActivity
            if (selectedThemeMode == "Auto Theme") {
                // Enable auto theming
                mainActivity?.updateThemeFromServer()
            } else {
                // Use app theme - clear any saved theme and use default
                val autoThemeProvider =
                    com.example.luteforandroidv2.theme.AutoThemeProvider(requireContext())
                autoThemeProvider.clearSavedTheme()
                // Reinitialize the activity to apply default theme
                mainActivity?.recreate()
            }
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error updating theme application", e)
        }
    }

    private fun saveAiSettings() {
        // Get values from UI elements
        val aiEndpoint = aiEndpointEditText.text.toString().trim()
        val aiApiKey = aiApiKeyEditText.text.toString().trim()
        val aiPromptTerm = aiPromptTermEditText.text.toString().trim()
        val aiPromptSentence = aiPromptSentenceEditText.text.toString().trim()
        val aiModel = aiModelEditText.text.toString().trim()
        val showAiButtonTerm = showAiButtonTermCheckbox.isChecked
        val showAiButtonSentence = showAiButtonSentenceCheckbox.isChecked

        // Validate AI endpoint URL if provided
        if (aiEndpoint.isNotEmpty() && !isValidUrl(aiEndpoint)) {
            statusTextView.text = "Please enter a valid AI endpoint URL"
            statusTextView.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, null)
            )
            return
        }

        // Save to AiSettingsManager
        aiSettingsManager.aiEndpoint = aiEndpoint
        aiSettingsManager.aiApiKey = aiApiKey
        aiSettingsManager.aiPromptTerm = aiPromptTerm
        aiSettingsManager.aiPromptSentence = aiPromptSentence
        aiSettingsManager.aiModel = aiModel
        aiSettingsManager.showAiButtonTerm = showAiButtonTerm
        aiSettingsManager.showAiButtonSentence = showAiButtonSentence

        // Update status
        statusTextView.text = "AI settings saved successfully!"
        statusTextView.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show toast
        Toast.makeText(context, "AI settings saved successfully", Toast.LENGTH_SHORT).show()

        // Test AI connection if endpoint is configured
        if (aiEndpoint.isNotEmpty()) {
            testAiConnection(aiEndpoint, aiModel)
        }
    }

    /** Test AI connection and display results in a toast */
    private fun testAiConnection(endpoint: String, model: String) {
        Thread {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 10000 // 10 seconds
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                // Prepare a minimal test request in OpenAI format
                val testRequest =
                    """
                {
                  "model": "$model",
                  "messages": [
                    {"role": "user", "content": "Test connection"}
                  ],
                  "max_tokens": 10,
                  "stream": false
                }
                """.trimIndent()

                // Write request body
                val outputStream = connection.outputStream
                outputStream.write(testRequest.toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                connection.disconnect()

                // Show result in UI thread
                activity?.runOnUiThread {
                    if (responseCode == 200) {
                        Toast.makeText(
                            context,
                            "AI connection test successful!",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    } else {
                        Toast.makeText(
                            context,
                            "AI connection test failed. Response code: $responseCode",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
            } catch (e: Exception) {
                // Show error in UI thread
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "AI connection test failed: ${e.message}",
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
            .start()
    }

    private fun loadAiSettings() {
        // Load values from AiSettingsManager
        val aiEndpoint = aiSettingsManager.aiEndpoint
        val aiApiKey = aiSettingsManager.aiApiKey
        val aiPromptTerm = aiSettingsManager.aiPromptTerm
        val aiPromptSentence = aiSettingsManager.aiPromptSentence
        val aiModel = aiSettingsManager.aiModel
        val showAiButtonTerm = aiSettingsManager.showAiButtonTerm
        val showAiButtonSentence = aiSettingsManager.showAiButtonSentence

        // Set values in UI elements
        aiEndpointEditText.setText(aiEndpoint)
        aiApiKeyEditText.setText(aiApiKey)
        aiPromptTermEditText.setText(aiPromptTerm)
        aiPromptSentenceEditText.setText(aiPromptSentence)
        aiModelEditText.setText(aiModel)
        showAiButtonTermCheckbox.isChecked = showAiButtonTerm
        showAiButtonSentenceCheckbox.isChecked = showAiButtonSentence
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            parsedUrl.protocol == "http" || parsedUrl.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun setupTtsEngineSelection() {
        // Create TTS engine options array - starting with Android TTS and adding Kokoro
        val ttsEngineOptions = arrayOf("Android TTS", "Kokoro TTS")
        ttsEngineAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, ttsEngineOptions)
        ttsEngineAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        ttsEngineSpinner.adapter = ttsEngineAdapter

        // Load previously selected TTS engine setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = sharedPref.getString("tts_engine", "Android TTS")
        val position = ttsEngineOptions.indexOf(selectedTtsEngine)
        if (position >= 0) {
            ttsEngineSpinner.setSelection(position)
        } else {
            // Default to "Android TTS"
            ttsEngineSpinner.setSelection(0)
        }

        // Set up visibility for Kokoro URL when engine selection changes
        ttsEngineSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedEngine = ttsEngineOptions[position]
                if (selectedEngine == "Kokoro TTS") {
                    kokoroUrlLabel.visibility = View.VISIBLE
                    kokoroUrlEditText.visibility = View.VISIBLE
                    // Load saved Kokoro URL
                    val kokoroUrl = sharedPref.getString(KOKORO_SERVER_URL, "http://192.168.1.100:8880")
                    kokoroUrlEditText.setText(kokoroUrl)
                } else {
                    kokoroUrlLabel.visibility = View.GONE
                    kokoroUrlEditText.visibility = View.GONE
                }

                // Load settings specific to the selected engine
                loadTtsSettingsForEngine(selectedEngine)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                kokoroUrlLabel.visibility = View.GONE
                kokoroUrlEditText.visibility = View.GONE
            }
        }

        // Set up TTS language selection spinner
        setupTtsLanguageSelection()
    }

    private fun setupTtsLanguageSelection() {
        // Create TTS language options - "Auto" first, then common languages
        val ttsLanguageOptions =
            arrayOf(
                "Auto (Detect from Book)",
                "English",
                "Spanish",
                "French",
                "German",
                "Italian",
                "Portuguese",
                "Russian",
                "Chinese (Simplified)",
                "Chinese (Traditional)",
                "Japanese",
                "Korean",
                "Arabic",
                "Hindi",
                "Bengali",
                "Punjabi",
                "Urdu",
                "Turkish",
                "Dutch",
                "Swedish",
                "Norwegian",
                "Danish",
                "Finnish",
                "Polish",
                "Czech",
                "Greek",
                "Hebrew",
                "Thai",
                "Vietnamese",
                "Indonesian",
                "Malay"
            )
        ttsLanguageAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item, ttsLanguageOptions)
        ttsLanguageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        ttsLanguageSpinner.adapter = ttsLanguageAdapter

        // Load previously selected TTS language setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsLanguage = sharedPref.getString("tts_language", "Auto (Detect from Book)")
        val position = ttsLanguageOptions.indexOf(selectedTtsLanguage)
        if (position >= 0) {
            ttsLanguageSpinner.setSelection(position)
        } else {
            // Default to "Auto (Detect from Book)"
            ttsLanguageSpinner.setSelection(0)
        }
    }

    private fun saveTtsSettings() {
        // Save the selected TTS engine and language
        val selectedTtsEngine = ttsEngineSpinner.selectedItem.toString()
        val selectedTtsLanguage = ttsLanguageSpinner.selectedItem.toString()

        // Get the TTS rate from the seekbar
        val rateProgress = speechRateSeekBar.progress
        val ttsRate = (rateProgress / 100f) + 0.5f // Convert 0-150 range to 0.5-2.0 range

        // Get the TTS pitch from the seekbar
        val pitchProgress = speechPitchSeekBar.progress
        val ttsPitch = (pitchProgress / 100f) + 0.8f // Convert 0-40 range to 0.8-1.2 range

        // Get the selected TTS voice from the text field
        val selectedTtsVoice = voiceEditText.text.toString().trim()

        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        if (selectedTtsEngine == "Kokoro TTS") {
            // Validate Kokoro server URL before saving
            validateKokoroServerUrl { isValid ->
                if (isValid) {
                    // Save settings to engine-specific keys
                    with(sharedPref.edit()) {
                        putString(TTS_ENGINE, selectedTtsEngine)
                        putString(TTS_LANGUAGE_KOKORO, selectedTtsLanguage)
                        putFloat(TTS_RATE_KOKORO, ttsRate)
                        putFloat(TTS_PITCH_KOKORO, ttsPitch)
                        putString(TTS_VOICE_KOKORO, selectedTtsVoice)

                        val kokoroUrl = kokoroUrlEditText.text.toString().trim()
                        if (kokoroUrl.isNotEmpty()) {
                            putString(KOKORO_SERVER_URL, kokoroUrl)
                        } else {
                            // Use default URL if empty
                            putString(KOKORO_SERVER_URL, "http://192.168.1.100:8880")
                        }

                        apply()
                    }

                    // Apply the settings to the TTS manager
                    applyTtsSettingsToManager(ttsRate, ttsPitch, selectedTtsVoice)

                    // Update the status
                    statusTextView.text = "TTS settings saved successfully!"
                    statusTextView.setTextColor(
                        resources.getColor(android.R.color.holo_green_dark, null)
                    )

                    // Show a toast message
                    Toast.makeText(context, "TTS settings saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    // Show error message
                    statusTextView.text = "Error: Kokoro server is not reachable. Please check the URL."
                    statusTextView.setTextColor(
                        resources.getColor(android.R.color.holo_red_dark, null)
                    )
                    Toast.makeText(context, "Kokoro server is not reachable. Please check the URL.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        } else {
            // Save settings to engine-specific keys for Android TTS
            with(sharedPref.edit()) {
                putString(TTS_ENGINE, selectedTtsEngine)
                putString(TTS_LANGUAGE_ANDROID, selectedTtsLanguage)
                putFloat(TTS_RATE_ANDROID, ttsRate)
                putFloat(TTS_PITCH_ANDROID, ttsPitch)
                putString(TTS_VOICE_ANDROID, selectedTtsVoice)

                apply()
            }

            // Apply the settings to the TTS manager
            applyTtsSettingsToManager(ttsRate, ttsPitch, selectedTtsVoice)

            // Update the status
            statusTextView.text = "TTS settings saved successfully!"
            statusTextView.setTextColor(
                resources.getColor(android.R.color.holo_green_dark, null)
            )

            // Show a toast message
            Toast.makeText(context, "TTS settings saved successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateKokoroServerUrl(callback: (Boolean) -> Unit) {
        val serverUrl = kokoroUrlEditText.text.toString().trim()

        if (serverUrl.isEmpty()) {
            callback(false)
            return
        }

        // Basic URL validation
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            callback(false)
            return
        }

        // Create a coroutine to make the network request
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                // Test the server by making a simple request to a known endpoint
                val apiUrl = "$serverUrl/v1/audio/voices"
                val request = Request.Builder()
                    .url(apiUrl)
                    .build()

                val response = client.newCall(request).execute()
                val isValid = response.isSuccessful

                withContext(Dispatchers.Main) {
                    callback(isValid)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppSettingsFragment", "Error validating Kokoro server", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    private fun applyTtsSettingsToManager(rate: Float, pitch: Float, voice: String?) {
        try {
            val ttsManager = TTSManager.getInstance(requireContext())
            ttsManager.setSpeechRate(rate)
            ttsManager.setPitch(pitch)
            if (!voice.isNullOrEmpty()) {
                ttsManager.setVoice(voice)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error applying TTS settings to manager", e)
        }
    }

    private fun loadCssInjectionSetting() {
        // Load the CSS injection setting from SharedPreferences
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isCssInjectionDisabled = sharedPref.getBoolean("disable_css_injection", false)
        cssInjectionCheckbox.isChecked = isCssInjectionDisabled
    }

    private fun loadTtsSettings() {
        // Load the selected TTS engine from SharedPreferences
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = sharedPref.getString(TTS_ENGINE, "Android TTS") ?: "Android TTS"
        val ttsEngineOptions = arrayOf("Android TTS", "Kokoro TTS")
        val position = ttsEngineOptions.indexOf(selectedTtsEngine)
        if (position >= 0) {
            ttsEngineSpinner.setSelection(position)

            // Show/hide Kokoro URL field based on selected engine
            if (selectedTtsEngine == "Kokoro TTS") {
                kokoroUrlLabel.visibility = View.VISIBLE
                kokoroUrlEditText.visibility = View.VISIBLE
                // Load saved Kokoro URL
                val kokoroUrl =
                    sharedPref.getString(KOKORO_SERVER_URL, "http://192.168.1.100:8880") ?: "http://192.168.1.100:8880"
                kokoroUrlEditText.setText(kokoroUrl)
            } else {
                kokoroUrlLabel.visibility = View.GONE
                kokoroUrlEditText.visibility = View.GONE
            }

            // Load settings specific to the selected engine
            loadTtsSettingsForEngine(selectedTtsEngine)
        } else {
            // Default to "Android TTS"
            ttsEngineSpinner.setSelection(0)
            kokoroUrlLabel.visibility = View.GONE
            kokoroUrlEditText.visibility = View.GONE

            // Load default Android TTS settings
            loadTtsSettingsForEngine("Android TTS")
        }
    }

    private fun loadTtsSettingsForEngine(engine: String) {
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Determine which keys to use based on the engine
        val languageKey = if (engine == "Kokoro TTS") TTS_LANGUAGE_KOKORO else TTS_LANGUAGE_ANDROID
        val rateKey = if (engine == "Kokoro TTS") TTS_RATE_KOKORO else TTS_RATE_ANDROID
        val pitchKey = if (engine == "Kokoro TTS") TTS_PITCH_KOKORO else TTS_PITCH_ANDROID
        val voiceKey = if (engine == "Kokoro TTS") TTS_VOICE_KOKORO else TTS_VOICE_ANDROID

        // Load the selected TTS language from SharedPreferences
        val selectedTtsLanguage =
            sharedPref.getString(languageKey, "Auto (Detect from Book)") ?: "Auto (Detect from Book)"
        val ttsLanguageOptions =
            arrayOf(
                "Auto (Detect from Book)",
                "English",
                "Spanish",
                "French",
                "German",
                "Italian",
                "Portuguese",
                "Russian",
                "Chinese (Simplified)",
                "Chinese (Traditional)",
                "Japanese",
                "Korean",
                "Arabic",
                "Hindi",
                "Bengali",
                "Punjabi",
                "Urdu",
                "Turkish",
                "Dutch",
                "Swedish",
                "Norwegian",
                "Danish",
                "Finnish",
                "Polish",
                "Czech",
                "Greek",
                "Hebrew",
                "Thai",
                "Vietnamese",
                "Indonesian",
                "Malay"
            )
        val languagePosition = ttsLanguageOptions.indexOf(selectedTtsLanguage)
        if (languagePosition >= 0) {
            ttsLanguageSpinner.setSelection(languagePosition)
        } else {
            // Default to "Auto (Detect from Book)"
            ttsLanguageSpinner.setSelection(0)
        }

        // Load TTS rate from SharedPreferences
        val ttsRate = sharedPref.getFloat(rateKey, 1.0f)
        val rateProgress = ((ttsRate - 0.5f) * 100).toInt() // Convert 0.5-2.0 range to 0-150 range
        speechRateSeekBar.progress = rateProgress.coerceIn(0, 150)
        speechRateValueTextView.text = "Current rate: ${String.format("%.1f", ttsRate)}x"

        // Load TTS pitch from SharedPreferences
        val ttsPitch = sharedPref.getFloat(pitchKey, 1.0f)
        val pitchProgress = ((ttsPitch - 0.8f) * 100).toInt() // Convert 0.8-1.2 range to 0-40 range
        speechPitchSeekBar.progress = pitchProgress.coerceIn(0, 40)
        speechPitchValueTextView.text = "Current pitch: ${String.format("%.1f", ttsPitch)}x"

        // Load TTS voice from SharedPreferences into the text field
        val selectedTtsVoice = sharedPref.getString(voiceKey, "")
        voiceEditText.setText(selectedTtsVoice)

        // Update the TTSManager to use the correct engine
        val ttsManager = TTSManager.getInstance(requireContext())
        ttsManager.setSelectedTTSEngine(engine)
    }

    private fun setupTtsRateSeekBar() {
        // Initialize the seekbar progress based on saved values for the currently selected engine
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = ttsEngineSpinner.selectedItem?.toString() ?: "Android TTS"

        // Determine which key to use based on the selected engine
        val rateKey = if (selectedTtsEngine == "Kokoro TTS") TTS_RATE_KOKORO else TTS_RATE_ANDROID
        val savedRate = sharedPref.getFloat(rateKey, 1.0f)
        val rateProgress =
            ((savedRate - 0.5f) * 100).toInt() // Convert 0.5-2.0 range to 0-150 range
        speechRateSeekBar.progress = rateProgress.coerceIn(0, 150)
        speechRateValueTextView.text = "Current rate: ${String.format("%.1f", savedRate)}x"

        // Set up the rate seekbar listener
        speechRateSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // Convert progress (0-150) to rate (0.5-2.0)
                    val rate = (progress / 100f) + 0.5f
                    speechRateValueTextView.text =
                        "Current rate: ${String.format("%.1f", rate)}x"
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun setupTtsPitchSeekBar() {
        // Initialize the seekbar progress based on saved values for the currently selected engine
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = ttsEngineSpinner.selectedItem?.toString() ?: "Android TTS"

        // Determine which key to use based on the selected engine
        val pitchKey = if (selectedTtsEngine == "Kokoro TTS") TTS_PITCH_KOKORO else TTS_PITCH_ANDROID
        val savedPitch = sharedPref.getFloat(pitchKey, 1.0f)
        val pitchProgress =
            ((savedPitch - 0.8f) * 100).toInt() // Convert 0.8-1.2 range to 0-40 range
        speechPitchSeekBar.progress = pitchProgress.coerceIn(0, 40)
        speechPitchValueTextView.text = "Current pitch: ${String.format("%.1f", savedPitch)}x"

        // Set up the pitch seekbar listener
        speechPitchSeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // Convert progress (0-40) to pitch (0.8-1.2)
                    val pitch = (progress / 100f) + 0.8f
                    speechPitchValueTextView.text =
                        "Current pitch: ${String.format("%.1f", pitch)}x"
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun setupTtsVoiceSelection() {
        // Set up the button to show available voices
        showVoicesButton.setOnClickListener {
            showAvailableVoicesDialog()
        }

        // Load previously selected TTS voice for the currently selected engine
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = ttsEngineSpinner.selectedItem?.toString() ?: "Android TTS"

        // Determine which key to use based on the selected engine
        val voiceKey = if (selectedTtsEngine == "Kokoro TTS") TTS_VOICE_KOKORO else TTS_VOICE_ANDROID
        val selectedTtsVoice = sharedPref.getString(voiceKey, "")
        voiceEditText.setText(selectedTtsVoice)
    }

    private fun showAvailableVoicesDialog() {
        // Get available voices based on the selected TTS engine
        val selectedTtsEngine = ttsEngineSpinner.selectedItem?.toString() ?: "Android TTS"

        // Initialize TTSManager if needed before retrieving voices
        val ttsManager = TTSManager.getInstance(requireContext())
        if (!ttsManager.isInitialized()) {
            ttsManager.initializeTTS { success ->
                if (!success) {
                    android.util.Log.e("AppSettingsFragment", "Failed to initialize TTS for voice retrieval")
                    // Continue with the dialog using defaults if initialization failed
                    showVoicesDialogWithEngine(selectedTtsEngine)
                } else {
                    // TTSManager initialized successfully, proceed with dialog
                    showVoicesDialogWithEngine(selectedTtsEngine)
                }
            }
        } else {
            // TTSManager already initialized, proceed with dialog
            showVoicesDialogWithEngine(selectedTtsEngine)
        }
    }

    private fun showVoicesDialogWithEngine(selectedTtsEngine: String) {
        if (selectedTtsEngine == "Kokoro TTS") {
            // For Kokoro TTS, provide all available voice names with language and grade info
            val voiceOptions = listOf(
                // American English (F: 11, M: 9)
                "af_heart (English A)",
                "af_alloy (English C)",
                "af_aoede (English C+)",
                "af_bella (English A-)",
                "af_jessica (English D)",
                "af_kore (English C+)",
                "af_nicole (English B-)",
                "af_nova (English C)",
                "af_river (English D)",
                "af_sarah (English C+)",
                "af_sky (English C-)",
                "am_adam (English F+)",
                "am_echo (English D)",
                "am_eric (English D)",
                "am_fenrir (English C+)",
                "am_liam (English D)",
                "am_michael (English C+)",
                "am_onyx (English D)",
                "am_puck (English C+)",
                "am_santa (English D-)",

                // British English (F: 4, M: 4)
                "bf_alice (English D)",
                "bf_emma (English B-)",
                "bf_isabella (English C)",
                "bf_lily (English D)",
                "bm_daniel (English D)",
                "bm_fable (English C)",
                "bm_george (English C)",
                "bm_lewis (English D+)",

                // Japanese (F: 4, M: 1)
                "jf_alpha (Japanese C+)",
                "jf_gongitsune (Japanese C)",
                "jf_nezumi (Japanese C-)",
                "jf_tebukuro (Japanese C)",
                "jm_kumo (Japanese C)",

                // Mandarin Chinese (F: 4, M: 4)
                "zf_xiaobei (Chinese D)",
                "zf_xiaoni (Chinese D)",
                "zf_xiaoxiao (Chinese D)",
                "zf_xiaoyi (Chinese D)",

                // Spanish (F: 1, M: 2)
                "ef_dora (Spanish)",
                "em_alex (Spanish)",
                "em_santa (Spanish)",

                // French (F: 1)
                "ff_siwis (French B-)",

                // Hindi (F: 2, M: 2)
                "hf_alpha (Hindi C)",
                "hf_beta (Hindi C)",
                "hm_omega (Hindi C)",
                "hm_psi (Hindi C)",

                // Italian (F: 1, M: 1)
                "if_sara (Italian C)",
                "im_nicola (Italian C)",

                // Brazilian Portuguese (F: 1, M: 2)
                "pf_dora (Portuguese)",
                "pm_alex (Portuguese)",
                "pm_santa (Portuguese)"
            )

            // Show the dialog with Kokoro voices
            showVoiceSelectionDialog(voiceOptions)
        } else {
            // For Android TTS, get actual system voices
            val ttsManager = TTSManager.getInstance(requireContext())

            // Check if we need to switch to Android TTS engine
            // This will properly initialize the TextToSpeech engine if needed
            ttsManager.switchToEngine("Android TTS") { success ->
                if (success) {
                    // Add a small delay to allow the TTS engine to load voices
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val androidVoices = ttsManager.getAvailableVoices()
                        val voiceOptions = if (androidVoices.isNotEmpty()) {
                            androidVoices.sorted()
                        } else {
                            listOf("No system voices available")
                        }
                        showVoiceSelectionDialog(voiceOptions)
                    }, 300)
                } else {
                    showVoiceSelectionDialog(listOf("Failed to initialize Android TTS - no voices available"))
                }
            }
        }
    }

    private fun showVoiceSelectionDialog(voiceOptions: List<String>) {
        // Create a dialog with the voice options
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select Voice(s)")

        // Create a string array from the voice options
        val voiceArray = voiceOptions.toTypedArray()

        // Create a single-select list for now (can be enhanced for multi-select later)
        builder.setItems(voiceArray) { _, which ->
            val selectedVoiceWithInfo = voiceArray[which]
            // Extract just the voice name part (before the parentheses)
            val selectedVoice = if (selectedVoiceWithInfo.contains(" (")) {
                selectedVoiceWithInfo.substring(0, selectedVoiceWithInfo.indexOf(" ("))
            } else {
                selectedVoiceWithInfo
            }
            voiceEditText.setText(selectedVoice)
        }

        builder.setNegativeButton("Cancel", null)

        // Only show "Voice Mixing Help" for Kokoro TTS, not for Android TTS
        val selectedTtsEngine = ttsEngineSpinner.selectedItem?.toString() ?: "Android TTS"
        if (selectedTtsEngine == "Kokoro TTS") {
            builder.setPositiveButton("Voice Mixing Help") { _, _ ->
                // Show help message about voice mixing
                val helpMessage = "Voice mixing:\n- Use single voice: af_bella\n" +
                        "- Mix voices: af_bella+af_heart\n" +
                        "- Weighted mix: af_bella(2)+af_heart(1) for 67%/33% mix"
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Voice Mixing Help")
                    .setMessage(helpMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        } else {
            // For Android TTS, just use an OK button
            builder.setPositiveButton("OK", null)
        }

        builder.show()
    }

    private fun fetchAvailableModels() {
        val endpoint = aiEndpointEditText.text.toString().trim()

        if (endpoint.isEmpty()) {
            android.widget.Toast.makeText(
                requireContext(),
                "Please enter the OpenAI endpoint URL first",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Convert /v1/chat/completions to /v1/models
        val modelsEndpoint = endpoint.replace("/v1/chat/completions", "/v1/models")

        android.util.Log.d("AppSettingsFragment", "Fetching models from: $modelsEndpoint")

        Thread {
            try {
                val url = java.net.URL(modelsEndpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 30000

                val aiApiKey = aiApiKeyEditText.text.toString().trim()
                if (aiApiKey.isNotEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer $aiApiKey")
                }

                val responseCode = connection.responseCode
                android.util.Log.d("AppSettingsFragment", "Models response code: $responseCode")

                if (responseCode == 200) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()

                    android.util.Log.d("AppSettingsFragment", "Models response: $response")

                    val models = parseModelsResponse(response)
                    activity?.runOnUiThread {
                        if (models.isNotEmpty()) {
                            showModelSelectionDialog(models)
                        } else {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "No models found",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    errorStream?.close()

                    android.util.Log.e("AppSettingsFragment", "Models error response: $errorResponse")
                    activity?.runOnUiThread {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to fetch models: HTTP $responseCode",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("AppSettingsFragment", "Error fetching models", e)
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Error fetching models: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun parseModelsResponse(response: String): List<String> {
        val models = mutableListOf<String>()
        try {
            val jsonObject = org.json.JSONObject(response)
            val data = jsonObject.optJSONArray("data")

            if (data != null && data.length() > 0) {
                for (i in 0 until data.length()) {
                    val model = data.getJSONObject(i)
                    val modelId = model.optString("id", "")
                    if (modelId.isNotEmpty()) {
                        models.add(modelId)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error parsing models response", e)
        }
        return models
    }

    private fun showModelSelectionDialog(modelOptions: List<String>) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Select AI Model")

        val modelArray = modelOptions.sorted().toTypedArray()

        builder.setItems(modelArray) { _, which ->
            val selectedModel = modelArray[which]
            aiModelEditText.setText(selectedModel)
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAiInstructions() {
        val instructionsMessage =
            "AI Integration Instructions:\n\n" +
                    "For local LLM usage with OpenAI endpoints like Ollama or llama.cpp:\n\n" +
                    "1. Install and run Ollama (default port: 11434) or llama.cpp server (default port: 8080)\n" +
                    "2. Enter the endpoint URL (e.g., http://localhost:11434/v1/chat/completions for Ollama)\n" +
                    "3. Enter the model name (e.g., llama3, mistral, phi, Qwen3-30B-A3B)\n" +
                    "4. Customize prompts as needed and save settings"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("AI Integration Instructions")
            .setMessage(instructionsMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
