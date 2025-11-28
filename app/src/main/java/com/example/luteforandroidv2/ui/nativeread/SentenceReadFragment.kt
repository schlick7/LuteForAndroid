package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.luteforandroidv2.MainActivity
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.FragmentSentenceReadBinding
import com.example.luteforandroidv2.ui.nativeread.Audio.AudioPlayerFragment
import com.example.luteforandroidv2.ui.nativeread.Audio.AudioPlayerManager
import com.example.luteforandroidv2.ui.nativeread.Bookmark.BookStateManager
import com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionaryDialogFragment
import com.example.luteforandroidv2.ui.nativeread.Dictionary.SentenceTranslationDialogFragment
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTermFormFragment
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTextView
import com.example.luteforandroidv2.ui.nativeread.Term.Paragraph
import com.example.luteforandroidv2.ui.nativeread.Term.TermData
import com.example.luteforandroidv2.ui.nativeread.Term.TermDataExtractor
import com.example.luteforandroidv2.ui.nativeread.Term.TermFormData
import com.example.luteforandroidv2.ui.nativeread.Term.TermInteractionManager
import com.example.luteforandroidv2.ui.nativeread.Term.TextContent
import com.example.luteforandroidv2.ui.nativeread.Term.TextRenderer
import com.example.luteforandroidv2.ui.nativeread.Term.WordPopup
import com.example.luteforandroidv2.ui.settings.AiSettingsManager
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class SentenceReadFragment :
        Fragment(),
        NativeTermFormFragment.DictionaryListener,
        DictionaryDialogFragment.DictionaryListener,
        NativeTextView.TermInteractionListener,
        TermInteractionManager.TermInteractionListener {
    private var _binding: FragmentSentenceReadBinding? = null
    private val binding
        get() = _binding!!

    private val args: NativeReadFragmentArgs by navArgs()
    private var savedBookId: String? = null
    private var savedBookLanguage: String? = null

    // Listener for communication with MainActivity
    private var fragmentListener: NativeReadFragmentListener? = null

    // SharedPreferences for persistent book data
    private lateinit var readerSettingsPrefs: SharedPreferences

    // SharedPreferences for text formatting settings
    private lateinit var textFormattingPrefs: SharedPreferences

    // ViewModel for this fragment
    private lateinit var viewModel: NativeReadViewModel

    // Child fragments
    private var toolbarFragment: ToolbarFragment? = null
    private var pageIndicatorFragment: PageIndicatorFragment? = null
    private var audioPlayerFragment: AudioPlayerFragment? = null

    // Footer visibility state tracking
    private var lastFooterVisibility: Int? = null

    // Text renderer for displaying content
    private val textRenderer = TextRenderer()

    // Text formatting manager for applying text formatting
    private var textFormattingManager: TextFormattingManager? = null

    // Theme manager for applying themes to UI components
    private var themeManager: ThemeManager? = null

    // Navigation controller for page navigation
    private val navigationController = NavigationController()

    // Book state manager for persistence
    private lateinit var bookStateManager: BookStateManager

    // Term interaction manager for handling term selection
    private val termInteractionManager = TermInteractionManager()

    // Audio player manager for handling audio playback
    private var audioPlayerManager: AudioPlayerManager? = null

    // Navigation manager for handling page navigation
    private var navigationManager: NavigationManager? = null

    // Word popup for showing term information directly from server
    private var wordPopup: WordPopup? = null

    // Track the last selected term to prevent repeated popup
    private var lastSelectedTerm: String? = null

    // Track the current text selection
    private var currentTextSelection: String? = null

    // Track the currently selected term data
    private var currentSelectedTerm: TermData? = null

    // Track if we're showing a term form (to differentiate from popup translation)
    private var isShowingTermForm: Boolean = false

    // Reference to the currently shown NativeTermFormFragment
    private var currentTermFormFragment: NativeTermFormFragment? = null

    // Map to store term data after saving to avoid refetching from server
    private val termDataMap = mutableMapOf<Int, TermFormData>()

    // TTS variables
    private var ttsManager: TTSManager? = null
    private var isTtsEnabled = false

    // Sentence-based reading variables
    private var currentSentenceIndex = 0
    private var sentenceGroups = emptyList<List<Paragraph>>()

    // Track reader menu preferences
    private var showAudioPlayer = true
    private var showStatusTerms = true

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        Log.d("SentenceReadFragment", "onCreateView called")
        // Use the dedicated fragment_sentence_reader layout instead of the shared
        // fragment_native_read layout
        _binding = FragmentSentenceReadBinding.inflate(inflater, container, false)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[NativeReadViewModel::class.java]

        // Initialize word popup
        wordPopup = WordPopup(requireContext())

        // Initialize theme manager
        themeManager = ThemeManager(requireContext())

        // Initialize text formatting manager
        textFormattingManager = TextFormattingManager(requireContext())

        // Register callback for status terms font size changes
        textFormattingManager?.setStatusTermsFontSizeChangeListener { refreshStatusTerms() }

        // Initialize menu preferences
        initializePreferences()

        // Initialize text formatting settings
        initializeTextFormattingSettings()

        // Initialize TTS
        initializeTTS()

        Log.d("SentenceReadFragment", "onCreateView completed")
        return binding.root
    }

    private fun initializeTTS() {
        ttsManager = TTSManager.getInstance(requireContext())
        ttsManager?.setTTSListener(
                object : TTSManager.TTSListener {
                    override fun onTTSStateChanged(isPlaying: Boolean) {
                        activity?.runOnUiThread { updateTtsButtonState(isPlaying) }
                    }
                }
        )

        // Initialize TTS engine
        ttsManager?.initializeTTS { success ->
            if (success) {
                Log.d("SentenceReadFragment", "TTS initialized successfully")
            } else {
                Log.e("SentenceReadFragment", "Failed to initialize TTS")
            }
        }
    }

    /** Toggle status terms visibility based on preference */
    private fun toggleStatusTermsVisibility(show: Boolean) {
        Log.d("SentenceReadFragment", "toggleStatusTermsVisibility called with: $show")
        try {
            if (show) {
                // Show the status terms container
                binding.statusTermsContainer.visibility = View.VISIBLE
                // Limit the height of the status terms container to prevent overlay
                val layoutParams = binding.statusTermsContainer.layoutParams
                layoutParams.height =
                        resources.getDimension(R.dimen.max_status_terms_height).toInt()
                binding.statusTermsContainer.layoutParams = layoutParams

                // Setup AI button
                setupAiButton()

                // Setup TTS button
                setupTtsButton()

                // Load and display status terms
                loadAndDisplayStatusTerms()
            } else {
                // Hide the status terms container
                binding.statusTermsContainer.visibility = View.GONE
            }
            Log.d("SentenceReadFragment", "toggleStatusTermsVisibility completed successfully")
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in toggleStatusTermsVisibility", e)
        }
    }

    /** Setup AI button in the status terms header */
    private fun setupAiButton() {
        try {
            // Get AI settings
            val aiSettingsManager = AiSettingsManager.getInstance(requireContext())

            // Check if AI button should be shown
            if (aiSettingsManager.shouldShowAiButtonInSentenceReader()) {
                // Find the AI button in the status terms container
                val aiButton =
                        binding.statusTermsContainer.findViewById<ImageButton>(R.id.ai_button)

                // Show the AI button
                aiButton?.visibility = View.VISIBLE

                // Set click listener for the AI button
                aiButton?.setOnClickListener {
                    // Get current sentence text
                    val currentSentenceText = getCurrentSentenceText()

                    if (currentSentenceText.isNotEmpty()) {
                        // Send sentence to AI endpoint
                        sendSentenceToAi(currentSentenceText)
                    } else {
                        // Show a toast message if the sentence is empty
                        Toast.makeText(
                                        requireContext(),
                                        "No sentence text available for AI processing",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            } else {
                // Hide the AI button
                val aiButton =
                        binding.statusTermsContainer.findViewById<ImageButton>(R.id.ai_button)
                aiButton?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error setting up AI button", e)
        }
    }

    /** Setup TTS button in the status terms header */
    private fun setupTtsButton() {
        try {
            // Get user preference for TTS button visibility from SharedPreferences
            val sharedPref =
                    requireContext()
                            .getSharedPreferences("sentence_reader_settings", Context.MODE_PRIVATE)
            val showTtsButton = sharedPref.getBoolean("show_tts_button", true) // Default to true

            // Find the TTS button in the status terms container
            val ttsButton = binding.statusTermsContainer.findViewById<ImageButton>(R.id.tts_button)

            if (showTtsButton) {
                // Show the TTS button
                ttsButton?.visibility = View.VISIBLE

                // Set click listener for the TTS button
                ttsButton?.setOnClickListener {
                    if (ttsManager?.isSpeaking() == true) {
                        // If TTS is playing, stop/pause it
                        ttsManager?.pause()
                    } else {
                        // If TTS is not playing, start it with current sentence
                        val currentSentenceText = getCurrentSentenceText()
                        if (currentSentenceText.isNotEmpty()) {
                            // Check user's TTS language preference
                            val ttsLanguagePreference = getTtsLanguagePreference()

                            if (ttsLanguagePreference == "Auto (Detect from Book)") {
                                // Auto-detect language from book
                                val bookLanguage = getCurrentBookLanguageName()
                                if (!bookLanguage.isNullOrEmpty()) {
                                    ttsManager?.setLanguageForBook(bookLanguage)
                                }
                            } else {
                                // Use user's selected language
                                val selectedLanguageCode =
                                        mapLanguageDisplayNameToCode(ttsLanguagePreference)
                                if (!selectedLanguageCode.isNullOrEmpty()) {
                                    ttsManager?.setLanguage(selectedLanguageCode)
                                }
                            }
                            ttsManager?.speak(currentSentenceText)
                        } else {
                            Toast.makeText(
                                            requireContext(),
                                            "No sentence text to read aloud",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    }
                }
            } else {
                // Hide the TTS button
                ttsButton?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error setting up TTS button", e)
        }
    }

    /** Update TTS button state based on whether TTS is playing */
    private fun updateTtsButtonState(isPlaying: Boolean) {
        try {
            val ttsButton = binding.statusTermsContainer.findViewById<ImageButton>(R.id.tts_button)
            if (ttsButton?.visibility == View.VISIBLE) {
                if (isPlaying) {
                    // TTS is playing, show pause icon
                    ttsButton.setImageResource(android.R.drawable.ic_media_pause)
                    ttsButton.contentDescription = "Pause Text-to-Speech"
                } else {
                    // TTS is not playing, show play icon
                    ttsButton.setImageResource(android.R.drawable.ic_media_play)
                    ttsButton.contentDescription = "Play Text-to-Speech"
                }
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error updating TTS button state", e)
        }
    }

    /** Load and display status terms with enhanced translation data */
    private fun loadAndDisplayStatusTerms() {
        Log.d("SentenceReadFragment", "loadAndDisplayStatusTerms called")
        try {
            // Clear previous content
            binding.statusTermsContent.removeAllViews()

            // Get the NativeTextView from the content container
            val nativeTextView = findNativeTextView(binding.textContentContainer)

            if (nativeTextView != null) {
                // Get segment information from the NativeTextView
                val segmentInfoList = nativeTextView.getSegmentInfo()

                // Group terms by status (1-5)
                val termsByStatus = mutableMapOf<Int, MutableList<TermData>>()

                // Initialize maps for each status
                for (status in 1..5) {
                    termsByStatus[status] = mutableListOf()
                }

                // Extract terms with status 1-5, deduplicating by termId
                val addedTermIds = mutableSetOf<Int>()
                for (segmentInfo in segmentInfoList) {
                    val segment = segmentInfo.segment
                    if (segment.status in 1..5 && segment.isInteractive) {
                        // Only add the term if we haven't added it already (deduplication)
                        if (!addedTermIds.contains(segment.termId)) {
                            val termData =
                                    TermData(
                                            termId = segment.termId,
                                            term = segment.text,
                                            languageId = segment.languageId,
                                            translation = segment.translation,
                                            status = segment.status,
                                            parentsList = segment.parentList,
                                            parentTranslations = segment.parentTranslations,
                                            tapX = -1f,
                                            tapY = -1f,
                                            segmentId = segment.id
                                    )
                            termsByStatus[segment.status]?.add(termData)
                            addedTermIds.add(segment.termId)
                        }
                    }
                }

                // Display all terms without category headers
                for (status in 1..5) {
                    val terms = termsByStatus[status]
                    if (!terms.isNullOrEmpty()) {
                        // Add each term without headers
                        for (term in terms) {
                            val termView = createStatusTermView(term)
                            binding.statusTermsContent.addView(termView)
                        }
                    }
                }

                // Fetch enhanced translation data for all terms asynchronously
                fetchEnhancedTermData(termsByStatus)

                // Post to ensure refresh happens after all operations complete
                binding.statusTermsContent.post {
                    binding.statusTermsContent.requestLayout()
                    binding.statusTermsContent.invalidate()
                }
            } else {
                Log.d("SentenceReadFragment", "No NativeTextView found in content container")
            }

            Log.d("SentenceReadFragment", "loadAndDisplayStatusTerms completed successfully")
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in loadAndDisplayStatusTerms", e)
        }
    }

    /** Fetch enhanced translation data for all status terms */
    private fun fetchEnhancedTermData(termsByStatus: Map<Int, List<TermData>>) {
        Log.d("SentenceReadFragment", "fetchEnhancedTermData called")

        // Flatten all terms into a single list
        val allTerms = termsByStatus.values.flatten()

        // For each term, fetch enhanced translation data from wordpopup endpoint
        // We'll do this asynchronously to avoid blocking the UI
        lifecycleScope.launch {
            try {
                // Process terms in batches to avoid overwhelming the server
                val batchSize = 5
                for (i in allTerms.indices step batchSize) {
                    val batch = allTerms.subList(i, minOf(i + batchSize, allTerms.size))

                    // Launch coroutines for each term in the batch
                    val jobs =
                            batch.map { term ->
                                async {
                                    try {
                                        Log.d(
                                                "SentenceReadFragment",
                                                "Fetching wordpopup data for term ID: ${term.termId}"
                                        )
                                        val result = viewModel.getTermPopupData(term.termId)
                                        if (result.isSuccess) {
                                            val popupContent = result.getOrNull()
                                            if (popupContent != null) {
                                                // Parse the enhanced data and update the UI
                                                updateTermWithEnhancedData(term, popupContent)
                                            }
                                        } else {
                                            Log.e(
                                                    "SentenceReadFragment",
                                                    "Failed to fetch wordpopup data for term ID: ${term.termId}"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                                "SentenceReadFragment",
                                                "Error fetching wordpopup data for term ID: ${term.termId}",
                                                e
                                        )
                                    }
                                }
                            }

                    // Wait for all jobs in this batch to complete
                    jobs.awaitAll()
                }

                Log.d(
                        "SentenceReadFragment",
                        "Completed fetching enhanced term data for ${allTerms.size} terms"
                )
            } catch (e: Exception) {
                Log.e("SentenceReadFragment", "Error in fetchEnhancedTermData", e)
            }
        }
    }

    /** Update term display with enhanced translation data */
    private fun updateTermWithEnhancedData(term: TermData, popupContent: String) {
        Log.d(
                "SentenceReadFragment",
                "Updating term ${term.termId} (${term.term}) with enhanced data"
        )
        try {
            // Parse the HTML content to extract enhanced translation information
            val enhancedTranslation = parseEnhancedTranslationData(popupContent)

            if (enhancedTranslation.isNotBlank()) {
                // Find the corresponding term layout in the status terms container
                val termLayout = findTermLayoutByTermId(term.termId)

                if (termLayout != null) {
                    // Get the translation text view we stored earlier
                    val translationTextView =
                            termLayout.getTag(R.id.translation_text_view_tag) as? TextView

                    if (translationTextView != null) {
                        // Update the translation text with the enhanced data
                        translationTextView.text = enhancedTranslation
                        Log.d(
                                "SentenceReadFragment",
                                "Successfully updated translation for term ${term.termId}"
                        )
                    } else {
                        Log.d(
                                "SentenceReadFragment",
                                "Could not find translation text view for term ${term.termId}"
                        )
                    }
                } else {
                    Log.d(
                            "SentenceReadFragment",
                            "Could not find term layout for term ${term.termId}"
                    )
                }
            } else {
                Log.d(
                        "SentenceReadFragment",
                        "No enhanced translation data found for term ${term.termId}"
                )
            }
        } catch (e: Exception) {
            Log.e(
                    "SentenceReadFragment",
                    "Error updating term ${term.termId} with enhanced data",
                    e
            )
        }
    }

    /** Parse enhanced translation data from wordpopup HTML content */
    private fun parseEnhancedTranslationData(htmlContent: String): String {
        return try {
            if (htmlContent.isBlank()) {
                return ""
            }

            val doc = Jsoup.parse(htmlContent)

            // Extract term and parents text (first <b> inside first <p>)
            val termAndParentsElement = doc.select("p b").first()
            val termAndParentsText = termAndParentsElement?.text()?.trim() ?: ""

            // Extract main translation (first <p> without class that's not the first one)
            var translationText = ""
            val pTags = doc.select("p")
            for (p in pTags) {
                // Skip the first <p> which contains the term name
                if (p == pTags.first()) continue

                // Skip <p> tags with specific classes
                val className = p.className()
                if (className == "small-flash-notice") continue

                // Skip <p> tags that contain only <i> (romanization)
                if (p.children().size == 1 && p.child(0).tagName() == "i") continue

                // If we get here, this <p> tag likely contains the translation
                val text = p.text().trim()
                if (text.isNotBlank()) {
                    translationText = text
                    break
                }
            }

            // Extract parent translation (from the first parent in the parents div)
            var parentTranslationText = ""
            var parentTermText = ""
            // Look for the parents div section
            val parentsDivs = doc.select("div")
            for (div in parentsDivs) {
                // Check if this div contains parents (look for "Parents" text or div structure)
                val parentEntries = div.select("p")
                if (parentEntries.isNotEmpty()) {
                    // Look for a parent entry that has a translation
                    for (parentEntry in parentEntries) {
                        val children = parentEntry.childNodes()

                        // Extract parent term (text before <br>)
                        val parentTermBuilder = StringBuilder()
                        for (node in children) {
                            if (node.nodeName() == "br") break
                            if (node.nodeName() == "#text") {
                                parentTermBuilder.append(node.toString().trim())
                            }
                        }
                        parentTermText = parentTermBuilder.toString().trim()

                        // Extract parent translation (text after <br>)
                        for (i in children.indices) {
                            val node = children[i]
                            // Look for <br> tag which separates parent term info from translation
                            if (node.nodeName() == "br") {
                                // Get text after the <br> tag
                                val textNodes = children.drop(i + 1)
                                val translationBuilder = StringBuilder()
                                for (textNode in textNodes) {
                                    if (textNode.nodeName() == "#text") {
                                        translationBuilder.append(textNode.toString().trim())
                                    }
                                }
                                val extractedText = translationBuilder.toString().trim()
                                if (extractedText.isNotBlank()) {
                                    parentTranslationText = extractedText
                                    break
                                }
                            }
                        }
                        if (parentTranslationText.isNotBlank()) break
                    }
                }
                if (parentTranslationText.isNotBlank()) break
            }

            // Format the complete translation information
            val formattedText = buildString {
                var hasMainTranslation = false
                var hasParentTranslation = false

                // Add main translation if available
                if (translationText.isNotBlank()) {
                    append(translationText)
                    hasMainTranslation = true
                }

                // Add parent translation if available (in parentheses)
                if (parentTranslationText.isNotBlank() && parentTermText.isNotBlank()) {
                    if (hasMainTranslation) {
                        append(" ") // Space separator if we already have main translation
                    }
                    append("($parentTermText: $parentTranslationText)")
                    hasParentTranslation = true
                }

                // If we have nothing, return empty string
                if (!hasMainTranslation && !hasParentTranslation) {
                    append("[No translation]")
                }
            }

            Log.d("SentenceReadFragment", "Parsed enhanced translation: '$formattedText'")
            formattedText
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error parsing enhanced translation data", e)
            "[Parse error]"
        }
    }

    /** Find term layout by term ID in the status terms container */
    private fun findTermLayoutByTermId(termId: Int): LinearLayout? {
        try {
            val container = binding.statusTermsContent
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is LinearLayout) {
                    val tag = child.tag as? String
                    if (tag == "term_$termId") {
                        return child
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error finding term layout by term ID: $termId", e)
        }
        return null
    }

    /** Create a view for a status term */
    private fun createStatusTermView(termData: TermData): View {
        val context = requireContext()

        // Create a linear layout for the term and translation
        val termLayout =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 4, 0, 4) // Reduced vertical padding
                    // Store term ID as tag for later reference
                    tag = "term_${termData.termId}"
                }

        // Create term text view with status-specific background color
        val termTextView =
                TextView(context).apply {
                    text = termData.term
                    // Convert progress to font size (e.g., 8-24sp)
                    val fontSize =
                            8f + ((textFormattingManager?.currentStatusTermsFontSize ?: 50) * 0.2f)
                    textSize = fontSize
                    setTextColor(ContextCompat.getColor(context, R.color.lute_on_background))

                    // Apply status-specific background color with rounded corners
                    val backgroundColor = getStatusColor(termData.status)
                    if (backgroundColor != android.graphics.Color.TRANSPARENT) {
                        // Create a drawable with rounded corners matching sentence terms
                        val drawable = android.graphics.drawable.GradientDrawable()
                        drawable.setColor(backgroundColor)
                        drawable.cornerRadius = 6f // Match corner radius from WordBackgroundSpan
                        background = drawable
                    } else {
                        // For transparent backgrounds, use a subtle background to maintain
                        // visibility
                        setBackgroundResource(R.drawable.term_status_background)
                    }

                    setPadding(8, 4, 8, 4) // Reduced padding to match sentence terms more closely
                }

        // Create translation text view and store reference for later updates
        val translationTextView =
                TextView(context).apply {
                    text = termData.translation.ifEmpty { "[No translation]" }
                    // Convert progress to font size (e.g., 8-24sp)
                    val fontSize =
                            8f + ((textFormattingManager?.currentStatusTermsFontSize ?: 50) * 0.2f)
                    textSize = fontSize
                    setTextColor(ContextCompat.getColor(context, R.color.lute_on_background))
                    setPadding(8, 4, 8, 4) // Reduced padding
                }

        // Store translation text view as a tag so we can update it later
        termLayout.setTag(R.id.translation_text_view_tag, translationTextView)

        // Add views to layout
        termLayout.addView(termTextView)
        termLayout.addView(translationTextView)

        return termLayout
    }

    /** Get background color based on term status - matches TextRenderer implementation */
    private fun getStatusColor(status: Int): Int {
        return when (status) {
            0 -> android.graphics.Color.parseColor("#8095FF") // light blue - Unknown (status 0)
            1 -> android.graphics.Color.parseColor("#b46b7a") // light red - Learning (status 1)
            2 -> android.graphics.Color.parseColor("#BA8050") // light orange - Learning (status 2)
            3 ->
                    android.graphics.Color.parseColor(
                            "#BD9C7B"
                    ) // light yellow/tan - Learning (status 3)
            4 -> android.graphics.Color.parseColor("#756D6B") // light grey - Learning (status 4)
            5 ->
                    android.graphics.Color.parseColor(
                            "#40756D6B"
                    ) // 25% transparent light grey - Known (status 5)
            98 -> android.graphics.Color.TRANSPARENT // No background for ignored terms (status 98)
            99 ->
                    android.graphics.Color
                            .TRANSPARENT // No background for well-known terms (status 99)
            else -> android.graphics.Color.TRANSPARENT // Default - no background
        }
    }

    /** Find the NativeTextView within a container */
    private fun findNativeTextView(container: ViewGroup): NativeTextView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is NativeTextView) {
                return child
            } else if (child is ViewGroup) {
                val result = findNativeTextView(child)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    /** Initialize menu preferences from SharedPreferences */
    private fun initializePreferences() {
        readerSettingsPrefs =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        textFormattingPrefs =
                requireContext().getSharedPreferences("text_formatting", Context.MODE_PRIVATE)

        // Set up preference change listener for text formatting
        val sharedPref =
                requireActivity()
                        .getSharedPreferences("reader_menu_preferences", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        // Restore reader menu settings
        showAudioPlayer = sharedPref.getBoolean("show_audio_player", true)
        showStatusTerms = sharedPref.getBoolean("show_status_terms", true)
        editor.apply()
    }

    /** Initialize text formatting settings from SharedPreferences */
    private fun initializeTextFormattingSettings() {
        // Settings are now handled by TextFormattingManager
        // The values are automatically loaded during initialization
    }

    /** Save menu preferences to SharedPreferences */
    private fun saveMenuPreferences() {
        val sharedPref =
                requireContext()
                        .getSharedPreferences("reader_menu_preferences", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("show_audio_player", showAudioPlayer)
            putBoolean("show_status_terms", showStatusTerms)
            apply()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("SentenceReadFragment", "onViewCreated called")

        // Initialize menu preferences
        initializePreferences()
        Log.d("SentenceReadFragment", "Menu preferences initialized")

        // Initialize text formatting settings
        initializeTextFormattingSettings()
        Log.d("SentenceReadFragment", "Text formatting settings initialized")

        // Initialize book state manager
        bookStateManager = BookStateManager(requireContext())
        Log.d("SentenceReadFragment", "Book state manager initialized")

        // Initialize navigation manager
        navigationManager =
                NavigationManager(
                        this,
                        viewModel,
                        bookStateManager,
                        navigationController,
                        savedBookId,
                        savedBookLanguage
                )
        Log.d("SentenceReadFragment", "Navigation manager initialized")

        // Set up term interaction manager listener
        termInteractionManager.setTermInteractionListener(this)
        Log.d("SentenceReadFragment", "Term interaction manager listener set")

        // Load the last book ID if available
        loadLastBookId()
        Log.d("SentenceReadFragment", "Last book ID loaded: '$savedBookId'")
        Log.d("SentenceReadFragment", "Last book language loaded: '$savedBookLanguage'")

        // Clear the content container to prevent any raw HTML from showing
        binding.textContentContainer.removeAllViews()

        // Check if we have a book ID from arguments or saved state
        val bookId = args.bookId
        Log.d("SentenceReadFragment", "Book ID from args: '$bookId'")
        Log.d("SentenceReadFragment", "Saved book language: '$savedBookLanguage'")

        if (bookId.isNotEmpty()) {
            savedBookId = bookId
            Log.d("SentenceReadFragment", "Set savedBookId from args: '$savedBookId'")
        }

        Log.d("SentenceReadFragment", "Final savedBookId: '$savedBookId'")
        Log.d("SentenceReadFragment", "Final savedBookLanguage: '$savedBookLanguage'")

        // Update the NavigationManager with the final book ID and language
        navigationManager?.updateBookInfo(savedBookId, savedBookLanguage)
        Log.d("SentenceReadFragment", "Navigation manager updated with final book info")

        // If we don't have a book ID, navigate to the books view
        if (savedBookId.isNullOrEmpty()) {
            Log.d("SentenceReadFragment", "No book ID available, navigating to books view")
            try {
                findNavController().navigate(R.id.nav_books)
                return
            } catch (e: Exception) {
                Log.e("SentenceReadFragment", "Error navigating to books view", e)
            }
        }

        // Setup the UI components
        Log.d("SentenceReadFragment", "Setting up UI components")
        setupUI()
        Log.d("SentenceReadFragment", "UI components setup completed")

        // Load book content
        Log.d("SentenceReadFragment", "Loading book content")
        loadBookContent()

        Log.d("SentenceReadFragment", "onViewCreated completed")
    }

    private fun loadLastBookId() {
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        savedBookId = sharedPref.getString("last_book_id", null)
        savedBookLanguage = sharedPref.getString("last_book_language", null)
        Log.d("SentenceReadFragment", "Loaded savedBookId: '$savedBookId'")
        Log.d("SentenceReadFragment", "Loaded savedBookLanguage: '$savedBookLanguage'")

        // Validate the loaded language ID
        if (!savedBookLanguage.isNullOrEmpty()) {
            try {
                val langId = savedBookLanguage?.toIntOrNull()
                if (langId == null || langId <= 0) {
                    Log.d("SentenceReadFragment", "Invalid saved language ID, resetting to default")
                    savedBookLanguage = "1"
                }
            } catch (e: Exception) {
                Log.e(
                        "SentenceReadFragment",
                        "Error parsing saved language ID, resetting to default",
                        e
                )
                savedBookLanguage = "1"
            }
        }
    }

    private fun saveLastBookId(bookId: String) {
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        Log.d(
                "SentenceReadFragment",
                "Saving book ID: '$bookId' and language: '$savedBookLanguage'"
        )
        with(sharedPref.edit()) {
            putString("last_book_id", bookId)
            if (savedBookLanguage != null) {
                putString("last_book_language", savedBookLanguage)
            }
            apply()
        }
    }

    private fun setupUI() {
        Log.d("SentenceReadFragment", "setupUI called")
        // Setup toolbar
        setupToolbar()
        Log.d("SentenceReadFragment", "Toolbar setup completed")

        // Setup page indicator
        setupPageIndicator()
        Log.d("SentenceReadFragment", "Page indicator setup completed")

        // Setup main content area
        setupContentArea()
        Log.d("SentenceReadFragment", "Content area setup completed")

        // Setup sentence navigation controls
        setupSentenceNavigationControls()
        Log.d("SentenceReadFragment", "Sentence navigation controls setup completed")

        // Apply theme to all UI components
        // This needs to be posted to ensure all child fragments are fully created
        binding.root.post {
            themeManager?.applyNativeReaderTheme(binding.root)
            // Initialize status terms visibility based on preference
            toggleStatusTermsVisibility(showStatusTerms)
        }
        Log.d("SentenceReadFragment", "setupUI completed")
    }

    /** Apply saved text formatting settings to the content */
    private fun applySavedTextFormatting() {
        Log.d("SentenceReadFragment", "applySavedTextFormatting called")
        Log.d(
                "SentenceReadFragment",
                "Container class: ${binding.textContentContainer.javaClass.simpleName}"
        )

        // Apply saved font
        textFormattingManager?.applySelectedFont(
                textFormattingManager?.currentFontName ?: "Default",
                binding.textContentContainer
        )

        // Apply saved font size
        textFormattingManager?.updateFontSize(
                textFormattingManager?.currentFontSize ?: 35,
                binding.textContentContainer
        )

        // Apply saved line spacing
        textFormattingManager?.updateLineSpacing(
                textFormattingManager?.currentLineSpacing ?: 45,
                binding.textContentContainer
        )

        // Refresh status terms to apply any font size changes
        refreshStatusTerms()

        Log.d("SentenceReadFragment", "applySavedTextFormatting completed")
    }

    /** Refresh status terms to apply current formatting settings */
    private fun refreshStatusTerms() {
        Log.d("SentenceReadFragment", "refreshStatusTerms called")
        // Reload and display status terms with current formatting
        loadAndDisplayStatusTerms()
        Log.d("SentenceReadFragment", "refreshStatusTerms completed")
    }

    private fun setupToolbar() {
        toolbarFragment = ToolbarFragment()
        childFragmentManager
                .beginTransaction()
                .replace(R.id.toolbar_container, toolbarFragment!!)
                .commit()
        Log.d("SentenceReadFragment", "Toolbar fragment added")

        // Create the audio player manager with the repository
        audioPlayerManager = AudioPlayerManager(requireContext(), viewModel.repository)

        // Create and setup the audio player fragment
        audioPlayerFragment = AudioPlayerFragment()
        audioPlayerFragment?.setAudioPlayerManager(audioPlayerManager!!)

        childFragmentManager
                .beginTransaction()
                .replace(R.id.audio_player_container, audioPlayerFragment!!)
                .commit()

        // Hide the audio player container for now
        binding.audioPlayerContainer.visibility = View.GONE
        Log.d("SentenceReadFragment", "Audio player fragment added (hidden)")
    }

    private fun setupPageIndicator() {
        pageIndicatorFragment = PageIndicatorFragment()
        pageIndicatorFragment?.setPageNavigationListener(
                object : PageIndicatorFragment.PageNavigationListener {
                    override fun onAllKnown() {
                        // Implementation for handling 'all known' button click
                        // Add your logic here
                    }

                    override fun onPreviousPage() {
                        navigateToPreviousPage()
                    }

                    override fun onLuteMenu() {
                        Log.d("SentenceReadFragment", "onLuteMenu called")
                        try {
                            if (isReaderOptionsMenuVisible()) {
                                Log.d("SentenceReadFragment", "Menu is visible, hiding it")
                                hideReaderOptionsMenu()
                            } else {
                                Log.d("SentenceReadFragment", "Menu is not visible, showing it")
                                showReaderOptionsMenu()
                            }
                        } catch (e: Exception) {
                            Log.e("SentenceReadFragment", "Error in onLuteMenu", e)
                        }
                    }

                    override fun onNextPage() {
                        navigationManager?.navigateToNextPageWithoutMarkingDone()
                    }

                    override fun onMarkPageDone() {
                        navigationManager?.markPageAsDoneAndNavigateToNext()
                    }
                }
        )
        childFragmentManager
                .beginTransaction()
                .replace(R.id.page_indicator_container, pageIndicatorFragment!!)
                .commit()
        Log.d("SentenceReadFragment", "Page indicator fragment added")

        // Hide the all known button in the page indicator for the sentence reader
        // This needs to be posted to ensure the fragment is fully created
        binding.root.post {
            pageIndicatorFragment?.view?.findViewById<View>(R.id.all_known_button)?.visibility =
                    View.GONE
        }
    }

    private fun setupContentArea() {
        // Observe the current page content from the ViewModel
        viewModel.currentPageContent.observe(viewLifecycleOwner) { content ->
            Log.d("SentenceReadFragment", "=== RECEIVED PAGE CONTENT ===")
            Log.d("SentenceReadFragment", "Content paragraphs count: ${content.paragraphs.size}")
            Log.d("SentenceReadFragment", "Page metadata:")
            Log.d("SentenceReadFragment", "  Book ID: ${content.pageMetadata.bookId}")
            Log.d("SentenceReadFragment", "  Page num: ${content.pageMetadata.pageNum}")
            Log.d("SentenceReadFragment", "  Page count: ${content.pageMetadata.pageCount}")
            Log.d("SentenceReadFragment", "  Has audio: ${content.pageMetadata.hasAudio}")
            Log.d("SentenceReadFragment", "  Is RTL: ${content.pageMetadata.isRTL}")
            Log.d("SentenceReadFragment", "  Language ID: ${content.pageMetadata.languageId}")
            Log.d("SentenceReadFragment", "  Language name: ${content.pageMetadata.languageName}")

            // Log first few paragraphs for debugging
            content.paragraphs.take(3).forEachIndexed { index, paragraph ->
                Log.d(
                        "SentenceReadFragment",
                        "Paragraph $index: ${paragraph.id} with ${paragraph.segments.size} segments"
                )
                paragraph.segments.take(3).forEachIndexed { segIndex, segment ->
                    Log.d(
                            "SentenceReadFragment",
                            "  Segment $segIndex: '${segment.text}' (status: ${segment.status}, interactive: ${segment.isInteractive})"
                    )
                }
                if (paragraph.segments.size > 3) {
                    Log.d(
                            "SentenceReadFragment",
                            "  ... and ${paragraph.segments.size - 3} more segments"
                    )
                }
            }
            if (content.paragraphs.size > 3) {
                Log.d(
                        "SentenceReadFragment",
                        "... and ${content.paragraphs.size - 3} more paragraphs"
                )
            }

            // Process content into sentence groups for sentence-by-sentence reading
            Log.d("SentenceReadFragment", "Processing content into sentence groups")
            sentenceGroups = processContentIntoSentenceGroups(content)
            currentSentenceIndex = 0

            // Clear the container first to ensure no raw HTML is displayed
            binding.textContentContainer.removeAllViews()

            // Temporarily allow scrolling during content rendering to prevent rendering issues
            (binding.contentScrollView as? NonScrollingScrollView)?.setAutoScrollBlocked(false)

            // Render only the first sentence group
            Log.d(
                    "SentenceReadFragment",
                    "Rendering first sentence group with language ID: ${content.pageMetadata.languageId}"
            )
            renderCurrentSentence(content)

            // Monitor scroll position to understand the jumping behavior
            binding.contentScrollView.post {
                val initialScrollY = binding.contentScrollView.scrollY
                Log.d(
                        "SentenceReadFragment",
                        "Initial scroll position after render: $initialScrollY"
                )

                // Now that content is rendered, block auto-scrolling to prevent unwanted scrolling
                (binding.contentScrollView as? NonScrollingScrollView)?.setAutoScrollBlocked(true)

                // Add scroll listener to track changes
                binding.contentScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY
                    ->
                    if (Math.abs(scrollY - oldScrollY) > 5) { // Only log significant changes
                        Log.d(
                                "SentenceReadFragment",
                                "Scroll position changed from $oldScrollY to $scrollY"
                        )
                    }
                }
            }

            // Apply saved text formatting settings
            applySavedTextFormatting()

            // Set up scroll listener for fullscreen mode

            // Update navigation controller with page info
            Log.d(
                    "SentenceReadFragment",
                    "Setting total page count to ${content.pageMetadata.pageCount}"
            )
            navigationController.setTotalPageCount(content.pageMetadata.pageCount)
            Log.d("SentenceReadFragment", "Setting current page to ${content.pageMetadata.pageNum}")
            navigationController.setCurrentPage(content.pageMetadata.pageNum)

            // Save the book language if we have it
            if (content.pageMetadata.languageId > 0) {
                // Prefer the language name from page metadata if available
                if (!content.pageMetadata.languageName.isNullOrEmpty()) {
                    savedBookLanguage = content.pageMetadata.languageName
                    Log.d(
                            "SentenceReadFragment",
                            "Saved book language name from metadata: ${content.pageMetadata.languageName}"
                    )
                } else {
                    // If language name is not in metadata, try to get it from the book ID
                    savedBookId?.let { bookId ->
                        // Try to get the language name from MainActivity's cache or fetch it
                        val mainActivity = activity as? MainActivity
                        mainActivity?.fetchBookLanguage(bookId) { languageName ->
                            if (!languageName.isNullOrEmpty()) {
                                activity?.runOnUiThread {
                                    savedBookLanguage = languageName
                                    Log.d(
                                            "SentenceReadFragment",
                                            "Fetched and saved book language name: $languageName"
                                    )
                                    // Update NavigationManager with the proper language name
                                    navigationManager?.updateBookInfo(
                                            savedBookId,
                                            savedBookLanguage
                                    )
                                    // Save to SharedPreferences
                                    saveLastBookId(savedBookId ?: "")
                                }
                            }
                        }
                    }
                            ?: run {
                                // If we can't get book ID, but we have a valid language ID,
                                // try to get the language name from MainActivity
                                if (content.pageMetadata.languageId > 0) {
                                    val mainActivity = activity as? MainActivity
                                    mainActivity?.fetchBookLanguage(content.pageMetadata.bookId) {
                                            languageName ->
                                        if (!languageName.isNullOrEmpty()) {
                                            activity?.runOnUiThread {
                                                savedBookLanguage = languageName
                                                Log.d(
                                                        "SentenceReadFragment",
                                                        "Fetched language name for ID ${content.pageMetadata.languageId}: $languageName"
                                                )
                                                // Save to SharedPreferences
                                                saveLastBookId(savedBookId ?: "")
                                            }
                                        } else {
                                            // Fallback to using language ID as string if we can't
                                            // get language name
                                            savedBookLanguage =
                                                    content.pageMetadata.languageId.toString()
                                            Log.d(
                                                    "SentenceReadFragment",
                                                    "Saved book language ID as final fallback: ${content.pageMetadata.languageId}"
                                            )
                                        }
                                    }
                                } else {
                                    // Fallback to using language ID as string if we have no valid
                                    // language ID
                                    savedBookLanguage = content.pageMetadata.languageId.toString()
                                    Log.d(
                                            "SentenceReadFragment",
                                            "Saved book language ID as fallback: ${content.pageMetadata.languageId}"
                                    )
                                }
                            }
                }
                // Save to SharedPreferences
                saveLastBookId(savedBookId ?: "")
            } else {
                // If we don't have a valid language ID from page metadata,
                // try to use the saved one or default to 1
                if (savedBookLanguage.isNullOrEmpty()) {
                    savedBookLanguage = "1"
                    Log.d(
                            "SentenceReadFragment",
                            "No valid language ID in page metadata, using default: 1"
                    )
                } else {
                    Log.d(
                            "SentenceReadFragment",
                            "Using saved book language ID: $savedBookLanguage"
                    )
                }
            }

            // Log the page metadata for debugging
            Log.d(
                    "SentenceReadFragment",
                    "Page metadata - languageId: ${content.pageMetadata.languageId}, languageName: '${content.pageMetadata.languageName}'"
            )

            // Update page indicator
            pageIndicatorFragment?.updatePageCounter(
                    content.pageMetadata.pageNum,
                    content.pageMetadata.pageCount
            )

            // Do not perform any auto-scrolling - all scrolling should be manual
            // Previously we had various auto-scroll behaviors that have been removed

            // Check if audio is available by testing the actual audio endpoint
            checkAudioAvailability(content.pageMetadata.bookId)
        }

        // Observe translation results
        viewModel.translationResult.observe(viewLifecycleOwner) { content ->
            // Check if we're showing a term form (content will be the term edit page HTML)
            if (isShowingTermForm) {
                // Parse the term edit page to extract complete term data
                try {
                    val term = currentSelectedTerm
                    if (term != null) {
                        // Use TermDataExtractor to parse the HTML content
                        val termFormData =
                                TermDataExtractor.parseTermDataFromHtml(
                                        content,
                                        term.termId,
                                        term.term
                                )

                        // Create and show the NativeTermFormFragment with the complete term data
                        val termFormFragment =
                                NativeTermFormFragment.newInstance(
                                        termFormData = termFormData,
                                        storedTermData = term,
                                        onSave = { updatedTerm ->
                                            Log.d(
                                                    "SentenceReadFragment",
                                                    "Term form saved: ${updatedTerm.termText}"
                                            )
                                            // Store the updated term data
                                            if (updatedTerm.termId != null) {
                                                termDataMap[updatedTerm.termId] = updatedTerm
                                            }

                                            // Handle save - update the term in the UI
                                            // The dictionary listener will handle the actual UI
                                            // refresh with scroll preservation
                                        },
                                        onCancel = {
                                            Log.d("SentenceReadFragment", "Term form cancelled")
                                            // Handle cancel
                                        },
                                        dictionaryListener = this
                                )
                        termFormFragment.show(childFragmentManager, "term_form")
                    }
                } catch (e: Exception) {
                    Log.e("SentenceReadFragment", "Error parsing term edit page content", e)
                    // Fallback to showing the form with the original term data
                    showTermFormWithTranslation("")
                } finally {
                    // Reset the flag
                    isShowingTermForm = false
                }
            } else {
                // Show the word popup with server data
                val lastTerm = currentSelectedTerm
                if (lastTerm != null) {
                    Log.d("SentenceReadFragment", "Showing word popup with server data")
                    // Parse the HTML content to extract formatted text
                    val formattedText = extractAndFormatTermInfo(content)
                    if (formattedText.isNotBlank()) {
                        // Show the word popup at the tap location
                        wordPopup?.show(
                                binding.contentScrollView,
                                formattedText,
                                lastTerm.tapX,
                                lastTerm.tapY
                        )
                    }
                }
            }
        }

        // Observe loading state
        viewModel.loadingState.observe(viewLifecycleOwner) { state ->
            when (state) {
                LoadingState.LOADING -> {
                    // Show loading indicator
                    Log.d("SentenceReadFragment", "Loading content...")
                    // Clear the content container to prevent any raw HTML from showing
                    binding.textContentContainer.removeAllViews()
                }
                LoadingState.LOADED -> {
                    // Hide loading indicator
                    Log.d("SentenceReadFragment", "Content loaded")
                }
                LoadingState.ERROR -> {
                    // Show error message
                    Log.e("SentenceReadFragment", "Error loading content")
                    // Clear the content container on error
                    binding.textContentContainer.removeAllViews()
                }
            }
        }

        // Observe error state
        viewModel.errorState.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e("SentenceReadFragment", "Error: $error")
                // TODO: Display error to user
            }
        }

        Log.d("SentenceReadFragment", "Content area setup complete")
    }

    /** Check if audio is available for the book by testing the audio endpoint */
    private fun checkAudioAvailability(bookId: String) {
        Log.d("SentenceReadFragment", "Checking audio availability for book: $bookId")

        // Launch coroutine to test audio endpoint
        lifecycleScope.launch {
            try {
                val hasAudioResult = viewModel.hasAudioForBook(bookId)

                if (hasAudioResult.isSuccess) {
                    val hasAudio = hasAudioResult.getOrNull() == true
                    Log.d(
                            "SentenceReadFragment",
                            "Audio availability check result for book $bookId: $hasAudio"
                    )

                    // Show or hide audio player based on actual endpoint test
                    if (hasAudio) {
                        // Only show the audio player container if the user wants to see it
                        showAudioPlayer = true // Default to showing if available
                        updateAudioPlayerVisibility()

                        // Initialize audio player with the audio file URL
                        val audioFileUrl = "${getServerUrl()}/useraudio/stream/$bookId"
                        audioPlayerManager?.initializePlayer(audioFileUrl, bookId)

                        // Show the audio toggle in the menu
                        binding.readerOptionsMenu.audioPlayerToggleContainer.visibility =
                                View.VISIBLE
                        // Set the initial state of the switch
                        binding.readerOptionsMenu.showAudioSwitch.isChecked = showAudioPlayer
                    } else {
                        binding.audioPlayerContainer.visibility = View.GONE
                        // Hide the audio toggle in the menu since no audio is available
                        binding.readerOptionsMenu.audioPlayerToggleContainer.visibility = View.GONE
                    }
                } else {
                    Log.e(
                            "SentenceReadFragment",
                            "Error checking audio availability: ${hasAudioResult.exceptionOrNull()?.message}"
                    )
                    // If we can't check, default to hiding the audio player for safety
                    binding.audioPlayerContainer.visibility = View.GONE
                    // Hide the audio toggle in the menu since we can't confirm audio exists
                    binding.readerOptionsMenu.audioPlayerToggleContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("SentenceReadFragment", "Exception in checkAudioAvailability", e)
                // On exception, hide the audio player
                binding.audioPlayerContainer.visibility = View.GONE
                // Hide the audio toggle in the menu since we can't confirm audio exists
                binding.readerOptionsMenu.audioPlayerToggleContainer.visibility = View.GONE
            }
        }
    }

    /** Get the current server URL from settings */
    private fun getServerUrl(): String {
        // Get the server URL from the ServerSettingsManager singleton
        return ServerSettingsManager.getInstance(requireContext()).getServerUrl()
    }

    /** Set up scroll listener for fullscreen mode */
    private fun loadBookContent() {
        Log.d("SentenceReadFragment", "Loading book content for book ID: $savedBookId")

        savedBookId?.let { bookId ->
            // Save the book ID
            saveLastBookId(bookId)

            // Clear the content container to prevent any raw HTML from showing
            binding.textContentContainer.removeAllViews()

            // Open the book to current page with initial probe
            Log.d(
                    "SentenceReadFragment",
                    "Calling viewModel.openBookToCurrentPageWithProbe($bookId)"
            )
            viewModel.openBookToCurrentPageWithProbe(bookId)
        }
                ?: run { Log.d("SentenceReadFragment", "savedBookId is null, not loading content") }
    }

    // DictionaryListener implementation for DictionaryDialogFragment
    override fun onDictionaryClosed() {
        Log.d("SentenceReadFragment", "Dictionary closed")
        // No need to do anything here since the dialog handles its own cleanup
    }

    override fun onDictionaryTextSelected(text: String) {
        Log.d("SentenceReadFragment", "Dictionary text selected: $text")
        // Update the translation text in the term form if it's visible
        currentTermFormFragment?.setTranslationText(text)
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onDictionaryLookup(term: String) {
        Log.d("SentenceReadFragment", "Dictionary lookup requested for term: $term")
        handleDictionaryLookup(term)
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onTermFormDestroyed() {
        Log.d(
                "SentenceReadFragment",
                "Term form destroyed, clearing currentTermFormFragment reference"
        )
        currentTermFormFragment = null
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onTermSaved(updatedTermData: TermFormData?) {
        Log.d(
                "SentenceReadFragment",
                "Term saved, refreshing all term highlights to update linked terms"
        )
        // Refresh all term highlights to ensure linked terms are properly updated
        // This will preserve scroll position using the logic in updateTermHighlights
        refreshAllTermHighlights()

        // Refresh all term highlights and update status terms after completion
        refreshAllTermHighlights {
            // Post to the UI thread to ensure NativeTextView is updated before
            // refreshing status terms
            binding.root.post {
                // Also refresh the status terms list to show the updated status
                if (showStatusTerms) {
                    loadAndDisplayStatusTerms()
                }
            }
        }
    }

    // Method to handle dictionary lookup from NativeTermFormFragment
    private fun handleDictionaryLookup(term: String) {
        Log.d("SentenceReadFragment", "Dictionary lookup requested for term: $term")

        // Get the language ID from the current selected term or saved book language
        val languageId = currentSelectedTerm?.languageId ?: savedBookLanguage?.toIntOrNull() ?: 1

        // Create and show the DictionaryDialogFragment
        val dictionaryDialogFragment = DictionaryDialogFragment.newInstance(term, languageId)
        dictionaryDialogFragment.setDictionaryListener(this)

        // Show the dictionary dialog on top of the term form
        dictionaryDialogFragment.show(childFragmentManager, "dictionary_dialog")
    }

    // NativeTextView.TermInteractionListener implementation
    override fun onTermTapped(termData: TermData) {
        Log.d("SentenceReadFragment", "Term tapped: ${termData.term}")
        termInteractionManager.onTermTapped(termData)
    }

    // NativeTextView.TermInteractionListener implementation for sentence long press
    override fun onSentenceLongPressed(
            sentence: String,
            languageId: Int,
            tapX: Float,
            tapY: Float
    ) {
        Log.d("SentenceReadFragment", "Sentence long pressed: $sentence")
        Log.d("SentenceReadFragment", "Language ID: $languageId, Tap coordinates: ($tapX, $tapY)")
        showSentenceTranslationDialog(sentence, languageId, tapX, tapY)
    }

    // TermInteractionManager.TermInteractionListener implementation
    override fun onTermSingleTap(term: TermData) {
        Log.d("SentenceReadFragment", "=== TERM SINGLE TAP DETECTED ===")
        Log.d("SentenceReadFragment", "Term data received:")
        Log.d("SentenceReadFragment", "  Term: '${term.term}'")
        Log.d("SentenceReadFragment", "  Term ID: ${term.termId}")
        Log.d("SentenceReadFragment", "  Language ID: ${term.languageId}")
        Log.d("SentenceReadFragment", "  Status: ${term.status}")
        Log.d("SentenceReadFragment", "  Parents: ${term.parentsList}")
        Log.d("SentenceReadFragment", "  Translation: '${term.translation}'")
        Log.d("SentenceReadFragment", "  Tap coordinates: (${term.tapX}, ${term.tapY})")

        // Validate the term data
        if (term.term.isBlank()) {
            Log.e("SentenceReadFragment", "ERROR: Term is blank")
            return
        }

        Log.d("SentenceReadFragment", "Valid term data, proceeding")

        // Store the last selected term
        lastSelectedTerm = term.term
        // Store the current text selection
        currentTextSelection = term.term
        // Store the current selected term data with tap coordinates
        currentSelectedTerm = term

        // Show the word popup by fetching data from the server
        showWordPopup(term)

        Log.d("SentenceReadFragment", "=== TERM SINGLE TAP HANDLING COMPLETE ===")
    }

    override fun onTermDoubleTap(term: TermData) {
        Log.d("SentenceReadFragment", "Double tap detected for term: ${term.term}")
        Log.d("SentenceReadFragment", "Term data for double tap:")
        Log.d("SentenceReadFragment", "  Term: '${term.term}'")
        Log.d("SentenceReadFragment", "  Term ID: ${term.termId}")
        Log.d("SentenceReadFragment", "  Language ID: ${term.languageId}")
        Log.d("SentenceReadFragment", "  Status: ${term.status}")
        Log.d("SentenceReadFragment", "  Parents: ${term.parentsList}")
        Log.d("SentenceReadFragment", "  Translation: '${term.translation}'")
        Log.d("SentenceReadFragment", "  Tap coordinates: (${term.tapX}, ${term.tapY})")

        // Set the flag to indicate we're showing a term form
        isShowingTermForm = true

        // Extract and store the sentence context from the current displayed content
        // In Sentence Reader, we're already showing sentence by sentence, so we can use the
        // entire content as the sentence context
        val sentenceContext = getCurrentSentenceText()

        // Create updated TermData with sentence context
        val updatedTerm =
                TermData(
                        termId = term.termId,
                        term = term.term,
                        languageId = term.languageId,
                        translation = term.translation,
                        status = term.status,
                        parentsList = term.parentsList,
                        parentTranslations = term.parentTranslations,
                        tapX = term.tapX,
                        tapY = term.tapY,
                        segmentId = term.segmentId,
                        sentenceContext = sentenceContext
                )

        // Store the current text selection
        currentTextSelection = term.term
        // Store the current selected term data with sentence context
        currentSelectedTerm = updatedTerm

        // Show NativeTermFormFragment by fetching the complete term data
        showTermForm(term)
    }

    private fun showSentenceTranslationDialog(
            sentence: String,
            languageId: Int,
            tapX: Float,
            tapY: Float
    ) {
        Log.d(
                "SentenceReadFragment",
                "showSentenceTranslationDialog called with sentence: $sentence"
        )

        // Create and show the SentenceTranslationDialogFragment
        val sentenceTranslationDialogFragment =
                SentenceTranslationDialogFragment.newInstance(sentence, languageId)
        sentenceTranslationDialogFragment.show(childFragmentManager, "sentence_translation_dialog")
    }

    /** Show the NativeTermFormFragment for a term with the fetched translation */
    private fun showTermFormWithTranslation(translation: String) {
        val term = currentSelectedTerm
        if (term != null) {
            Log.d(
                    "SentenceReadFragment",
                    "showTermFormWithTranslation called with term: ${term.term}, translation: '$translation'"
            )

            // Create TermFormData from TermData with the fetched translation
            val termFormData =
                    TermFormData(
                            termId = term.termId,
                            termText = term.term,
                            languageId = term.languageId,
                            context = "", // This would be determined from context in a full
                            // implementation
                            translation = translation,
                            status = term.status,
                            parents = term.parentsList,
                            tags = emptyList()
                    )

            // Use nativeread.TermFormData directly
            val readTermFormData = termFormData

            // Create and show the NativeTermFormFragment with required parameters
            val termFormFragment =
                    NativeTermFormFragment.newInstance(
                            termFormData = readTermFormData,
                            storedTermData = term,
                            onSave = { updatedTerm ->
                                Log.d(
                                        "SentenceReadFragment",
                                        "Term form saved: ${updatedTerm.termText}"
                                )
                                // Store the updated term data
                                if (updatedTerm.termId != null) {
                                    termDataMap[updatedTerm.termId] = updatedTerm
                                }

                                // Handle save - update the term in the UI
                                // The dictionary listener will handle the actual UI refresh with
                                // scroll preservation
                            },
                            onCancel = {
                                Log.d("SentenceReadFragment", "Term form cancelled")
                                // Handle cancel
                            },
                            dictionaryListener = this
                    )

            // Store a reference to the term form fragment
            currentTermFormFragment = termFormFragment

            termFormFragment.show(childFragmentManager, "term_form")
        }
    }

    /** Refresh all term highlights on the page to reflect status changes in linked terms */
    fun refreshAllTermHighlights(onComplete: (() -> Unit)? = null) {
        Log.d("SentenceReadFragment", "Refreshing all term highlights on the page")

        savedBookId?.let { bookId ->
            val pageInfo = navigationController.getCurrentPageInfo()

            // For non-scrollable content or when we need a more complete refresh,
            // we'll try a different approach - reload the page content completely
            // but preserve scroll position
            val scrollView = binding.contentScrollView
            val currentScrollY = scrollView.scrollY

            // Fetch updated term data and update only highlights
            viewModel.fetchUpdatedTermData(bookId, pageInfo.currentPage) { updatedContent ->
                if (updatedContent != null) {
                    // Update the sentence groups with the new content to ensure that
                    // when users navigate between sentences, they get the updated statuses
                    sentenceGroups = processContentIntoSentenceGroups(updatedContent)

                    // Update only the term highlights using the updated content
                    updateTermHighlights(updatedContent)

                    // After updating highlights, if the content doesn't fill the screen,
                    // which means no scrolling is possible, we may need additional refresh
                    val contentHeight = scrollView.getChildAt(0)?.height ?: 0
                    val scrollViewHeight = scrollView.height

                    // If content is shorter than scroll view (non-scrollable), force a complete
                    // visual refresh by making a small adjustment that forces redraw
                    if (contentHeight <= scrollViewHeight) {
                        Log.d(
                                "SentenceReadFragment",
                                "Content is non-scrollable, forcing complete refresh"
                        )

                        // Try to force a complete refresh of the NativeTextView
                        val textView = findNativeTextView(binding.textContentContainer)
                        textView?.let { tv ->
                            // Use a complete approach that should work for non-scrollable content
                            tv.post {
                                // Capture current text and reassign to force complete redraw
                                val currentText = tv.text
                                // Temporarily change something to force re-layout
                                val currentAlpha = tv.alpha
                                tv.alpha = currentAlpha - 0.01f // Small change
                                tv.alpha = currentAlpha // Restore

                                // Then invalidate to force redraw
                                tv.invalidate()
                            }
                        }
                    }
                } else {
                    // Fallback to full page refresh if we can't fetch updated term data
                    Log.d(
                            "SentenceReadFragment",
                            "Failed to fetch updated term data, falling back to full refresh"
                    )
                    viewModel.loadBookPage(bookId, pageInfo.currentPage)
                }

                // Restore the scroll position and call completion callback
                scrollView.post {
                    scrollView.scrollTo(0, currentScrollY)
                    // Now that all visual updates are complete, call the completion callback
                    onComplete?.invoke()
                }
            }
            Log.d(
                    "SentenceReadFragment",
                    "Term data fetch triggered for book ID: $bookId, page: ${pageInfo.currentPage}"
            )
        }
    }

    /** Update term highlights using updated term data */
    private fun updateTermHighlights(updatedContent: TextContent) {
        Log.d("SentenceReadFragment", "Updating term highlights with new data")

        // Collect all term ID and status pairs to update
        val termUpdates = mutableMapOf<Int, Int>() // termId to status

        // For each paragraph in the updated content
        for (updatedParagraph in updatedContent.paragraphs) {
            // For each segment in the paragraph
            for (updatedSegment in updatedParagraph.segments) {
                // If this is an interactive segment with a term ID, track the update
                if (updatedSegment.isInteractive && updatedSegment.termId > 0) {
                    Log.d(
                            "SentenceReadFragment",
                            "Planning highlight update for term ID: ${updatedSegment.termId} to status: ${updatedSegment.status}"
                    )
                    termUpdates[updatedSegment.termId] = updatedSegment.status
                }
            }
        }

        // Find the NativeTextView to update directly
        val textView = findNativeTextView(binding.textContentContainer)
        if (textView != null && termUpdates.isNotEmpty()) {
            // Apply all updates in one operation to avoid multiple text refreshes
            textRenderer.updateMultipleSegmentsWithTermIds(textView, termUpdates)

            // Force the TextView to refresh its display after span updates
            textView.post {
                textView.invalidate()
                textView.requestLayout()
            }
        }

        Log.d("SentenceReadFragment", "Term highlight updates applied")

        Log.d("SentenceReadFragment", "Finished updating term highlights")
    }

    /** Show word popup at the tap location */
    private fun showWordPopup(term: TermData) {
        // Request term information from the server
        Log.d("SentenceReadFragment", "Requesting term popup for term ID: ${term.termId}")

        // Get term popup information using the term ID
        viewModel.getTermPopup(term.termId)

        // The popup will be shown when the translation result is received
        // in the translationResult observer
    }

    /** Extract and format term information from HTML content */
    private fun extractAndFormatTermInfo(htmlContent: String): String {
        return try {
            if (htmlContent.isBlank()) {
                return ""
            }

            val doc = Jsoup.parse(htmlContent)

            // Extract term and parents text (first <b> inside first <p>)
            val termAndParentsElement = doc.select("p b").first()
            val termAndParentsText = termAndParentsElement?.text()?.trim() ?: "Unknown term"

            // Extract main translation (first <p> without class that's not the first one)
            var translationText = ""
            val pTags = doc.select("p")
            for (p in pTags) {
                // Skip the first <p> which contains the term name
                if (p == pTags.first()) continue

                // Skip <p> tags with specific classes
                val className = p.className()
                if (className == "small-flash-notice") continue

                // Skip <p> tags that contain only <i> (romanization)
                if (p.children().size == 1 && p.child(0).tagName() == "i") continue

                // If we get here, this <p> tag likely contains the translation
                val text = p.text().trim()
                if (text.isNotBlank()) {
                    translationText = text
                    break
                }
            }

            // Extract parent translation (from the first parent in the parents div)
            var parentTranslationText = ""
            var parentTermText = ""
            // Look for the parents div section
            val parentsDivs = doc.select("div")
            for (div in parentsDivs) {
                // Check if this div contains parents (look for "Parents" text or div structure)
                val parentEntries = div.select("p")
                if (parentEntries.isNotEmpty()) {
                    // Look for a parent entry that has a translation
                    for (parentEntry in parentEntries) {
                        val children = parentEntry.childNodes()

                        // Extract parent term (text before <br>)
                        val parentTermBuilder = StringBuilder()
                        for (node in children) {
                            if (node.nodeName() == "br") break
                            if (node.nodeName() == "#text") {
                                parentTermBuilder.append(node.toString().trim())
                            }
                        }
                        parentTermText = parentTermBuilder.toString().trim()

                        // Extract parent translation (text after <br>)
                        for (i in children.indices) {
                            val node = children[i]
                            // Look for <br> tag which separates parent term info from translation
                            if (node.nodeName() == "br") {
                                // Get text after the <br> tag
                                val textNodes = children.drop(i + 1)
                                val translationBuilder = StringBuilder()
                                for (textNode in textNodes) {
                                    if (textNode.nodeName() == "#text") {
                                        translationBuilder.append(textNode.toString().trim())
                                    }
                                }
                                val extractedText = translationBuilder.toString().trim()
                                if (extractedText.isNotBlank()) {
                                    parentTranslationText = extractedText
                                    break
                                }
                            }
                        }
                        if (parentTranslationText.isNotBlank()) break
                    }
                }
                if (parentTranslationText.isNotBlank()) break
            }

            // Format the display text according to the requirements:
            // 1. First line: term with parents, underlined and with a colon
            // 2. Second line: translation indented with 2 spaces
            // 3. Third line: parent translation with parent term in parentheses, indented with 2
            // spaces

            // Extract parent term from the termAndParentsText which is in format "term(parentterm)"
            var parentTermInFirstLine = ""
            val parenStart = termAndParentsText.indexOf('(')
            val parenEnd = termAndParentsText.indexOf(')')
            if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
                parentTermInFirstLine = termAndParentsText.substring(parenStart + 1, parenEnd)
            }

            val formattedText = buildString {
                // First line: term with parents (underlined and with colon)
                append("$termAndParentsText:")

                // Second line: translation (if available) with 2 spaces indentation
                if (translationText.isNotBlank()) {
                    append("\n")
                    append("  $translationText")
                }

                // Third line: parent translation with parent term in parentheses (if available)
                if (parentTranslationText.isNotBlank() && parentTermInFirstLine.isNotBlank()) {
                    append("\n")
                    append("($parentTermInFirstLine): $parentTranslationText")
                }
            }

            if (formattedText.isNotBlank()) formattedText else ""
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error extracting term info from HTML", e)
            ""
        }
    }

    override fun onPause() {
        super.onPause()
        // Dismiss the word popup if it's showing
        wordPopup?.dismiss()
        // Clear the last selected term
        lastSelectedTerm = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Dismiss the word popup if it's showing
        wordPopup?.dismiss()
        wordPopup = null

        // Stop and release TTS resources
        ttsManager?.stop()
        ttsManager = null

        _binding = null
        toolbarFragment = null
        pageIndicatorFragment = null
        themeManager = null
        navigationManager = null
    }

    // Method to set the fragment listener
    fun setFragmentListener(listener: NativeReadFragmentListener) {
        this.fragmentListener = listener
        // Note: navigationManager might not be used in SentenceReadFragment
    }

    // FAB menu action handlers
    fun onCreateAnkiCardsForSelection(text: String) {
        fragmentListener?.onCreateAnkiCardsForSelection(text)
    }

    fun onCreateAnkiCardForTerm(termId: String) {
        fragmentListener?.onCreateAnkiCardForTerm(termId)
    }

    fun onCreateAnkiCardForCurrentTerm() {
        val currentTerm = getCurrentSelectedTerm()
        if (currentTerm != null && currentTerm.termId != 0) {
            onCreateAnkiCardForTerm(currentTerm.termId.toString())
        } else {
            // Show a message that no term is selected
            Toast.makeText(requireContext(), "No term selected for Anki card", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    fun onTranslateSentence() {
        fragmentListener?.onTranslateSentence()
    }

    fun onTranslatePage() {
        fragmentListener?.onTranslatePage()
    }

    fun onShowTextFormatting() {
        fragmentListener?.onShowTextFormatting()
        // Show the popup menu with the 4 specific text formatting options
        val activity = requireActivity()
        if (activity is MainActivity) {
            activity.showTextFormattingPopupMenu(activity.findViewById(R.id.fab))
        }
    }

    /**
     * Update the line spacing of the text content
     *
     * Progress range: 0-100 Multiplier range: 0.5-2.0 (0.5 = very tight lines, 2.0 = very loose
     * lines) Default value: 50 (progress) = 1.25 (multiplier) = normal line spacing with some
     * breathing room 1.0 = single spacing, 1.5 = 1.5 line spacing, 2.0 = double spacing
     */
    fun onAddBookmark() {
        navigationManager?.onAddBookmark()
    }

    fun onListBookmarks() {
        navigationManager?.onListBookmarks()
    }

    fun onEditCurrentPage() {
        navigationManager?.onEditCurrentPage()
    }

    // Method to get the ID of the current book
    fun getBookId(): String? {
        return navigationManager?.getBookId()
    }

    // Method to get the language of the current book
    fun getBookLanguage(): String? {
        return navigationManager?.getBookLanguage()
    }

    // Method to get the current book language name from page metadata
    private fun getCurrentBookLanguageName(): String? {
        val pageMetadata = viewModel.currentPageContent.value?.pageMetadata
        var languageName = pageMetadata?.languageName

        // If we don't have a language name in the metadata, try to fetch it from the server
        if (languageName.isNullOrEmpty() &&
                        pageMetadata?.languageId != null &&
                        pageMetadata.languageId > 0
        ) {
            Log.d(
                    "SentenceReadFragment",
                    "Language name not found in metadata, attempting to fetch from server"
            )

            // Fetch the language name using the language ID
            viewModel.fetchLanguageName(pageMetadata.languageId) { result ->
                if (result.isSuccess) {
                    val fetchedLanguageName = result.getOrNull()
                    if (!fetchedLanguageName.isNullOrEmpty()) {
                        Log.d(
                                "SentenceReadFragment",
                                "Successfully fetched language name: $fetchedLanguageName"
                        )
                        // Update the savedBookLanguage variable
                        savedBookLanguage = fetchedLanguageName
                    } else {
                        Log.d("SentenceReadFragment", "Fetched language name was empty")
                    }
                } else {
                    Log.e(
                            "SentenceReadFragment",
                            "Failed to fetch language name: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }

        // Return the language name if we have it, or fall back to savedBookLanguage
        return if (!languageName.isNullOrEmpty()) {
            languageName
        } else {
            savedBookLanguage
        }
    }

    // Method to get the current text selection
    fun getCurrentTextSelection(): String? {
        return currentTextSelection
    }

    // Method to get the current selected term
    fun getCurrentSelectedTerm(): TermData? {
        return currentSelectedTerm
    }

    // Method to handle back navigation (similar to WebView-based fragments)
    fun goBackInWebView(callback: (Boolean) -> Unit) {
        navigationManager?.goBackInWebView(callback)
    }

    /** Show the reader options menu */
    private fun showReaderOptionsMenu() {
        Log.d("SentenceReadFragment", "showReaderOptionsMenu called")
        try {
            // Show the menu container
            binding.readerOptionsMenu.root.visibility = View.VISIBLE
            Log.d(
                    "SentenceReadFragment",
                    "Menu container visibility set to VISIBLE"
            ) // Set initial state of switches based on current preferences
            // Hide the entire menu items for fullscreen and all known for sentence reader
            // The parent of the switch is the LinearLayout container
            (binding.readerOptionsMenu.fullscreenSwitch.parent as? View)?.visibility = View.GONE
            (binding.readerOptionsMenu.showAllKnownSwitch.parent as? View)?.visibility = View.GONE

            // Show the show status terms toggle (this is relevant for both reader modes)
            binding.readerOptionsMenu.showStatusTermsSwitchContainer.visibility = View.VISIBLE

            // Set the audio switch state only if the audio toggle is visible
            if (binding.readerOptionsMenu.audioPlayerToggleContainer.visibility == View.VISIBLE) {
                binding.readerOptionsMenu.showAudioSwitch.isChecked = showAudioPlayer
            }
            binding.readerOptionsMenu.showStatusTermsSwitch.isChecked = showStatusTerms

            // Show book title in the header
            displayBookTitle()

            Log.d(
                    "SentenceReadFragment",
                    "Switch states set"
            ) // Set up text formatting option click listener
            binding.readerOptionsMenu.textFormattingOption.setOnClickListener {
                Log.d("SentenceReadFragment", "Text formatting option clicked")
                hideReaderOptionsMenu()
                Log.d(
                        "SentenceReadFragment",
                        "Calling textFormattingManager.showEnhancedTextFormattingDialogWithPersistence"
                )
                textFormattingManager?.showEnhancedTextFormattingDialogWithPersistence(
                        binding.textContentContainer,
                        true // isSentenceReader = true
                )
                Log.d("SentenceReadFragment", "Text formatting dialog method called")
            }

            // Update the text to indicate returning to native reader and set up click listener
            binding.readerOptionsMenu.sentenceReaderOption.apply {
                // Find the text view inside the LinearLayout and update its text
                for (i in 0 until this.childCount) {
                    val child = this.getChildAt(i)
                    if (child is android.widget.TextView) {
                        child.text = "Return to Native Reader"
                        break
                    }
                }

                setOnClickListener {
                    hideReaderOptionsMenu()
                    // Navigate back to NativeReadFragment
                    findNavController().navigate(R.id.action_nav_sentence_reader_to_nav_native_read)
                }
            }

            // Set up show audio switch listener (only if audio toggle is visible)
            if (binding.readerOptionsMenu.audioPlayerToggleContainer.visibility == View.VISIBLE) {
                binding.readerOptionsMenu.showAudioSwitch.setOnCheckedChangeListener { _, isChecked
                    ->
                    Log.d("SentenceReadFragment", "Show audio switch changed to: $isChecked")
                    showAudioPlayer = isChecked
                    updateAudioPlayerVisibility()
                    saveMenuPreferences()
                }
            }

            // Set up show status terms switch listener
            binding.readerOptionsMenu.showStatusTermsSwitch.setOnCheckedChangeListener {
                    _,
                    isChecked ->
                Log.d("SentenceReadFragment", "Show status terms switch changed to: $isChecked")
                showStatusTerms = isChecked
                toggleStatusTermsVisibility(isChecked)
                saveMenuPreferences()
            }

            // Show and set up the TTS button visibility toggle (only for sentence reader)
            binding.readerOptionsMenu.showTtsButtonSwitchContainer.visibility = View.VISIBLE
            binding.readerOptionsMenu.showTtsButtonSwitch.isChecked =
                    getTtsButtonVisibilityPreference()

            binding.readerOptionsMenu.showTtsButtonSwitch.setOnCheckedChangeListener { _, isChecked
                ->
                Log.d("SentenceReadFragment", "Show TTS button switch changed to: $isChecked")
                saveTtsButtonVisibilityPreference(isChecked)
                // Update the visibility of the TTS button in the UI
                updateTtsButtonVisibility(isChecked)
            }

            // Set up help option click listener
            binding.readerOptionsMenu.helpOption.setOnClickListener {
                hideReaderOptionsMenu()
                showHelpDialog()
            }
            Log.d("SentenceReadFragment", "showReaderOptionsMenu completed successfully")
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in showReaderOptionsMenu", e)
        }
    }

    /** Hide the reader options menu */
    private fun hideReaderOptionsMenu() {
        Log.d("SentenceReadFragment", "hideReaderOptionsMenu called")
        try {
            binding.readerOptionsMenu.root.visibility = View.GONE
            Log.d("SentenceReadFragment", "Menu container visibility set to GONE")
            Log.d("SentenceReadFragment", "hideReaderOptionsMenu completed successfully")
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in hideReaderOptionsMenu", e)
        }
    }

    /** Show help dialog with basic controls information */
    private fun showHelpDialog() {
        Log.d("SentenceReadFragment", "showHelpDialog called")

        // Inflate the help dialog layout
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_help, null)

        // Create the dialog
        val helpDialog =
                android.app.AlertDialog.Builder(requireContext()).setView(dialogView).create()

        // Set up the close button
        val closeButton = dialogView.findViewById<android.widget.Button>(R.id.btn_close_help)
        closeButton.setOnClickListener { helpDialog.dismiss() }

        // Show the dialog
        helpDialog.show()
    }

    /** Get TTS button visibility preference from SharedPreferences */
    private fun getTtsButtonVisibilityPreference(): Boolean {
        val sharedPref =
                requireContext()
                        .getSharedPreferences("sentence_reader_settings", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("show_tts_button", true) // Default to true
    }

    /** Save TTS button visibility preference to SharedPreferences */
    private fun saveTtsButtonVisibilityPreference(isVisible: Boolean) {
        val sharedPref =
                requireContext()
                        .getSharedPreferences("sentence_reader_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("show_tts_button", isVisible)
            apply()
        }
    }

    /** Update TTS button visibility in the UI based on preference */
    private fun updateTtsButtonVisibility(isVisible: Boolean) {
        try {
            val ttsButton = binding.statusTermsContainer.findViewById<ImageButton>(R.id.tts_button)
            ttsButton?.visibility = if (isVisible) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error updating TTS button visibility", e)
        }
    }

    /** Get the user's TTS language preference from SharedPreferences */
    private fun getTtsLanguagePreference(): String {
        val sharedPref = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPref.getString("tts_language", "Auto (Detect from Book)")
                ?: "Auto (Detect from Book)"
    }

    /** Map language display names to language codes */
    private fun mapLanguageDisplayNameToCode(displayName: String): String? {
        return when (displayName.trim()) {
            "English" -> "en"
            "Spanish" -> "es"
            "French" -> "fr"
            "German" -> "de"
            "Italian" -> "it"
            "Portuguese" -> "pt"
            "Russian" -> "ru"
            "Chinese (Simplified)" -> "zh"
            "Chinese (Traditional)" -> "zh-Hant"
            "Japanese" -> "ja"
            "Korean" -> "ko"
            "Arabic" -> "ar"
            "Hindi" -> "hi"
            "Bengali" -> "bn"
            "Punjabi" -> "pa"
            "Urdu" -> "ur"
            "Turkish" -> "tr"
            "Dutch" -> "nl"
            "Swedish" -> "sv"
            "Norwegian" -> "no"
            "Danish" -> "da"
            "Finnish" -> "fi"
            "Polish" -> "pl"
            "Czech" -> "cs"
            "Greek" -> "el"
            "Hebrew" -> "he"
            "Thai" -> "th"
            "Vietnamese" -> "vi"
            "Indonesian" -> "id"
            "Malay" -> "ms"
            else -> null
        }
    }

    /** Check if the reader options menu is currently visible */
    private fun isReaderOptionsMenuVisible(): Boolean {
        Log.d("SentenceReadFragment", "isReaderOptionsMenuVisible called")
        try {
            val isVisible = binding.readerOptionsMenu.root.visibility == View.VISIBLE
            Log.d("SentenceReadFragment", "Menu visibility: $isVisible")
            return isVisible
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in isReaderOptionsMenuVisible", e)
            return false
        }
    }

    /** Toggle audio player visibility based on preference */
    private fun updateAudioPlayerVisibility() {
        Log.d("SentenceReadFragment", "updateAudioPlayerVisibility called with: $showAudioPlayer")
        try {
            binding.audioPlayerContainer.visibility =
                    if (showAudioPlayer) View.VISIBLE else View.GONE
            Log.d("SentenceReadFragment", "Toggling audio player visibility to: $showAudioPlayer")
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in updateAudioPlayerVisibility", e)
        }
    }

    /** Fetch content for a specific page to be used by sentence reader */
    fun fetchPageContentForSentenceReader(
            bookId: String,
            pageNum: Int,
            callback: (Result<String>) -> Unit
    ) {
        Log.d(
                "SentenceReadFragment",
                "fetchPageContentForSentenceReader called with bookId: $bookId, pageNum: $pageNum"
        )

        lifecycleScope.launch {
            try {
                Log.d(
                        "SentenceReadFragment",
                        "Calling repository.fetchPageContent($bookId, $pageNum)"
                )
                val result = viewModel.repository.fetchPageContent(bookId, pageNum)

                if (result.isSuccess) {
                    val textContentAndHtml = result.getOrNull()!!
                    Log.d(
                            "SentenceReadFragment",
                            "Received HTML content for sentence reader, length: ${textContentAndHtml.htmlContent.length}"
                    )

                    // Return the HTML content for sentence processing
                    callback(Result.success(textContentAndHtml.htmlContent))
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(
                            "SentenceReadFragment",
                            "Failed to fetch page content for sentence reader",
                            error
                    )
                    callback(Result.failure(error ?: Exception("Unknown error")))
                }
            } catch (e: Exception) {
                Log.e(
                        "SentenceReadFragment",
                        "Exception while fetching page content for sentence reader",
                        e
                )
                callback(Result.failure(e))
            }
        }
    }

    /** Show the NativeTermFormFragment for a term */
    private fun showTermForm(term: TermData) {
        Log.d(
                "SentenceReadFragment",
                "showTermForm called with term: ${term.term}, translation: '${term.translation}'"
        )

        // Fetch complete term data from the server before showing the form
        // This ensures we have the latest translation and other term information
        viewModel.getTermEditPage(term.termId)

        // The term form will be shown when the term data is received
        // in the translationResult observer
    }

    /**
     * Process content into sentence groups for sentence-by-sentence reading Combines short
     * sentences (< 4 terms) with neighbors
     */
    /**
     * Process content into sentence groups for sentence-by-sentence reading Now includes logic to
     * split paragraphs at sentence boundaries when server doesn't do so
     */
    private fun processContentIntoSentenceGroups(content: TextContent): List<List<Paragraph>> {
        val sentenceGroups = mutableListOf<List<Paragraph>>()
        val paragraphs = content.paragraphs

        // Process each paragraph
        paragraphs.forEach { paragraph ->
            // Check if this paragraph is very large which might indicate multiple sentences
            val hasManySegments = paragraph.segments.size > 10
            val hasManyTerms = countTermsInParagraph(paragraph) > 10

            if (hasManySegments || hasManyTerms) {
                // Look for sentence boundaries within this large paragraph
                sentenceGroups.addAll(findSentenceGroupsInParagraph(paragraph))
            } else {
                // For smaller paragraphs, add as single groups
                if (countTermsInParagraph(paragraph) > 0 ||
                                paragraph.segments.joinToString("") { it.text }.isNotBlank()
                ) {
                    sentenceGroups.add(listOf(paragraph))
                }
            }
        }

        return sentenceGroups
    }

    /** Find sentence groups within a large paragraph by detecting sentence boundaries */
    private fun findSentenceGroupsInParagraph(paragraph: Paragraph): List<List<Paragraph>> {
        val sentenceGroups = mutableListOf<List<Paragraph>>()
        val sentenceEndings =
                setOf('.', '!', '?', '。', '！', '？', '।', '…') // Common sentence ending punctuation
        val allSegments = paragraph.segments

        // Find sentence boundaries by looking for ending punctuation in segments
        val boundaryIndices = mutableListOf<Int>()

        allSegments.forEachIndexed { index, segment ->
            val text = segment.text
            for (i in text.indices) {
                if (sentenceEndings.contains(text[i])) {
                    // Found a sentence ending, mark boundary after this segment
                    boundaryIndices.add(index + 1)
                    break // Move to the next segment after finding first sentence ending
                }
            }
        }

        if (boundaryIndices.isNotEmpty()) {
            // We found sentence boundaries, so split the paragraph
            var startIdx = 0

            boundaryIndices.distinct().sorted().forEach { boundaryIdx ->
                if (boundaryIdx > startIdx && boundaryIdx <= allSegments.size) {
                    val segmentGroup = allSegments.subList(startIdx, boundaryIdx)
                    // Only add this group if it has actual terms (not just punctuation/spaces)
                    if (segmentGroup.isNotEmpty() && countTermsInSegmentList(segmentGroup) > 0) {
                        sentenceGroups.add(
                                listOf(
                                        Paragraph(
                                                id = "${paragraph.id}_part_${sentenceGroups.size}",
                                                segments = segmentGroup
                                        )
                                )
                        )
                    }
                    startIdx = boundaryIdx
                }
            }

            // Add any remaining segments after the last boundary
            if (startIdx < allSegments.size) {
                val remainingSegments = allSegments.subList(startIdx, allSegments.size)
                // Only add if this section has actual terms
                if (remainingSegments.isNotEmpty() && countTermsInSegmentList(remainingSegments) > 0
                ) {
                    sentenceGroups.add(
                            listOf(
                                    Paragraph(
                                            id = "${paragraph.id}_part_${sentenceGroups.size}",
                                            segments = remainingSegments
                                    )
                            )
                    )
                }
            }
        } else {
            // No sentence boundaries found, return the paragraph as a single unit
            if (countTermsInParagraph(paragraph) > 0) {
                sentenceGroups.add(listOf(paragraph))
            }
        }

        return sentenceGroups
    }

    /** Count interactive terms (words that can be clicked) in a list of segments */
    private fun countTermsInSegmentList(
            segments: List<com.example.luteforandroidv2.ui.nativeread.Term.TextSegment>
    ): Int {
        return segments.count { it.isInteractive && !isPunctuationOrSpace(it.text) }
    }

    private fun countTermsInParagraph(paragraph: Paragraph): Int {
        return paragraph.segments.count { it.isInteractive && !isPunctuationOrSpace(it.text) }
    }

    /** Check if text is only punctuation or whitespace */
    private fun isPunctuationOrSpace(text: String): Boolean {
        return text.isNotEmpty() && text.all { !it.isLetterOrDigit() }
    }

    /** Render only the current sentence group */
    private fun renderCurrentSentence(content: TextContent) {
        if (sentenceGroups.isEmpty() || currentSentenceIndex >= sentenceGroups.size) {
            return
        }

        // Clear the container first
        binding.textContentContainer.removeAllViews()

        // Temporarily allow scrolling during content rendering to prevent rendering issues
        (binding.contentScrollView as? NonScrollingScrollView)?.setAutoScrollBlocked(false)

        // Create a new TextContent with only the current sentence group
        val currentSentenceContent =
                TextContent(
                        paragraphs = sentenceGroups[currentSentenceIndex],
                        pageMetadata = content.pageMetadata
                )

        // Render the current sentence group
        textRenderer.renderTextContent(binding.textContentContainer, currentSentenceContent, this)

        // Show sentence navigation controls
        binding.sentenceNavigationControls.visibility = View.VISIBLE
        updateSentenceCounter()
        // Apply saved text formatting settings
        applySavedTextFormatting()

        // If status terms should be visible, load them
        if (showStatusTerms) {
            toggleStatusTermsVisibility(true)
        }
    }

    /** Move to the next sentence group */
    fun goToNextSentence(content: TextContent) {
        if (currentSentenceIndex < sentenceGroups.size - 1) {
            currentSentenceIndex++
            renderCurrentSentence(content)
        }
    }

    /** Move to the previous sentence group */
    fun goToPreviousSentence(content: TextContent) {
        if (currentSentenceIndex > 0) {
            currentSentenceIndex--
            renderCurrentSentence(content)
        }
    }

    /** Move to the next sentence group - uses current ViewModel content */
    fun goToNextSentence() {
        // Stop TTS when moving to next sentence
        ttsManager?.stop()

        if (currentSentenceIndex < sentenceGroups.size - 1) {
            currentSentenceIndex++
            viewModel.currentPageContent.value?.let { content -> renderCurrentSentence(content) }
        } else {
            // On the last sentence, mark the page as done and navigate to next page
            navigationManager?.markPageAsDoneAndNavigateToNext()
        }
    }

    /** Move to the previous sentence group - uses current ViewModel content */
    fun goToPreviousSentence() {
        // Stop TTS when moving to previous sentence
        ttsManager?.stop()

        if (currentSentenceIndex > 0) {
            currentSentenceIndex--
            viewModel.currentPageContent.value?.let { content -> renderCurrentSentence(content) }
        }
    }

    /** Set up sentence navigation controls */
    private fun setupSentenceNavigationControls() {
        Log.d("SentenceReadFragment", "setupSentenceNavigationControls called")

        // Set up previous sentence button
        binding.prevSentenceButton.setOnClickListener {
            Log.d("SentenceReadFragment", "Previous sentence button clicked")
            goToPreviousSentence()
            updateSentenceCounter()
        }

        // Set up next sentence button
        binding.nextSentenceButton.setOnClickListener {
            Log.d("SentenceReadFragment", "Next sentence button clicked")
            goToNextSentence()
            updateSentenceCounter()
        }

        // Initially hide the controls
        binding.sentenceNavigationControls.visibility = View.GONE
        Log.d("SentenceReadFragment", "Sentence navigation controls hidden initially")
    }

    /** Update the sentence counter display */
    private fun updateSentenceCounter() {
        if (sentenceGroups.isNotEmpty()) {
            binding.sentenceCounterText.text = "${currentSentenceIndex + 1}/${sentenceGroups.size}"
        } else {
            binding.sentenceCounterText.text = "0/0"
        }
    }

    /** Method to navigate to the previous page (public wrapper for private method) */
    fun navigateToPreviousPage() {
        // Call the navigation manager method
        navigationManager?.navigateToPreviousPageInternal()
    }

    /**
     * Process content into sentence groups for sentence-by-sentence reading Combines short
     * sentences (< 4 terms) with neighbors
     */
    private fun processContentIntoSentenceGroupsFallback(
            content: TextContent
    ): List<List<Paragraph>> {
        Log.d("SentenceReadFragment", "processContentIntoSentenceGroupsFallback called")
        val sentenceGroups = mutableListOf<List<Paragraph>>()
        val paragraphs = content.paragraphs
        var i = 0

        try {
            while (i < paragraphs.size) {
                val currentGroup = mutableListOf<Paragraph>()
                var currentTermCount = 0

                // Keep adding paragraphs to the group until we reach 4+ terms or run out of
                // paragraphs
                while (i < paragraphs.size) {
                    val paragraph = paragraphs[i]
                    val paragraphTermCount = countTermsInParagraph(paragraph)

                    // Skip paragraphs with 0 terms
                    if (paragraphTermCount == 0) {
                        i++
                        continue
                    }

                    // Add paragraph to current group
                    currentGroup.add(paragraph)
                    currentTermCount += paragraphTermCount

                    // If we've reached 4+ terms, finalize this group
                    if (currentTermCount >= 4) {
                        sentenceGroups.add(currentGroup.toList())
                        i++
                        break
                    }

                    // Continue to next paragraph to try to reach 4+ terms
                    i++
                }

                // If we've built a group with some terms but less than 4 and we've reached the end
                // of paragraphs, we still add it as the final group
                if (currentGroup.isNotEmpty() && currentTermCount < 4 && i >= paragraphs.size) {
                    sentenceGroups.add(currentGroup.toList())
                }
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error in processContentIntoSentenceGroupsFallback", e)
        }

        return sentenceGroups
    }

    /** Force a fresh load of the current page content to get updated term data */
    private fun refreshPageContent() {
        Log.d("SentenceReadFragment", "refreshPageContent called")
        savedBookId?.let { bookId ->
            val pageInfo = navigationController.getCurrentPageInfo()
            Log.d(
                    "SentenceReadFragment",
                    "Refreshing page content for book ID: $bookId, page: ${pageInfo.currentPage}"
            )

            // Reload the current page to get updated term statuses
            viewModel.loadBookPage(bookId, pageInfo.currentPage)
        }
    }

    /** Get current sentence text from the displayed content */
    private fun getCurrentSentenceText(): String {
        try {
            // Get the NativeTextView from the content container
            val nativeTextView = findNativeTextView(binding.textContentContainer)

            if (nativeTextView != null) {
                // Get the text content from the NativeTextView
                return nativeTextView.text.toString().trim()
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error getting current sentence text", e)
        }

        return ""
    }

    /** Send sentence to AI endpoint for processing */
    private fun sendSentenceToAi(sentence: String) {
        // Get AI settings
        val aiSettingsManager = AiSettingsManager.getInstance(requireContext())

        // Check if AI is configured
        if (!aiSettingsManager.isAiConfigured()) {
            Toast.makeText(
                            requireContext(),
                            "AI endpoint not configured. Please check app settings.",
                            Toast.LENGTH_LONG
                    )
                    .show()
            return
        }

        val aiEndpoint = aiSettingsManager.aiEndpoint
        val aiModel = aiSettingsManager.aiModel

        // Get language name from the current page metadata
        var languageName = ""
        viewModel.currentPageContent.value?.pageMetadata?.languageId?.let { languageId ->
            languageName = getLanguageNameById(languageId)
        }

        // Replace {sentence} and {language} placeholders in the sentence prompt
        var aiPrompt = aiSettingsManager.aiPromptSentence.replace("{sentence}", sentence)
        aiPrompt = aiPrompt.replace("{language}", languageName)

        // Show loading indicator
        Log.d("SentenceReadFragment", "Sending sentence to AI endpoint: $aiEndpoint")

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

                        Log.d("SentenceReadFragment", "Sending request body: $requestBody")

                        // Write request body
                        val outputStream = connection.outputStream
                        outputStream.write(requestBody.toByteArray())
                        outputStream.flush()
                        outputStream.close()

                        val responseCode = connection.responseCode
                        Log.d("SentenceReadFragment", "AI response code: $responseCode")

                        if (responseCode == 200) {
                            val inputStream = connection.inputStream
                            val response = inputStream.bufferedReader().use { it.readText() }
                            inputStream.close()

                            Log.d("SentenceReadFragment", "AI response: $response")

                            // Parse response and update UI
                            activity?.runOnUiThread { handleAiResponse(response) }
                        } else {
                            // Handle error response
                            val errorStream = connection.errorStream
                            val errorResponse =
                                    errorStream?.bufferedReader()?.use { it.readText() }
                                            ?: "Unknown error"
                            errorStream?.close()

                            Log.e("SentenceReadFragment", "AI error response: $errorResponse")

                            activity?.runOnUiThread {
                                Toast.makeText(
                                                requireContext(),
                                                "AI request failed: $errorResponse",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }

                        connection.disconnect()
                    } catch (e: Exception) {
                        Log.e("SentenceReadFragment", "Error sending sentence to AI", e)
                        activity?.runOnUiThread {
                            Toast.makeText(
                                            requireContext(),
                                            "Error connecting to AI service: ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }
                .start()
    }

    /** Handle AI response and display in popup */
    private fun handleAiResponse(response: String) {
        try {
            // Parse OpenAI response format
            val aiTranslation = parseOpenAiResponse(response)

            // Show AI response in a popup dialog
            activity?.runOnUiThread { showAiResponseDialog(aiTranslation) }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error parsing AI response", e)
            Toast.makeText(
                            requireContext(),
                            "Error processing AI response: ${e.message}",
                            Toast.LENGTH_LONG
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
            Log.e("SentenceReadFragment", "Error parsing OpenAI response", e)
        }

        // Fallback to original response if parsing fails
        return response
    }

    /** Display the book title in the menu header */
    private fun displayBookTitle() {
        // Get the book ID from current page content
        val bookId = savedBookId
        if (bookId.isNullOrEmpty()) {
            // If no book ID, hide the title header or show a default message
            binding.readerOptionsMenu.bookTitleHeader.text = "No Book Loaded"
            return
        }

        // Launch coroutine to fetch book information
        lifecycleScope.launch {
            try {
                // Use the new method to get only the book title (more reliable)
                val bookTitleResult = viewModel.getBookTitle(bookId)
                if (bookTitleResult.isSuccess) {
                    val bookTitle = bookTitleResult.getOrNull()
                    if (!bookTitle.isNullOrEmpty()) {
                        // Set the book title in the header
                        binding.readerOptionsMenu.bookTitleHeader.text = bookTitle
                    } else {
                        // If book title not found, display the book ID
                        binding.readerOptionsMenu.bookTitleHeader.text = "Book $bookId"
                    }
                } else {
                    // If there was an error fetching the book title, show the book ID
                    binding.readerOptionsMenu.bookTitleHeader.text = "Book $bookId"
                    Log.e(
                            "SentenceReadFragment",
                            "Error fetching book title: ${bookTitleResult.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("SentenceReadFragment", "Exception in displayBookTitle", e)
                binding.readerOptionsMenu.bookTitleHeader.text = "Book $bookId"
            }
        }
    }

    /** Show AI response in a dialog */
    private fun showAiResponseDialog(response: String) {
        try {
            // Create a custom dialog that behaves like a popup using the same layout as word popup
            val dialog = android.app.Dialog(requireContext())

            // Inflate the popup layout
            val inflater = LayoutInflater.from(requireContext())
            val view = inflater.inflate(R.layout.popup_translation, null)

            // Find the text view in the layout and set the response
            val textView = view.findViewById<TextView>(R.id.popup_text)
            textView.text = response
            textView.setTextIsSelectable(true) // Allow text selection/copy

            dialog.setContentView(view)

            // Make dialog dismiss when touching outside
            dialog.setCanceledOnTouchOutside(true)

            // Show the dialog
            dialog.show()
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error showing AI response dialog", e)
            Toast.makeText(
                            requireContext(),
                            "Error showing AI response: ${e.message}",
                            Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    // Get language name by ID by fetching from the server
    // Since there's no direct languages API endpoint in Lute, we'll get language name by
    // fetching the language edit page and parsing the language name from it
    private fun getLanguageNameById(languageId: Int): String {
        var languageName = "Unknown Language"

        // We need to use ServerSettingsManager to get the server URL
        val serverSettingsManager =
                com.example.luteforandroidv2.ui.settings.ServerSettingsManager.getInstance(
                        requireContext()
                )
        if (!serverSettingsManager.isServerUrlConfigured()) {
            Log.e("SentenceReadFragment", "Server URL not configured")
            return languageName
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        // Use the language edit endpoint to get language info (this returns HTML)
        val languageUrl = "$serverUrl/language/edit/$languageId"

        try {
            val url = java.net.URL(languageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "text/html")

            val responseCode = connection.responseCode
            Log.d("SentenceReadFragment", "Language info response code: $responseCode")

            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                // Parse the HTML response to extract the language name
                languageName = parseLanguageNameFromHtml(content)
                Log.d(
                        "SentenceReadFragment",
                        "Parsed language name: $languageName for ID: $languageId"
                )
            } else {
                Log.e(
                        "SentenceReadFragment",
                        "Language info fetch failed with response code: $responseCode"
                )
            }
        } catch (e: Exception) {
            Log.e("SentenceReadFragment", "Error fetching language info", e)
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
            Log.e("SentenceReadFragment", "Error parsing language from HTML", e)
        }
        return "Unknown Language"
    }
}
