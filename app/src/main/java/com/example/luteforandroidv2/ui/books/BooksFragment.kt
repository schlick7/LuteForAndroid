package com.example.luteforandroidv2.ui.books

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.luteforandroidv2.databinding.FragmentBooksBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class BooksFragment : Fragment(), BookSelectionInterface.BookSelectionListener {

    private var _binding: FragmentBooksBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if this is the initial app launch by checking if the fragment is at the root
        // of the back stack and navigate to the preferred reader if needed
        checkAndNavigateToLastBookIfNeeded()

        setupWebView()
    }

    private fun checkAndNavigateToLastBook() {
        val readerSettings =
                requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        val lastBookId = readerSettings.getString("last_book_id", null)

        if (!lastBookId.isNullOrEmpty()) {
            // Get the default reader setting
            val appSettingsPref =
                    requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val defaultReader = appSettingsPref.getString("default_reader", "Native Reader")

            // Navigate based on the default reader setting
            if (defaultReader == "Native Reader") {
                // Navigate to the native reader view with the last book ID
                val action = BooksFragmentDirections.actionNavBooksToNavNativeRead(lastBookId)
                findNavController().navigate(action)
            } else {
                // Navigate to the webview reader view with the last book ID
                val action = BooksFragmentDirections.actionNavBooksToNavRead(lastBookId)
                findNavController().navigate(action)
            }
        }
    }

    private fun checkAndNavigateToLastBookIfNeeded() {
        // Use a flag to ensure auto-navigation only happens once per app launch
        val sharedPreferences =
                requireContext().getSharedPreferences("navigation_flags", Context.MODE_PRIVATE)
        val hasAutoNavigated = sharedPreferences.getBoolean("has_auto_navigated", false)

        // Only auto-navigate if we haven't done it already in this app session
        if (!hasAutoNavigated) {
            val readerSettings =
                    requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
            val lastBookId = readerSettings.getString("last_book_id", null)

            if (!lastBookId.isNullOrEmpty()) {
                // Get the default reader setting
                val appSettingsPref =
                        requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val defaultReader = appSettingsPref.getString("default_reader", "Native Reader")

                // Update the flag before navigating so it doesn't happen again
                with(sharedPreferences.edit()) {
                    putBoolean("has_auto_navigated", true)
                    apply()
                }

                // Navigate based on the default reader setting
                if (defaultReader == "Native Reader") {
                    // Navigate to the native reader view with the last book ID
                    val action = BooksFragmentDirections.actionNavBooksToNavNativeRead(lastBookId)
                    findNavController().navigate(action)
                } else {
                    // Navigate to the webview reader view with the last book ID
                    val action = BooksFragmentDirections.actionNavBooksToNavRead(lastBookId)
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onBookSelected(bookId: String) {
        try {
            // Log the book ID
            android.util.Log.d("BooksFragment", "Book selected with ID: $bookId")

            // Save the book ID as the last opened book
            val sharedPref =
                    requireContext().getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("last_book_id", bookId)
                apply()
            }

            // Get the default reader setting
            val appSettingsPref =
                    requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val defaultReader = appSettingsPref.getString("default_reader", "Native Reader")

            // Navigate based on the default reader setting
            if (defaultReader == "Native Reader") {
                // Navigate to the native reader view with the book ID
                val action = BooksFragmentDirections.actionNavBooksToNavNativeRead(bookId)
                findNavController().navigate(action)
            } else {
                // Navigate to the webview reader view with the book ID
                val action = BooksFragmentDirections.actionNavBooksToNavRead(bookId)
                findNavController().navigate(action)
            }
        } catch (e: Exception) {
            android.util.Log.e("BooksFragment", "Error navigating to reader: ${e.message}", e)
        }
    }

    private fun setupWebView() {
        var startX = 0f
        var startY = 0f

        // Keep WebView transparent during loading to prevent FOUC (Opacity Control approach)
        binding.webview.alpha = 0f

        // Add JavaScript interface for communication between WebView and Android
        binding.webview.addJavascriptInterface(BookSelectionInterface(this), "Android")

        // Add touch listener to prevent horizontal scrolling/dragging while allowing vertical
        // scrolling
        binding.webview.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Store initial touch position
                    startX = motionEvent.x
                    startY = motionEvent.y
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(motionEvent.x - startX)
                    val deltaY = Math.abs(motionEvent.y - startY)

                    // If horizontal movement is greater than vertical, consume the touch event
                    // to prevent horizontal scrolling
                    if (deltaX > deltaY) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        return@setOnTouchListener true
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        binding.webview.webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        android.util.Log.d("BooksFragment", "URL loading requested: $url")

                        // Check if this is a book reading URL
                        if (url != null && url.contains("/read/")) {
                            android.util.Log.d("BooksFragment", "Book reading URL detected: $url")

                            // Extract book ID
                            val bookId = extractBookIdFromUrl(url)
                            if (bookId != null) {
                                android.util.Log.d("BooksFragment", "Extracted book ID: $bookId")
                                // Handle the book selection
                                onBookSelected(bookId)
                                return true // We're handling this URL
                            }
                        }

                        // For all other URLs, let the WebView handle them normally
                        return false
                    }

                    override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        // Add Android app identification class as early as possible
                        WebViewClassHelper.ensureAndroidAppClassAdded(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Test if JavaScript interface is working
                        view?.evaluateJavascript(
                                "if (typeof Android !== 'undefined') { Android.testConnection(); }",
                                null
                        )

                        // Add Android app identification class again to ensure it's applied
                        WebViewClassHelper.addAndroidAppClass(view)

                        // Fade in WebView now that everything is loaded (Opacity Control approach)
                        view?.animate()?.alpha(1f)?.setDuration(200)?.start()
                    }
                }

        // Configure WebView settings for optimal mobile display
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.useWideViewPort = false
        binding.webview.settings.loadWithOverviewMode = true
        binding.webview.settings.builtInZoomControls = false
        binding.webview.settings.displayZoomControls = false
        binding.webview.settings.setSupportZoom(false)

        // Hide scrollbars in the WebView
        binding.webview.scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
        binding.webview.isHorizontalScrollBarEnabled = false
        binding.webview.isVerticalScrollBarEnabled = false

        // Enable JavaScript interface
        binding.webview.settings.allowFileAccess = true
        binding.webview.settings.allowContentAccess = true

        // Add JavaScript interface for communication between WebView and Android
        binding.webview.addJavascriptInterface(BookSelectionInterface(this), "Android")

        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            binding.webview.loadUrl("$serverUrl/")
        } else {
            // Load a local HTML page or show an error message
            binding.webview.loadData(
                    "<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>",
                    "text/html",
                    "UTF-8"
            )
        }
    }

    private fun extractBookIdFromUrl(url: String): String? {
        android.util.Log.d("BooksFragment", "Extracting book ID from URL: $url")

        // Method 1: Standard /read/{id} pattern
        val urlParts = url.split("/")
        for (i in urlParts.indices) {
            if (urlParts[i] == "read" && i + 1 < urlParts.size) {
                val bookId = urlParts[i + 1]
                android.util.Log.d("BooksFragment", "Method 1 - Extracted book ID: $bookId")
                if (bookId.isNotEmpty() && bookId.all { it.isDigit() }) {
                    return bookId
                }
            }
        }

        // Method 2: Regex pattern
        val regex = Regex(".*/read/(\\d+)")
        val matchResult = regex.find(url)
        if (matchResult != null) {
            val bookId = matchResult.groupValues[1]
            android.util.Log.d("BooksFragment", "Method 2 - Extracted book ID: $bookId")
            return bookId
        }

        android.util.Log.d("BooksFragment", "Could not extract book ID from URL")
        return null
    }

    fun refreshWebView() {
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            binding.webview.loadUrl("$serverUrl/")
        } else {
            // Load a local HTML page or show an error message
            binding.webview.loadData(
                    "<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>",
                    "text/html",
                    "UTF-8"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
