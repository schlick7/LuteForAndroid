package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import org.jsoup.Jsoup

class AppSettingsFragment : Fragment() {

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding
        get() = _binding!!
    private lateinit var serverSettingsManager: ServerSettingsManager
    private lateinit var languageAdapter: ArrayAdapter<String>
    private lateinit var visibilityAdapter: ArrayAdapter<String>
    private lateinit var themeModeAdapter: ArrayAdapter<String>
    private lateinit var nativeReaderThemeModeAdapter: ArrayAdapter<String>
    private lateinit var defaultReaderAdapter: ArrayAdapter<String>
    private lateinit var aiSettingsManager: AiSettingsManager
    private lateinit var ttsEngineAdapter: ArrayAdapter<String>
    private lateinit var ttsLanguageAdapter: ArrayAdapter<String>
    private lateinit var ttsVoiceAdapter: ArrayAdapter<String>

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

        // Set up language selection spinner by fetching languages from server
        setupLanguageSelection()

        // Load the current server URL and settings
        loadCurrentSettings()

        // Set up the save buttons
        binding.buttonSaveSettings.setOnClickListener { saveUrlSettings() }

        // Combined button for header display settings (language + visibility)
        binding.buttonSaveLanguage.setOnClickListener {
            saveLanguageSettings()
            saveVisibilitySettings()
            // Show combined success message
            binding.textViewStatus.text = "Header display settings saved successfully!"
            binding.textViewStatus.setTextColor(
                resources.getColor(android.R.color.holo_green_dark, null)
            )
            Toast.makeText(
                context,
                "Header display settings saved successfully",
                Toast.LENGTH_SHORT
            )
                .show()
        }

        binding.buttonSaveTheme.setOnClickListener { saveThemeSettings() }

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

        // Set up the default reader selection spinner
        setupDefaultReaderSelection()

        // Set up the save default reader button
        binding.buttonSaveDefaultReader.setOnClickListener { saveDefaultReaderSettings() }

        // Set up the save AI settings button
        binding.buttonSaveAiSettings.setOnClickListener { saveAiSettings() }

        // Load AI settings
        loadAiSettings()

        // Set up TTS engine selection spinner
        setupTtsEngineSelection()

        // Set up the save TTS settings button
        binding.buttonSaveTtsSettings.setOnClickListener { saveTtsSettings() }

        // Set up speech rate SeekBar
        setupTtsRateSeekBar()

        // Set up speech pitch SeekBar
        setupTtsPitchSeekBar()

        // Set up TTS voice selection spinner
        setupTtsVoiceSelection()

