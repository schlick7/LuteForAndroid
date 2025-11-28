package com.example.luteforandroidv2.ui.nativeread.Term

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.DialogNativeTermFormBinding
import com.example.luteforandroidv2.ui.nativeread.Translation.TranslationCacheManager
import com.example.luteforandroidv2.ui.settings.AiSettingsManager
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.NetworkTestUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*

class NativeTermFormFragment : DialogFragment(), TranslationCacheManager.TranslationObserver {

    private var _binding: DialogNativeTermFormBinding? = null
    private val binding
        get() = _binding ?: throw IllegalStateException("Binding is not initialized")

    private var termFormData: TermFormData? = null
    private var storedTermData: TermData? = null // Stored term data from ReadFragment
    private var parentTermDataMap: MutableMap<String, TermFormData> =
            mutableMapOf() // Map to store parent term data
    private var saveCallback: ((TermFormData) -> Unit)? = null
    private var cancelCallback: (() -> Unit)? = null
    private var dictionaryListener: DictionaryListener? = null
    // Whether this is a parent term form (used to avoid clearing cache when saving parent terms)
    private var isParentTermForm: Boolean = false

    // Add the missing variable declarations
    private var selectedStatus = 0
    private val parentButtons = mutableListOf<androidx.appcompat.widget.AppCompatTextView>()
    private val statusButtons = mutableListOf<Button>()
    private var lastClickedParentView: View? = null
    // Flag to prevent infinite loop when updating text programmatically
    private var isProgrammaticUpdate = false
    // Flag to track if the term is linked to its parents
    private var isLinked = false
    // Map to track last click times for parent double tap detection
    private val lastParentClickTimes = mutableMapOf<String, Long>()
    // RecyclerView for parent term suggestions
    private var parentSuggestionsAdapter: ParentTermSuggestionsAdapter? = null

    interface DictionaryListener {
        fun onDictionaryClosed()
        fun onDictionaryLookup(term: String)
        fun onDictionaryTextSelected(text: String)
        fun onTermFormDestroyed()
        fun onTermSaved(updatedTermData: TermFormData? = null)
    }

    companion object {
        // Tag key for autocomplete text watcher
        private const val AUTOCOMPLETE_TEXT_WATCHER_TAG = 1001

        fun newInstance(
                termFormData: TermFormData,
                storedTermData: TermData? = null,
                isParentTermForm: Boolean = false,
                onSave: (TermFormData) -> Unit,
                onCancel: () -> Unit,
                dictionaryListener: DictionaryListener
        ): NativeTermFormFragment {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "newInstance called with termFormData: $termFormData, storedTermData: $storedTermData, isParentTermForm: $isParentTermForm"
            )
            if (storedTermData != null) {
                android.util.Log.d(
                        "NativeTermFormFragment",
                        "storedTermData language ID: ${storedTermData.languageId}"
                )
            }
            return NativeTermFormFragment().apply {
                this.termFormData = termFormData
                this.storedTermData = storedTermData
                this.saveCallback = onSave
                this.cancelCallback = onCancel
                this.dictionaryListener = dictionaryListener
                this.isParentTermForm = isParentTermForm
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext()).setCancelable(false).create()
        // Also set this property to ensure the dialog is not cancellable
        dialog.setCanceledOnTouchOutside(false)
        // Remove the dimming effect that might cause dismissal when clicking outside
        dialog.window?.setDimAmount(0f)
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the dialog fragment is not cancellable
        isCancelable = false
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = DialogNativeTermFormBinding.inflate(inflater, container, false)

        // Initialize selectedStatus with the value from termFormData if available
        selectedStatus = termFormData?.status ?: 1 // Default to status 1 if not available

        // Register as observer for translation text changes, but only for main term forms, not
        // parent term forms
        if (!isParentTermForm) {
            TranslationCacheManager.getInstance().addObserver(this)
        } else {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Not registering as cache observer because this is a parent term form"
            )
        }

