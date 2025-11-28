package com.example.luteforandroidv2.ui.read

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.FragmentReadBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class ReadFragment : Fragment() {

    private var _binding: FragmentReadBinding? = null
    private val binding
        get() = _binding!!
    private var webView: WebView? = null
    private var startX = 0f
    private var startY = 0f
    private val args: ReadFragmentArgs by navArgs()
    private var savedBookId: String? = null
    private var savedBookLanguage: String? = null

    // SharedPreferences for persistent book data
    private lateinit var readerSettingsPrefs: SharedPreferences

    // Term data storage

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadBinding.inflate(inflater, container, false)
        // Initialize SharedPreferences
        readerSettingsPrefs =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load the last book ID if available
        loadLastBookId()

        // Check if we have a book ID from arguments or saved state
        val bookId = args.bookId
        if (bookId.isNotEmpty()) {
            savedBookId = bookId
        }

        // If we don't have a book ID, navigate to the books view
        if (savedBookId.isNullOrEmpty()) {
            try {
                findNavController().navigate(R.id.nav_books)
                return
            } catch (e: Exception) {
                android.util.Log.e("ReadFragment", "Error navigating to books view", e)
            }
        }

        setupWebView()
    }

    private fun loadLastBookId() {
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        savedBookId = sharedPref.getString("last_book_id", null)
        savedBookLanguage = sharedPref.getString("last_book_language", null)
    }

    private fun saveLastBookId(bookId: String) {
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("last_book_id", bookId)
            if (savedBookLanguage != null) {
                putString("last_book_language", savedBookLanguage)
            }
            apply()
        }
    }

    private fun setupWebView() {
        webView = binding.webview

        // Add JavaScript interface for communication between WebView and Android
        // This needs to be done BEFORE loading any content
        webView?.addJavascriptInterface(PageTurnInterface(), "Android")
        // Removed TermDataInterface since ReadFragment no longer implements TermDataListener
        webView?.addJavascriptInterface(AndroidInterface(), "AndroidInterface")
        android.util.Log.d(
                "ReadFragment",
                "JavaScript interfaces added with names 'Android', 'TermData', and 'AndroidInterface'"
        )

        // Keep WebView transparent during loading to prevent FOUC (Opacity Control approach)
        webView?.alpha = 0f

        webView?.webViewClient =
                object : WebViewClient() {
                    override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        // Add Android app identification class as early as possible
                        WebViewClassHelper.ensureAndroidAppClassAdded(view)
                        android.util.Log.d(
                                "ReadFragment",
                                "onPageStarted called, Android app class added"
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Add Android app identification class again to ensure it's applied
                        WebViewClassHelper.addAndroidAppClass(view)

                        // Fade in WebView now that everything is loaded (Opacity Control approach)
                        view?.animate()?.alpha(1f)?.setDuration(200)?.start()

                        // Verify that the Android interface is available
                        view?.evaluateJavascript(
                                "(typeof Android !== 'undefined')",
                                { result ->
                                    android.util.Log.d(
                                            "ReadFragment",
                                            "Android interface available: $result"
                                    )
                                }
                        )

                        // Verify that the TermData interface is available
                        view?.evaluateJavascript(
                                "(typeof TermData !== 'undefined')",
                                { result ->
                                    android.util.Log.d(
                                            "ReadFragment",
                                            "TermData interface available: $result"
                                    )
                                }
                        )

                        // Inject debugging JavaScript to log when goto_relative_page is called
                        view?.evaluateJavascript(
                                """
                    (function() {
                        // Save the original goto_relative_page function
                        var originalGotoRelativePage = window.goto_relative_page;

                        // Override goto_relative_page to add logging
                        if (originalGotoRelativePage) {
                            window.goto_relative_page = function(p, initial_page_load) {
                                console.log("goto_relative_page called with p=" + p);
                                // Call the original function
                                var result = originalGotoRelativePage.call(this, p, initial_page_load);

                                // Try to call Android.onPageTurn if available
                                try {
                                    if (typeof Android !== 'undefined' && Android.onPageTurn) {
                                        console.log("Calling Android.onPageTurn()");
                                        Android.onPageTurn();
                                    } else {
                                        console.log("Android interface not available or onPageTurn not defined");
                                    }
                                } catch (e) {
                                    console.log("Error calling Android.onPageTurn(): " + e.message);
                                }

                                return result;
                            };
                        }
                    })();
                """.trimIndent(),
                                null
                        )

                        // Fix for audio player visibility - ensure it's only shown when appropriate
                        view?.evaluateJavascript(
                                """
                    (function() {
                        // Fix for audio player visibility - ensure it's only shown when appropriate
                        function fixAudioPlayerVisibility() {
                            console.log("Fixing audio player visibility in ReadFragment");
                            try {
                                // Check if we have an audio file
                                var haveAudioFile = (typeof have_audio_file === 'function') ? have_audio_file() : ($('#book_audio_file').val() != '');
                                console.log("Have audio file:", haveAudioFile);

                                var audioPlayerContainer = $('div.audio-player-container');
                                if (audioPlayerContainer.length > 0) {
                                    if (haveAudioFile) {
                                        console.log("Showing audio player");
                                        audioPlayerContainer.show();
                                        // Add a class to indicate we have audio (for CSS rules)
                                        $('body').addClass('has-audio');
                                    } else {
                                        console.log("Hiding audio player");
                                        audioPlayerContainer.hide();
                                        // Remove the class if we don't have audio
                                        $('body').removeClass('has-audio');
                                    }
                                }
                            } catch (error) {
                                console.error("Error fixing audio player visibility:", error);
                            }
                        }

                        // Call the fix function immediately
                        fixAudioPlayerVisibility();

                        // Also call it after a short delay to handle any timing issues
                        setTimeout(fixAudioPlayerVisibility, 100);
                        setTimeout(fixAudioPlayerVisibility, 500);
                        setTimeout(fixAudioPlayerVisibility, 1000);
                    })();
                """.trimIndent(),
                                null
                        )

                        android.util.Log.d(
                                "ReadFragment",
                                "onPageFinished called, Android app class added"
                        )

                        // Update word count now that the page is loaded
                        (activity as? com.example.luteforandroidv2.MainActivity)?.updateWordCount()

                        // Add a 100px padding div to the bottom of the page
                        view?.evaluateJavascript(
                                "javascript:(function() { " +
                                        "var padder = document.getElementById('gemini-padder');" +
                                        "if (padder === null) {" +
                                        "    padder = document.createElement('div');" +
                                        "    padder.id = 'gemini-padder';" +
                                        "    padder.style.height = '100px';" +
                                        "    document.body.appendChild(padder);" +
                                        "}" +
                                        "})();",
                                null
                        )

                        // Restore the title and progress bar visibility state
                        restoreTitleProgressBarState()
                    }
                }

        // Add WebChromeClient for better JavaScript dialog handling and console logging
        webView?.webChromeClient =
                object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        // Log JavaScript console messages for debugging
                        consoleMessage?.let {
                            android.util.Log.d(
                                    "ReadFragment",
                                    "JS Console: ${it.message()} at ${it.sourceId()}:${it.lineNumber()}"
                            )
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                    ): Boolean {
                        // Handle JavaScript alert dialogs
                        activity?.runOnUiThread {
                            try {
                                AlertDialog.Builder(requireContext())
                                        .setTitle("Alert")
                                        .setMessage(message)
                                        .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                                            result?.confirm()
                                        }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "ReadFragment",
                                        "Error showing JS alert dialog",
                                        e
                                )
                                result?.cancel()
                            }
                        }
                        return true
                    }

                    override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?
                    ): Boolean {
                        // Handle JavaScript confirm dialogs
                        activity?.runOnUiThread {
                            try {
                                AlertDialog.Builder(requireContext())
                                        .setTitle("Confirm")
                                        .setMessage(message)
                                        .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                                            result?.confirm()
                                        }
                                        .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                                            result?.cancel()
                                        }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "ReadFragment",
                                        "Error showing JS confirm dialog",
                                        e
                                )
                                result?.cancel()
                            }
                        }
                        return true
                    }

                    override fun onJsPrompt(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult?
                    ): Boolean {
                        // Handle JavaScript prompt dialogs
                        activity?.runOnUiThread {
                            try {
                                val editText = android.widget.EditText(requireContext())
                                editText.setText(defaultValue)

                                AlertDialog.Builder(requireContext())
                                        .setTitle("Prompt")
                                        .setMessage(message)
                                        .setView(editText)
                                        .setPositiveButton("OK") { _: DialogInterface, _: Int ->
                                            result?.confirm(editText.text.toString())
                                        }
                                        .setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                                            result?.cancel()
                                        }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                            } catch (e: Exception) {
                                android.util.Log.e(
                                        "ReadFragment",
                                        "Error showing JS prompt dialog",
                                        e
                                )
                                result?.cancel()
                            }
                        }
                        return true
                    }
                }

        // Disable horizontal scrolling at the WebView level and hide scrollbars
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.useWideViewPort = false
        webView?.settings?.loadWithOverviewMode = true

        // Try to hide scrollbars in the WebView
        webView?.scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
        webView?.isHorizontalScrollBarEnabled = false
        webView?.isVerticalScrollBarEnabled = false

        // Add touch listener to prevent horizontal scrolling
        webView?.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = motionEvent.x
                    startY = motionEvent.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(motionEvent.x - startX)
                    val deltaY = Math.abs(motionEvent.y - startY)

                    // If horizontal movement is greater than vertical, consume the touch event
                    // to prevent horizontal scrolling
                    if (deltaX > deltaY) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        val bookIdToUse =
                if (args.bookId.isNotEmpty()) {
                    args.bookId.also { saveLastBookId(it) }
                } else {
                    savedBookId
                }

        if (serverSettingsManager.isServerUrlConfigured() &&
                        bookIdToUse != null &&
                        bookIdToUse.isNotEmpty()
        ) {
            val serverUrl = serverSettingsManager.getServerUrl()
            webView?.loadUrl("$serverUrl/read/$bookIdToUse")
        } else {
            // Show an error message in the WebView
            webView?.loadData(
                    "<html><body><h2>Server not configured or no book selected</h2><p>Please configure your server URL in App Settings and select a book.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>",
                    "text/html",
                    "UTF-8"
            )
        }

        // Restore the title and progress bar visibility state
        restoreTitleProgressBarState()
    }

    fun executeJavaScript(jsCode: String, callback: (Boolean) -> Unit) {
        webView?.evaluateJavascript(jsCode) { result ->
            // JavaScript execution completed successfully
            callback(true)
        }
                ?: run {
                    // WebView is null, execution failed
                    callback(false)
                }
    }

    // Method to toggle the visibility of the title and progress bar
    fun toggleTitleAndProgressBar() {
        executeJavaScript(
                """
            (function() {
                // Toggle a class on the body to control visibility
                if (document.body.classList.contains('hide-title-progress')) {
                    document.body.classList.remove('hide-title-progress');
                    // Save the state
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onTitleProgressBarVisibilityChanged) {
                        AndroidInterface.onTitleProgressBarVisibilityChanged(false);
                    }
                    // Resize WebView content to accommodate visible elements
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onResizeWebView) {
                        AndroidInterface.onResizeWebView(false); // Elements are now visible
                    }
                } else {
                    document.body.classList.add('hide-title-progress');
                    // Save the state
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onTitleProgressBarVisibilityChanged) {
                        AndroidInterface.onTitleProgressBarVisibilityChanged(true);
                    }
                    // Resize WebView content to fill space from hidden elements
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onResizeWebView) {
                        AndroidInterface.onResizeWebView(true); // Elements are now hidden
                    }
                }
            })();
            """.trimIndent()
        ) { success ->
            if (!success) {
                Log.e("ReadFragment", "Failed to execute toggleTitleAndProgressBar")
            }
        }
    }

    // Method to inject CSS for resizing the WebView content when elements are hidden
    private fun injectResizeCSS() {
        executeJavaScript(
                """
            (function() {
                // Create or update the resize CSS
                var styleId = 'lute-android-resize-style';
                var existingStyle = document.getElementById(styleId);

                if (existingStyle) {
                    // Update existing style
                    existingStyle.textContent = `
                        /* When title and progress bar are hidden, adjust margins to use freed space */
                        .hide-title-progress #reading-header {
                            margin-bottom: 0 !important;
                            padding-bottom: 0 !important;
                        }

                        .hide-title-progress #thetext {
                            margin-top: -80px !important;
                            padding-top: 20px !important;
                        }

                        /* Adjust other elements to fill the space */
                        .hide-title-progress .reading_header_page {
                            height: 0 !important;
                            min-height: 0 !important;
                            padding: 0 !important;
                            margin: 0 !important;
                        }
                    `;
                } else {
                    // Create new style element
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `
                        /* When title and progress bar are hidden, adjust margins to use freed space */
                        .hide-title-progress #reading-header {
                            margin-bottom: 0 !important;
                            padding-bottom: 0 !important;
                        }

                        .hide-title-progress #thetext {
                            margin-top: -80px !important;
                            padding-top: 20px !important;
                        }

                        /* Adjust other elements to fill the space */
                        .hide-title-progress .reading_header_page {
                            height: 0 !important;
                            min-height: 0 !important;
                            padding: 0 !important;
                            margin: 0 !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
            })();
            """.trimIndent()
        ) { success ->
            if (!success) {
                Log.e("ReadFragment", "Failed to inject resize CSS")
            }
        }
    }

    fun goBackInWebView(callback: (Boolean) -> Unit) {
        webView?.let { webView ->
            if (webView.canGoBack()) {
                webView.goBack()
                callback(true)
            } else {
                callback(false)
            }
        }
                ?: run { callback(false) }
    }

    // JavaScript interface for page turn notifications
    inner class PageTurnInterface {
        @android.webkit.JavascriptInterface
        fun onPageTurn() {
            // Update the word count when a page is turned
            android.util.Log.d("ReadFragment", "onPageTurn called")
            activity?.runOnUiThread {
                // Notify the MainActivity to update the word count
                try {
                    android.util.Log.d("ReadFragment", "Calling updateWordCount from onPageTurn")
                    val mainActivity = activity
                    if (mainActivity is com.example.luteforandroidv2.MainActivity) {
                        // Clear the word count cache to force a fresh fetch
                        mainActivity.clearWordCountCache()
                        // Call updateWordCount directly without delay like in AppSettingsFragment
                        mainActivity.updateWordCount()
                        android.util.Log.d("ReadFragment", "updateWordCount called successfully")
                    } else {
                        android.util.Log.e("ReadFragment", "Activity is not MainActivity")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                            "ReadFragment",
                            "Error updating word count in MainActivity",
                            e
                    )
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onTermStatusChanged() {
            // Update the word count when a term status is changed (e.g., green check mark)
            android.util.Log.d("ReadFragment", "onTermStatusChanged called")
            activity?.runOnUiThread {
                // Notify the MainActivity to update the word count
                try {
                    android.util.Log.d(
                            "ReadFragment",
                            "Calling updateWordCount from onTermStatusChanged"
                    )
                    val mainActivity = activity
                    if (mainActivity is com.example.luteforandroidv2.MainActivity) {
                        // Clear the word count cache to force a fresh fetch
                        mainActivity.clearWordCountCache()
                        // Call updateWordCount directly without delay like in AppSettingsFragment
                        mainActivity.updateWordCount()
                        android.util.Log.d(
                                "ReadFragment",
                                "updateWordCount called successfully from onTermStatusChanged"
                        )
                    } else {
                        android.util.Log.e("ReadFragment", "Activity is not MainActivity")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                            "ReadFragment",
                            "Error updating word count in MainActivity",
                            e
                    )
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onBookLanguageDetected(language: String) {
            // Update the saved book language when detected
            android.util.Log.d(
                    "ReadFragment",
                    "onBookLanguageDetected called with language: $language"
            )
            activity?.runOnUiThread {
                try {
                    savedBookLanguage = language
                    android.util.Log.d("ReadFragment", "Saved book language: $savedBookLanguage")

                    // Save to SharedPreferences
                    if (savedBookId != null) {
                        val sharedPref =
                                requireContext()
                                        .getSharedPreferences(
                                                "reader_settings",
                                                Context.MODE_PRIVATE
                                        )
                        with(sharedPref.edit()) {
                            putString("last_book_language", language)
                            apply()
                        }
                    }

                    // Also update the word count when language changes
                    val mainActivity = activity
                    if (mainActivity is com.example.luteforandroidv2.MainActivity) {
                        mainActivity.clearWordCountCache()
                        // Call updateWordCount directly without delay like in AppSettingsFragment
                        mainActivity.updateWordCount()
                        android.util.Log.d(
                                "ReadFragment",
                                "updateWordCount called successfully from onBookLanguageDetected"
                        )
                    } else {
                        android.util.Log.e("ReadFragment", "Activity is not MainActivity")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReadFragment", "Error saving book language", e)
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onRequestTermFormNew(lid: String, text: String) {
            android.util.Log.d(
                    "ReadFragment",
                    "onRequestTermFormNew called with lid: $lid, text: $text"
            )
            // TODO: Implement term form new functionality
            android.util.Log.w("ReadFragment", "Term form new functionality not implemented yet")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        webView = null
    }

    // Method to show the text formatting popup
    fun showTextFormattingPopup() {
        val dialogView =
                LayoutInflater.from(context).inflate(R.layout.dialog_text_formatting_popup, null)

        val popupWindow =
                android.widget.PopupWindow(
                        dialogView,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                )

        // Find the buttons
        val btnIncreaseTextSize =
                dialogView.findViewById<android.widget.ImageButton>(R.id.btnIncreaseTextSize)
        val btnDecreaseTextSize =
                dialogView.findViewById<android.widget.ImageButton>(R.id.btnDecreaseTextSize)
        val btnIncreaseLineSpacing =
                dialogView.findViewById<android.widget.ImageButton>(R.id.btnIncreaseLineSpacing)
        val btnDecreaseLineSpacing =
                dialogView.findViewById<android.widget.ImageButton>(R.id.btnDecreaseLineSpacing)

        // Set click listeners
        btnIncreaseTextSize.setOnClickListener {
            executeJavaScript("incrementFontSize(1);") { success ->
                if (!success) {
                    android.util.Log.e("ReadFragment", "Failed to execute incrementFontSize(1)")
                }
            }
            popupWindow.dismiss()
        }

        btnDecreaseTextSize.setOnClickListener {
            executeJavaScript("incrementFontSize(-1);") { success ->
                if (!success) {
                    android.util.Log.e("ReadFragment", "Failed to execute incrementFontSize(-1)")
                }
            }
            popupWindow.dismiss()
        }

        btnIncreaseLineSpacing.setOnClickListener {
            executeJavaScript("incrementLineHeight(0.1);") { success ->
                if (!success) {
                    android.util.Log.e("ReadFragment", "Failed to execute incrementLineHeight(0.1)")
                }
            }
            popupWindow.dismiss()
        }

        btnDecreaseLineSpacing.setOnClickListener {
            executeJavaScript("incrementLineHeight(-0.1);") { success ->
                if (!success) {
                    android.util.Log.e(
                            "ReadFragment",
                            "Failed to execute incrementLineHeight(-0.1)"
                    )
                }
            }
            popupWindow.dismiss()
        }

        // Show the popup in the center of the screen
        popupWindow.showAtLocation(webView, android.view.Gravity.CENTER, 0, 0)
    }

    fun getBookId(): String? {
        try {
            // Return the saved book ID if available
            return savedBookId
        } catch (e: Exception) {
            android.util.Log.e("ReadFragment", "Error getting book ID", e)
            return null
        }
    }

    // Method to get the language of the current book
    fun getBookLanguage(): String? {
        // This would need to be implemented to extract the language from the book
        // For now, we can try to get it from the WebView or stored data
        try {
            // Return the saved book language if available
            return savedBookLanguage
        } catch (e: Exception) {
            android.util.Log.e("ReadFragment", "Error getting book language", e)
            return null
        }
    }

    // Method to restore the title and progress bar visibility state
    private fun restoreTitleProgressBarState() {
        // Get the saved state from SharedPreferences
        val sharedPref =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        val isHidden = sharedPref.getBoolean("title_progress_hidden", false)

        if (isHidden) {
            // Apply the hide class immediately
            webView?.evaluateJavascript(
                    """
                (function() {
                    document.body.classList.add('hide-title-progress');
                })();
                """.trimIndent(),
                    null
            )

            // Also apply the resizing CSS to fill the freed space
            webView?.evaluateJavascript(
                    """
                (function() {
                    // Create or update the resize style
                    var styleId = 'lute-android-resize-style';
                    var existingStyle = document.getElementById(styleId);

                    if (existingStyle) {
                        // Update existing style with reduced movement (about half of previous)
                        existingStyle.textContent = `
                            /* Move content up to fill space from hidden elements */
                            #thetext, #text, .content, body {
                                margin-top: -35px !important;
                                padding-top: 10px !important;
                            }

                            /* Hide any remaining header elements */
                            #reading-header, .reading_header_container {
                                display: none !important;
                                height: 0 !important;
                                min-height: 0 !important;
                                padding: 0 !important;
                                margin: 0 !important;
                            }

                            /* Adjust page content to use freed space */
                            #thetexttitle, #headertexttitle, .read-slide-container, #page_indicator, .reading_header_page {
                                margin-bottom: 0 !important;
                            }
                        `;
                    } else {
                        // Create new style element with reduced movement (about half of previous)
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = `
                            /* Move content up to fill space from hidden elements */
                            #thetext, #text, .content, body {
                                margin-top: -35px !important;
                                padding-top: 10px !important;
                            }

                            /* Hide any remaining header elements */
                            #reading-header, .reading_header_container {
                                display: none !important;
                                height: 0 !important;
                                min-height: 0 !important;
                                padding: 0 !important;
                                margin: 0 !important;
                            }

                            /* Adjust page content to use freed space */
                            #thetexttitle, #headertexttitle, .read-slide-container, #page_indicator, .reading_header_page {
                                margin-bottom: 0 !important;
                            }
                        `;
                        document.head.appendChild(style);
                    }
                })();
                """.trimIndent(),
                    null
            )
        }
    }

    // JavaScript interface for receiving state changes from the web page
    inner class AndroidInterface {
        @JavascriptInterface
        fun onTitleProgressBarVisibilityChanged(hidden: Boolean) {
            // Save the state to SharedPreferences
            val sharedPref =
                    requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("title_progress_hidden", hidden)
                apply()
            }
        }

        @JavascriptInterface
        fun onResizeWebView(elementsHidden: Boolean) {
            // Adjust the web page content CSS to fill the freed space
            activity?.runOnUiThread {
                if (elementsHidden) {
                    // Elements are hidden, shift content up and reduce top padding
                    webView?.evaluateJavascript(
                            """
                        (function() {
                            // Create or update the resize style
                            var styleId = 'lute-android-resize-style';
                            var existingStyle = document.getElementById(styleId);

                            if (existingStyle) {
                                // Update existing style with reduced movement (about half of previous)
                                existingStyle.textContent = `
                                    /* Move content up to fill space from hidden elements */
                                    #thetext, #text, .content, body {
                                        margin-top: -40px !important;
                                        padding-top: 10px !important;
                                    }

                                    /* Hide any remaining header elements */
                                    #reading-header, .reading_header_container {
                                        display: none !important;
                                        height: 0 !important;
                                        min-height: 0 !important;
                                        padding: 0 !important;
                                        margin: 0 !important;
                                    }

                                    /* Adjust page content to use freed space */
                                    #thetexttitle, #headertexttitle, .read-slide-container, #page_indicator, .reading_header_page {
                                        margin-bottom: 0 !important;
                                    }
                                `;
                            } else {
                                // Create new style element with reduced movement (about half of previous)
                                var style = document.createElement('style');
                                style.id = styleId;
                                style.textContent = `
                                    /* Move content up to fill space from hidden elements */
                                    #thetext, #text, .content, body {
                                        margin-top: -40px !important;
                                        padding-top: 10px !important;
                                    }

                                    /* Hide any remaining header elements */
                                    #reading-header, .reading_header_container {
                                        display: none !important;
                                        height: 0 !important;
                                        min-height: 0 !important;
                                        padding: 0 !important;
                                        margin: 0 !important;
                                    }

                                    /* Adjust page content to use freed space */
                                    #thetexttitle, #headertexttitle, .read-slide-container, #page_indicator, .reading_header_page {
                                        margin-bottom: 0 !important;
                                    }
                                `;
                                document.head.appendChild(style);
                            }
                        })();
                        """.trimIndent(),
                            null
                    )
                } else {
                    // Elements are visible again, remove the resize CSS
                    webView?.evaluateJavascript(
                            """
                        (function() {
                            // Remove the resize CSS
                            var style = document.getElementById('lute-android-resize-style');
                            if (style) {
                                style.parentNode.removeChild(style);
                            }
                        })();
                        """.trimIndent(),
                            null
                    )
                }
            }
        }
    }
}
