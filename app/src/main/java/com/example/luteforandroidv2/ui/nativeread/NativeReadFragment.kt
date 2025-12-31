package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.luteforandroidv2.MainActivity
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.FragmentNativeReadBinding
import com.example.luteforandroidv2.ui.nativeread.Audio.AudioPlayerFragment
import com.example.luteforandroidv2.ui.nativeread.Audio.AudioPlayerManager
import com.example.luteforandroidv2.ui.nativeread.Bookmark.BookStateManager
import com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionaryDialogFragment
import com.example.luteforandroidv2.ui.nativeread.Dictionary.SentenceTranslationDialogFragment
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTermFormFragment
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTextView
import com.example.luteforandroidv2.ui.nativeread.Term.TermData
import com.example.luteforandroidv2.ui.nativeread.Term.TermDataExtractor
import com.example.luteforandroidv2.ui.nativeread.Term.TermFormData
import com.example.luteforandroidv2.ui.nativeread.Term.TermInteractionManager
import com.example.luteforandroidv2.ui.nativeread.Term.TextContent
import com.example.luteforandroidv2.ui.nativeread.Term.TextRenderer
import com.example.luteforandroidv2.ui.nativeread.Term.WordPopup
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

class NativeReadFragment :
        Fragment(),
        NativeTermFormFragment.DictionaryListener,
        DictionaryDialogFragment.DictionaryListener,
        NativeTextView.TermInteractionListener,
        TermInteractionManager.TermInteractionListener {
    private var _binding: FragmentNativeReadBinding? = null
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

    // Track reader menu preferences
    private var showAllKnownButton = false
    private var isFullscreenMode = false
    private var showAudioPlayer = false
    private var showTermHighlights = true // Default to true (on)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        Log.d("NativeReadFragment", "onCreateView called")
        _binding = FragmentNativeReadBinding.inflate(inflater, container, false)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[NativeReadViewModel::class.java]

        // Initialize word popup
        wordPopup = WordPopup(requireContext())

        // Initialize theme manager
        themeManager = ThemeManager(requireContext())

        // Initialize text formatting manager
        textFormattingManager = TextFormattingManager(requireContext())

        // Initialize menu preferences
        initializePreferences()

        // Initialize text formatting settings
        initializeTextFormattingSettings()

        Log.d("NativeReadFragment", "onCreateView completed")
        return binding.root
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
        isFullscreenMode = sharedPref.getBoolean("fullscreen_mode", false)
        showAllKnownButton =
                sharedPref.getBoolean("show_all_known_button", false) // Added this line
        showAudioPlayer = sharedPref.getBoolean("show_audio_player", true)
        showTermHighlights = sharedPref.getBoolean("show_term_highlights", true) // Default to true

        editor.apply()

        // Apply fullscreen mode if it was enabled
        if (isFullscreenMode) {
            toggleFullscreenMode()
        }
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
            putBoolean("fullscreen_mode", isFullscreenMode)
            putBoolean("show_all_known_button", showAllKnownButton)
            putBoolean("show_audio_player", showAudioPlayer)
            putBoolean("show_term_highlights", showTermHighlights)
            apply()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("NativeReadFragment", "onViewCreated called")

        // Initialize menu preferences
        initializePreferences()
        Log.d("NativeReadFragment", "Menu preferences initialized")

        // Initialize text formatting settings
        initializeTextFormattingSettings()
        Log.d("NativeReadFragment", "Text formatting settings initialized")

        // Initialize book state manager
        bookStateManager = BookStateManager(requireContext())
        Log.d("NativeReadFragment", "Book state manager initialized")

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
        Log.d("NativeReadFragment", "Navigation manager initialized")

        // Set up term interaction manager listener
        termInteractionManager.setTermInteractionListener(this)
        Log.d("NativeReadFragment", "Term interaction manager listener set")

        // Load the last book ID if available
        loadLastBookId()
        Log.d("NativeReadFragment", "Last book ID loaded: '$savedBookId'")
        Log.d("NativeReadFragment", "Last book language loaded: '$savedBookLanguage'")

        // Clear the content container to prevent any raw HTML from showing
        binding.textContentContainer.removeAllViews()

        // Check if we have a book ID from arguments or saved state
        val bookId = args.bookId
        Log.d("NativeReadFragment", "Book ID from args: '$bookId'")
        Log.d("NativeReadFragment", "Saved book language: '$savedBookLanguage'")

        if (bookId.isNotEmpty()) {
            savedBookId = bookId
            Log.d("NativeReadFragment", "Set savedBookId from args: '$savedBookId'")
        }

        Log.d("NativeReadFragment", "Final savedBookId: '$savedBookId'")
        Log.d("NativeReadFragment", "Final savedBookLanguage: '$savedBookLanguage'")

        // Update the NavigationManager with the final book ID and language
        navigationManager?.updateBookInfo(savedBookId, savedBookLanguage)
        Log.d("NativeReadFragment", "Navigation manager updated with final book info")

        // If we don't have a book ID, navigate to the books view
        if (savedBookId.isNullOrEmpty()) {
            Log.d("NativeReadFragment", "No book ID available, navigating to books view")
            try {
                findNavController().navigate(R.id.nav_books)
                return
            } catch (e: Exception) {
                Log.e("NativeReadFragment", "Error navigating to books view", e)
            }
        }

        // Setup the UI components
        Log.d("NativeReadFragment", "Setting up UI components")
        setupUI()
        Log.d("NativeReadFragment", "UI components setup completed")

        // Load book content
        Log.d("NativeReadFragment", "Loading book content")
        loadBookContent()

        Log.d("NativeReadFragment", "onViewCreated completed")
    }

    private fun loadLastBookId() {
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        savedBookId = sharedPref.getString("last_book_id", null)
        savedBookLanguage = sharedPref.getString("last_book_language", null)
        Log.d("NativeReadFragment", "Loaded savedBookId: '$savedBookId'")
        Log.d("NativeReadFragment", "Loaded savedBookLanguage: '$savedBookLanguage'")

        // Validate the loaded language ID
        if (!savedBookLanguage.isNullOrEmpty()) {
            try {
                val langId = savedBookLanguage?.toIntOrNull()
                if (langId == null || langId <= 0) {
                    Log.d("NativeReadFragment", "Invalid saved language ID, resetting to default")
                    savedBookLanguage = "1"
                }
            } catch (e: Exception) {
                Log.e(
                        "NativeReadFragment",
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
        Log.d("NativeReadFragment", "Saving book ID: '$bookId' and language: '$savedBookLanguage'")
        with(sharedPref.edit()) {
            putString("last_book_id", bookId)
            if (savedBookLanguage != null) {
                putString("last_book_language", savedBookLanguage)
            }
            apply()
        }
    }

    private fun setupUI() {
        Log.d("NativeReadFragment", "setupUI called")
        // Setup toolbar
        setupToolbar()
        Log.d("NativeReadFragment", "Toolbar setup completed")

        // Setup page indicator
        setupPageIndicator()
        Log.d("NativeReadFragment", "Page indicator setup completed")

        // Setup main content area
        setupContentArea()
        Log.d("NativeReadFragment", "Content area setup completed")

        // Apply theme to all UI components
        // This needs to be posted to ensure all child fragments are fully created
        binding.root.post { toggleAllKnownButtonVisibility() }
        binding.root.post { themeManager?.applyNativeReaderTheme(binding.root) }
        Log.d("NativeReadFragment", "setupUI completed")
    }

    /** Apply saved text formatting settings to the content */
    private fun applySavedTextFormatting() {
        Log.d("NativeReadFragment", "applySavedTextFormatting called")
        Log.d(
                "NativeReadFragment",
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

        // In fullscreen mode, recheck footer visibility when content changes
        if (isFullscreenMode) {
            binding.root.post {
                handleFullscreenFooterVisibility(binding.contentScrollView.scrollY)
            }
        }
        Log.d("NativeReadFragment", "applySavedTextFormatting completed")
    }

    private fun setupToolbar() {
        toolbarFragment = ToolbarFragment()
        childFragmentManager
                .beginTransaction()
                .replace(R.id.toolbar_container, toolbarFragment!!)
                .commit()
        Log.d("NativeReadFragment", "Toolbar fragment added")

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
        Log.d("NativeReadFragment", "Audio player fragment added (hidden)")
    }

    private fun setupPageIndicator() {
        pageIndicatorFragment = PageIndicatorFragment()
        pageIndicatorFragment?.setPageNavigationListener(
                object : PageIndicatorFragment.PageNavigationListener {
                    override fun onAllKnown() {
                        navigationManager?.markPageAsAllKnown()
                    }

                    override fun onPreviousPage() {
                        navigateToPreviousPage()
                    }

                    override fun onLuteMenu() {
                        Log.d("NativeReadFragment", "onLuteMenu called")
                        try {
                            if (isReaderOptionsMenuVisible()) {
                                Log.d("NativeReadFragment", "Menu is visible, hiding it")
                                hideReaderOptionsMenu()
                            } else {
                                Log.d("NativeReadFragment", "Menu is not visible, showing it")
                                showReaderOptionsMenu()
                            }
                        } catch (e: Exception) {
                            Log.e("NativeReadFragment", "Error in onLuteMenu", e)
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
        Log.d("NativeReadFragment", "Page indicator fragment added")
    }

    private fun setupContentArea() {
        // Observe the current page content from the ViewModel
        viewModel.currentPageContent.observe(viewLifecycleOwner) { content ->
            Log.d("NativeReadFragment", "=== RECEIVED PAGE CONTENT ===")
            Log.d("NativeReadFragment", "Content paragraphs count: ${content.paragraphs.size}")
            Log.d("NativeReadFragment", "Page metadata:")
            Log.d("NativeReadFragment", "  Book ID: ${content.pageMetadata.bookId}")
            Log.d("NativeReadFragment", "  Page num: ${content.pageMetadata.pageNum}")
            Log.d("NativeReadFragment", "  Page count: ${content.pageMetadata.pageCount}")
            Log.d("NativeReadFragment", "  Has audio: ${content.pageMetadata.hasAudio}")
            Log.d("NativeReadFragment", "  Is RTL: ${content.pageMetadata.isRTL}")
            Log.d("NativeReadFragment", "  Language ID: ${content.pageMetadata.languageId}")
            Log.d("NativeReadFragment", "  Language name: ${content.pageMetadata.languageName}")

            // Log first few paragraphs for debugging
            content.paragraphs.take(3).forEachIndexed { index, paragraph ->
                Log.d(
                        "NativeReadFragment",
                        "Paragraph $index: ${paragraph.id} with ${paragraph.segments.size} segments"
                )
                paragraph.segments.take(3).forEachIndexed { segIndex, segment ->
                    Log.d(
                            "NativeReadFragment",
                            "  Segment $segIndex: '${segment.text}' (status: ${segment.status}, interactive: ${segment.isInteractive})"
                    )
                }
                if (paragraph.segments.size > 3) {
                    Log.d(
                            "NativeReadFragment",
                            "  ... and ${paragraph.segments.size - 3} more segments"
                    )
                }
            }
            if (content.paragraphs.size > 3) {
                Log.d(
                        "NativeReadFragment",
                        "... and ${content.paragraphs.size - 3} more paragraphs"
                )
            }

            // Clear the container first to ensure no raw HTML is displayed
            binding.textContentContainer.removeAllViews()

            // Temporarily allow scrolling during content rendering to prevent rendering issues
            (binding.contentScrollView as? NonScrollingScrollView)?.setAutoScrollBlocked(false)

            // Render the parsed content
            Log.d(
                    "NativeReadFragment",
                    "Rendering content with language ID: ${content.pageMetadata.languageId}"
            )
            // Get custom text color from theme manager and set it on text renderer
            val customTextColor = themeManager?.getCurrentTextColor()
            textRenderer.setCustomTextColor(customTextColor)
            textRenderer.renderTextContent(binding.textContentContainer, content, this)

            // Monitor scroll position to understand the jumping behavior
            binding.contentScrollView.post {
                val initialScrollY = binding.contentScrollView.scrollY
                Log.d("NativeReadFragment", "Initial scroll position after render: $initialScrollY")

                // Now that content is rendered, block auto-scrolling to prevent unwanted scrolling
                (binding.contentScrollView as? NonScrollingScrollView)?.setAutoScrollBlocked(true)

                // Add scroll listener to track changes
                binding.contentScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY
                    ->
                    if (Math.abs(scrollY - oldScrollY) > 5) { // Only log significant changes
                        Log.d(
                                "NativeReadFragment",
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
                    "NativeReadFragment",
                    "Setting total page count to ${content.pageMetadata.pageCount}"
            )
            navigationController.setTotalPageCount(content.pageMetadata.pageCount)
            Log.d("NativeReadFragment", "Setting current page to ${content.pageMetadata.pageNum}")
            navigationController.setCurrentPage(content.pageMetadata.pageNum)

            // Save the book language if we have it
            if (content.pageMetadata.languageId > 0) {
                savedBookLanguage = content.pageMetadata.languageId.toString()
                Log.d(
                        "NativeReadFragment",
                        "Saved book language ID: ${content.pageMetadata.languageId}"
                )
                // Save to SharedPreferences
                saveLastBookId(savedBookId ?: "")
            } else {
                // If we don't have a valid language ID from page metadata,
                // try to use the saved one or default to 1
                if (savedBookLanguage.isNullOrEmpty()) {
                    savedBookLanguage = "1"
                    Log.d(
                            "NativeReadFragment",
                            "No valid language ID in page metadata, using default: 1"
                    )
                } else {
                    Log.d("NativeReadFragment", "Using saved book language ID: $savedBookLanguage")
                }
            }

            // Log the page metadata for debugging
            Log.d(
                    "NativeReadFragment",
                    "Page metadata - languageId: ${content.pageMetadata.languageId}, languageName: '${content.pageMetadata.languageName}'"
            )

            // Update page indicator
            pageIndicatorFragment?.updatePageCounter(
                    content.pageMetadata.pageNum,
                    content.pageMetadata.pageCount
            )

            // In fullscreen mode, recheck footer visibility when new content loads
            if (isFullscreenMode) {
                binding.root.post {
                    handleFullscreenFooterVisibility(binding.contentScrollView.scrollY)
                }
            }

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
                                                    "NativeReadFragment",
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
                                            Log.d("NativeReadFragment", "Term form cancelled")
                                            // Handle cancel
                                        },
                                        dictionaryListener = this
                                )
                        termFormFragment.show(childFragmentManager, "term_form")
                    }
                } catch (e: Exception) {
                    Log.e("NativeReadFragment", "Error parsing term edit page content", e)
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
                    Log.d("NativeReadFragment", "Showing word popup with server data")
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
                    Log.d("NativeReadFragment", "Loading content...")
                    // Clear the content container to prevent any raw HTML from showing
                    binding.textContentContainer.removeAllViews()
                }
                LoadingState.LOADED -> {
                    // Hide loading indicator
                    Log.d("NativeReadFragment", "Content loaded")
                }
                LoadingState.ERROR -> {
                    // Show error message
                    Log.e("NativeReadFragment", "Error loading content")
                    // Clear the content container on error
                    binding.textContentContainer.removeAllViews()
                }
            }
        }

        // Observe error state
        viewModel.errorState.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e("NativeReadFragment", "Error: $error")
                // TODO: Display error to user
            }
        }

        Log.d("NativeReadFragment", "Content area setup complete")
    }

    /** Check if audio is available for the book by testing the audio endpoint */
    private fun checkAudioAvailability(bookId: String) {
        Log.d("NativeReadFragment", "Checking audio availability for book: $bookId")

        // Launch coroutine to test audio endpoint
        lifecycleScope.launch {
            try {
                val hasAudioResult = viewModel.hasAudioForBook(bookId)

                if (hasAudioResult.isSuccess) {
                    val hasAudio = hasAudioResult.getOrNull() == true
                    Log.d(
                            "NativeReadFragment",
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
                            "NativeReadFragment",
                            "Error checking audio availability: ${hasAudioResult.exceptionOrNull()?.message}"
                    )
                    // If we can't check, default to hiding the audio player for safety
                    binding.audioPlayerContainer.visibility = View.GONE
                    // Hide the audio toggle in the menu since we can't confirm audio exists
                    binding.readerOptionsMenu.audioPlayerToggleContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("NativeReadFragment", "Exception in checkAudioAvailability", e)
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

    /** Handle fullscreen footer visibility based on scroll position and content */
    private fun handleFullscreenFooterVisibility(scrollY: Int) {
        try {
            val scrollView = binding.contentScrollView
            val scrollViewHeight = scrollView.height
            val scrollViewContentHeight = scrollView.getChildAt(0)?.height ?: 0

            Log.d(
                    "NativeReadFragment",
                    "handleFullscreenFooterVisibility - scrollY: $scrollY, scrollViewHeight: $scrollViewHeight, scrollViewContentHeight: $scrollViewContentHeight"
            )

            // Calculate actual scrollable area
            val maxScroll = maxOf(0, scrollViewContentHeight - scrollViewHeight)
            Log.d("NativeReadFragment", "maxScroll calculated as: $maxScroll")

            // Determine desired visibility
            // Check if content is shorter than screen (non-scrollable) OR if user is near bottom of
            // scrollable content
            // Use a more adaptive threshold to account for potential differences between calculated
            // maxScroll and actual max scroll position in NonScrollingScrollView
            val adaptiveThreshold =
                    maxOf(
                            50,
                            (maxScroll * 0.20).toInt()
                    ) // 20% of max scroll or 50px, whichever is larger
            val shouldShowFooter =
                    scrollViewContentHeight <= scrollViewHeight ||
                            scrollY >= (maxScroll - adaptiveThreshold)
            val desiredVisibility = if (shouldShowFooter) View.VISIBLE else View.GONE

            Log.d(
                    "NativeReadFragment",
                    "shouldShowFooter: $shouldShowFooter, desiredVisibility: $desiredVisibility"
            )

            // Update visibility if it has changed OR if this is the first check
            // (lastFooterVisibility is null)
            if (lastFooterVisibility != desiredVisibility) {
                binding.pageIndicatorContainer.visibility = desiredVisibility
                lastFooterVisibility = desiredVisibility
                Log.d(
                        "NativeReadFragment",
                        "Footer visibility changed to: $desiredVisibility (scrollY: $scrollY, maxScroll: $maxScroll, contentHeight: $scrollViewContentHeight, scrollViewHeight: $scrollViewHeight)"
                )
            } else {
                // Even if visibility hasn't changed, log to help debug
                Log.d(
                        "NativeReadFragment",
                        "Footer visibility unchanged as: $desiredVisibility (scrollY: $scrollY, maxScroll: $maxScroll)"
                )
            }
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error handling fullscreen footer visibility", e)
            // Fallback: always show footer in case of error
            binding.pageIndicatorContainer.visibility = View.VISIBLE
            lastFooterVisibility = View.VISIBLE
        }
    }

    private fun loadBookContent() {
        Log.d("NativeReadFragment", "Loading book content for book ID: $savedBookId")

        savedBookId?.let { bookId ->
            // Save the book ID
            saveLastBookId(bookId)

            // Clear the content container to prevent any raw HTML from showing
            binding.textContentContainer.removeAllViews()

            // Open the book to current page with initial probe
            Log.d("NativeReadFragment", "Calling viewModel.openBookToCurrentPageWithProbe($bookId)")
            viewModel.openBookToCurrentPageWithProbe(bookId)
        }
                ?: run { Log.d("NativeReadFragment", "savedBookId is null, not loading content") }
    }

    // DictionaryListener implementation for DictionaryDialogFragment
    override fun onDictionaryClosed() {
        Log.d("NativeReadFragment", "Dictionary closed")
        // No need to do anything here since the dialog handles its own cleanup
    }

    override fun onDictionaryTextSelected(text: String) {
        Log.d("NativeReadFragment", "Dictionary text selected: $text")
        // Update the translation text in the term form if it's visible
        currentTermFormFragment?.setTranslationText(text)
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onDictionaryLookup(term: String) {
        Log.d("NativeReadFragment", "Dictionary lookup requested for term: $term")
        handleDictionaryLookup(term)
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onTermFormDestroyed() {
        Log.d(
                "NativeReadFragment",
                "Term form destroyed, clearing currentTermFormFragment reference"
        )
        currentTermFormFragment = null
    }

    // DictionaryListener implementation for NativeTermFormFragment
    override fun onTermSaved(updatedTermData: TermFormData?) {
        Log.d(
                "NativeReadFragment",
                "Term saved, refreshing all term highlights to update linked terms"
        )
        // Refresh all term highlights to ensure linked terms are properly updated
        // This will preserve scroll position using the logic in updateTermHighlights
        refreshAllTermHighlights()
    }

    // Method to handle dictionary lookup from NativeTermFormFragment
    private fun handleDictionaryLookup(term: String) {
        Log.d("NativeReadFragment", "Dictionary lookup requested for term: $term")

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
        Log.d("NativeReadFragment", "Term tapped: ${termData.term}")
        termInteractionManager.onTermTapped(termData)
    }

    // NativeTextView.TermInteractionListener implementation for sentence long press
    override fun onSentenceLongPressed(
            sentence: String,
            languageId: Int,
            tapX: Float,
            tapY: Float
    ) {
        Log.d("NativeReadFragment", "Sentence long pressed: $sentence")
        Log.d("NativeReadFragment", "Language ID: $languageId, Tap coordinates: ($tapX, $tapY)")
        showSentenceTranslationDialog(sentence, languageId, tapX, tapY)
    }

    // TermInteractionManager.TermInteractionListener implementation
    override fun onTermSingleTap(term: TermData) {
        Log.d("NativeReadFragment", "=== TERM SINGLE TAP DETECTED ===")
        Log.d("NativeReadFragment", "Term data received:")
        Log.d("NativeReadFragment", "  Term: '${term.term}'")
        Log.d("NativeReadFragment", "  Term ID: ${term.termId}")
        Log.d("NativeReadFragment", "  Language ID: ${term.languageId}")
        Log.d("NativeReadFragment", "  Status: ${term.status}")
        Log.d("NativeReadFragment", "  Parents: ${term.parentsList}")
        Log.d("NativeReadFragment", "  Translation: '${term.translation}'")
        Log.d("NativeReadFragment", "  Tap coordinates: (${term.tapX}, ${term.tapY})")

        // Validate the term data
        if (term.term.isBlank()) {
            Log.e("NativeReadFragment", "ERROR: Term is blank")
            return
        }

        Log.d("NativeReadFragment", "Valid term data, proceeding")

        // Store the last selected term
        lastSelectedTerm = term.term
        // Store the current text selection
        currentTextSelection = term.term
        // Store the current selected term data with tap coordinates
        currentSelectedTerm = term

        // Show the word popup by fetching data from the server
        showWordPopup(term)

        Log.d("NativeReadFragment", "=== TERM SINGLE TAP HANDLING COMPLETE ===")
    }

    override fun onTermDoubleTap(term: TermData) {
        Log.d("NativeReadFragment", "Double tap detected for term: ${term.term}")
        Log.d("NativeReadFragment", "Term data for double tap:")
        Log.d("NativeReadFragment", "  Term: '${term.term}'")
        Log.d("NativeReadFragment", "  Term ID: ${term.termId}")
        Log.d("NativeReadFragment", "  Language ID: ${term.languageId}")
        Log.d("NativeReadFragment", "  Status: ${term.status}")
        Log.d("NativeReadFragment", "  Parents: ${term.parentsList}")
        Log.d("NativeReadFragment", "  Translation: '${term.translation}'")
        Log.d("NativeReadFragment", "  Tap coordinates: (${term.tapX}, ${term.tapY})")

        // Set the flag to indicate we're showing a term form
        isShowingTermForm = true

        // Extract and store the sentence context using the NativeTextView's current tap information
        val textView = findNativeTextView(binding.textContentContainer)
        val sentenceContext =
                if (textView != null) {
                    // Use the NativeTextView's current tap information which was set when the term
                    // was identified
                    textView.extractCurrentSentence()
                } else {
                    ""
                }

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
        Log.d("NativeReadFragment", "showSentenceTranslationDialog called with sentence: $sentence")

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
                    "NativeReadFragment",
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
                                        "NativeReadFragment",
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
                                Log.d("NativeReadFragment", "Term form cancelled")
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
    fun refreshAllTermHighlights() {
        Log.d("NativeReadFragment", "Refreshing all term highlights on the page")

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
                                "NativeReadFragment",
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
                            "NativeReadFragment",
                            "Failed to fetch updated term data, falling back to full refresh"
                    )
                    viewModel.loadBookPage(bookId, pageInfo.currentPage)
                }

                // Restore the scroll position
                scrollView.post { scrollView.scrollTo(0, currentScrollY) }
            }
            Log.d(
                    "NativeReadFragment",
                    "Term data fetch triggered for book ID: $bookId, page: ${pageInfo.currentPage}"
            )
        }
    }

    /** Update term highlights using updated term data */
    private fun updateTermHighlights(updatedContent: TextContent) {
        Log.d("NativeReadFragment", "Updating term highlights with new data")

        // Collect all term ID and status pairs to update
        val termUpdates = mutableMapOf<Int, Int>() // termId to status

        // For each paragraph in the updated content
        for (updatedParagraph in updatedContent.paragraphs) {
            // For each segment in the paragraph
            for (updatedSegment in updatedParagraph.segments) {
                // If this is an interactive segment with a term ID, track the update
                if (updatedSegment.isInteractive && updatedSegment.termId > 0) {
                    Log.d(
                            "NativeReadFragment",
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

        Log.d("NativeReadFragment", "Term highlight updates applied")

        Log.d("NativeReadFragment", "Finished updating term highlights")
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

    /** Show word popup at the tap location */
    private fun showWordPopup(term: TermData) {
        // Request term information from the server
        Log.d("NativeReadFragment", "Requesting term popup for term ID: ${term.termId}")

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
            Log.e("NativeReadFragment", "Error extracting term info from HTML", e)
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

        _binding = null
        toolbarFragment = null
        pageIndicatorFragment = null
        themeManager = null
        navigationManager = null
    }

    // Method to set the fragment listener
    fun setFragmentListener(listener: NativeReadFragmentListener) {
        this.fragmentListener = listener
        navigationManager?.fragmentListener = listener
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
        Log.d("NativeReadFragment", "showReaderOptionsMenu called")
        try {
            // Show the menu container
            binding.readerOptionsMenu.root.visibility = View.VISIBLE
            Log.d(
                    "NativeReadFragment",
                    "Menu container visibility set to VISIBLE"
            ) // Set initial state of switches based on current preferences
            // Set the audio switch state only if the audio toggle is visible
            if (binding.readerOptionsMenu.audioPlayerToggleContainer.visibility == View.VISIBLE) {
                binding.readerOptionsMenu.showAudioSwitch.isChecked = showAudioPlayer
            }
            binding.readerOptionsMenu.fullscreenSwitch.isChecked = isFullscreenMode
            binding.readerOptionsMenu.showAllKnownSwitch.isChecked = showAllKnownButton

            // Hide the status terms toggle since it's not relevant for native reader
            binding.readerOptionsMenu.showStatusTermsSwitchContainer.visibility = View.GONE

            // Show book title in the header
            displayBookTitle()

            Log.d(
                    "NativeReadFragment",
                    "Switch states set"
            ) // Set up text formatting option click listener
            binding.readerOptionsMenu.textFormattingOption.setOnClickListener {
                Log.d("NativeReadFragment", "Text formatting option clicked")
                hideReaderOptionsMenu()
                Log.d(
                        "NativeReadFragment",
                        "Calling textFormattingManager.showEnhancedTextFormattingDialogWithPersistence"
                )
                textFormattingManager?.showEnhancedTextFormattingDialogWithPersistence(
                        binding.textContentContainer,
                        false // isSentenceReader = false
                )
                Log.d("NativeReadFragment", "Text formatting dialog method called")
            }

            // Set up sentence reader option click listener
            binding.readerOptionsMenu.sentenceReaderOption.setOnClickListener {
                hideReaderOptionsMenu()
                // Navigate to SentenceReadFragment
                findNavController()
                        .navigate(
                                R.id.action_nav_native_read_to_nav_sentence_reader,
                                args.toBundle()
                        )
            }

            // Set up help option click listener
            binding.readerOptionsMenu.helpOption.setOnClickListener {
                hideReaderOptionsMenu()
                showHelpDialog()
            }

            // Set up fullscreen switch listener
            binding.readerOptionsMenu.fullscreenSwitch.setOnCheckedChangeListener { _, isChecked ->
                Log.d("NativeReadFragment", "Fullscreen switch changed to: $isChecked")
                isFullscreenMode = isChecked
                toggleFullscreenMode()
                saveMenuPreferences()
            }

            binding.readerOptionsMenu.showAllKnownSwitch.setOnCheckedChangeListener { _, isChecked
                ->
                Log.d("NativeReadFragment", "All known switch changed to: $isChecked")
                showAllKnownButton = isChecked
                toggleAllKnownButtonVisibility()
                saveMenuPreferences()
            }

            // Set up term highlights switch listener
            binding.readerOptionsMenu.termHighlightsSwitch.isChecked = showTermHighlights
            binding.readerOptionsMenu.termHighlightsSwitch.setOnCheckedChangeListener { _, isChecked
                ->
                Log.d("NativeReadFragment", "Term highlights switch changed to: $isChecked")
                showTermHighlights = isChecked
                // Update term highlights visibility across all content
                updateTermHighlightsVisibility()
                saveMenuPreferences()
            }

            Log.d("NativeReadFragment", "showReaderOptionsMenu completed successfully")
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in showReaderOptionsMenu", e)
        }
    }

    /** Update term highlights visibility */
    private fun updateTermHighlightsVisibility() {
        Log.d(
                "NativeReadFragment",
                "updateTermHighlightsVisibility called with showTermHighlights: $showTermHighlights"
        )

        // Find the NativeTextView within the content container
        val textView = findNativeTextView(binding.textContentContainer)
        if (textView != null) {
            // Get the current text as SpannableString
            val spannableText = textView.text as? android.text.Spannable
            if (spannableText != null) {
                // Get all existing WordBackgroundSpans
                val existingSpans =
                        spannableText.getSpans(
                                0,
                                spannableText.length,
                                com.example.luteforandroidv2.ui.nativeread.Term
                                                .WordBackgroundSpan::class
                                        .java
                        )

                if (showTermHighlights) {
                    // If showing highlights, we want to restore them based on the segment info
                    // Reapply the status-based styling
                    val segmentInfoList = textView.getSegmentInfo()
                    segmentInfoList.forEach { info ->
                        val segment = info.segment
                        if (segment.isInteractive &&
                                        !isPunctuationOnly(segment.text) &&
                                        segment.status > 0
                        ) {
                            // Reapply background color for terms with status > 0
                            val backgroundColor = getStatusColor(segment.status)
                            if (backgroundColor != android.graphics.Color.TRANSPARENT) {
                                // Only add the span if it doesn't already exist at this position
                                val existingSpansArray =
                                        spannableText.getSpans(
                                                info.start,
                                                info.end,
                                                com.example.luteforandroidv2.ui.nativeread.Term
                                                                .WordBackgroundSpan::class
                                                        .java
                                        )
                                val existingSpan =
                                        if (existingSpansArray.isNotEmpty()) existingSpansArray[0]
                                        else null
                                if (existingSpan == null) {
                                    spannableText.setSpan(
                                            com.example.luteforandroidv2.ui.nativeread.Term
                                                    .WordBackgroundSpan(backgroundColor),
                                            info.start,
                                            info.end,
                                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // If hiding highlights, remove all WordBackgroundSpans but preserve the blue
                    // text color for status 0 terms
                    for (span in existingSpans) {
                        spannableText.removeSpan(span)
                    }

                    // For status 0 terms, we still keep the blue text color since it indicates
                    // interactiveness
                    val segmentInfoList = textView.getSegmentInfo()
                    segmentInfoList.forEach { info ->
                        val segment = info.segment
                        if (segment.isInteractive &&
                                        segment.status == 0 &&
                                        !isPunctuationOnly(segment.text)
                        ) {
                            // Only reapply the blue text color for status 0 interactive terms
                            val statusZeroTextColor =
                                    android.graphics.Color.parseColor("#8095FF") // Solid blue
                            // Remove any existing color spans first
                            val existingColorSpans =
                                    spannableText.getSpans(
                                            info.start,
                                            info.end,
                                            android.text.style.ForegroundColorSpan::class.java
                                    ) as
                                            Array<android.text.style.ForegroundColorSpan>
                            for (colorSpan in existingColorSpans) {
                                spannableText.removeSpan(colorSpan)
                            }

                            spannableText.setSpan(
                                    android.text.style.ForegroundColorSpan(statusZeroTextColor),
                                    info.start,
                                    info.end,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                }

                // Update the text view to reflect changes
                textView.setText(spannableText)

                // Force refresh of the view
                textView.post {
                    textView.invalidate() // Force redraw
                    textView.requestLayout() // Request layout update
                    textView.refreshDrawableState() // Refresh drawable states
                }
            }
        }
    }

    /** Helper function to determine if text is punctuation only */
    private fun isPunctuationOnly(text: String): Boolean {
        return text.isNotEmpty() && text.all { !it.isLetterOrDigit() && !it.isWhitespace() }
    }

    /** Get status color based on term status */
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

    /** Hide the reader options menu */
    private fun hideReaderOptionsMenu() {
        Log.d("NativeReadFragment", "hideReaderOptionsMenu called")
        try {
            binding.readerOptionsMenu.root.visibility = View.GONE
            Log.d("NativeReadFragment", "Menu container visibility set to GONE")
            Log.d("NativeReadFragment", "hideReaderOptionsMenu completed successfully")
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in hideReaderOptionsMenu", e)
        }
    }

    /** Toggle all known button visibility based on preference */
    private fun toggleAllKnownButtonVisibility() {
        Log.d(
                "NativeReadFragment",
                "toggleAllKnownButtonVisibility called with: $showAllKnownButton"
        )
        try {
            pageIndicatorFragment?.toggleAllKnownButtonVisibility(showAllKnownButton)
            Log.d(
                    "NativeReadFragment",
                    "Toggling all known button visibility to: $showAllKnownButton"
            )
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in toggleAllKnownButtonVisibility", e)
        }
    }

    /** Check if the reader options menu is currently visible */
    private fun isReaderOptionsMenuVisible(): Boolean {
        Log.d("NativeReadFragment", "isReaderOptionsMenuVisible called")
        try {
            val isVisible = binding.readerOptionsMenu.root.visibility == View.VISIBLE
            Log.d("NativeReadFragment", "Menu visibility: $isVisible")
            return isVisible
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in isReaderOptionsMenuVisible", e)
            return false
        }
    }
    /** Toggle fullscreen mode */
    private fun toggleFullscreenMode() {
        Log.d("NativeReadFragment", "toggleFullscreenMode called with: $isFullscreenMode")
        try {
            val activity = activity
            if (activity is MainActivity) {
                if (isFullscreenMode) {
                    // Enter fullscreen mode - hide system UI and app UI
                    enterImmersiveMode(activity)
                    Log.d("NativeReadFragment", "Entered fullscreen mode")
                } else {
                    // Exit fullscreen mode - show system UI and app UI
                    exitImmersiveMode(activity)
                    Log.d("NativeReadFragment", "Exited fullscreen mode")
                }

                // Handle footer visibility in fullscreen mode
                if (isFullscreenMode) {
                    // In fullscreen mode, rely on scroll listener to control visibility
                    // Don't set visibility directly here as it's handled in enterImmersiveMode
                    // Trigger initial check to show footer if at bottom
                    binding.root.post {
                        handleFullscreenFooterVisibility(binding.contentScrollView.scrollY)
                    }
                } else {
                    // In normal mode, always show footer
                    binding.pageIndicatorContainer.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in toggleFullscreenMode", e)
        }
    }

    /** Enter immersive fullscreen mode */
    private fun enterImmersiveMode(activity: MainActivity) {
        val window = activity.window
        val decorView = window.decorView
        decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN)

        // Hide the toolbar container and page indicator
        binding.toolbarContainer.visibility = View.GONE
        // In fullscreen mode, we'll control page indicator visibility based on scroll position
        // Initially hide it and let scroll listener show it when needed
        binding.pageIndicatorContainer.visibility = View.GONE

        // Hide the main app bar in MainActivity
        activity.supportActionBar?.hide()

        // Start polling for footer visibility updates
        setupFullscreenFooterPolling()

        // Close the reader options menu
        hideReaderOptionsMenu()

        // Adjust window insets for fullscreen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        // Trigger initial check for footer visibility
        binding.root.post { handleFullscreenFooterVisibility(binding.contentScrollView.scrollY) }
    }

    /** Exit immersive fullscreen mode */
    private fun exitImmersiveMode(activity: MainActivity) {
        val window = activity.window
        // Reset window insets
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }

        val decorView = window.decorView
        decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        // Show the toolbar container and page indicator
        binding.toolbarContainer.visibility = View.VISIBLE
        binding.pageIndicatorContainer.visibility = View.VISIBLE

        // Show the main app bar in MainActivity
        activity.supportActionBar?.show()

        // Stop polling for footer visibility updates
        stopFullscreenFooterPolling()
    }

    /** Toggle audio player visibility based on preference */
    private fun updateAudioPlayerVisibility() {
        Log.d("NativeReadFragment", "updateAudioPlayerVisibility called with: $showAudioPlayer")
        try {
            binding.audioPlayerContainer.visibility =
                    if (showAudioPlayer) View.VISIBLE else View.GONE
            Log.d("NativeReadFragment", "Toggling audio player visibility to: $showAudioPlayer")
        } catch (e: Exception) {
            Log.e("NativeReadFragment", "Error in updateAudioPlayerVisibility", e)
        }
    }

    /** Toggle all known button visibility based on preference */

    /** Show help dialog with basic controls information */
    private fun showHelpDialog() {
        Log.d("NativeReadFragment", "showHelpDialog called")

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

    /** Fetch content for a specific page to be used by sentence reader */
    fun fetchPageContentForSentenceReader(
            bookId: String,
            pageNum: Int,
            callback: (Result<String>) -> Unit
    ) {
        Log.d(
                "NativeReadFragment",
                "fetchPageContentForSentenceReader called with bookId: $bookId, pageNum: $pageNum"
        )

        lifecycleScope.launch {
            try {
                Log.d(
                        "NativeReadFragment",
                        "Calling repository.fetchPageContent($bookId, $pageNum)"
                )
                val result = viewModel.repository.fetchPageContent(bookId, pageNum)

                if (result.isSuccess) {
                    val textContentAndHtml = result.getOrNull()!!
                    Log.d(
                            "NativeReadFragment",
                            "Received HTML content for sentence reader, length: ${textContentAndHtml.htmlContent.length}"
                    )

                    // Return the HTML content for sentence processing
                    callback(Result.success(textContentAndHtml.htmlContent))
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(
                            "NativeReadFragment",
                            "Failed to fetch page content for sentence reader",
                            error
                    )
                    callback(Result.failure(error ?: Exception("Unknown error")))
                }
            } catch (e: Exception) {
                Log.e(
                        "NativeReadFragment",
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
                "NativeReadFragment",
                "showTermForm called with term: ${term.term}, translation: '${term.translation}'"
        )

        // Fetch complete term data from the server before showing the form
        // This ensures we have the latest translation and other term information
        viewModel.getTermEditPage(term.termId)

        // The term form will be shown when the term data is received
        // in the translationResult observer
    }

    // Display the book title in the menu header
    private fun displayBookTitle() {
        // Get the book ID from current page content
        val bookId = navigationManager?.getBookId()
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
                            "NativeReadFragment",
                            "Error fetching book title: ${bookTitleResult.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("NativeReadFragment", "Exception in displayBookTitle", e)
                binding.readerOptionsMenu.bookTitleHeader.text = "Book $bookId"
            }
        }
    }

    // Method to navigate to the previous page (public wrapper for private method)
    fun navigateToPreviousPage() {
        // Call the navigation manager method
        navigationManager?.navigateToPreviousPageInternal()
    }

    /** Set up a simple polling mechanism to check footer visibility in fullscreen mode */
    private fun setupFullscreenFooterPolling() {
        // Remove any existing callbacks to prevent duplicates
        binding.root.removeCallbacks(footerVisibilityChecker)

        // Start the polling mechanism
        binding.root.post(footerVisibilityChecker)
    }

    /** Runnable that checks footer visibility periodically in fullscreen mode */
    private val footerVisibilityChecker =
            object : Runnable {
                override fun run() {
                    if (isFullscreenMode && isAdded) {
                        // Get current scroll position
                        val scrollY = binding.contentScrollView.scrollY
                        handleFullscreenFooterVisibility(scrollY)

                        // Schedule next check
                        binding.root.postDelayed(this, 250) // Check every 250ms
                    }
                }
            }

    /** Stop the fullscreen footer polling mechanism */
    private fun stopFullscreenFooterPolling() {
        binding.root.removeCallbacks(footerVisibilityChecker)
    }
}