        val dialog = dialog
        if (dialog is androidx.appcompat.app.AlertDialog) {
            dialog.setView(binding.root)
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Setup UI elements after the dialog is created
        setupUI()

        // Add TextWatcher to sync translation text changes to cache
        binding.translationText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}
                    override fun afterTextChanged(s: Editable?) {
                        // Only update cache if this is not a programmatic update to avoid loops
                        // Only do this for main term forms, not parent term forms
                        if (!isProgrammaticUpdate && !isParentTermForm) {
                            val currentText = s?.toString() ?: ""
                            TranslationCacheManager.getInstance()
                                    .setTemporaryTranslation(currentText)
                        }
                    }
                }
        )
    }

    private fun setupUI() {
        android.util.Log.d("NativeTermFormFragment", "setupUI called")
        // Set initial data in the form fields
        termFormData?.let { data ->
            android.util.Log.d("NativeTermFormFragment", "Setting term text: ${data.termText}")
            binding.termText.setText(data.termText)
            // Set the text color to use the theme's default text color instead of default black
            val typedValue = android.util.TypedValue()
            requireContext()
                    .theme
                    .resolveAttribute(
                            com.google.android.material.R.attr.colorOnSurface,
                            typedValue,
                            true
                    )
            binding.termText.setTextColor(typedValue.data)
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Setting translation text: ${data.translation}"
            )
            // For parent term forms, set the text directly without updating the shared cache
            // For main term forms, use setTranslationText to ensure both UI and cache are updated
            if (isParentTermForm) {
                activity?.runOnUiThread { binding.translationText.setText(data.translation) }
            } else {
                // Use the setTranslationText method to ensure both UI and cache are updated
                setTranslationText(data.translation)
            }
            // Set the text color to use the theme's default text color instead of default black
            binding.translationText.setTextColor(typedValue.data)
            // Create parent buttons with default colors first
            android.util.Log.d("NativeTermFormFragment", "Creating parent buttons: ${data.parents}")
            createParentButtons(data.parents)

            // Then fetch parent term data to update button colors
            if (data.parents.isNotEmpty()) {
                fetchParentTermData(data.parents)
            }

            // Initialize selectedStatus with the value from termFormData if available
            selectedStatus = data.status
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Initialized selectedStatus from termFormData: $selectedStatus"
            )

            // Initialize linking state from termFormData if available
            isLinked = data.isLinked
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Initialized isLinked from termFormData: $isLinked"
            )

            // If linked and has parents, update status to match parent
            if (isLinked && data.parents.isNotEmpty()) {
                updateStatusBasedOnParent()
            }

            // Also log the term data to see what we're working with
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Term data: termId=${data.termId}, termText=${data.termText}, isLinked=${data.isLinked}"
            )
            // Log all term data for debugging
            android.util.Log.d("NativeTermFormFragment", "Complete term data: $data")

            // Log the link button state after initialization
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Link button state after initialization - isLinked: $isLinked, button isSelected: ${binding.linkTermButton.isSelected}"
            )
        }
                ?: run {
                    // Default to status 1 if no termFormData is available
                    selectedStatus = 1
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Initialized selectedStatus to default: $selectedStatus"
                    )

                    // Initialize linking state from termFormData if available, or false if not
                    isLinked = false // Keep as false for new terms
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Initialized isLinked to default for new term: $isLinked"
                    )
                }

        // Log the selectedStatus before creating buttons
        android.util.Log.d(
                "NativeTermFormFragment",
                "Creating status buttons with selectedStatus: $selectedStatus"
        )

        // Create status buttons (this will use the selectedStatus set above)
        createStatusButtons()

        // Setup button listeners
        binding.addParentToggleButton.setOnClickListener { toggleAddParentVisibility() }

        // Setup link button
        binding.linkTermButton.setOnClickListener { toggleLinking() }
        // Update link button state immediately and also after UI is fully initialized
        updateLinkButtonState()
        binding.linkTermButton.post {
            // Only update if fragment is still added and binding is valid
            if (isAdded && _binding != null) {
                updateLinkButtonState()
                android.util.Log.d(
                        "NativeTermFormFragment",
                        "Link button state after updateLinkButtonState() - isLinked: $isLinked, button isSelected: ${binding.linkTermButton.isSelected}"
                )
            }
        }

        // Setup autocomplete functionality for the add parent text field
        setupParentAutocomplete()

        // Initialize the parent suggestions RecyclerView
        initializeParentSuggestionsRecyclerView()

        // Setup add parent button
        binding.addParentButton.setOnClickListener { addNewParent() }

        // Setup button listeners
        binding.saveButton.setOnClickListener { testServerAndSaveTerm() }

        binding.cancelButton.setOnClickListener {
            android.util.Log.d("NativeTermFormFragment", "Cancel button clicked")
            cancelCallback?.invoke()
            // Clear cache on cancel, but only for main term forms, not parent term forms
            if (!isParentTermForm) {
                TranslationCacheManager.getInstance().clearTemporaryTranslation()
            } else {
                android.util.Log.d(
                        "NativeTermFormFragment",
                        "Not clearing cache because this is a parent term form"
                )
            }
            android.util.Log.d("NativeTermFormFragment", "Calling dismiss()")
            dismiss()
            android.util.Log.d("NativeTermFormFragment", "dismiss() called")
        }

        binding.dictionaryLookupButton.setOnClickListener {
            val term = binding.termText.text.toString().trim()
            if (term.isNotEmpty()) {
                dictionaryListener?.onDictionaryLookup(term)
                // Don't dismiss the form when opening the dictionary
                // The dictionary will be shown on top of the term form
            } else {
                // Show a toast message if the term is empty
                android.widget.Toast.makeText(
                                requireContext(),
                                "Please enter a term to look up",
                                android.widget.Toast.LENGTH_SHORT
                        )
                        .show()
            }
        }

        // Setup AI button - uses term from term field with sentence context from stored data
        val aiSettingsManager = AiSettingsManager.getInstance(requireContext())
        if (aiSettingsManager.shouldShowAiButtonInTermForm()) {
            binding.aiButton.visibility = android.view.View.VISIBLE
            binding.aiButton.setOnClickListener {
                val term = binding.termText.text.toString().trim()

                if (term.isNotEmpty()) {
                    // Process using the term AI prompt, with sentence context from stored data
                    sendTermToAi(term)
                } else {
                    // Show a toast message if the term is empty
                    android.widget.Toast.makeText(
                                    requireContext(),
                                    "Please enter a term for AI processing",
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }
        } else {
            binding.aiButton.visibility = android.view.View.GONE
        }
    }

    private fun initializeParentSuggestionsRecyclerView() {
        val recyclerView = binding.parentSuggestionsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // Initially set an empty adapter
        parentSuggestionsAdapter =
                ParentTermSuggestionsAdapter(emptyList()) { suggestion ->
                    // When a suggestion is clicked
                    binding.newParentText.setText(suggestion)
                    // Move cursor to the end
                    binding.newParentText.setSelection(suggestion.length)
                    // Hide the suggestions list
                    recyclerView.visibility = View.GONE
                    // Clear the adapter
                    parentSuggestionsAdapter = ParentTermSuggestionsAdapter(emptyList()) {}
                    recyclerView.adapter = parentSuggestionsAdapter
                }
        recyclerView.adapter = parentSuggestionsAdapter
    }

    private fun setupTermAutocomplete() {
        // Add a text watcher to implement autocomplete functionality
        var searchJob: kotlinx.coroutines.Job? = null

        binding.termText.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        val term = s?.toString()?.trim() ?: ""
                        if (term.length >= 2) { // Start search after 2 characters
                            // Cancel previous search job if it's still running
                            searchJob?.cancel()

                            // Start a new coroutine with a delay to avoid excessive API calls
                            searchJob =
                                    kotlinx.coroutines.GlobalScope.launch(
                                            kotlinx.coroutines.Dispatchers.Main
                                    ) {
                                        kotlinx.coroutines.delay(
                                                300
                                        ) // 300ms delay to allow for typing

                                        // Check if the term text has changed since the delay
                                        // started
                                        if (binding.termText.text.toString().trim() == term) {
                                            performTermSearch(term)
                                        }
                                    }
                        }
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }
        )
    }

    private fun performTermSearch(searchTerm: String) {
        // Get the language ID for the search
        val langId = storedTermData?.languageId ?: termFormData?.languageId ?: return

        // Start a thread to make the network request
        Thread {
                    try {
                        val serverSettingsManager =
                                com.example.luteforandroidv2.ui.settings.ServerSettingsManager
                                        .getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Server URL not configured"
                            )
                            return@Thread
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()
                        val searchUrl = "$serverUrl/term/search/$searchTerm/$langId"
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Searching for term: $searchTerm, langId: $langId, URL: $searchUrl"
                        )

                        val url = java.net.URL(searchUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Accept", "application/json")

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Search response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            // Parse the JSON response
                            val searchResults = parseTermSearchResults(content)
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Search results parsed: ${searchResults.size} items"
                            )

                            // Update the UI on the main thread
                            activity?.runOnUiThread {
                                showTermSuggestions(searchResults, searchTerm)
                            }
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Search failed with response code: $responseCode"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error searching for term: $searchTerm",
                                e
                        )
                    }
                }
                .start()
    }

    private fun parseTermSearchResults(content: String): List<String> {
        try {
            val jsonArray = org.json.JSONArray(content)
            val results = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val termObj = jsonArray.getJSONObject(i)
                val termText = termObj.getString("text")
                results.add(termText)
            }

            return results
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error parsing search results", e)
            return emptyList()
        }
    }

    private fun showTermSuggestions(suggestions: List<String>, searchTerm: String) {
        // Create a custom popup window to show the suggestions
        if (suggestions.isEmpty() || !isAdded || _binding == null) {
            // If no suggestions or fragment is not valid, don't show anything
            return
        }

        // Limit to the first 10 suggestions to avoid too many options
        val limitedSuggestions = suggestions.take(10)

        // Create a popup window with the suggestions
        val popupView =
                android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.WHITE)
                    setPadding(16, 16, 16, 16)
                }

        limitedSuggestions.forEach { suggestion ->
            val textView =
                    android.widget.TextView(requireContext()).apply {
                        text = suggestion
                        setPadding(16, 12, 16, 12)
                        setTextColor(android.graphics.Color.BLACK)
                        setTextSize(16f)

                        // Add click listener to select the term
                        setOnClickListener {
                            // Check if fragment is still added before updating UI
                            if (!isAdded || _binding == null) {
                                android.util.Log.w(
                                        "NativeTermFormFragment",
                                        "Fragment not added or binding is null when clicking suggestion"
                                )
                                return@setOnClickListener
                            }

                            binding.termText.setText(suggestion)
                            // Move cursor to the end
                            binding.termText.setSelection(suggestion.length)

                            // Dismiss the popup
                            // Note: We'll handle the dismissal when we create the popup
                        }

                        // Add a simple hover effect
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    v.setBackgroundColor(0xFFF0F0F0.toInt())
                                    true
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
            popupView.addView(textView)
        }

        // Create the popup window
        val popupWindow =
                android.widget.PopupWindow(
                        popupView,
                        binding.termText.width, // Match the width of the input field
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        true // Allow outside touch to dismiss
                )

        // Set background and elevation for the popup
        popupWindow.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        )
        popupWindow.elevation = 8f

        // Dismiss the popup when a suggestion is clicked
        limitedSuggestions.forEachIndexed { index, suggestion ->
            val textView = popupView.getChildAt(index) as? android.widget.TextView
            textView?.setOnClickListener {
                // Check if fragment is still added before updating UI
                if (!isAdded || _binding == null) {
                    android.util.Log.w(
                            "NativeTermFormFragment",
                            "Fragment not added or binding is null when clicking suggestion"
                    )
                    return@setOnClickListener
                }

                binding.termText.setText(suggestion)
                // Move cursor to the end
                binding.termText.setSelection(suggestion.length)

                // Dismiss the popup
                popupWindow.dismiss()
            }
        }

        // Show the popup below the term text field
        popupWindow.showAsDropDown(binding.termText)

        // Add a text watcher that dismisses the popup when text changes
        // First, remove any existing text watcher to avoid multiple watchers
        binding.termText.setTag(AUTOCOMPLETE_TEXT_WATCHER_TAG, null)
        val textWatcher =
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        // Dismiss the popup if the user types more characters to refine the search
                        // Only if fragment is still valid
                        try {
                            // Remove this text watcher after dismissing the popup
                            // Only if fragment is still valid
                            if (isAdded && _binding != null) {
                                binding.termText.removeTextChangedListener(this)
                            }
                        } catch (e: IllegalStateException) {
                            // Handle case where binding might have become invalid between
                            // condition check and access
                            android.util.Log.w(
                                    "NativeTermFormFragment",
                                    "Error in Term TextWatcher: ${e.message}"
                            )
                        }
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }

        // Store reference to the text watcher to allow removal later
        binding.termText.setTag(AUTOCOMPLETE_TEXT_WATCHER_TAG, textWatcher)
        binding.termText.addTextChangedListener(textWatcher)
    }

    private fun setupParentAutocomplete() {
        // Add a text watcher to implement autocomplete functionality for the parent text field
        var searchJob: kotlinx.coroutines.Job? = null

        binding.newParentText.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        val term = s?.toString()?.trim() ?: ""
                        if (term.length >= 2) { // Start search after 2 characters
                            // Cancel previous search job if it's still running
                            searchJob?.cancel()

                            // Start a new coroutine with a delay to avoid excessive API calls
                            searchJob =
                                    kotlinx.coroutines.GlobalScope.launch(
                                            kotlinx.coroutines.Dispatchers.Main
                                    ) {
                                        kotlinx.coroutines.delay(
                                                300
                                        ) // 300ms delay to allow for typing

                                        // Check if the term text has changed since the delay
                                        // started
                                        if (binding.newParentText.text.toString().trim() == term) {
                                            performParentTermSearch(term)
                                        }
                                    }
                        } else {
                            // Hide suggestions if text is less than 2 characters
                            // and fragment is still added
                            if (isAdded && _binding != null) {
                                binding.parentSuggestionsList.visibility = View.GONE
                            }
                        }
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }
        )
    }

    private fun performParentTermSearch(searchTerm: String) {
        // Get the language ID for the search
        val langId = storedTermData?.languageId ?: termFormData?.languageId ?: return

        // Start a thread to make the network request
        Thread {
                    try {
                        val serverSettingsManager =
                                com.example.luteforandroidv2.ui.settings.ServerSettingsManager
                                        .getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Server URL not configured"
                            )
                            return@Thread
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()
                        val searchUrl = "$serverUrl/term/search/$searchTerm/$langId"
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Searching for parent term: $searchTerm, langId: $langId, URL: $searchUrl"
                        )

                        val url = java.net.URL(searchUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Accept", "application/json")

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Parent search response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            // Parse the JSON response
                            val searchResults = parseTermSearchResults(content)
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Parent search results parsed: ${searchResults.size} items"
                            )

                            // Update the UI on the main thread
                            activity?.runOnUiThread {
                                showParentTermSuggestions(searchResults, searchTerm)
                            }
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Parent search failed with response code: $responseCode"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error searching for parent term: $searchTerm",
                                e
                        )
                    }
                }
                .start()
    }

    private fun showParentTermSuggestions(suggestions: List<String>, searchTerm: String) {
        // Show suggestions in the RecyclerView below the parent text field
        if (!isAdded || _binding == null) {
            android.util.Log.w(
                    "NativeTermFormFragment",
                    "Fragment not added or binding is null, skipping parent term suggestions"
            )
            return
        }

        val recyclerView = binding.parentSuggestionsList
        val limitedSuggestions = suggestions.take(10) // Limit to 10 suggestions

        if (limitedSuggestions.isEmpty()) {
            // Hide the suggestions list if no suggestions
            recyclerView.visibility = View.GONE
            return
        }

        // Update the adapter with new suggestions
        parentSuggestionsAdapter =
                ParentTermSuggestionsAdapter(limitedSuggestions) { suggestion ->
                    // When a suggestion is clicked
                    binding.newParentText.setText(suggestion)
                    // Move cursor to the end
                    binding.newParentText.setSelection(suggestion.length)
                    // Hide the suggestions list
                    recyclerView.visibility = View.GONE
                    // Clear the adapter
                    parentSuggestionsAdapter = ParentTermSuggestionsAdapter(emptyList()) {}
                    recyclerView.adapter = parentSuggestionsAdapter
                }
        recyclerView.adapter = parentSuggestionsAdapter

        // Show the suggestions list
        recyclerView.visibility = View.VISIBLE
    }
    private fun createAndShowPopup(suggestions: List<String>, popupWidth: Int) {
        // Create the popup view
        val popupView =
                android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.WHITE)
                    setPadding(16, 16, 16, 16)
                }

        suggestions.forEach { suggestion ->
            val textView =
                    android.widget.TextView(requireContext()).apply {
                        text = suggestion
                        setPadding(16, 12, 16, 12)
                        setTextColor(android.graphics.Color.BLACK)
                        setTextSize(16f)

                        // Add click listener to select the term
                        setOnClickListener {
                            binding.newParentText.setText(suggestion)
                            // Move cursor to the end
                            binding.newParentText.setSelection(suggestion.length)

                            // Dismiss the popup
                        }

                        // Add a simple hover effect
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    v.setBackgroundColor(0xFFF0F0F0.toInt())
                                    true
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
            popupView.addView(textView)
        }

        // Create the popup window with the determined width
        val popupWindow =
                android.widget.PopupWindow(
                        popupView,
                        popupWidth,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        true // Allow outside touch to dismiss
                )

        // Set background and elevation for the popup
        popupWindow.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        )
        popupWindow.elevation = 8f

        // Dismiss the popup when a suggestion is clicked
        suggestions.forEachIndexed { index, suggestion ->
            val textView = popupView.getChildAt(index) as android.widget.TextView
            textView.setOnClickListener {
                binding.newParentText.setText(suggestion)
                // Move cursor to the end
                binding.newParentText.setSelection(suggestion.length)

                // Dismiss the popup
                popupWindow.dismiss()
            }
        }

        // Add a text watcher that dismisses the popup when text changes
        // First, remove any existing text watcher to avoid multiple watchers
        binding.newParentText.setTag(AUTOCOMPLETE_TEXT_WATCHER_TAG, null)
        val textWatcher =
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        // Dismiss the popup if the user types more characters to refine the search
                        if (popupWindow.isShowing) {
                            popupWindow.dismiss()
                        }
                        // Remove this text watcher after dismissing the popup
                        binding.newParentText.removeTextChangedListener(this)
                    }

                    override fun afterTextChanged(s: android.text.Editable?) {}
                }

        // Store reference to the text watcher to allow removal later
        binding.newParentText.setTag(AUTOCOMPLETE_TEXT_WATCHER_TAG, textWatcher)
        binding.newParentText.addTextChangedListener(textWatcher)

        // Show the popup below the parent text field
        popupWindow.showAsDropDown(binding.newParentText)
    }

    private fun createParentButtons(parents: List<String>) {
        val parentsContainer = binding.parentsButtonsContainer
        parentsContainer.removeAllViews()
        parentButtons.clear()

        // Define minimum button width (45dp) and convert to pixels
        val density = resources.displayMetrics.density
        val minButtonWidth = (45 * density).toInt()
        val buttonHeight = resources.getDimensionPixelSize(R.dimen.parent_button_height)

        // Define padding in dp and convert to pixels (increased to 3dp)
        val paddingDp = 3
        val paddingPixels = (paddingDp * density).toInt()

        // Define status colors (Dusk theme) - more flexible mapping
        val statusColors =
                mapOf(
                        1 to "#b46b7a", // Status 1 - Pink
                        2 to "#BA8050", // Status 2 - Orange
                        3 to "#BD9C7B", // Status 3 - Yellow
                        4 to "#756D6B", // Status 4 - Gray
                        5 to "#48484a", // Status 5 - Dark gray
                        99 to "#419252", // Status 99 - Green (Well known)
                        98 to "#8095FF" // Status 98 - Blue (Ignored)
                )

        for (parent in parents) {
            // Use TextView instead of Button for more precise control
            val textView = androidx.appcompat.widget.AppCompatTextView(requireContext())
            textView.text = parent

            // Set text size to 18sp (increased by 2sp from default)
            textView.textSize = 18f

            // Set horizontal padding in dp (converted to pixels)
            textView.setPadding(paddingPixels, 0, paddingPixels, 0)

            // Remove any default padding that might affect centering
            textView.setPaddingRelative(paddingPixels, 0, paddingPixels, 0)

            // Get the status for this parent, default to 1 if not found
            val parentTermData = parentTermDataMap[parent]
            val parentStatus = parentTermData?.status ?: 1
            val statusColor = statusColors[parentStatus] ?: "#b46b7a" // Default to status 1 color

            // Make it look like a button with the correct status color
            val drawable =
                    GradientDrawable().apply {
                        setColor(Color.parseColor(statusColor))
                        cornerRadius = 8f // 8dp corner radius as per specification
                    }
            textView.background = drawable
            textView.setTextColor(getContrastColor(Color.parseColor(statusColor)))

            // Ensure proper vertical and horizontal centering
            textView.gravity = android.view.Gravity.CENTER

            // Remove any compound drawables that might affect layout
            textView.setCompoundDrawables(null, null, null, null)

            // Set layout params with fixed height and calculated width
            val layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, // Use WRAP_CONTENT for width
                            buttonHeight
                    )
            layoutParams.setMargins(2, 0, 2, 0)

            // Ensure proper layout gravity for centering
            layoutParams.gravity = android.view.Gravity.CENTER_VERTICAL
            textView.layoutParams = layoutParams

            // Measure the text width using Paint (proper way)
            val paint = android.graphics.Paint()
            paint.textSize = 20f * density // 20sp converted to pixels
            paint.typeface = textView.typeface // Use the same typeface as the textview
            val textWidth = paint.measureText(parent)

            // Calculate button width: text width + padding on both sides
            val calculatedWidth = (textWidth + (paddingPixels * 2)).toInt()

            // Use the calculated width if it's >= minimum, otherwise use minimum width
            val buttonWidth = kotlin.math.max(minButtonWidth, calculatedWidth)

            // Update the layout params with the calculated width
            layoutParams.width = buttonWidth
            textView.layoutParams = layoutParams

            // Set click listener to show translation popup (single tap) or term form (double tap)
            textView.setOnClickListener { view -> handleParentClick(parent, view) }

            // Set long click listener for delete confirmation with vibration
            textView.setOnLongClickListener {
                vibrate()
                showDeleteConfirmation(parent, textView)
                true // Consume the long click
            }

            parentButtons.add(textView)
            parentsContainer.addView(textView)
        }
    }

    private fun createStatusButtons() {
        val statusContainer = binding.statusButtonsContainer
        statusContainer.removeAllViews()
        statusButtons.clear()

        // Define status information: ID, Label, Color (using Dusk theme colors)
        val statuses =
                arrayOf(
                        StatusInfo(1, "1", "#b46b7a"), // Dusk theme status 1
                        StatusInfo(2, "2", "#BA8050"), // Dusk theme status 2
                        StatusInfo(3, "3", "#BD9C7B"), // Dusk theme status 3
                        StatusInfo(4, "4", "#756D6B"), // Dusk theme status 4
                        StatusInfo(
                                5,
                                "5",
                                "#48484a"
                        ), // Dusk theme status 5 (transparent in CSS, using background color)
                        StatusInfo(99, "✓", "#419252"), // Dusk theme status 99 (well known)
                        StatusInfo(
                                98,
                                "✕",
                                "#8095FF"
                        ) // Dusk theme status 98 (ignored) - using status-0-color
                )

        val layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
        layoutParams.setMargins(2, 0, 2, 0) // Reduced margin for more compact layout

        for (status in statuses) {
            val button = Button(requireContext())
            button.text = status.label
            button.tag = status.id
            button.layoutParams = layoutParams

            // Remove any padding that might interfere with centering
            button.setPadding(0, 0, 0, 0)

            // Set background color based on status
            val drawable =
                    GradientDrawable().apply {
                        setColor(Color.parseColor(status.color))
                        cornerRadius = 8f // 8dp corner radius as per specification
                    }
            button.background = drawable

            // Set text color based on background for better contrast
            button.setTextColor(getContrastColor(Color.parseColor(status.color)))

            // Center text vertically and horizontally
            button.gravity = android.view.Gravity.CENTER

            // Remove any default transformations that might affect the text
            button.transformationMethod = null

            // Set initial selection state with shadow (8dp elevation as per specification)
            if (status.id == selectedStatus) {
                button.isSelected = true
                // Add shadow for selected state
                button.elevation = 8f

                // Add a white border to make selection more visible
                drawable.setStroke(4, Color.WHITE) // 4dp white stroke
            }

            button.setOnClickListener {
                // Update selection
                selectedStatus = status.id
                updateButtonSelection()
            }

            statusButtons.add(button)
            statusContainer.addView(button)
        }
    }

    private fun updateButtonSelection() {
        statusButtons.forEach { button ->
            val statusId = button.tag as Int
            if (statusId == selectedStatus) {
                button.isSelected = true
                // Add shadow for selected state (8dp elevation as per specification)
                button.elevation = 8f

                // Add a white border to make selection more visible
                val drawable = button.background as? GradientDrawable
                drawable?.setStroke(4, Color.WHITE) // 4dp white stroke
                button.background = drawable
            } else {
                button.isSelected = false
                button.elevation = 0f

                // Remove the white border for non-selected buttons
                val drawable = button.background as? GradientDrawable
                drawable?.setStroke(0, Color.TRANSPARENT) // No stroke
                button.background = drawable
            }
        }
    }

    private fun getContrastColor(color: Int): Int {
        // Calculate luminance to determine if we need black or white text
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255

        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private fun saveTerm() {
        // Get the original term text from the termFormData (which was loaded from server)
        val originalTermText = termFormData?.termText ?: ""
        // Get the current term text from the UI
        val currentTermText = binding.termText.text.toString()

        // Validate that the term text hasn't been changed except for capitalization
        if (!isTermTextValidModification(originalTermText, currentTermText)) {
            // Show error message and prevent saving
            activity?.runOnUiThread {
                android.widget.Toast.makeText(
                                requireContext(),
                                "Term text can only be modified for capitalization. Current term: '$originalTermText'",
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }
            return
        }

        // Get translation text from cache instead of direct binding to ensure consistency
        // For parent term forms, get translation directly from textbox since they don't use cache
        val translation =
                if (isParentTermForm) {
                    binding.translationText.text.toString()
                } else {
                    TranslationCacheManager.getInstance().getTemporaryTranslation()
                }

        // Collect parent terms from parentTermDataMap keys to avoid issues with parentButtons list
        // which might be modified during the save process
        val currentParents = parentTermDataMap.keys.toList()

        // Debugging: Log the current isLinked value before creating updatedData
        android.util.Log.d(
                "NativeTermFormFragment",
                "Current isLinked value before save: $isLinked"
        )

        val updatedData =
                termFormData?.copy(
                        termText =
                                currentTermText, // Use the current term text (allows capitalization
                        // changes)
                        translation = translation,
                        parents = currentParents,
                        status = selectedStatus,
                        isLinked = isLinked
                )

        // Debugging: Log the updated data
        android.util.Log.d(
                "NativeTermFormFragment",
                "Creating updatedData with isLinked: ${updatedData?.isLinked}"
        )

        // Save to server
        saveTermToServer(updatedData) { success ->
            if (success) {
                android.util.Log.d(
                        "NativeTermFormFragment",
                        "Save successful, calling callbacks and dismissing"
                )
                // Only invoke callback and dismiss if save was successful
                updatedData?.let { saveCallback?.invoke(it) }
                // Notify listener that term was saved
                dictionaryListener?.onTermSaved(updatedData)
                // Clear cache after successful save, but only for main term forms, not parent term
                // forms
                if (!isParentTermForm) {
                    TranslationCacheManager.getInstance().clearTemporaryTranslation()
                } else {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Not clearing cache because this is a parent term form"
                    )
                }
                dismiss()
            } else {
                android.util.Log.d("NativeTermFormFragment", "Save failed, not dismissing")
                // Check if this is a linking-related error
                if (isLinked && termFormData?.parents?.isNotEmpty() == true) {
                    // Show specific error message for linking failures
                    activity?.runOnUiThread {
                        android.widget.Toast.makeText(
                                        requireContext(),
                                        "Failed to save term. Linking may have failed. Please check that the parent term exists and try again.",
                                        android.widget.Toast.LENGTH_LONG
                                )
                                .show()
                    }
                }
            }
            // If save failed, we don't dismiss the form and let the user try again
        }
    }

    /** Check if the term text modification is valid (only capitalization changes allowed) */
    private fun isTermTextValidModification(original: String, modified: String): Boolean {
        // Check if they're the same when compared case-insensitively
        return original.equals(modified, ignoreCase = true)
    }

    /** Save term data to the Lute server */
    private fun saveTermToServer(termData: TermFormData?, callback: (Boolean) -> Unit) {
        Thread {
                    try {
                        val serverSettingsManager =
                                ServerSettingsManager.getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Server URL not configured"
                            )
                            activity?.runOnUiThread {
                                android.widget.Toast.makeText(
                                                requireContext(),
                                                "Server URL not configured. Cannot save term.",
                                                android.widget.Toast.LENGTH_LONG
                                        )
                                        .show()
                                callback(false)
                            }
                            return@Thread
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()

                        // Determine if this is a new term or existing term
                        if (termData?.termId != null && termData.termId > 0) {
                            // Existing term - use edit_term endpoint
                            val editUrl = "$serverUrl/read/edit_term/${termData.termId}"
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Saving existing term to: $editUrl"
                            )

                            val url = java.net.URL(editUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            connection.doOutput = true
                            connection.setRequestProperty(
                                    "Content-Type",
                                    "application/x-www-form-urlencoded"
                            )

                            // Prepare form data
                            // For existing terms, we need to be careful with parent terms
                            // Format parents as JSON array to prevent issues with the server
                            val parentsJson = org.json.JSONArray()
                            termData.parents.forEach { parent ->
                                val parentObj = org.json.JSONObject()
                                parentObj.put("value", parent)
                                parentsJson.put(parentObj)
                            }

                            // For Flask-WTF BooleanField, we only send the sync_status parameter
                            // when the term is linked
                            // If not linked, we don't send the parameter at all
                            val syncStatusParam = if (termData.isLinked) "&sync_status=on" else ""
                            val postData =
                                    "text=${java.net.URLEncoder.encode(termData.termText, "UTF-8")}" +
                                            "&translation=${java.net.URLEncoder.encode(termData.translation, "UTF-8")}" +
                                            "&status=${termData.status}" +
                                            "&parentslist=${java.net.URLEncoder.encode(parentsJson.toString(), "UTF-8")}" +
                                            syncStatusParam

                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Sending POST data: $postData"
                            )
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Sync status being sent: ${termData.isLinked} (1 for linked, 0 for unlinked)"
                            )

                            // Additional logging for debugging
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Term data details - termId: ${termData.termId}, termText: ${termData.termText}, isLinked: ${termData.isLinked}"
                            )

                            // Log the individual sync_status value being sent
                            val syncStatusValue = if (termData.isLinked) 1 else 0
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Calculated sync_status value: $syncStatusValue"
                            )

                            // Write data to output stream
                            val outputStream = connection.outputStream
                            outputStream.write(postData.toByteArray())
                            outputStream.flush()
                            outputStream.close()

                            val responseCode = connection.responseCode
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Edit term response code: $responseCode"
                            )

                            if (responseCode == 200) {
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Successfully saved existing term"
                                )
                                // Try to read the response content for debugging
                                try {
                                    val responseContent =
                                            connection.inputStream?.bufferedReader()?.use {
                                                it.readText()
                                            }
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Server response content (first 1000 chars): ${responseContent?.take(1000)}"
                                    )
                                    // Also log the full response length
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Server response full length: ${responseContent?.length ?: 0}"
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Error reading response content",
                                            e
                                    )
                                }
                                activity?.runOnUiThread { callback(true) }
                            } else {
                                android.util.Log.e(
                                        "NativeTermFormFragment",
                                        "Failed to save term, response code: $responseCode"
                                )
                                // Try to read error response
                                try {
                                    val errorStream = connection.errorStream
                                    if (errorStream != null) {
                                        val errorContent =
                                                errorStream.bufferedReader().use { it.readText() }
                                        android.util.Log.e(
                                                "NativeTermFormFragment",
                                                "Error response: $errorContent"
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Error reading error response",
                                            e
                                    )
                                }

                                activity?.runOnUiThread {
                                    android.widget.Toast.makeText(
                                                    requireContext(),
                                                    "Failed to save term. Please try again.",
                                                    android.widget.Toast.LENGTH_LONG
                                            )
                                            .show()
                                    callback(false)
                                }
                            }

                            connection.disconnect()
                        } else {
                            // New term - use termform endpoint
                            // We need language ID and term text for this
                            val storedData = storedTermData
                            if (storedData != null) {
                                val termformUrl =
                                        "$serverUrl/read/termform/${storedData.languageId}/${java.net.URLEncoder.encode(termData?.termText ?: "", "UTF-8")}"
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Saving new term to: $termformUrl"
                                )

                                val url = java.net.URL(termformUrl)
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = "POST"
                                connection.connectTimeout = 5000
                                connection.readTimeout = 5000
                                connection.doOutput = true
                                connection.setRequestProperty(
                                        "Content-Type",
                                        "application/x-www-form-urlencoded"
                                )

                                // Prepare form data
                                // For new terms, we also need to format parents correctly
                                val parentsJson = org.json.JSONArray()
                                termData?.parents?.forEach { parent ->
                                    val parentObj = org.json.JSONObject()
                                    parentObj.put("value", parent)
                                    parentsJson.put(parentObj)
                                }

                                // For new terms, we also need to handle sync_status correctly
                                // Only send sync_status parameter when the term is linked
                                val syncStatusParam =
                                        if (termData?.isLinked == true) "&sync_status=on" else ""
                                val postData =
                                        "text=${java.net.URLEncoder.encode(termData?.termText ?: "", "UTF-8")}" +
                                                "&translation=${java.net.URLEncoder.encode(termData?.translation ?: "", "UTF-8")}" +
                                                "&status=${termData?.status ?: 1}" +
                                                "&parentslist=${java.net.URLEncoder.encode(parentsJson.toString(), "UTF-8")}" +
                                                syncStatusParam

                                // Write data to output stream
                                val outputStream = connection.outputStream
                                outputStream.write(postData.toByteArray())
                                outputStream.flush()
                                outputStream.close()

                                val responseCode = connection.responseCode
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "New term response code: $responseCode"
                                )

                                if (responseCode == 200) {
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Successfully saved new term"
                                    )
                                    activity?.runOnUiThread { callback(true) }
                                } else {
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Failed to save new term, response code: $responseCode"
                                    )
                                    // Try to read error response
                                    try {
                                        val errorStream = connection.errorStream
                                        if (errorStream != null) {
                                            val errorContent =
                                                    errorStream.bufferedReader().use {
                                                        it.readText()
                                                    }
                                            android.util.Log.e(
                                                    "NativeTermFormFragment",
                                                    "Error response: $errorContent"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e(
                                                "NativeTermFormFragment",
                                                "Error reading error response",
                                                e
                                        )
                                    }

                                    activity?.runOnUiThread {
                                        android.widget.Toast.makeText(
                                                        requireContext(),
                                                        "Failed to save term. Please try again.",
                                                        android.widget.Toast.LENGTH_LONG
                                                )
                                                .show()
                                        callback(false)
                                    }
                                }

                                connection.disconnect()
                            } else {
                                android.util.Log.e(
                                        "NativeTermFormFragment",
                                        "Cannot save new term - no stored term data with language ID"
                                )
                                activity?.runOnUiThread {
                                    android.widget.Toast.makeText(
                                                    requireContext(),
                                                    "Cannot save new term - missing language information",
                                                    android.widget.Toast.LENGTH_LONG
                                            )
                                            .show()
                                    callback(false)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error saving term to server",
                                e
                        )
                        activity?.runOnUiThread {
                            android.widget.Toast.makeText(
                                            requireContext(),
                                            "Error saving term: ${e.message}",
                                            android.widget.Toast.LENGTH_LONG
                                    )
                                    .show()
                            callback(false)
                        }
                    }
                }
                .start()
    }

    /**
     * Test server accessibility before saving the term. If the server is accessible, proceed with
     * saving. If the server is not accessible, show a toast message and block the save.
     */
    private fun testServerAndSaveTerm() {
        // Get the server URL
        val serverUrl = ServerSettingsManager.getInstance(requireContext()).getServerUrl()

        // Check if server URL is configured
        if (serverUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Server URL is not configured", Toast.LENGTH_SHORT)
                    .show()
            return
        }

        // Test server accessibility
        NetworkTestUtil.getInstance().isServerAccessible(serverUrl) { isAccessible ->
            activity?.runOnUiThread {
                if (isAccessible) {
                    // Proceed with saving the term
                    saveTerm()
                } else {
                    // Show error message
                    Toast.makeText(
                                    requireContext(),
                                    "No server connection. Term cannot be saved.",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                    // Block the save action (do nothing)
                }
            }
        }
    }

    private fun toggleAddParentVisibility() {
        val isVisible = binding.addParentContainer.visibility == View.VISIBLE
        binding.addParentContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
    }

    private fun addNewParent() {
        val newParentText = binding.newParentText.text.toString().trim()
        if (newParentText.isNotEmpty()) {
            // Check if the parent term exists in the database
            checkAndAddParentTerm(newParentText)
        }
    }

    /**
     * Check if a parent term exists in the database, and if not, ask the user if they want to
     * create it
     */
    private fun checkAndAddParentTerm(parentText: String) {
        Thread {
                    try {
                        val serverSettingsManager =
                                ServerSettingsManager.getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Server URL not configured"
                            )
                            activity?.runOnUiThread {
                                android.widget.Toast.makeText(
                                                requireContext(),
                                                "Server URL not configured",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                            return@Thread
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()
                        val storedData = storedTermData
                        if (storedData == null) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "No stored term data with language ID"
                            )
                            activity?.runOnUiThread {
                                android.widget.Toast.makeText(
                                                requireContext(),
                                                "Cannot check term - missing language information",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                            return@Thread
                        }

                        // Search for the parent term
                        val searchUrl =
                                "$serverUrl/term/search/$parentText/${storedData.languageId}"
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Searching for parent term: $parentText, URL: $searchUrl"
                        )

                        val url = java.net.URL(searchUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Accept", "application/json")

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Search response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            // Parse the JSON response to check if term exists
                            val termExists = parseTermExistsFromSearchResults(content, parentText)
                            android.util.Log.d("NativeTermFormFragment", "Term exists: $termExists")

                            activity?.runOnUiThread {
                                if (termExists) {
                                    // Term exists, add it normally
                                    addParentTermToList(parentText)
                                } else {
                                    // Term doesn't exist, ask user if they want to create it
                                    showCreateTermDialog(parentText)
                                }
                            }
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Search failed with response code: $responseCode"
                            )
                            activity?.runOnUiThread {
                                // On error, still allow adding the parent term
                                addParentTermToList(parentText)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error checking term existence",
                                e
                        )
                        activity?.runOnUiThread {
                            // On error, still allow adding the parent term
                            addParentTermToList(parentText)
                        }
                    }
                }
                .start()
    }

    /** Parse search results to check if a term exists */
    private fun parseTermExistsFromSearchResults(content: String, parent: String): Boolean {
        try {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Parsing search results for parent: $parent"
            )

            // Parse the JSON array response
            val jsonArray = org.json.JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val termObj = jsonArray.getJSONObject(i)
                val termText = termObj.getString("text")
                android.util.Log.d("NativeTermFormFragment", "Checking term: $termText")
                // Use case-insensitive comparison since Lute considers terms with different
                // capitalization as the same
                if (termText.equals(parent, ignoreCase = true)) {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Found matching term (case-insensitive)"
                    )
                    return true
                }
            }
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "No matching term found for parent: $parent"
            )
            return false
        } catch (e: Exception) {
            android.util.Log.e(
                    "NativeTermFormFragment",
                    "Error parsing term existence from search results",
                    e
            )
            return false
        }
    }

    /** Show dialog asking user if they want to create a new term */
    private fun showCreateTermDialog(parentText: String) {
        activity?.runOnUiThread {
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Create New Term")
                    .setMessage("The term '$parentText' does not exist. Do you want to create it?")
                    .setPositiveButton("Create") { _, _ -> createNewTerm(parentText) }
                    .setNegativeButton("Cancel") { _, _ ->
                        // Don't add the term if user cancels
                        // Just clear the input field and hide the container
                        activity?.runOnUiThread {
                            binding.newParentText.setText("")
                            binding.addParentContainer.visibility = View.GONE
                        }
                    }
                    .show()
        }
    }

    /** Create a new term in the database */
    private fun createNewTerm(parentText: String) {
        Thread {
                    try {
                        val serverSettingsManager =
                                ServerSettingsManager.getInstance(requireContext())
                        val serverUrl = serverSettingsManager.getServerUrl()
                        val storedData = storedTermData

                        if (storedData != null) {
                            // Use the new term endpoint
                            val newTermUrl = "$serverUrl/term/new"
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Creating new term at: $newTermUrl"
                            )

                            val url = java.net.URL(newTermUrl)
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            connection.doOutput = true
                            connection.setRequestProperty(
                                    "Content-Type",
                                    "application/x-www-form-urlencoded"
                            )

                            // Prepare form data for new term
                            val postData =
                                    "text=${java.net.URLEncoder.encode(parentText, "UTF-8")}" +
                                            "&language_id=${storedData.languageId}" +
                                            "&status=1" + // Default status
                                            "&translation=" // Empty translation

                            // Write data to output stream
                            val outputStream = connection.outputStream
                            outputStream.write(postData.toByteArray())
                            outputStream.flush()
                            outputStream.close()

                            val responseCode = connection.responseCode
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "New term response code: $responseCode"
                            )

                            activity?.runOnUiThread {
                                if (responseCode == 200) {
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Successfully created new term: $parentText"
                                    )
                                    android.widget.Toast.makeText(
                                                    requireContext(),
                                                    "Created new term: $parentText",
                                                    android.widget.Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    // Add the term to the list
                                    addParentTermToList(parentText)
                                } else {
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Failed to create new term, response code: $responseCode"
                                    )
                                    android.widget.Toast.makeText(
                                                    requireContext(),
                                                    "Failed to create term. Adding anyway.",
                                                    android.widget.Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    // Still add the term to the list
                                    addParentTermToList(parentText)
                                }
                            }

                            connection.disconnect()
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Cannot create new term - no stored term data with language ID"
                            )
                            activity?.runOnUiThread {
                                android.widget.Toast.makeText(
                                                requireContext(),
                                                "Cannot create term - missing language information",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                                // Still add the term to the list
                                addParentTermToList(parentText)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NativeTermFormFragment", "Error creating new term", e)
                        activity?.runOnUiThread {
                            android.widget.Toast.makeText(
                                            requireContext(),
                                            "Error creating term. Adding anyway.",
                                            android.widget.Toast.LENGTH_SHORT
                                    )
                                    .show()
                            // Still add the term to the list
                            addParentTermToList(parentText)
                        }
                    }
                }
                .start()
    }

    private fun addParentTermToList(parentText: String) {
        activity?.runOnUiThread {
            // Add the new parent to the list
            val parentsList = parentTermDataMap.keys.toMutableList()
            parentsList.add(parentText)

            // Recreate the parent buttons with the new list
            createParentButtons(parentsList)

            // Clear the input field
            binding.newParentText.setText("")

            // Hide the add parent container
            binding.addParentContainer.visibility = View.GONE

            // Fetch data for the new parent (this will populate parentTermDataMap)
            fetchParentTermData(listOf(parentText))
        }
    }

    private fun fetchParentTermData(parents: List<String>) {
        android.util.Log.d(
                "NativeTermFormFragment",
                "fetchParentTermData called with parents: $parents"
        )

        // Fetch data for each parent
        for (parent in parents) {
            // Skip if we already have data for this parent
            if (parentTermDataMap.containsKey(parent)) {
                // Even if we have data, we should update the button color
                updateParentButtonColor(parent)
                continue
            }

            // Fetch data for this parent
            // We don't pass a view here because this is called during setup, not from a button
            // click
            searchAndFetchParentTermData(parent, null, null)
        }
    }

    private fun showParentTranslationPopup(parent: String, view: View) {
        android.util.Log.d("NativeTermFormFragment", "=== SHOW PARENT TRANSLATION POPUP START ===")
        android.util.Log.d(
                "NativeTermFormFragment",
                "showParentTranslationPopup called for parent: $parent"
        )
        android.util.Log.d(
                "NativeTermFormFragment",
                "View class: ${view.javaClass.simpleName}, View ID: ${view.id}"
        )

        // Store the view reference for use in callbacks
        lastClickedParentView = view

        // Check if we have stored term data for this parent
        val parentTermData = parentTermDataMap[parent]
        if (parentTermData != null) {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Using stored term data for parent: $parent"
            )
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Term text: ${parentTermData.termText}, Translation: ${parentTermData.translation}"
            )
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Calling showTranslationPopup with stored data"
            )
            showTranslationPopup(parentTermData.termText, parentTermData.translation, view)
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "=== SHOW PARENT TRANSLATION POPUP END (using stored data) ==="
            )
        } else {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "No stored term data available for parent: $parent, fetching now"
            )
            // Show loading dialog
            android.util.Log.d("NativeTermFormFragment", "Creating progress dialog")
            val progressDialog =
                    MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Fetching Translation")
                            .setMessage("Fetching translation for '$parent'...")
                            .setCancelable(false)
                            .show()
            android.util.Log.d("NativeTermFormFragment", "Progress dialog created")

            // We need to search for the parent term and then fetch its data
            // Parent terms are just normal terms, so we search for them the same way
            android.util.Log.d("NativeTermFormFragment", "Calling searchAndFetchParentTermData")
            searchAndFetchParentTermData(parent, progressDialog, view)
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "=== SHOW PARENT TRANSLATION POPUP END (fetching data) ==="
            )
        }
    }

    private fun searchAndFetchParentTermData(
            parent: String,
            progressDialog: android.app.Dialog?,
            view: View?
    ) {
        android.util.Log.d(
                "NativeTermFormFragment",
                "searchAndFetchParentTermData called for parent: $parent"
        )
        android.util.Log.d("NativeTermFormFragment", "View is null: ${view == null}")

        // Store the view reference for use in callbacks (if provided)
        if (view != null) {
            lastClickedParentView = view
        }

        // First, we need to search for the parent term to get its ID
        // Parent terms are just normal terms, so we search for them the same way we'd search for
        // any term

        val serverSettingsManager =
                com.example.luteforandroidv2.ui.settings.ServerSettingsManager.getInstance(
                        requireContext()
                )
        if (!serverSettingsManager.isServerUrlConfigured()) {
            android.util.Log.e("NativeTermFormFragment", "Server URL not configured")
            activity?.runOnUiThread {
                progressDialog?.dismiss()
                // Only show error popup if this was triggered by a user action (view is not null)
                if (view != null) {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Showing error popup for server URL not configured"
                    )
                    // Use the stored view reference if available
                    lastClickedParentView?.let {
                        showTranslationPopup("Error", "Server URL not configured", it)
                    }
                } else {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Not showing error popup (setup mode)"
                    )
                }
            }
            return
        }

        // Log the stored term data for debugging
        android.util.Log.d("NativeTermFormFragment", "Stored term data: $storedTermData")
        if (storedTermData != null) {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Stored term data language ID: ${storedTermData?.languageId}"
            )
        }

        // Log the term form data for debugging
        android.util.Log.d("NativeTermFormFragment", "Term form data: $termFormData")
        if (termFormData != null) {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Term form data language ID: ${termFormData?.languageId}"
            )
        }

        // Get the language ID from the stored term data (which we already have)
        var langId = storedTermData?.languageId

        // If not available in storedTermData, try to get from termFormData as fallback
        if (langId == null) {
            langId = termFormData?.languageId
        }

        // If still not available, show an error
        if (langId == null) {
            android.util.Log.e(
                    "NativeTermFormFragment",
                    "Language ID not available in stored term data or term form data"
            )
            activity?.runOnUiThread {
                progressDialog?.dismiss()
                // Only show error popup if this was triggered by a user action (view is not null)
                if (view != null) {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Showing error popup for language ID not available"
                    )
                    // Use the stored view reference if available
                    lastClickedParentView?.let {
                        showTranslationPopup(
                                "Error",
                                "Unable to determine language for translation. Please try again.",
                                it
                        )
                    }
                } else {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Not showing error popup (setup mode)"
                    )
                }
            }
            return
        }

        android.util.Log.d(
                "NativeTermFormFragment",
                "Using language ID: $langId for parent term search"
        )

        val serverUrl = serverSettingsManager.getServerUrl()
        val searchUrl = "$serverUrl/term/search/$parent/$langId"

        android.util.Log.d(
                "NativeTermFormFragment",
                "Searching for parent term: $parent, langId: $langId, URL: $searchUrl"
        )

        Thread {
                    try {
                        val url = java.net.URL(searchUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Accept", "application/json")

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Search response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Search response content length: ${content.length}"
                            )
                            // Log only first 500 characters to avoid huge logs
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Search response content (first 500 chars): ${content.take(500)}"
                            )

                            // Parse the JSON response to find the term ID
                            val termId = parseTermIdFromSearchResults(content, parent)

                            if (termId != null) {
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Found term ID: $termId for parent: $parent"
                                )
                                // Now fetch the full term data using the ID
                                fetchTermDataById(termId, parent) { termData ->
                                    activity?.runOnUiThread {
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "fetchTermDataById callback called for parent: $parent"
                                        )
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "View is null in callback: ${view == null}"
                                        )

                                        // Dismiss the progress dialog
                                        progressDialog?.dismiss()

                                        if (termData != null) {
                                            android.util.Log.d(
                                                    "NativeTermFormFragment",
                                                    "Successfully fetched term data for: $parent"
                                            )
                                            android.util.Log.d(
                                                    "NativeTermFormFragment",
                                                    "Fetched term text: ${termData.termText}, Translation: ${termData.translation}"
                                            )
                                            // Store the parent term data for future use
                                            parentTermDataMap[parent] = termData

                                            // If this term is linked, update its status to match
                                            // the parent's status
                                            if (isLinked) {
                                                updateStatusBasedOnParent()
                                            }

                                            // Only show the popup if this was triggered by a user
                                            // action (view is not null)
                                            if (view != null) {
                                                android.util.Log.d(
                                                        "NativeTermFormFragment",
                                                        "View is not null, showing popup"
                                                )
                                                // Show the popup with the fetched translation
                                                // Use the stored view reference if available
                                                lastClickedParentView?.let {
                                                    android.util.Log.d(
                                                            "NativeTermFormFragment",
                                                            "Calling showTranslationPopup with fetched data"
                                                    )
                                                    showTranslationPopup(
                                                            termData.termText,
                                                            termData.translation,
                                                            it
                                                    )
                                                    android.util.Log.d(
                                                            "NativeTermFormFragment",
                                                            "showTranslationPopup called with fetched data"
                                                    )
                                                }
                                                        ?: run {
                                                            android.util.Log.e(
                                                                    "NativeTermFormFragment",
                                                                    "No valid view reference available to show popup for parent: $parent"
                                                            )
                                                        }
                                            } else {
                                                android.util.Log.d(
                                                        "NativeTermFormFragment",
                                                        "View is null, not showing popup (setup mode)"
                                                )
                                            }

                                            // Update the specific parent button color
                                            updateParentButtonColor(parent)
                                        } else {
                                            android.util.Log.e(
                                                    "NativeTermFormFragment",
                                                    "Failed to fetch term data for: $parent"
                                            )
                                            // Only show error popup if this was triggered by a user
                                            // action (view is not null)
                                            if (view != null) {
                                                android.util.Log.d(
                                                        "NativeTermFormFragment",
                                                        "Showing error popup for failed term data fetch"
                                                )
                                                // Show error popup using the stored view reference
                                                lastClickedParentView?.let {
                                                    showTranslationPopup(
                                                            "Error",
                                                            "Could not fetch translation for '$parent'",
                                                            it
                                                    )
                                                }
                                            } else {
                                                android.util.Log.d(
                                                        "NativeTermFormFragment",
                                                        "Not showing error popup (setup mode)"
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                android.util.Log.e(
                                        "NativeTermFormFragment",
                                        "Could not find term ID for parent: $parent"
                                )
                                activity?.runOnUiThread {
                                    progressDialog?.dismiss()
                                    // Only show error popup if this was triggered by a user action
                                    // (view is not null)
                                    if (view != null) {
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "Showing error popup for term ID not found"
                                        )
                                        // Show error popup using the stored view reference
                                        lastClickedParentView?.let {
                                            showTranslationPopup(
                                                    "Error",
                                                    "Could not find term '$parent'",
                                                    it
                                            )
                                        }
                                    } else {
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "Not showing error popup (setup mode)"
                                        )
                                    }
                                }
                            }
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Search failed with response code: $responseCode"
                            )
                            // Log error response if available
                            try {
                                val errorStream = connection.errorStream
                                if (errorStream != null) {
                                    val errorContent =
                                            errorStream.bufferedReader().use { it.readText() }
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Search error response: $errorContent"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "NativeTermFormFragment",
                                        "Error reading error response",
                                        e
                                )
                            }
                            activity?.runOnUiThread {
                                progressDialog?.dismiss()
                                // Only show error popup if this was triggered by a user action
                                // (view is not null)
                                if (view != null) {
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Showing error popup for search failure"
                                    )
                                    // Show error popup using the stored view reference
                                    lastClickedParentView?.let {
                                        showTranslationPopup(
                                                "Error",
                                                "Search failed for '$parent'",
                                                it
                                        )
                                    }
                                } else {
                                    android.util.Log.d(
                                            "NativeTermFormFragment",
                                            "Not showing error popup (setup mode)"
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error fetching term data for parent: $parent",
                                e
                        )
                        activity?.runOnUiThread {
                            progressDialog?.dismiss()
                            // Only show error popup if this was triggered by a user action (view is
                            // not null)
                            if (view != null) {
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Showing error popup for exception"
                                )
                                // Show error popup using the stored view reference
                                lastClickedParentView?.let {
                                    showTranslationPopup(
                                            "Error",
                                            "Failed to fetch translation for '$parent'. Please check your connection and try again.",
                                            it
                                    )
                                }
                            } else {
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Not showing error popup (setup mode)"
                                )
                            }
                        }
                    }
                }
                .start()
    }

    private fun parseTermIdFromSearchResults(content: String, parent: String): Int? {
        try {
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Parsing search results for parent: $parent"
            )

            // Parse the JSON array response
            val jsonArray = org.json.JSONArray(content)
            for (i in 0 until jsonArray.length()) {
                val termObj = jsonArray.getJSONObject(i)
                val termText = termObj.getString("text")
                android.util.Log.d("NativeTermFormFragment", "Checking term: $termText")
                // Use case-insensitive comparison since Lute considers terms with different
                // capitalization as the same
                if (termText.equals(parent, ignoreCase = true)) {
                    val termId = termObj.getInt("id")
                    android.util.Log.d("NativeTermFormFragment", "Found matching term ID: $termId")
                    return termId
                }
            }
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "No matching term found for parent: $parent"
            )
            return null
        } catch (e: Exception) {
            android.util.Log.e(
                    "NativeTermFormFragment",
                    "Error parsing term ID from search results",
                    e
            )
            return null
        }
    }

    private fun fetchTermDataById(termId: Int, parent: String, callback: (TermFormData?) -> Unit) {
        val serverSettingsManager =
                com.example.luteforandroidv2.ui.settings.ServerSettingsManager.getInstance(
                        requireContext()
                )
        val serverUrl = serverSettingsManager.getServerUrl()
        val termEditUrl = "$serverUrl/term/edit/$termId"

        android.util.Log.d(
                "NativeTermFormFragment",
                "Fetching term data for ID: $termId, URL: $termEditUrl"
        )

        Thread {
                    try {
                        val url = java.net.URL(termEditUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Term edit response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Term edit response length: ${content.length}"
                            )
                            // Log only first 500 characters to avoid huge logs
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Term edit response (first 500 chars): ${content.take(500)}"
                            )

                            // Parse the HTML to extract term data
                            val termData = parseTermDataFromHtml(content, termId, parent)
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Parsed term data after save - isLinked: ${termData.isLinked}"
                            )
                            // Log more details about the parsed data
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Complete parsed term data after save: $termData"
                            )
                            callback(termData)
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Term edit failed with response code: $responseCode"
                            )
                            // Log error response if available
                            try {
                                val errorStream = connection.errorStream
                                if (errorStream != null) {
                                    val errorContent =
                                            errorStream.bufferedReader().use { it.readText() }
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Term edit error response: $errorContent"
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "NativeTermFormFragment",
                                        "Error reading error response",
                                        e
                                )
                            }
                            callback(null)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error fetching term data by ID: $termId",
                                e
                        )
                        callback(null)
                    }
                }
                .start()
    }

    private fun parseTermDataFromHtml(
            htmlContent: String,
            termId: Int,
            parent: String
    ): TermFormData {
        // Use the new TermDataExtractor utility class
        return TermDataExtractor.parseTermDataFromHtml(htmlContent, termId, parent)
    }

    private fun showTranslationPopup(title: String, translation: String, anchorView: View) {
        android.util.Log.d("NativeTermFormFragment", "=== SHOW TRANSLATION POPUP START ===")
        android.util.Log.d(
                "NativeTermFormFragment",
                "showTranslationPopup called with title: $title, translation: $translation"
        )
        android.util.Log.d(
                "NativeTermFormFragment",
                "Anchor view class: ${anchorView.javaClass.simpleName}, Anchor view ID: ${anchorView.id}"
        )

        // Don't show popup if there's no translation to display (matching Native Reader behavior)
        if (translation.isBlank()) {
            android.util.Log.d("NativeTermFormFragment", "Translation is blank, not showing popup")
            return
        }

        try {
            android.util.Log.d("NativeTermFormFragment", "Creating popup view")
            // Create a custom popup with just the translation text
            val popupView =
                    LayoutInflater.from(requireContext()).inflate(R.layout.popup_translation, null)
            val popupText = popupView.findViewById<TextView>(R.id.popup_text)
            // Show the actual translation text (no fallback message needed since we check above)
            popupText.text = translation
            android.util.Log.d("NativeTermFormFragment", "Popup text set to: $translation")

            val popupWindow =
                    PopupWindow(
                            popupView,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            false
                    )

            // Add click listener to dismiss the popup when tapped
            popupView.setOnClickListener {
                try {
                    if (popupWindow.isShowing) {
                        popupWindow.dismiss()
                        android.util.Log.d("NativeTermFormFragment", "Popup dismissed by tap")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NativeTermFormFragment", "Error dismissing popup", e)
                }
            }
            android.util.Log.d("NativeTermFormFragment", "PopupWindow created")

            // Removed background drawable that makes popup dismissible when touching outside
            // popupWindow.setBackgroundDrawable(
            //         android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            // )

            // Use showAsDropDown with proper offset calculation
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Attempting to show popup with showAsDropDown"
            )

            // Measure the popup view
            popupView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val popupWidth = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Popup dimensions: width=$popupWidth, height=$popupHeight"
            )
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Anchor dimensions: width=${anchorView.width}, height=${anchorView.height}"
            )

            // Calculate offset to center the popup horizontally on the anchor view
            val anchorWidth = anchorView.width
            val xOffset = anchorWidth / 2 - popupWidth / 2
            val yOffset = -popupHeight
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Calculated offset: xOffset=$xOffset, yOffset=$yOffset"
            )

            // Show the popup above the anchor view, centered horizontally
            popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
            android.util.Log.d("NativeTermFormFragment", "Popup shown successfully with offset")

            // Auto dismiss after 3 seconds
            android.util.Log.d("NativeTermFormFragment", "Setting up auto-dismiss timer")
            android.os.Handler()
                    .postDelayed(
                            {
                                android.util.Log.d(
                                        "NativeTermFormFragment",
                                        "Auto-dismiss timer triggered"
                                )
                                try {
                                    if (popupWindow.isShowing) {
                                        popupWindow.dismiss()
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "Popup auto-dismissed"
                                        )
                                    } else {
                                        android.util.Log.d(
                                                "NativeTermFormFragment",
                                                "Popup was not showing, no need to dismiss"
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                            "NativeTermFormFragment",
                                            "Error dismissing popup",
                                            e
                                    )
                                }
                            },
                            3000
                    )
            android.util.Log.d("NativeTermFormFragment", "Auto-dismiss timer set")

            android.util.Log.d(
                    "NativeTermFormFragment",
                    "=== SHOW TRANSLATION POPUP END (SUCCESS) ==="
            )
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error creating or showing popup", e)
            android.util.Log.d("NativeTermFormFragment", "Trying fallback dialog")
            // Fallback to a simple dialog if popup fails
            activity?.runOnUiThread {
                MaterialAlertDialogBuilder(requireContext())
                        .setTitle(title)
                        .setMessage(translation) // Show actual translation (no fallback needed)
                        .setPositiveButton("OK", null)
                        .show()
            }
            android.util.Log.d("NativeTermFormFragment", "Fallback dialog shown")
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "=== SHOW TRANSLATION POPUP END (FALLBACK) ==="
            )
        }
    }

    private fun updateParentButtonColor(parent: String) {
        android.util.Log.d(
                "NativeTermFormFragment",
                "updateParentButtonColor called for parent: $parent"
        )

        // Find the button for this parent and update its color
        parentButtons.find { it.text.toString() == parent }?.let { button ->
            val parentTermData = parentTermDataMap[parent]
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Parent term data for $parent: $parentTermData"
            )

            val parentStatus = parentTermData?.status ?: 1
            android.util.Log.d("NativeTermFormFragment", "Parent status for $parent: $parentStatus")

            val statusColors =
                    mapOf(
                            1 to "#b46b7a", // Status 1 - Pink
                            2 to "#BA8050", // Status 2 - Orange
                            3 to "#BD9C7B", // Status 3 - Yellow
                            4 to "#756D6B", // Status 4 - Gray
                            5 to "#48484a", // Status 5 - Dark gray
                            99 to "#419252", // Status 99 - Green (Well known)
                            98 to "#8095FF" // Status 98 - Blue (Ignored)
                    )
            val statusColor = statusColors[parentStatus] ?: "#b46b7a" // Default to status 1 color
            android.util.Log.d("NativeTermFormFragment", "Status color for $parent: $statusColor")

            // Update the button's background color
            val drawable =
                    GradientDrawable().apply {
                        setColor(Color.parseColor(statusColor))
                        cornerRadius = 8f // 8dp corner radius as per specification
                    }
            button.background = drawable
            button.setTextColor(getContrastColor(Color.parseColor(statusColor)))

            android.util.Log.d("NativeTermFormFragment", "Updated button color for parent: $parent")
        }
                ?: run {
                    android.util.Log.d(
                            "NativeTermFormFragment",
                            "Button not found for parent: $parent"
                    )
                }
    }

    private fun showDeleteConfirmation(parent: String, textView: AppCompatTextView) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Parent?")
                .setMessage("Are you sure you want to delete '$parent'?")
                .setPositiveButton("Yes") { _, _ ->
                    // Remove the parent from the list
                    parentButtons.remove(textView)
                    (textView.parent as ViewGroup).removeView(textView)
                    // Also remove the parent from parentTermDataMap to ensure it's not sent to
                    // server
                    parentTermDataMap.remove(parent)
                }
                .setNegativeButton("No", null)
                .show()
    }

    private fun vibrate() {
        val vibrator =
                context?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(
                                50,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        )
                )
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(50) // Vibrate for 50 milliseconds
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        android.util.Log.d("NativeTermFormFragment", "onDestroyView called")
        // Unregister as observer for translation text changes
        TranslationCacheManager.getInstance().removeObserver(this)

        // Clean up the parent suggestions RecyclerView
        if (_binding != null) {
            _binding?.parentSuggestionsList?.visibility = View.GONE
            _binding?.parentSuggestionsList?.adapter = null
        }
        parentSuggestionsAdapter = null

        _binding = null
        // Notify the fragment listener that the term form is being destroyed
        dictionaryListener?.onTermFormDestroyed()
    }

    // TranslationCacheManager.TranslationObserver implementation
    override fun onTranslationTextChanged(text: String) {
        // Update the translation text field with the new text if it's different
        // Only do this for main term forms, not parent term forms
        if (!isParentTermForm) {
            activity?.runOnUiThread {
                val currentText = binding.translationText.text.toString()
                if (currentText != text) {
                    isProgrammaticUpdate = true
                    binding.translationText.setText(text)
                    isProgrammaticUpdate = false
                }
            }
        }
    }

    // Method to set the translation text in the form (replaces existing text)
    fun setTranslationText(text: String) {
        activity?.runOnUiThread {
            isProgrammaticUpdate = true
            binding.translationText.setText(text)
            // Update cache with new text
            TranslationCacheManager.getInstance().setTemporaryTranslation(text)
            isProgrammaticUpdate = false
        }
    }

    // Method to append a word to the translation text in the form
    fun appendTranslationText(word: String) {
        activity?.runOnUiThread {
            isProgrammaticUpdate = true
            val currentText = binding.translationText.text.toString()
            val newText = if (currentText.isNotEmpty()) "$currentText $word" else word
            binding.translationText.setText(newText)
            // Update cache with new text
            TranslationCacheManager.getInstance().setTemporaryTranslation(newText)
            isProgrammaticUpdate = false
        }
    }

    // Method to update the translation text in the form (deprecated - use set or append instead)
    fun updateTranslationText(text: String) {
        setTranslationText(text)
    }

    // Method to get the current translation text from the form
    fun getCurrentTranslationText(): String {
        return binding.translationText.text.toString()
    }

    // Handle click on parent term (single tap for popup, double tap for term form)
    private fun handleParentClick(parent: String, view: View) {
        val currentTime = System.currentTimeMillis()
        val lastClickTime = lastParentClickTimes[parent] ?: 0
        val timeDiff = currentTime - lastClickTime

        // If the time difference is less than 200ms, consider it a double tap
        if (timeDiff < 200) {
            // Double tap - open term form for parent
            handleParentDoubleTap(parent)
            // Remove the entry to prevent triple taps from being treated as double taps
            lastParentClickTimes.remove(parent)
        } else {
            // Single tap - show translation popup
            showParentTranslationPopup(parent, view)
            // Store the click time for double tap detection
            lastParentClickTimes[parent] = currentTime
        }
    }

    // Handle double-tap on parent term to open term form for that parent
    private fun handleParentDoubleTap(parent: String) {
        android.util.Log.d("NativeTermFormFragment", "Double tap detected for parent term: $parent")

        // Get parent term data if available
        val parentTermData = parentTermDataMap[parent]

        // Create basic TermFormData for the parent term
        val termFormData =
                if (parentTermData != null) {
                    // Use existing data if available
                    parentTermData
                } else {
                    // Create minimal TermFormData if no data available
                    TermFormData(
                            termId = 0, // Unknown, will need to be fetched
                            termText = parent,
                            languageId = storedTermData?.languageId ?: 1,
                            context = "",
                            translation = "",
                            status = 1, // Default status
                            parents = emptyList(),
                            tags = emptyList()
                    )
                }

        // Create TermData for the parent term
        val parentTerm =
                TermData(
                        termId = termFormData.termId ?: 0,
                        languageId = termFormData.languageId ?: (storedTermData?.languageId ?: 1),
                        term = parent,
                        status = termFormData.status,
                        parentsList = termFormData.parents,
                        translation = termFormData.translation,
                        tapX = 0f, // We don't have tap coordinates
                        tapY = 0f
                )

        // Create and show the NativeTermFormFragment for the parent term
        // We need to be careful about the circular reference in the callbacks
        var fragmentRef: NativeTermFormFragment? = null

        val fragment =
                NativeTermFormFragment.newInstance(
                        termFormData = termFormData,
                        storedTermData = parentTerm,
                        isParentTermForm = true,
                        onSave = { updatedTerm ->
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Parent term form saved: ${updatedTerm.termText}"
                            )
                            // Update the parent term data in this form's map with the new status
                            val parentName = updatedTerm.termText
                            val currentParentData = parentTermDataMap[parentName]
                            if (currentParentData != null) {
                                // Create updated TermFormData with the new status
                                val updatedParentData =
                                        currentParentData.copy(
                                                termId = updatedTerm.termId,
                                                status = updatedTerm.status,
                                                translation = updatedTerm.translation
                                        )
                                // Update the map
                                parentTermDataMap[parentName] = updatedParentData
                                // Update the button color to reflect the new status
                                updateParentButtonColor(parentName)

                                // If this term is linked, update its status to match the parent's
                                // new status
                                if (isLinked) {
                                    updateStatusBasedOnParent()
                                }
                            }
                            // Handle save - dismiss the form
                            // Don't clear the cache when saving a parent term form as it affects
                            // the original form
                            fragmentRef?.dismiss()
                        },
                        onCancel = {
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Parent term form cancelled"
                            )
                            // Handle cancel - just dismiss the form
                            // Don't clear cache for parent term forms as it affects the original
                            // form
                            fragmentRef?.dismiss()
                        },
                        dictionaryListener = this.dictionaryListener
                                        ?: object : DictionaryListener {
                                            override fun onDictionaryClosed() {
                                                // Do nothing
                                            }

                                            override fun onDictionaryLookup(term: String) {
                                                // Forward to the parent's dictionary listener if
                                                // available
                                                this@NativeTermFormFragment.dictionaryListener
                                                        ?.onDictionaryLookup(term)
                                            }

                                            override fun onDictionaryTextSelected(text: String) {
                                                // Forward to the parent's dictionary listener if
                                                // available
                                                this@NativeTermFormFragment.dictionaryListener
                                                        ?.onDictionaryTextSelected(text)
                                            }

                                            override fun onTermFormDestroyed() {
                                                // Do nothing
                                            }

                                            override fun onTermSaved(
                                                    updatedTermData: TermFormData?
                                            ) {
                                                // Forward to the parent's dictionary listener if
                                                // available
                                                this@NativeTermFormFragment.dictionaryListener
                                                        ?.onTermSaved(updatedTermData)
                                            }
                                        }
                )

        // Keep a reference to avoid circular reference issues
        fragmentRef = fragment

        fragment.show(parentFragmentManager, "parent_term_form_${parent}")
    }

    /** Toggle the linking state of the term */
    private fun toggleLinking() {
        android.util.Log.d(
                "NativeTermFormFragment",
                "toggleLinking called, current isLinked: $isLinked"
        )
        isLinked = !isLinked
        android.util.Log.d("NativeTermFormFragment", "Linking toggled, new isLinked: $isLinked")

        // When linking state changes, update the term's status to match parent if linked
        if (isLinked && parentTermDataMap.isNotEmpty()) {
            updateStatusBasedOnParent()
        }

        updateLinkButtonState()

        // Also log the button state after updating
        android.util.Log.d(
                "NativeTermFormFragment",
                "Link button state after toggle - isLinked: $isLinked, button isSelected: ${binding.linkTermButton.isSelected}"
        )

        // Additional debugging to check button properties
        android.util.Log.d(
                "NativeTermFormFragment",
                "Button enabled: ${binding.linkTermButton.isEnabled}, Button clickable: ${binding.linkTermButton.isClickable}"
        )
    }

    /** Update the status buttons state based on linking state */

    /** Update the term's status to match the parent's status when linked */
    private fun updateStatusBasedOnParent() {
        if (!isLinked || parentTermDataMap.isEmpty()) {
            return
        }

        // Get the first parent's status (for single parent linking)
        // In case of multiple parents, we'll use the first one's status
        val firstParent = parentTermDataMap.values.firstOrNull()
        if (firstParent != null) {
            val parentStatus = firstParent.status
            android.util.Log.d(
                    "NativeTermFormFragment",
                    "Updating status to match parent status: $parentStatus"
            )
            selectedStatus = parentStatus
            updateButtonSelection()
        }
    }

    /** Update the link button appearance based on linking state */
    private fun updateLinkButtonState() {
        android.util.Log.d(
                "NativeTermFormFragment",
                "updateLinkButtonState called, isLinked: $isLinked"
        )
        binding.linkTermButton.isSelected = isLinked
        android.util.Log.d(
                "NativeTermFormFragment",
                "Link button isSelected set to: ${binding.linkTermButton.isSelected}"
        )

        // Force refresh the drawable state
        binding.linkTermButton.refreshDrawableState()

        // Also log the drawable state
        android.util.Log.d(
                "NativeTermFormFragment",
                "Link button drawable state: ${binding.linkTermButton.drawableState.contentToString()}"
        )

        // Additional debugging
        android.util.Log.d(
                "NativeTermFormFragment",
                "Button visibility: ${binding.linkTermButton.visibility}, Button alpha: ${binding.linkTermButton.alpha}"
        )

        // Force the button to redraw
        binding.linkTermButton.invalidate()
        binding.linkTermButton.requestLayout()
    }

    /** Send term to AI endpoint for processing */
    private fun sendTermToAi(term: String) {
        // Get AI settings
        val aiSettingsManager = AiSettingsManager.getInstance(requireContext())

        // Check if AI is configured
        if (!aiSettingsManager.isAiConfigured()) {
            android.widget.Toast.makeText(
                            requireContext(),
                            "AI endpoint not configured. Please check app settings.",
                            android.widget.Toast.LENGTH_LONG
                    )
                    .show()
            return
        }

        val aiEndpoint = aiSettingsManager.aiEndpoint
        val aiModel = aiSettingsManager.aiModel

        // Get sentence context from the stored term data (the original sentence from the text)
        val originalSentenceContext = storedTermData?.sentenceContext ?: ""

        // The proper fix is to ensure sentenceContext is captured when TermData is created
        // during the long-press event in the reading interface, but for now use what's available
        val sentenceContext = originalSentenceContext

        // Get language name from stored term data or fetch from server if needed
        var languageName = ""
        val languageId = storedTermData?.languageId ?: termFormData?.languageId
        if (languageId != null) {
            // For now, we'll use a placeholder approach - in a full implementation we'd fetch the
            // actual language name
            // This could be optimized by adding a method to fetch language name by ID from the
            // server
            languageName = getLanguageNameById(languageId)
        }

        // Replace {term}, {sentence}, and {language} placeholders in the term prompt
        var aiPrompt = aiSettingsManager.aiPromptTerm.replace("{term}", term)
        aiPrompt = aiPrompt.replace("{sentence}", sentenceContext)
        aiPrompt = aiPrompt.replace("{language}", languageName)

        // Show loading indicator
        android.util.Log.d("NativeTermFormFragment", "Sending term to AI endpoint: $aiEndpoint")

        // Make network request to AI endpoint
        Thread {
                    try {
                        val url = java.net.URL(aiEndpoint)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 10000 // 10 seconds
                        connection.readTimeout = 30000 // 30 seconds
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")

                        // Add Authorization header if API key is set (for OpenAI-compatible
                        // endpoints)
                        val aiApiKey = aiSettingsManager.aiApiKey
                        if (aiApiKey.isNotEmpty()) {
                            connection.setRequestProperty("Authorization", "Bearer $aiApiKey")
                        }

                        // Prepare request body in OpenAI format
                        val requestBody =
                                """
                        {
                          "model": "$aiModel",
                          "messages": [
                            {"role": "user", "content": "$aiPrompt"}
                          ],
                          "stream": false
                        }
                        """.trimIndent()

                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Sending request body: $requestBody"
                        )

                        // Write request body
                        val outputStream = connection.outputStream
                        outputStream.write(requestBody.toByteArray())
                        outputStream.flush()
                        outputStream.close()

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "AI response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val response = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            android.util.Log.d("NativeTermFormFragment", "AI response: $response")

                            // Parse response and update UI
                            activity?.runOnUiThread { handleAiResponse(response) }
                        } else {
                            // Handle error response
                            val errorStream = connection.errorStream
                            val errorResponse =
                                    errorStream?.bufferedReader()?.use { it.readText() }
                                            ?: "Unknown error"
                            errorStream?.close()

                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "AI error response: $errorResponse"
                            )

                            activity?.runOnUiThread {
                                android.widget.Toast.makeText(
                                                requireContext(),
                                                "AI request failed: $errorResponse",
                                                android.widget.Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }

                        connection.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("NativeTermFormFragment", "Error sending term to AI", e)
                        activity?.runOnUiThread {
                            android.widget.Toast.makeText(
                                            requireContext(),
                                            "Error connecting to AI service: ${e.message}",
                                            android.widget.Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }
                .start()
    }

    /** Handle AI response and update UI */
    private fun handleAiResponse(response: String) {
        try {
            // Parse OpenAI response format
            val aiTranslation = parseOpenAiResponse(response)

            // Append the AI response to the end of the current translation text
            appendTranslationText(aiTranslation)

            android.widget.Toast.makeText(
                            requireContext(),
                            "AI response added to translation",
                            android.widget.Toast.LENGTH_SHORT
                    )
                    .show()
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error parsing AI response", e)
            android.widget.Toast.makeText(
                            requireContext(),
                            "Error processing AI response: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    /** Parse OpenAI response format */
    private fun parseOpenAiResponse(response: String): String {
        try {
            // Parse the JSON response
            val jsonObject = org.json.JSONObject(response)
            val choices = jsonObject.getJSONArray("choices")

            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                return message.getString("content")
            }
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error parsing OpenAI response", e)
        }

        // Fallback to original response if parsing fails
        return response
    }

    // Get language name by ID by fetching from the server
    // Since there's no direct languages API endpoint in Lute, we'll get language name by
    // fetching the language edit page and parsing the language name from it
    private fun getLanguageNameById(languageId: Int): String {
        var languageName = "Unknown Language"

        // Start a thread to make the network request to get language information
        val latch = java.util.concurrent.CountDownLatch(1)
        Thread {
                    try {
                        val serverSettingsManager =
                                com.example.luteforandroidv2.ui.settings.ServerSettingsManager
                                        .getInstance(requireContext())
                        if (!serverSettingsManager.isServerUrlConfigured()) {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Server URL not configured"
                            )
                            latch.countDown()
                            return@Thread
                        }

                        val serverUrl = serverSettingsManager.getServerUrl()
                        // Use the language edit endpoint to get language info (this returns HTML)
                        val languageUrl = "$serverUrl/language/edit/$languageId"
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Fetching language info for ID: $languageId, URL: $languageUrl"
                        )

                        val url = java.net.URL(languageUrl)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.setRequestProperty("Accept", "text/html")

                        val responseCode = connection.responseCode
                        android.util.Log.d(
                                "NativeTermFormFragment",
                                "Language info response code: $responseCode"
                        )

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val content = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            // Parse the HTML response to extract the language name
                            languageName = parseLanguageNameFromHtml(content)
                            android.util.Log.d(
                                    "NativeTermFormFragment",
                                    "Parsed language name: $languageName for ID: $languageId"
                            )
                        } else {
                            android.util.Log.e(
                                    "NativeTermFormFragment",
                                    "Language info fetch failed with response code: $responseCode"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                                "NativeTermFormFragment",
                                "Error fetching language info",
                                e
                        )
                    } finally {
                        latch.countDown()
                    }
                }
                .start()

        // Wait for the network request to complete (with timeout)
        try {
            latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS) // Wait up to 3 seconds
        } catch (e: InterruptedException) {
            android.util.Log.e(
                    "NativeTermFormFragment",
                    "Interrupted while waiting for language info",
                    e
            )
        }

        return languageName
    }

    private fun parseLanguageNameFromHtml(htmlContent: String): String {
        try {
            val doc = org.jsoup.Jsoup.parse(htmlContent)
            // Look for the language name in the form - typically in an input field or title
            val titleElement = doc.select("title").first()
            if (titleElement != null) {
                val title = titleElement.text()
                // The title is typically "Edit Language: LanguageName"
                if (title.contains("Edit Language:")) {
                    return title.substringAfter("Edit Language:").trim()
                }
            }

            // Try to find the language name in the h1 or h2 tags
            val headerElements = doc.select("h1, h2")
            for (element in headerElements) {
                val text = element.text()
                if (text.contains("Edit", ignoreCase = true) &&
                                text.contains("Language", ignoreCase = true)
                ) {
                    // Extract the language name - this could be in format "Edit Language: English"
                    if (text.contains(":")) {
                        return text.substringAfter(":").trim()
                    } else {
                        // If there's no colon, try to extract the language from the text
                        val parts = text.split(" ")
                        if (parts.size >= 3) { // "Edit Language English" or similar
                            return parts.last().trim()
                        }
                    }
                }
            }

            // Try to find the language name in the name input field
            val nameInput = doc.select("input[name='name']").first()
            if (nameInput != null) {
                val nameValue = nameInput.attr("value")
                if (nameValue.isNotEmpty()) {
                    return nameValue
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error parsing language from HTML", e)
        }
        return "Unknown Language"
    }

    // Data class for status information
    private data class StatusInfo(val id: Int, val label: String, val color: String)

    override fun dismiss() {
        android.util.Log.d("NativeTermFormFragment", "dismiss() called")
        try {
            super.dismiss()
            android.util.Log.d("NativeTermFormFragment", "dismiss() completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("NativeTermFormFragment", "Error dismissing dialog", e)
        }
    }
}