        // Load TTS settings
        loadTtsSettings()
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
                    binding.spinnerLanguageSelection.adapter = languageAdapter

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
                        binding.spinnerLanguageSelection.setSelection(position)
                    } else {
                        // Default to first language in the list
                        binding.spinnerLanguageSelection.setSelection(0)
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
                    binding.spinnerLanguageSelection.adapter = languageAdapter

                    // Load previously selected language
                    val sharedPref =
                        requireContext()
                            .getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val selectedLanguage = sharedPref.getString("selected_language", "Auto")
                    val position = defaultLanguages.indexOf(selectedLanguage)
                    if (position >= 0) {
                        binding.spinnerLanguageSelection.setSelection(position)
                    } else {
                        // Default to "Auto"
                        binding.spinnerLanguageSelection.setSelection(0)
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
            binding.editTextServerUrl.setText(currentUrl)
            binding.textViewStatus.text = "Current server URL loaded"
        } else {
            // Set placeholder text if no URL is configured
            binding.editTextServerUrl.hint = "Enter server URL (e.g., http://192.168.1.100:5001)"
            binding.textViewStatus.text = "No server URL configured"
        }
    }

    private fun saveUrlSettings() {
        val serverUrl = binding.editTextServerUrl.text.toString().trim()

        if (serverUrl.isEmpty()) {
            binding.textViewStatus.text = "Please enter a server URL"
            binding.textViewStatus.setTextColor(
                resources.getColor(android.R.color.holo_red_dark, null)
            )
            return
        }

        if (!serverSettingsManager.isValidUrl(serverUrl)) {
            binding.textViewStatus.text =
                "Please enter a valid URL starting with http:// or https://"
            binding.textViewStatus.setTextColor(
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
        binding.textViewStatus.text = "URL saved successfully!"
        binding.textViewStatus.setTextColor(
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
        val selectedLanguage = binding.spinnerLanguageSelection.selectedItem.toString()
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
            binding.textViewStatus.append("\nTesting server connectivity...")

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
                binding.textViewStatus.append("\nServer is reachable")
                binding.textViewStatus.setTextColor(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )

                // Show a toast message to inform the user that the app will restart
                Toast.makeText(
                    context,
                    "Server is reachable! Restarting app to apply changes...",
                    Toast.LENGTH_LONG
                )
                    .show()

                // Restart the app to ensure proper navigation state
                restartApp()
            } else {
                binding.textViewStatus.append("\nServer not available")
                binding.textViewStatus.setTextColor(
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
        binding.spinnerWordCountVisibility.adapter = visibilityAdapter

        // Load previously selected visibility setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedVisibility = sharedPref.getString("word_count_visibility", "Visible")
        val position = visibilityOptions.indexOf(selectedVisibility)
        if (position >= 0) {
            binding.spinnerWordCountVisibility.setSelection(position)
        } else {
            // Default to "Visible"
            binding.spinnerWordCountVisibility.setSelection(0)
        }
    }

    private fun saveVisibilitySettings() {
        // Save the selected visibility
        val selectedVisibility = binding.spinnerWordCountVisibility.selectedItem.toString()
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
        binding.spinnerThemeMode.adapter = themeModeAdapter

        // Load previously selected theme mode setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedThemeMode = sharedPref.getString("theme_mode", "App Theme")
        val position = themeModeOptions.indexOf(selectedThemeMode)
        if (position >= 0) {
            binding.spinnerThemeMode.setSelection(position)
        } else {
            // Default to "App Theme"
            binding.spinnerThemeMode.setSelection(0)
        }
    }

    /** Set up the native reader theme mode spinner */
    private fun setupNativeReaderThemeModeSelection() {
        // Create theme mode options array
        val themeModeOptions = arrayOf("App Settings", "Custom")
        nativeReaderThemeModeAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item, themeModeOptions)
        nativeReaderThemeModeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerNativeReaderThemeMode.adapter = nativeReaderThemeModeAdapter

        // Load previously selected native reader theme mode setting
        val sharedPref =
            requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        val selectedThemeMode = sharedPref.getString("native_reader_theme_mode", "App Settings")
        val position = themeModeOptions.indexOf(selectedThemeMode)
        if (position >= 0) {
            binding.spinnerNativeReaderThemeMode.setSelection(position)
        } else {
            // Default to "App Settings"
            binding.spinnerNativeReaderThemeMode.setSelection(0)
        }

        // Set up listener to show/hide textboxes based on selection
        binding.spinnerNativeReaderThemeMode.onItemSelectedListener =
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
                            binding.textViewBackgroundColorLabel.visibility =
                                android.view.View.GONE
                            binding.editTextBackgroundColor.visibility = android.view.View.GONE
                            binding.textViewTextColorLabel.visibility = android.view.View.GONE
                            binding.editTextTextColor.visibility = android.view.View.GONE
                        }

                        1 -> { // Custom
                            // Show textboxes
                            binding.textViewBackgroundColorLabel.visibility =
                                android.view.View.VISIBLE
                            binding.editTextBackgroundColor.visibility =
                                android.view.View.VISIBLE
                            binding.textViewTextColorLabel.visibility =
                                android.view.View.VISIBLE
                            binding.editTextTextColor.visibility = android.view.View.VISIBLE
                        }
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                    // Hide textboxes by default
                    binding.textViewBackgroundColorLabel.visibility = android.view.View.GONE
                    binding.editTextBackgroundColor.visibility = android.view.View.GONE
                    binding.textViewTextColorLabel.visibility = android.view.View.GONE
                    binding.editTextTextColor.visibility = android.view.View.GONE
                }
            }

        // Trigger the listener to set initial visibility
        binding.spinnerNativeReaderThemeMode.onItemSelectedListener?.onItemSelected(
            binding.spinnerNativeReaderThemeMode,
            null,
            binding.spinnerNativeReaderThemeMode.selectedItemPosition,
            0
        )
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
            binding.spinnerNativeReaderThemeMode.setSelection(position)
        } else {
            // Default to "App Settings"
            binding.spinnerNativeReaderThemeMode.setSelection(0)
        }

        // Set text values
        binding.editTextBackgroundColor.setText(backgroundColor)
        binding.editTextTextColor.setText(textColor)

        // Trigger the listener to set initial visibility
        binding.spinnerNativeReaderThemeMode.onItemSelectedListener?.onItemSelected(
            binding.spinnerNativeReaderThemeMode,
            null,
            binding.spinnerNativeReaderThemeMode.selectedItemPosition,
            0
        )
    }

    /** Save Native Reader Theme settings to SharedPreferences */
    private fun saveNativeReaderThemeSettings() {
        val selectedThemeMode = binding.spinnerNativeReaderThemeMode.selectedItem.toString()

        val sharedPref =
            requireContext().getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("native_reader_theme_mode", selectedThemeMode)

            // Only save custom colors if Custom mode is selected
            if (selectedThemeMode == "Custom") {
                val backgroundColor = binding.editTextBackgroundColor.text.toString().trim()
                val textColor = binding.editTextTextColor.text.toString().trim()

                // Validate hex colors
                if (backgroundColor.isNotEmpty() && !isValidHexColor(backgroundColor)) {
                    binding.textViewStatus.text = "Invalid background color format. Use #RRGGBB."
                    binding.textViewStatus.setTextColor(
                        resources.getColor(android.R.color.holo_red_dark, null)
                    )
                    return
                }

                if (textColor.isNotEmpty() && !isValidHexColor(textColor)) {
                    binding.textViewStatus.text = "Invalid text color format. Use #RRGGBB."
                    binding.textViewStatus.setTextColor(
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
        binding.spinnerDefaultReader.adapter = defaultReaderAdapter

        // Load previously selected default reader setting, defaulting to "Native Reader"
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedDefaultReader = sharedPref.getString("default_reader", "Native Reader")
        val position = defaultReaderOptions.indexOf(selectedDefaultReader)
        if (position >= 0) {
            binding.spinnerDefaultReader.setSelection(position)
        } else {
            // Default to "Native Reader"
            binding.spinnerDefaultReader.setSelection(1) // Index 1 is "Native Reader"
        }
    }

    private fun saveDefaultReaderSettings() {
        // Save the selected default reader
        val selectedDefaultReader = binding.spinnerDefaultReader.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("default_reader", selectedDefaultReader)
            apply()
        }

        // Update the status
        binding.textViewStatus.text = "Default reader settings saved successfully!"
        binding.textViewStatus.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show a toast message
        Toast.makeText(context, "Default reader settings saved successfully", Toast.LENGTH_SHORT)
            .show()
    }

    private fun saveThemeSettings() {
        // Save the selected theme mode
        val selectedThemeMode = binding.spinnerThemeMode.selectedItem.toString()
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("theme_mode", selectedThemeMode)

            // Save the CSS injection setting
            putBoolean("disable_css_injection", binding.checkboxDisableCssInjection.isChecked)
            apply()
        }

        // Save Native Reader Theme settings
        saveNativeReaderThemeSettings()

        // Update the status
        binding.textViewStatus.text = "Theme settings saved successfully!"
        binding.textViewStatus.setTextColor(
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
        val aiEndpoint = binding.editTextAiEndpoint.text.toString().trim()
        val aiApiKey = binding.editTextAiApiKey.text.toString().trim()
        val aiPromptTerm = binding.editTextAiPromptTerm.text.toString().trim()
        val aiPromptSentence = binding.editTextAiPromptSentence.text.toString().trim()
        val aiModel = binding.editTextAiModel.text.toString().trim()
        val showAiButtonTerm = binding.checkboxShowAiButtonTerm.isChecked
        val showAiButtonSentence = binding.checkboxShowAiButtonSentence.isChecked

        // Validate AI endpoint URL if provided
        if (aiEndpoint.isNotEmpty() && !isValidUrl(aiEndpoint)) {
            binding.textViewStatus.text = "Please enter a valid AI endpoint URL"
            binding.textViewStatus.setTextColor(
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
        binding.textViewStatus.text = "AI settings saved successfully!"
        binding.textViewStatus.setTextColor(
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
        binding.editTextAiEndpoint.setText(aiEndpoint)
        binding.editTextAiApiKey.setText(aiApiKey)
        binding.editTextAiPromptTerm.setText(aiPromptTerm)
        binding.editTextAiPromptSentence.setText(aiPromptSentence)
        binding.editTextAiModel.setText(aiModel)
        binding.checkboxShowAiButtonTerm.isChecked = showAiButtonTerm
        binding.checkboxShowAiButtonSentence.isChecked = showAiButtonSentence
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
        // Create TTS engine options array - starting with Android TTS
        val ttsEngineOptions = arrayOf("Android TTS")
        ttsEngineAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, ttsEngineOptions)
        ttsEngineAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerTtsEngine.adapter = ttsEngineAdapter

        // Load previously selected TTS engine setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = sharedPref.getString("tts_engine", "Android TTS")
        val position = ttsEngineOptions.indexOf(selectedTtsEngine)
        if (position >= 0) {
            binding.spinnerTtsEngine.setSelection(position)
        } else {
            // Default to "Android TTS"
            binding.spinnerTtsEngine.setSelection(0)
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
        binding.spinnerTtsLanguage.adapter = ttsLanguageAdapter

        // Load previously selected TTS language setting
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsLanguage = sharedPref.getString("tts_language", "Auto (Detect from Book)")
        val position = ttsLanguageOptions.indexOf(selectedTtsLanguage)
        if (position >= 0) {
            binding.spinnerTtsLanguage.setSelection(position)
        } else {
            // Default to "Auto (Detect from Book)"
            binding.spinnerTtsLanguage.setSelection(0)
        }
    }

    private fun saveTtsSettings() {
        // Save the selected TTS engine and language
        val selectedTtsEngine = binding.spinnerTtsEngine.selectedItem.toString()
        val selectedTtsLanguage = binding.spinnerTtsLanguage.selectedItem.toString()

        // Get the TTS rate from the seekbar
        val rateProgress = binding.seekBarTtsRate.progress
        val ttsRate = (rateProgress / 100f) + 0.5f // Convert 0-150 range to 0.5-2.0 range

        // Get the TTS pitch from the seekbar
        val pitchProgress = binding.seekBarTtsPitch.progress
        val ttsPitch = (pitchProgress / 100f) + 0.8f // Convert 0-40 range to 0.8-1.2 range

        // Get the selected TTS voice
        val selectedTtsVoice =
            if (ttsVoiceAdapter.count > 0) {
                binding.spinnerTtsVoice.selectedItem?.toString() ?: ""
            } else {
                ""
            }

        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("tts_engine", selectedTtsEngine)
            putString("tts_language", selectedTtsLanguage)
            putFloat("tts_rate", ttsRate)
            putFloat("tts_pitch", ttsPitch)
            putString("tts_voice", selectedTtsVoice)
            apply()
        }

        // Apply the settings to the TTS manager
        applyTtsSettingsToManager(ttsRate, ttsPitch, selectedTtsVoice)

        // Update the status
        binding.textViewStatus.text = "TTS settings saved successfully!"
        binding.textViewStatus.setTextColor(
            resources.getColor(android.R.color.holo_green_dark, null)
        )

        // Show a toast message
        Toast.makeText(context, "TTS settings saved successfully", Toast.LENGTH_SHORT).show()
    }

    private fun applyTtsSettingsToManager(rate: Float, pitch: Float, voice: String) {
        try {
            val ttsManager = TTSManager.getInstance(requireContext())
            ttsManager.setSpeechRate(rate)
            ttsManager.setPitch(pitch)
            if (voice.isNotEmpty()) {
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
        binding.checkboxDisableCssInjection.isChecked = isCssInjectionDisabled
    }

    private fun loadTtsSettings() {
        // Load the selected TTS engine from SharedPreferences
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsEngine = sharedPref.getString("tts_engine", "Android TTS")
        val ttsEngineOptions = arrayOf("Android TTS")
        val position = ttsEngineOptions.indexOf(selectedTtsEngine)
        if (position >= 0) {
            binding.spinnerTtsEngine.setSelection(position)
        } else {
            // Default to "Android TTS"
            binding.spinnerTtsEngine.setSelection(0)
        }

        // Load the selected TTS language from SharedPreferences
        val selectedTtsLanguage = sharedPref.getString("tts_language", "Auto (Detect from Book)")
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
            binding.spinnerTtsLanguage.setSelection(languagePosition)
        } else {
            // Default to "Auto (Detect from Book)"
            binding.spinnerTtsLanguage.setSelection(0)
        }

        // Load TTS rate from SharedPreferences
        val ttsRate = sharedPref.getFloat("tts_rate", 1.0f)
        val rateProgress = ((ttsRate - 0.5f) * 100).toInt() // Convert 0.5-2.0 range to 0-150 range
        binding.seekBarTtsRate.progress = rateProgress.coerceIn(0, 150)
        binding.textViewTtsRateValue.text = "Current rate: ${String.format("%.1f", ttsRate)}x"

        // Load TTS pitch from SharedPreferences
        val ttsPitch = sharedPref.getFloat("tts_pitch", 1.0f)
        val pitchProgress = ((ttsPitch - 0.8f) * 100).toInt() // Convert 0.8-1.2 range to 0-40 range
        binding.seekBarTtsPitch.progress = pitchProgress.coerceIn(0, 40)
        binding.textViewTtsPitchValue.text = "Current pitch: ${String.format("%.1f", ttsPitch)}x"

        // Load TTS voice from SharedPreferences
        val selectedTtsVoice = sharedPref.getString("tts_voice", "")
        val voiceOptions = getAvailableTtsVoices()
        if (voiceOptions.isNotEmpty()) {
            val voicePosition = voiceOptions.indexOf(selectedTtsVoice)
            if (voicePosition >= 0) {
                binding.spinnerTtsVoice.setSelection(voicePosition)
            } else {
                // Default to first voice if available
                if (voiceOptions.isNotEmpty()) {
                    binding.spinnerTtsVoice.setSelection(0)
                }
            }
        }
    }

    private fun setupTtsRateSeekBar() {
        // Initialize the seekbar progress based on saved values
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedRate = sharedPref.getFloat("tts_rate", 1.0f)
        val rateProgress =
            ((savedRate - 0.5f) * 100).toInt() // Convert 0.5-2.0 range to 0-150 range
        binding.seekBarTtsRate.progress = rateProgress.coerceIn(0, 150)
        binding.textViewTtsRateValue.text = "Current rate: ${String.format("%.1f", savedRate)}x"

        // Set up the rate seekbar listener
        binding.seekBarTtsRate.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // Convert progress (0-150) to rate (0.5-2.0)
                    val rate = (progress / 100f) + 0.5f
                    binding.textViewTtsRateValue.text =
                        "Current rate: ${String.format("%.1f", rate)}x"
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun setupTtsPitchSeekBar() {
        // Initialize the seekbar progress based on saved values
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedPitch = sharedPref.getFloat("tts_pitch", 1.0f)
        val pitchProgress =
            ((savedPitch - 0.8f) * 100).toInt() // Convert 0.8-1.2 range to 0-40 range
        binding.seekBarTtsPitch.progress = pitchProgress.coerceIn(0, 40)
        binding.textViewTtsPitchValue.text = "Current pitch: ${String.format("%.1f", savedPitch)}x"

        // Set up the pitch seekbar listener
        binding.seekBarTtsPitch.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    // Convert progress (0-40) to pitch (0.8-1.2)
                    val pitch = (progress / 100f) + 0.8f
                    binding.textViewTtsPitchValue.text =
                        "Current pitch: ${String.format("%.1f", pitch)}x"
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )
    }

    private fun setupTtsVoiceSelection() {
        // Get available voices from TTS and sort them alphabetically
        val voiceOptions = getAvailableTtsVoices().sorted()
        ttsVoiceAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, voiceOptions)
        ttsVoiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.spinnerTtsVoice.adapter = ttsVoiceAdapter

        // Load previously selected TTS voice
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val selectedTtsVoice = sharedPref.getString("tts_voice", "")
        val position = voiceOptions.indexOf(selectedTtsVoice)
        if (position >= 0) {
            binding.spinnerTtsVoice.setSelection(position)
        } else if (voiceOptions.isNotEmpty()) {
            // Default to first voice
            binding.spinnerTtsVoice.setSelection(0)
        }
    }

    private fun getAvailableTtsVoices(): List<String> {
        // Create a dummy TTSManager to get available voices
        // or get them from the existing instance
        try {
            val ttsManager = TTSManager.getInstance(requireContext())
            val voices = ttsManager.getAvailableVoices()
            return if (voices.isNotEmpty()) {
                // Sort voices alphabetically
                voices.sorted()
            } else {
                // Return some default voices as fallback
                listOf("Default Voice")
            }
        } catch (e: Exception) {
            android.util.Log.e("AppSettingsFragment", "Error getting available TTS voices", e)
            return listOf("Default Voice")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
