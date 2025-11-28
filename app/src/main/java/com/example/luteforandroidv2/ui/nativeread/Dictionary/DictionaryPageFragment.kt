package com.example.luteforandroidv2.ui.nativeread.Dictionary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentDictionaryPageBinding
import com.example.luteforandroidv2.ui.nativeread.Translation.TranslationCacheManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class DictionaryPageFragment : Fragment() {

    private var _binding: FragmentDictionaryPageBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var webView: WebView
    private var dictionaryUrl: String? = null
    private var term: String? = null
    private var isSentenceMode: Boolean = false
    private lateinit var cacheManager: DictionaryCacheManager

    // Interface for close button functionality
    interface CloseButtonListener {
        fun onCloseButtonClicked()
    }

    private var closeButtonListener: CloseButtonListener? = null

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TERM = "term"
        private const val ARG_SENTENCE_MODE = "sentence_mode"

        fun newInstance(
                url: String,
                term: String,
                isSentenceMode: Boolean = false
        ): DictionaryPageFragment {
            val fragment = DictionaryPageFragment()
            val args =
                    Bundle().apply {
                        putString(ARG_URL, url)
                        putString(ARG_TERM, term)
                        putBoolean(ARG_SENTENCE_MODE, isSentenceMode)
                    }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dictionaryUrl = it.getString(ARG_URL)
            term = it.getString(ARG_TERM)
            isSentenceMode = it.getBoolean(ARG_SENTENCE_MODE, false)
        }
        cacheManager = DictionaryCacheManager.getInstance(requireContext())
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = binding.webView
        setupWebView()
        setupFloatingButtons()
        loadContent()
    }

    fun setCloseButtonListener(listener: CloseButtonListener?) {
        this.closeButtonListener = listener
    }

    private fun setupFloatingButtons() {
        // Hide the copy to term button when in sentence mode
        if (isSentenceMode) {
            binding.copyToTermButton.visibility = View.GONE
        } else {
            // Set up the copy to term button
            binding.copyToTermButton.setOnClickListener {
                webView.evaluateJavascript(
                        "(function() { return window.getSelection().toString(); })();"
                ) { selectedText ->
                    var textToCopy = selectedText.replace("\"", "").trim()
                    if (textToCopy.isBlank()) {
                        // If no text is selected, get all the text from the body
                        webView.evaluateJavascript("document.body.innerText") { allText ->
                            textToCopy = allText.replace("\"", "").trim()
                        }
                    }

                    if (textToCopy.isNotBlank()) {
                        // Update the parent DictionaryDialogFragment's translation field by
                        // appending
                        // the
                        // text
                        val parentFragment = parentFragment
                        if (parentFragment is
                                        com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionaryDialogFragment
                        ) {
                            parentFragment.appendTranslationText(textToCopy)
                            // Update cache with the new text
                            val currentText = parentFragment.getCurrentTranslationText()
                            TranslationCacheManager.getInstance()
                                    .setTemporaryTranslation(currentText)
                        }
                    } else {
                        // No text to copy - silently ignore
                    }
                }
            }
        }

        // Set up the close button
        binding.closeButton.setOnClickListener {
            // If we have a listener, delegate to it
            closeButtonListener?.onCloseButtonClicked()
                    ?: run {
                        // Fallback: try to dismiss using the activity context
                        activity?.finish()
                    }
        }
    }

    private fun setupWebView() {
        // Add JavaScript interface for communication
        webView.addJavascriptInterface(DictionaryJavaScriptInterface(), "DictionaryInterface")

        webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Add Android app identification class as early as possible
                        view?.let { WebViewClassHelper.addAndroidAppClass(it) }

                        // Inject custom CSS and JavaScript to improve readability and handle links
                        view?.evaluateJavascript(
                                """
                    (function() {
                        // Add custom styling for better dictionary display and ad blocking
                        var style = document.createElement('style');
                        style.textContent = `
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
                                padding: 16px;
                                line-height: 1.6;
                                color: #333;
                                background-color: #fff;
                                font-size: 16px;
                            }
                            h1, h2, h3 {
                                color: #202124;
                                margin-top: 1.5em;
                                margin-bottom: 0.5em;
                            }
                            h1 {
                                font-size: 1.8em;
                                border-bottom: 1px solid #e0e0e0;
                                padding-bottom: 0.3em;
                            }
                            h2 {
                                font-size: 1.5em;
                            }
                            h3 {
                                font-size: 1.3em;
                            }
                            p {
                                margin: 0.8em 0;
                            }
                            a {
                                color: #1a73e8;
                                text-decoration: none;
                                border-bottom: 1px dotted #1a73e8;
                            }
                            a:hover {
                                text-decoration: none;
                                border-bottom: 1px solid #1a73e8;
                            }
                            .word-highlight {
                                background-color: #1a73e8;
                                color: white;
                                padding: 2px 4px;
                                border-radius: 3px;
                            }
                            ul, ol {
                                padding-left: 1.5em;
                                margin: 0.8em 0;
                            }
                            li {
                                margin: 0.3em 0;
                            }
                            strong, b {
                                color: #202124;
                            }

                            /* Ad blocking CSS rules */
                            .ad, .ads, .advertisement, .banner, .sponsor, .sponsored,
                            .google-ads, .adsense, .ad-container, .ad-wrapper,
                            [class*="ad-"], [id*="ad-"], [class*="ads-"], [id*="ads-"],
                            [class*="advertisement"], [id*="advertisement"],
                            [class*="banner"], [id*="banner"],
                            [class*="sponsor"], [id*="sponsor"] {
                                display: none !important;
                                visibility: hidden !important;
                                height: 0 !important;
                                width: 0 !important;
                                position: absolute !important;
                                left: -10000px !important;
                                top: -10000px !important;
                            }

                            /* Hide common ad iframe containers */
                            iframe[src*="ads"], iframe[src*="doubleclick"],
                            iframe[src*="googlesyndication"], iframe[src*="adnxs"],
                            iframe[name*="ad"], iframe[id*="ad"] {
                                display: none !important;
                                visibility: hidden !important;
                                height: 0 !important;
                                width: 0 !important;
                                position: absolute !important;
                                left: -10000px !important;
                                top: -10000px !important;
                            }

                            /* Hide ad images */
                            img[src*="ads"], img[src*="doubleclick"], img[src*="googlesyndication"],
                            img[alt*="ad"], img[alt*="advertisement"], img[alt*="sponsor"] {
                                display: none !important;
                            }
                        `;
                        document.head.appendChild(style);

                        // Add click handlers for all links to prevent navigation but allow text selection
                        var links = document.getElementsByTagName('a');
                        for (var i = 0; i < links.length; i++) {
                            links[i].addEventListener('click', function(e) {
                                // Check if this is an external link
                                var href = this.getAttribute('href');
                                if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                                    // This is an external link, prevent navigation
                                    e.preventDefault();

                                    // Allow text selection on links by selecting the link's text content
                                    var range = document.createRange();
                                    range.selectNodeContents(this);
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    selection.addRange(range);

                                    // Notify Android interface that text was selected
                                    var text = this.textContent;
                                    if (typeof DictionaryInterface !== 'undefined' && DictionaryInterface.onTextSelected) {
                                        DictionaryInterface.onTextSelected(text);
                                    }
                                }
                                // For internal links, allow normal navigation
                            });

                            // Add custom attribute to mark as selectable
                            links[i].setAttribute('data-selectable', 'true');
                        }

                        // Variables to track tap
                        var lastTapElement = null;

                        // Also capture clicks on the document to handle dynamically added links and text selection
                        document.addEventListener('click', function(e) {
                            // Check if the clicked element is a link or inside a link
                            var link = e.target.closest('a');
                            if (link) {
                                // Check if this is an external link
                                var href = link.getAttribute('href');
                                if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                                    e.preventDefault();

                                    // Allow text selection on links by selecting the link's text content
                                    var range = document.createRange();
                                    range.selectNodeContents(link);
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    selection.addRange(range);

                                    // Notify Android interface that text was selected
                                    var text = link.textContent;
                                    if (typeof DictionaryInterface !== 'undefined' && DictionaryInterface.onTextSelected) {
                                        DictionaryInterface.onTextSelected(text);
                                    }
                                    return;
                                }
                                // For internal links, allow normal navigation by not preventing default
                            }

                            // Handle single-tap text selection
                            handleTextSelection(e);
                        }, true);

                        // Function to handle text selection on tap
                        function handleTextSelection(e) {
                            // Clear any existing selection
                            clearTextSelection();

                            // Get the element that was clicked
                            var element = e.target;

                            // Only handle text nodes or elements with text content
                            // AND only if the click was actually on text (not empty space)
                            if (element.nodeType === 3 || (element.nodeType === 1 && element.textContent && element.textContent.trim() !== '')) {
                                // Check if the click was directly on text by using a more precise method
                                // Only proceed if we can get a valid character at the click position
                                var charAtClick = getCharacterAtPosition(element, e.clientX, e.clientY);
                                if (charAtClick && /\S/.test(charAtClick)) {
                                    // Get the word at the click position
                                    var wordInfo = getExactWordAtPosition(element, e.clientX, e.clientY);
                                    if (wordInfo && wordInfo.word && wordInfo.word.trim() !== '') {
                                        // Select and highlight the specific word
                                        selectAndHighlightWord(element, wordInfo);

                                        // Notify Android that text was selected
                                        if (typeof DictionaryInterface !== 'undefined' && DictionaryInterface.onTextSelected) {
                                            DictionaryInterface.onTextSelected(wordInfo.word);
                                        }
                                    }
                                }
                                // If click was on empty space, do nothing (no text selection)
                            }
                        }

                        // Function to get the exact word at a position using precise text node measurement
                        function getExactWordAtPosition(element, clientX, clientY) {
                            try {
                                // Get computed style for font metrics
                                var computedStyle = window.getComputedStyle(element);
                                var fontSize = parseFloat(computedStyle.fontSize);
                                var lineHeight = parseFloat(computedStyle.lineHeight) || fontSize * 1.2;

                                // Use more precise measurement with font metrics consideration
                                if (document.caretPositionFromPoint) {
                                    var pos = document.caretPositionFromPoint(clientX, clientY);
                                    if (pos && pos.offsetNode) {
                                        var textNode = pos.offsetNode;
                                        var offset = pos.offset;
                                        var text = textNode.textContent;

                                        if (text && offset >= 0 && offset <= text.length) {
                                            // Find word boundaries
                                            var start = offset;
                                            var end = offset;

                                            // Move backward to find word start
                                            while (start > 0 && /\S/.test(text[start - 1])) {
                                                start--;
                                            }

                                            // Move forward to find word end
                                            while (end < text.length && /\S/.test(text[end])) {
                                                end++;
                                            }

                                            if (start < end) {
                                                var word = text.substring(start, end);
                                                return {
                                                    word: word,
                                                    start: start,
                                                    end: end,
                                                    textNode: textNode
                                                };
                                            }
                                        }
                                    }
                                } else if (document.caretRangeFromPoint) {
                                    var range = document.caretRangeFromPoint(clientX, clientY);
                                    if (range && range.startContainer) {
                                        var textNode = range.startContainer;
                                        var offset = range.startOffset;
                                        var text = textNode.textContent;

                                        if (text && offset >= 0 && offset <= text.length) {
                                            // Find word boundaries
                                            var start = offset;
                                            var end = offset;

                                            // Move backward to find word start
                                            while (start > 0 && /\S/.test(text[start - 1])) {
                                                start--;
                                            }

                                            // Move forward to find word end
                                            while (end < text.length && /\S/.test(text[end])) {
                                                end++;
                                            }

                                            if (start < end) {
                                                var word = text.substring(start, end);
                                                return {
                                                    word: word,
                                                    start: start,
                                                    end: end,
                                                    textNode: textNode
                                                };
                                            }
                                        }
                                    }
                                }
                            } catch (err) {
                                console.log("Error in precise word detection: " + err);
                            }

                            // Fallback method using simpler approach
                            return getWordFallback(element, clientX, clientY);
                        }

                        // Function to get the character at a specific position
                        function getCharacterAtPosition(element, clientX, clientY) {
                            try {
                                if (document.caretPositionFromPoint) {
                                    var pos = document.caretPositionFromPoint(clientX, clientY);
                                    if (pos && pos.offsetNode) {
                                        var textNode = pos.offsetNode;
                                        var offset = pos.offset;
                                        var text = textNode.textContent;

                                        if (text && offset >= 0 && offset < text.length) {
                                            return text.charAt(offset);
                                        }
                                    }
                                } else if (document.caretRangeFromPoint) {
                                    var range = document.caretRangeFromPoint(clientX, clientY);
                                    if (range && range.startContainer) {
                                        var textNode = range.startContainer;
                                        var offset = range.startOffset;
                                        var text = textNode.textContent;

                                        if (text && offset >= 0 && offset < text.length) {
                                            return text.charAt(offset);
                                        }
                                    }
                                }
                            } catch (err) {
                                console.log("Error getting character at position: " + err);
                            }
                            return null;
                        }

                        // Fallback method for word detection
                        function getWordFallback(element, clientX, clientY) {
                            var text = element.textContent || element.innerText;
                            if (!text) return null;

                            // Simple approach: get first word
                            var match = text.match(/\S+/);
                            if (match) {
                                return {
                                    word: match[0],
                                    start: match.index,
                                    end: match.index + match[0].length
                                };
                            }
                            return null;
                        }

                        // Function to select and highlight a specific word by wrapping it in a span
                        function selectAndHighlightWord(element, wordInfo) {
                            try {
                                // Clear any previous selection
                                clearTextSelection();

                                var textNode = wordInfo.textNode;
                                if (!textNode) {
                                    // Fallback: if we don't have the text node, use a simpler approach
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    var range = document.createRange();
                                    range.selectNodeContents(element);
                                    selection.addRange(range);
                                    return;
                                }

                                // Split the text node at the word boundaries
                                var text = textNode.textContent;
                                var beforeText = text.substring(0, wordInfo.start);
                                var wordText = text.substring(wordInfo.start, wordInfo.end);
                                var afterText = text.substring(wordInfo.end);

                                // Create new nodes
                                var parent = textNode.parentNode;
                                if (beforeText.length > 0) {
                                    parent.insertBefore(document.createTextNode(beforeText), textNode);
                                }

                                // Create span for the word
                                var wordSpan = document.createElement('span');
                                wordSpan.className = 'word-highlight';
                                wordSpan.textContent = wordText;
                                wordSpan.id = 'selected-word-' + Date.now(); // Unique ID
                                parent.insertBefore(wordSpan, textNode);

                                if (afterText.length > 0) {
                                    parent.insertBefore(document.createTextNode(afterText), textNode);
                                }

                                // Remove the original text node
                                parent.removeChild(textNode);

                                // Select the word span
                                var selection = window.getSelection();
                                selection.removeAllRanges();
                                var range = document.createRange();
                                range.selectNodeContents(wordSpan);
                                selection.addRange(range);

                                // Store reference to clean up later
                                window.currentSelectedWordSpan = wordSpan;

                            } catch (err) {
                                console.log("Error selecting and highlighting word: " + err);

                                // Fallback: select entire element
                                try {
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    var range = document.createRange();
                                    range.selectNodeContents(element);
                                    selection.addRange(range);
                                } catch (err2) {
                                    console.log("Error in fallback selection: " + err2);
                                }
                            }
                        }

                        // Function to clear text selection and remove highlights
                        function clearTextSelection() {
                            try {
                                // Clear browser selection
                                var selection = window.getSelection();
                                selection.removeAllRanges();

                                // Remove any previously added word span
                                if (window.currentSelectedWordSpan) {
                                    var span = window.currentSelectedWordSpan;
                                    var parent = span.parentNode;
                                    var text = span.textContent;

                                    // Replace span with text node
                                    parent.replaceChild(document.createTextNode(text), span);
                                    window.currentSelectedWordSpan = null;
                                }

                                // Also look for any remaining highlighted spans in the document
                                var highlightedSpans = document.querySelectorAll('span.word-highlight');
                                for (var i = 0; i < highlightedSpans.length; i++) {
                                    var span = highlightedSpans[i];
                                    var parent = span.parentNode;
                                    var text = span.textContent;
                                    parent.replaceChild(document.createTextNode(text), span);
                                }
                            } catch (err) {
                                console.log("Error clearing text selection: " + err);
                            }
                        }

                        // BLOCK CONTEXT MENUS - Add this at the end of the function
                        window.oncontextmenu = function(event) {
                            event.preventDefault();
                            event.stopPropagation();
                            return false;
                        };

                        // Also block other context menu triggers
                        document.addEventListener('contextmenu', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            return false;
                        }, true);

                        // Block ad-related JavaScript functions
                        try {
                            // Block common ad loading functions
                            window.eval = function() {
                                console.log("Blocked eval() call - possible ad script");
                                return null;
                            };

                            // Block document.write for ads
                            document.write = function() {
                                console.log("Blocked document.write() call - possible ad script");
                                return null;
                            };

                            // Block ad network APIs
                            var adNetworks = [
                                'doubleclick.net',
                                'googlesyndication.com',
                                'googleadservices.com',
                                'adservice.google.com',
                                'facebook.com/tr',
                                'facebook.net',
                                'ads.yahoo.com',
                                'adnxs.com',
                                'rubiconproject.com',
                                'openx.net',
                                'pubmatic.com',
                                'taboola.com',
                                'outbrain.com'
                            ];

                            // Override XMLHttpRequest to block ad requests
                            var originalXHR = window.XMLHttpRequest;
                            if (originalXHR) {
                                window.XMLHttpRequest = function() {
                                    var xhr = new originalXHR();
                                    var originalOpen = xhr.open;
                                    xhr.open = function(method, url) {
                                        for (var i = 0; i < adNetworks.length; i++) {
                                            if (url && url.indexOf(adNetworks[i]) !== -1) {
                                                console.log("Blocked XHR request to ad network: " + url);
                                                return;
                                            }
                                        }
                                        return originalOpen.apply(this, arguments);
                                    };
                                    return xhr;
                                };
                            }

                            // Override fetch API to block ad requests
                            var originalFetch = window.fetch;
                            if (originalFetch) {
                                window.fetch = function(input, init) {
                                    var url = (typeof input === 'string') ? input : input.url;
                                    for (var i = 0; i < adNetworks.length; i++) {
                                        if (url && url.indexOf(adNetworks[i]) !== -1) {
                                            console.log("Blocked fetch request to ad network: " + url);
                                            return Promise.reject(new Error("Blocked by ad blocker"));
                                        }
                                    }
                                    return originalFetch.apply(this, arguments);
                                };
                            }
                        } catch (err) {
                            console.log("Error setting up ad blocking functions: " + err);
                        }
                    })();
                """.trimIndent(),
                                null
                        )
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        // Allow loading of the initial dictionary content
                        if (url == null) return false

                        // Get the base URL of the dictionary
                        val baseUrl = dictionaryUrl?.replace("[LUTE]", "") ?: ""

                        // Allow loading content from the same domain as the dictionary ONLY for
                        // specific paths
                        // This is typically for dictionary-related AJAX requests or content loading
                        if (url.startsWith(baseUrl) ||
                                        (baseUrl.isNotEmpty() &&
                                                java.net.URL(url).host ==
                                                        java.net.URL(baseUrl).host)
                        ) {
                            // Allow specific dictionary-related paths (like API calls for
                            // definitions)
                            // You can customize these paths based on your dictionary implementation
                            val allowedPaths = listOf("/api/", "/definition/", "/word/", "/search/")
                            val isAllowedPath =
                                    allowedPaths.any { path ->
                                        url.contains(path, ignoreCase = true)
                                    }

                            // If it's an allowed path, permit the navigation
                            if (isAllowedPath) {
                                return false
                            }

                            // For other internal links, block them to prevent navigation within the
                            // dictionary site
                            // This prevents users from navigating away from the dictionary entry to
                            // other pages
                            android.util.Log.d(
                                    "DictionaryPageFragment",
                                    "Blocked internal link: $url"
                            )
                            return true
                        }

                        // Block external navigation
                        android.util.Log.d("DictionaryPageFragment", "Blocked external link: $url")
                        return true
                    }
                }

        // Set up WebChromeClient to block JavaScript popups
        webView.webChromeClient =
                object : WebChromeClient() {
                    override fun onJsAlert(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult
                    ): Boolean {
                        result.cancel()
                        return true
                    }

                    override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult
                    ): Boolean {
                        result.cancel()
                        return true
                    }

                    override fun onJsPrompt(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult
                    ): Boolean {
                        result.cancel()
                        return true
                    }
                }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Enhanced security settings to block ads and unwanted content
        webView.settings.setAllowFileAccess(false)
        webView.settings.setAllowContentAccess(false)
        webView.settings.setAllowFileAccessFromFileURLs(false)
        webView.settings.setAllowUniversalAccessFromFileURLs(false)
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.setGeolocationEnabled(false)
        webView.settings.setSaveFormData(false)
        webView.settings.setSavePassword(false)

        // Block mixed content (HTTP content on HTTPS pages)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Disable long-press context menu
        webView.setOnLongClickListener { true } // Consumes the long press event
        webView.isLongClickable = false // Additional measure

        // Apply additional dictionary security
        webView.applyDictionarySecurity()
    }

    private fun loadContent() {
        val url = dictionaryUrl ?: return
        val termToSearch = term ?: return
        val finalUrl = url.replace("[LUTE]", termToSearch)

        // Check if content is cached
        val cachedContent =
                cacheManager.getCachedContent(termToSearch, 0, url) // TODO: Get language ID
        if (cachedContent != null) {
            // Process cached content with JSoup to remove ads and unwanted elements
            val processedContent = filterContentWithJSoup(cachedContent)
            // Load cached content
            webView.loadDataWithBaseURL(null, processedContent, "text/html", "UTF-8", null)
            Toast.makeText(context, "Loaded from cache", Toast.LENGTH_SHORT).show()
        } else {
            // Load content from network
            webView.loadUrl(finalUrl)
        }
    }

    /** Filter content with JSoup to remove ads, tracking scripts, and unwanted elements */
    private fun filterContentWithJSoup(htmlContent: String): String {
        try {
            val document = org.jsoup.Jsoup.parse(htmlContent)

            // Remove script tags (commonly used for ads and tracking)
            document.select("script").remove()

            // Remove style tags that might contain ad-related CSS
            document.select("style").remove()

            // Remove iframe elements (commonly used for ads)
            document.select("iframe").remove()

            // Remove common ad-related elements by class or id
            document.select(".ad, .ads, .advertisement, .banner, .sponsor, .sponsored").remove()
            document.select("[id*=ad], [id*=ads], [id*=advertisement], [id*=banner], [id*=sponsor]")
                    .remove()
            document.select(
                            "[class*=ad], [class*=ads], [class*=advertisement], [class*=banner], [class*=sponsor]"
                    )
                    .remove()

            // Remove elements with known ad network attributes
            document.select(
                            "img[src*=doubleclick], img[src*=googlesyndication], img[src*=adservice]"
                    )
                    .remove()
            document.select(
                            "link[href*=doubleclick], link[href*=googlesyndication], link[href*=adservice]"
                    )
                    .remove()

            // Remove object and embed tags (often used for ads)
            document.select("object, embed").remove()

            // Remove noscript tags that might contain ad content
            document.select("noscript").remove()

            // Remove meta tags that might be used for tracking
            document.select(
                            "meta[name*=analytics], meta[name*=tracking], meta[property*=analytics]"
                    )
                    .remove()

            // Clean up any onclick, onerror, onload attributes that could contain malicious scripts
            document.select("*[onclick], *[onerror], *[onload]").forEach { element ->
                element.removeAttr("onclick")
                element.removeAttr("onerror")
                element.removeAttr("onload")
            }

            return document.html()
        } catch (e: Exception) {
            android.util.Log.e("DictionaryPageFragment", "Error filtering content with JSoup", e)
            // Return original content if filtering fails
            return htmlContent
        }
    }

    fun getWebView(): WebView {
        return webView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Method to update the dictionary translation field
    fun updateDictionaryTranslationField(word: String) {
        // This method appends a word to the parent DictionaryDialogFragment's translation field
        // We need to communicate with the parent DictionaryDialogFragment to update its translation
        // field

        // Get the parent fragment (DictionaryDialogFragment) and call its append method
        val parentFragment = parentFragment
        if (parentFragment is DictionaryDialogFragment) {
            parentFragment.appendTranslationText(word)
            // Update cache with the new text
            val currentText = parentFragment.getCurrentTranslationText()
            TranslationCacheManager.getInstance().setTemporaryTranslation(currentText)
        } else {
            android.util.Log.e(
                    "DictionaryPageFragment",
                    "Parent fragment is not DictionaryDialogFragment"
            )
        }
    }

    // JavaScript interface for handling link clicks
    inner class DictionaryJavaScriptInterface {
        @JavascriptInterface
        fun onLinkClicked(url: String, linkText: String) {
            activity?.runOnUiThread {
                // Determine the type of link and handle accordingly
                when {
                    // Handle popup links (typically these would have a specific pattern)
                    url.contains("popup") || url.contains("definition") -> {
                        // Handle popup links silently
                        Log.d("DictionaryPageFragment", "Popup link clicked: $linkText")
                        // In the future, we could show a popup with the content
                    }
                    // Handle external links - silently block
                    url.startsWith("http://") || url.startsWith("https://") -> {
                        Log.d("DictionaryPageFragment", "External link blocked: $linkText")
                        // Silently block without toast
                    }
                    // Handle other links - silently block
                    else -> {
                        Log.d("DictionaryPageFragment", "Link blocked: $linkText")
                        // Silently block without toast
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTextSelected(text: String) {
            activity?.runOnUiThread {
                // Optional: Show a toast or other feedback when text is selected
                // For now, we'll just log it
                android.util.Log.d("DictionaryPageFragment", "Text selected: $text")
            }
        }
    }

    // Method to cache the current page content
    fun cacheCurrentPage() {
        webView.evaluateJavascript(
                "(function() { return document.documentElement.outerHTML; })();"
        ) { html ->
            val termToSearch = term ?: return@evaluateJavascript
            val url = dictionaryUrl ?: return@evaluateJavascript
            val content = html.replace("\"", "")

            // Process content with JSoup before caching to ensure ads are removed
            val filteredContent = filterContentWithJSoup(content)

            cacheManager.cacheContent(
                    termToSearch,
                    0,
                    url,
                    filteredContent
            ) // TODO: Get language ID
        }
    }
}
