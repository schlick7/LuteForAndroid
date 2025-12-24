package com.example.luteforandroidv2

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.luteforandroidv2.databinding.ActivityMainBinding
import com.example.luteforandroidv2.lute.LuteApiClient
import com.example.luteforandroidv2.lute.LuteSettingsService
import com.example.luteforandroidv2.lute.PageDoneRequest
import com.example.luteforandroidv2.theme.initAutoTheming
import com.example.luteforandroidv2.theme.updateThemeFromServer
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MainActivity :
    AppCompatActivity(), com.example.luteforandroidv2.ui.nativeread.NativeReadFragmentListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var wordCountText: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var luteSettingsService: LuteSettingsService
    private val client = OkHttpClient()
    private var wordCountUpdateTimer: android.os.Handler? = null
    private val wordCountUpdateRunnable =
        object : Runnable {
            override fun run() {
                updateWordCount()
                wordCountUpdateTimer?.postDelayed(this, 60000) // Update every minute
            }
        }

    // Cache for word count data
    private var cachedWordCount: Int = -1
    private var cachedLanguage: String = ""
    private var lastUpdateDate: String = ""

    // Cache for book language data to avoid repeated server requests
    private val bookLanguageCache = mutableMapOf<String, BookLanguageCacheEntry>()
    private val CACHE_EXPIRATION_TIME = 5 * 60 * 1000 // 5 minutes cache expiration

    // SharedPreferences for persistent book language cache
    private lateinit var bookLanguagePrefs: SharedPreferences

    // Cache entry class for book language
    private data class BookLanguageCacheEntry(val language: String, val timestamp: Long)

    // Get current date string for caching
    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize auto theming BEFORE any UI operations
        // Check if auto theme is enabled
        val themeSharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = themeSharedPref.getString("theme_mode", "App Theme")

        // Only initialize auto theming if user has selected "Auto Theme"
        if (themeMode == "Auto Theme") {
            initAutoTheming()
        }

        // Initialize the settings service
        val apiClient = LuteApiClient.getInstance(this)
        luteSettingsService = LuteSettingsService(apiClient.apiService)

        // Initialize SharedPreferences for book language cache
        bookLanguagePrefs = getSharedPreferences("book_language_cache", Context.MODE_PRIVATE)

        // Send Android-specific styles to server (once per app launch)
        // This will be handled after serverSettingsManager is defined

        // Initialize word count text view
        wordCountText = binding.appBarMain.toolbar.findViewById(R.id.wordCountText)

        // Set initial visibility based on settings
        updateWordCountVisibility()

        // Initialize shared preferences for language selection
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Set up the toolbar as the support action bar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Check if server URL is configured
        val serverSettingsManager =
            com.example.luteforandroidv2.ui.settings.ServerSettingsManager.getInstance(this)

        // Send Android-specific styles to server (once per app launch)
        // But only if CSS injection is not disabled in settings
        if (!serverSettingsManager.isCssInjectionDisabled()) {
            sendAndroidCustomStyles()
        } else {
            android.util.Log.d(
                "MainActivity",
                "CSS injection is disabled in settings, skipping initial custom styles update"
            )
        }

        // If no URL is configured, navigate to settings and set flag to auto-switch to App Settings
        // tab
        if (!serverSettingsManager.isServerUrlConfigured()) {
            // Set flag to auto-switch to App Settings tab
            val sharedPref = getSharedPreferences("navigation_flags", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("auto_switch_to_app_settings", true)
                apply()
            }

            // Navigate to the settings page
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(R.id.nav_settings)
        } else {
            // Clear the auto-navigation flags
            val sharedPref = getSharedPreferences("navigation_flags", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("auto_switch_to_app_settings", false)
                putBoolean("first_launch_no_url", false)
                apply()
            }

            // Update Lute version info when server is configured
            updateLuteVersionInfo(binding.navView)

            // Check if there's a last opened book and navigate to the appropriate reader based on
            // the default reader setting
            checkAndNavigateToLastBook()
        }

        binding.appBarMain.fab.setOnClickListener { view -> showFABPopupMenu(view) }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Listen for navigation changes to control FAB visibility and word count display
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_read -> {
                    // Show FAB on reader screen
                    binding.appBarMain.fab.visibility = View.VISIBLE
                }

                R.id.nav_native_read -> {
                    // Hide FAB on native reader screen
                    binding.appBarMain.fab.visibility = View.GONE
                }

                R.id.nav_stats -> {
                    // Clear cache and update word count when navigating to stats page
                    // clearWordCountCache()
                    updateWordCount()
                    // Hide FAB on stats screen
                    binding.appBarMain.fab.visibility = View.GONE
                }

                else -> {
                    // Hide FAB on all other screens
                    binding.appBarMain.fab.visibility = View.GONE
                }
            }

            // Set word count visibility based on user settings
            updateWordCountVisibility()

            // Only update word count for screens other than stats and read
            if (destination.id != R.id.nav_stats && destination.id != R.id.nav_read) {
                // Clear cache and update word count
                // clearWordCountCache()
                updateWordCount()
            }
        }

        // Enable back button in toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set up back press callback
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // First, check if we're in fullscreen mode and exit it if so
                    if (isImmersiveModeEnabled()) {
                        exitImmersiveMode()
                        contractWebView()
                        return
                    }

                    // First, try to go back in the WebView history if we're on a read screen
                    val navController = findNavController(R.id.nav_host_fragment_content_main)
                    val currentFragment = navController.currentDestination?.id

                    if (currentFragment == R.id.nav_read ||
                        currentFragment == R.id.nav_native_read
                    ) {
                        // We're on a read screen, try WebView back navigation first
                        val fragment =
                            supportFragmentManager.findFragmentById(
                                R.id.nav_host_fragment_content_main
                            )
                        if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                            val readerFragment = fragment.childFragmentManager.fragments[0]
                            // Handle back navigation for ReadFragment and NativeReadFragment
                            if (readerFragment is
                                        com.example.luteforandroidv2.ui.read.ReadFragment
                            ) {
                                readerFragment.goBackInWebView { canGoBack ->
                                    if (!canGoBack) {
                                        // If WebView can't go back, check if we can navigate
                                        // back in nav controller
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            // If we can't go back anywhere, finish activity
                                            finish()
                                        }
                                    }
                                }
                                return
                            } else if (readerFragment is
                                        com.example.luteforandroidv2.ui.read.ReadFragment
                            ) {
                                readerFragment.goBackInWebView { canGoBack ->
                                    if (!canGoBack) {
                                        // If WebView can't go back, check if we can navigate
                                        // back in nav controller
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            // If we can't go back anywhere, finish activity
                                            finish()
                                        }
                                    }
                                }
                                return
                            } else if (readerFragment is
                                        com.example.luteforandroidv2.ui.nativeread.NativeReadFragment
                            ) {
                                readerFragment.goBackInWebView { canGoBack ->
                                    if (!canGoBack) {
                                        // If we can't go back in the native reader, check if we
                                        // can navigate
                                        // back in nav controller
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            // If we can't go back anywhere, finish activity
                                            finish()
                                        }
                                    } else {
                                        // Navigate to the previous page in the native reader
                                        readerFragment.navigateToPreviousPage()
                                    }
                                }
                                return
                            }
                        }
                    }

                    // For all other screens or if we can't access the WebView, use normal
                    // navigation
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        // If we can't go back in nav controller, finish activity
                        finish()
                    }
                }
            }
        )

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration =
            AppBarConfiguration(
                setOf(
                    R.id.nav_books,
                    R.id.nav_native_books,
                    R.id.nav_read,
                    R.id.nav_native_read,
                    R.id.nav_terms,
                    R.id.nav_settings,
                    R.id.nav_stats,
                    R.id.nav_about
                ),
                drawerLayout
            )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Set up custom navigation for books to respect user preference
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_books -> {
                    // Check user's preference for default books view
                    val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val defaultBooks = sharedPref.getString("default_books", "Native Books")

                    if (defaultBooks == "Native Books") {
                        navController.navigate(R.id.nav_native_books)
                    } else {
                        navController.navigate(R.id.nav_books)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }

                else -> {
                    // Use default navigation for all other items
                    if (navController.currentDestination?.id != menuItem.itemId) {
                        navController.navigate(menuItem.itemId)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
            }
        }
        updateVersionInfo(navView)
    }

    fun updateWordCount() {
        android.util.Log.d("MainActivity", "updateWordCount called")

        val serverSettingsManager = ServerSettingsManager.getInstance(this)
        if (!serverSettingsManager.isServerUrlConfigured()) {
            android.util.Log.d("MainActivity", "Server not configured, showing 0 words")
            wordCountText.text = "0 words"
            return
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        val url = "$serverUrl/stats/"

        android.util.Log.d("MainActivity", "Fetching word count from URL: $url")

        // Get selected language
        val selectedLanguage = sharedPreferences.getString("selected_language", "Auto") ?: "Auto"
        val currentDate = getCurrentDate()

        android.util.Log.d("MainActivity", "Selected language from settings: $selectedLanguage")

        // Handle "Auto" option - detect current book language
        val effectiveLanguage =
            if (selectedLanguage == "Auto") {
                android.util.Log.d(
                    "MainActivity",
                    "Auto selected, detecting current book language"
                )
                val detectedLanguage = detectCurrentBookLanguage()
                android.util.Log.d("MainActivity", "Detected language: '$detectedLanguage'")

                // If we couldn't detect the language, fall back to showing all languages
                if (detectedLanguage.isEmpty()) {
                    android.util.Log.d(
                        "MainActivity",
                        "Could not detect language, falling back to 'All Languages'"
                    )
                    // Try to get a default language from the stats page
                    "All Languages"
                } else {
                    android.util.Log.d(
                        "MainActivity",
                        "Using detected language: $detectedLanguage"
                    )
                    detectedLanguage
                }
            } else {
                android.util.Log.d("MainActivity", "Using selected language: $selectedLanguage")
                selectedLanguage
            }

        android.util.Log.d("MainActivity", "Effective language: $effectiveLanguage")

        // Check if we can use cached data
        if (cachedWordCount != -1 &&
            cachedLanguage == effectiveLanguage &&
            lastUpdateDate == currentDate
        ) {
            // Use cached data
            android.util.Log.d(
                "MainActivity",
                "Using cached word count: $cachedWordCount for language: $effectiveLanguage"
            )
            wordCountText.text = "$cachedWordCount words"
            return
        }

        // Debug: Log the URL and selected language
        android.util.Log.d("MainActivity", "Fetching word count from URL: $url")
        android.util.Log.d(
            "MainActivity",
            "Selected language: $selectedLanguage, Effective language: $effectiveLanguage"
        )
        android.util.Log.d(
            "MainActivity",
            "Last update date: $lastUpdateDate, Current date: $currentDate"
        )

        val request = Request.Builder().url(url).build()

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        android.util.Log.e("MainActivity", "Failed to fetch word count", e)
                        runOnUiThread {
                            // If we have cached data, use it instead of showing 0
                            if (cachedWordCount != -1) {
                                wordCountText.text = "$cachedWordCount words (cached)"
                            } else {
                                wordCountText.text = "0 words"
                            }
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to fetch word count, response code: ${response.code}"
                                )
                                runOnUiThread {
                                    // If we have cached data, use it instead of showing 0
                                    if (cachedWordCount != -1) {
                                        wordCountText.text =
                                            "$cachedWordCount words (cached)"
                                    } else {
                                        wordCountText.text = "0 words"
                                    }
                                }
                                return
                            }

                            val responseData = response.body?.string()
                            android.util.Log.d(
                                "MainActivity",
                                "Received response, length: ${responseData?.length}"
                            )

                            try {
                                // Extract today's word count for the selected language
                                val todayWordCount =
                                    extractTodayWordCount(
                                        responseData ?: "",
                                        effectiveLanguage
                                    )
                                android.util.Log.d(
                                    "MainActivity",
                                    "Extracted word count: $todayWordCount"
                                )

                                // Update cache
                                cachedWordCount = todayWordCount
                                cachedLanguage = effectiveLanguage
                                lastUpdateDate = currentDate

                                runOnUiThread {
                                    wordCountText.text = "$todayWordCount words"
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Error extracting word count",
                                    e
                                )
                                runOnUiThread {
                                    // If we have cached data, use it instead of showing 0
                                    if (cachedWordCount != -1) {
                                        wordCountText.text =
                                            "$cachedWordCount words (cached)"
                                    } else {
                                        wordCountText.text = "0 words"
                                    }
                                }
                            }
                        }
                    }
                }
            )
    }

    private fun extractTodayWordCount(html: String, selectedLanguage: String): Int {
        try {
            android.util.Log.d("MainActivity", "Parsing HTML for language: $selectedLanguage")

            // Parse the HTML using Jsoup
            val document: Document = Jsoup.parse(html)

            // Find the stats table
            val statsTable = document.selectFirst("div.stats-table table")
            if (statsTable == null) {
                android.util.Log.d("MainActivity", "Could not find stats table")
                // Try alternative selector
                val altTable = document.selectFirst("table")
                if (altTable == null) {
                    android.util.Log.d("MainActivity", "Could not find any table")
                    return 0
                }
                return parseTable(altTable!!, selectedLanguage)
            }

            return parseTable(statsTable!!, selectedLanguage)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing HTML", e)
            return 0
        }
    }

    private fun parseTable(table: Element, selectedLanguage: String): Int {
        try {
            // Find the header row to locate column indices
            val headerRow = table.selectFirst("tr")
            if (headerRow == null) {
                android.util.Log.d("MainActivity", "Could not find header row")
                return 0
            }

            val headers = headerRow.select("th")
            android.util.Log.d("MainActivity", "Headers: ${headers.map { it.text() }}")

            var todayIndex = -1
            for (i in headers.indices) {
                val headerText = headers[i].text().trim().lowercase()
                android.util.Log.d("MainActivity", "Header $i: '$headerText'")
                if (headerText == "today") {
                    todayIndex = i
                    break
                }
            }

            if (todayIndex == -1) {
                android.util.Log.d(
                    "MainActivity",
                    "Could not find 'today' column, checking column 1"
                )
                // Try column 1 as fallback
                todayIndex = 1
            }

            android.util.Log.d("MainActivity", "Today column index: $todayIndex")

            // Find the data rows
            val dataRows =
                table.select("tr").filter {
                    it.select("th").isEmpty() && it.select("td").isNotEmpty()
                }

            android.util.Log.d("MainActivity", "Found ${dataRows.size} data rows")

            // Find the row for the selected language
            for (row in dataRows) {
                val cells = row.select("td")
                if (cells.size >= 6) { // Ensure we have enough cells
                    val languageName = cells[0].text().trim()
                    android.util.Log.d("MainActivity", "Found language: '$languageName'")

                    if (languageName.equals(selectedLanguage, ignoreCase = true) &&
                        cells.size > todayIndex
                    ) {
                        val todayValue = cells[todayIndex].text().trim()
                        android.util.Log.d(
                            "MainActivity",
                            "Found today value for $selectedLanguage: '$todayValue'"
                        )
                        return todayValue.toIntOrNull() ?: 0
                    }
                }
            }

            android.util.Log.d(
                "MainActivity",
                "Could not find data for language: $selectedLanguage"
            )
            return 0
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing table", e)
            return 0
        }
    }

    private fun updateVersionInfo(navView: NavigationView) {
        // Get version name from strings.xml
        val versionName = getString(R.string.app_version)
        val versionsItem: MenuItem = navView.menu.findItem(R.id.nav_versions)
        versionsItem.title = versionName
        // Disable click for version items
        versionsItem.isEnabled = false

        // Update Lute version
        updateLuteVersionInfo(navView)
    }

    private fun updateLuteVersionInfo(navView: NavigationView) {
        val serverSettingsManager = ServerSettingsManager.getInstance(this)
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            val url = "$serverUrl/version"

            val request = Request.Builder().url(url).build()

            client.newCall(request)
                .enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            runOnUiThread {
                                val luteVersionItem: MenuItem =
                                    navView.menu.findItem(R.id.nav_lute_version)
                                luteVersionItem.title = "Lute Version: Unknown"
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (!response.isSuccessful) {
                                    runOnUiThread {
                                        val luteVersionItem: MenuItem =
                                            navView.menu.findItem(R.id.nav_lute_version)
                                        luteVersionItem.title = "Lute Version: Unknown"
                                    }
                                    return
                                }

                                val responseData = response.body?.string()
                                if (responseData != null) {
                                    // Parse the HTML to extract the version
                                    val document = Jsoup.parse(responseData)

                                    // Try multiple selectors to find the version element
                                    val versionElement =
                                        document.selectFirst("div.versioninfo")
                                            ?: document.selectFirst(".versioninfo")
                                            ?: document.selectFirst(
                                                "div#version"
                                            )
                                            ?: document.selectFirst(
                                                "#version"
                                            )
                                            ?: document.selectFirst(
                                                "p:contains(Version:)"
                                            )

                                    val versionText =
                                        if (versionElement != null) {
                                            // Extract just the version number from the
                                            // text
                                            val versionPattern =
                                                Regex("Version:?[\\s]*(.+)")
                                            val matchResult =
                                                versionPattern.find(
                                                    versionElement.text().trim()
                                                )
                                            if (matchResult != null) {
                                                "Lute Version: ${matchResult.groupValues[1]}"
                                            } else {
                                                versionElement.text().trim()
                                            }
                                        } else {
                                            // Try to find version in title tag
                                            val titleElement =
                                                document.selectFirst("title")
                                            if (titleElement != null &&
                                                titleElement
                                                    .text()
                                                    .contains(
                                                        "Lute",
                                                        ignoreCase =
                                                            true
                                                    )
                                            ) {
                                                titleElement.text().trim()
                                            } else {
                                                // Try to find any element containing
                                                // "Lute" and version numbers
                                                val bodyText = document.body().text()
                                                val luteVersionPattern =
                                                    Regex(
                                                        "Lute\\s+v?([\\d.]+)",
                                                        RegexOption.IGNORE_CASE
                                                    )
                                                val matchResult =
                                                    luteVersionPattern.find(
                                                        bodyText
                                                    )
                                                if (matchResult != null) {
                                                    "Lute Version: ${matchResult.groupValues[1]}"
                                                } else {
                                                    // Look for the specific pattern in
                                                    // the body text
                                                    val versionPattern =
                                                        Regex(
                                                            "Version:?[\\s]*([\\d.]+)"
                                                        )
                                                    val versionMatch =
                                                        versionPattern.find(
                                                            bodyText
                                                        )
                                                    if (versionMatch != null) {
                                                        "Lute Version: ${versionMatch.groupValues[1]}"
                                                    } else {
                                                        "Lute Version: Unknown"
                                                    }
                                                }
                                            }
                                        }

                                    runOnUiThread {
                                        val luteVersionItem: MenuItem =
                                            navView.menu.findItem(R.id.nav_lute_version)
                                        luteVersionItem.title = versionText
                                    }
                                } else {
                                    runOnUiThread {
                                        val luteVersionItem: MenuItem =
                                            navView.menu.findItem(R.id.nav_lute_version)
                                        luteVersionItem.title = "Lute Version: Unknown"
                                    }
                                }
                            }
                        }
                    }
                )
        } else {
            val luteVersionItem: MenuItem = navView.menu.findItem(R.id.nav_lute_version)
            luteVersionItem.title = "Lute Version: Unconfigured"
        }
    }

    private fun showFABPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.fab_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            // Check if we're in the native reader
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentBackStackEntry?.destination

            if (currentFragment?.id == R.id.nav_native_read) {
                // Handle native reader actions
                handleNativeReaderAction(menuItem.itemId)
            } else {
                // Handle WebView-based reader actions
                handleWebViewReaderAction(menuItem.itemId)
            }
        }

        popup.show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.appBarMain.fab, message, Snackbar.LENGTH_LONG).show()
    }

    private fun handleNativeReaderAction(itemId: Int): Boolean {
        // Get the actual fragment instance
        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
            val readerFragment = fragment.childFragmentManager.fragments[0]
            // Check if it's the NativeReadFragment
            if (readerFragment is com.example.luteforandroidv2.ui.nativeread.NativeReadFragment) {
                when (itemId) {
                    R.id.action_edit -> {
                        readerFragment.onEditCurrentPage()
                        return true
                    }

                    R.id.action_bookmarks_list -> {
                        readerFragment.onListBookmarks()
                        return true
                    }

                    R.id.action_add_bookmark -> {
                        readerFragment.onAddBookmark()
                        return true
                    }

                    R.id.action_translate_sentence -> {
                        readerFragment.onTranslateSentence()
                        return true
                    }

                    R.id.action_translate_page -> {
                        readerFragment.onTranslatePage()
                        return true
                    }

                    R.id.action_text_formatting -> {
                        // Show the popup menu with the 4 specific text formatting options
                        showTextFormattingPopupMenu(binding.appBarMain.fab)
                        return true
                    }

                    R.id.action_create_anki_cards -> {
                        // Get the current text selection and create Anki cards
                        val selectedText = readerFragment.getCurrentTextSelection()
                        if (selectedText != null && selectedText.isNotEmpty()) {
                            readerFragment.onCreateAnkiCardsForSelection(selectedText)
                        } else {
                            // Show a message that no text is selected
                            showSnackbar("No text selected for Anki cards")
                        }
                        return true
                    }

                    R.id.action_create_anki_card_term -> {
                        // Create Anki card for the current selected term
                        readerFragment.onCreateAnkiCardForCurrentTerm()
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun handleWebViewReaderAction(itemId: Int): Boolean {
        when (itemId) {
            R.id.action_edit -> {
                executeJavaScriptInWebView("edit_current_page();")
                return true
            }

            R.id.action_bookmarks_list -> {
                executeJavaScriptInWebView(
                    "window.location.href = '/bookmarks/' + document.querySelector('#book_id').value;"
                )
                return true
            }

            R.id.action_add_bookmark -> {
                executeJavaScriptInWebView("add_bookmark();")
                return true
            }

            R.id.action_translate_sentence -> {
                executeJavaScriptInWebView("handle_translate('sentence-id');")
                return true
            }

            R.id.action_translate_page -> {
                executeJavaScriptInWebView("Lute.util.translatePage()")
                return true
            }

            R.id.action_toggle_highlights -> {
                executeJavaScriptInWebView("toggle_highlight();")
                return true
            }

            R.id.action_toggle_title_progress -> {
                // Toggle title and progress bar visibility
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readerFragment = fragment.childFragmentManager.fragments[0]
                    if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        readerFragment.toggleTitleAndProgressBar()
                        return true
                    }
                }
                return false
            }

            R.id.action_toggle_fullscreen -> {
                // Toggle fullscreen mode
                toggleFullscreenMode()
                return true
            }

            R.id.action_text_formatting -> {
                // Show the text formatting popup in the ReadFragment
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readerFragment = fragment.childFragmentManager.fragments[0]
                    if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        readerFragment.showTextFormattingPopup()
                        return true
                    }
                }
                return false
            }
        }
        return false
    }

    private fun executeJavaScriptInWebView(jsCode: String) {
        try {
            // Get the current fragment from the nav controller
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentBackStackEntry?.destination

            // Check if we're in any of the read fragments
            if (currentFragment?.id == R.id.nav_read || currentFragment?.id == R.id.nav_native_read
            ) {
                // Get the actual fragment instance
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readerFragment = fragment.childFragmentManager.fragments[0]
                    // Execute JavaScript in either ReadFragment or NativeReadFragment
                    if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        // Execute JavaScript in the ReadFragment
                        readerFragment.executeJavaScript(jsCode) { success ->
                            if (!success) {
                                runOnUiThread {
                                    Snackbar.make(
                                        binding.appBarMain.fab,
                                        "Failed to execute command",
                                        Snackbar.LENGTH_LONG
                                    )
                                        .setAction("Action", null)
                                        .show()
                                }
                            }
                        }
                        return
                    } else if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment
                    ) {
                        // Execute JavaScript in the ReadFragment
                        readerFragment.executeJavaScript(jsCode) { success ->
                            if (!success) {
                                runOnUiThread {
                                    Snackbar.make(
                                        binding.appBarMain.fab,
                                        "Failed to execute command",
                                        Snackbar.LENGTH_LONG
                                    )
                                        .setAction("Action", null)
                                        .show()
                                }
                            }
                        }
                    }
                }
            }

            // Show a message that we're not in the right fragment
            Snackbar.make(binding.appBarMain.fab, "Not in reader screen", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show()
        } catch (e: Exception) {
            Snackbar.make(binding.appBarMain.fab, "Error: ${e.message}", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Start periodic word count updates
        startWordCountUpdates()
        // Update word count visibility based on settings
        updateWordCountVisibility()
        // Update theme from server periodically if auto theme is enabled
        val themeSharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = themeSharedPref.getString("theme_mode", "App Theme")

        // Only update theme from server if user has selected "Auto Theme"
        if (themeMode == "Auto Theme") {
            updateThemeFromServer()
        }

        // Update Lute version info
        val navView: NavigationView = binding.navView
        updateLuteVersionInfo(navView)
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic word count updates
        stopWordCountUpdates()
    }

    private fun startWordCountUpdates() {
        wordCountUpdateTimer = android.os.Handler()
        wordCountUpdateRunnable.run()
    }

    private fun stopWordCountUpdates() {
        wordCountUpdateTimer?.removeCallbacks(wordCountUpdateRunnable)
        wordCountUpdateTimer = null
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkAndNavigateToLastBook() {
        val readerSettings = getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        val lastBookId = readerSettings.getString("last_book_id", null)

        if (!lastBookId.isNullOrEmpty()) {
            // Get the default reader setting
            val appSettingsPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val defaultReader = appSettingsPref.getString("default_reader", "Native Reader")

            val navController = findNavController(R.id.nav_host_fragment_content_main)

            // Check the current destination to avoid navigating if already on a reader page
            val currentDestinationId = navController.currentDestination?.id

            // Only navigate to the last book if we're not already on a reader page
            if (currentDestinationId != R.id.nav_read &&
                currentDestinationId != R.id.nav_native_read
            ) {
                // Navigate based on the default reader setting
                if (defaultReader == "Native Reader") {
                    // Navigate to the native reader view with the last book ID
                    val action =
                        com.example.luteforandroidv2.ui.books.BooksFragmentDirections
                            .actionNavBooksToNavNativeRead(lastBookId)
                    navController.navigate(action)
                } else {
                    // Navigate to the webview reader view with the last book ID
                    val action =
                        com.example.luteforandroidv2.ui.books.BooksFragmentDirections
                            .actionNavBooksToNavRead(lastBookId)
                    navController.navigate(action)
                }
            }
        }
    }

    // Method to clear the cache, useful when language is changed
    fun clearWordCountCache() {
        cachedWordCount = -1
        cachedLanguage = ""
        lastUpdateDate = ""
        android.util.Log.d("MainActivity", "Word count cache cleared")
    }

    // Method to clear the book language cache, useful when books are changed
    fun clearBookLanguageCache() {
        bookLanguageCache.clear()
        // Also clear the persistent cache
        with(bookLanguagePrefs.edit()) {
            clear()
            apply()
        }
        android.util.Log.d("MainActivity", "Book language cache cleared")
    }

    // Method to get the language for a specific book ID from the server
    fun fetchBookLanguage(bookId: String, callback: (String?) -> Unit) {
        android.util.Log.d("MainActivity", "fetchBookLanguage called for bookId: $bookId")
        val serverSettingsManager = ServerSettingsManager.getInstance(this)
        if (!serverSettingsManager.isServerUrlConfigured()) {
            android.util.Log.d("MainActivity", "Server URL not configured")
            callback(null)
            return
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        val url = "$serverUrl/read/$bookId"

        android.util.Log.d("MainActivity", "Fetching book read page from URL: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        android.util.Log.e(
                            "MainActivity",
                            "Failed to fetch book read page",
                            e
                        )
                        callback(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to fetch book read page, response code: ${response.code}"
                                )
                                callback(null)
                                return
                            }

                            val responseData = response.body?.string()
                            android.util.Log.d(
                                "MainActivity",
                                "Received book read page response, length: ${responseData?.length}"
                            )

                            try {
                                // Extract the language ID from the book read page
                                val languageId =
                                    extractLanguageIdFromBookPage(responseData ?: "")
                                android.util.Log.d(
                                    "MainActivity",
                                    "Extracted language ID: $languageId"
                                )

                                if (languageId != null) {
                                    // Map language ID to language name
                                    val languageName = getLanguageNameFromId(languageId)
                                    if (languageName != null) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Mapped language ID $languageId to language: $languageName"
                                        )
                                        callback(languageName)
                                    } else {
                                        // Try to fetch the language mappings and try again
                                        fetchLanguageMappings { languageMap ->
                                            val name = languageMap[languageId]
                                            if (name != null) {
                                                // Update cache for future use
                                                languageIdToNameCache.clear()
                                                languageIdToNameCache.putAll(languageMap)
                                                languageCacheTimestamp =
                                                    System.currentTimeMillis()

                                                android.util.Log.d(
                                                    "MainActivity",
                                                    "Fetched and mapped language ID $languageId to language: $name"
                                                )
                                                callback(name)
                                            } else {
                                                android.util.Log.d(
                                                    "MainActivity",
                                                    "Could not map language ID $languageId to a name"
                                                )
                                                callback(null)
                                            }
                                        }
                                    }
                                } else {
                                    android.util.Log.d(
                                        "MainActivity",
                                        "Could not extract language ID from page"
                                    )
                                    callback(null)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Error extracting language ID",
                                    e
                                )
                                callback(null)
                            }
                        }
                    }
                }
            )
    }

    // Method to extract the language ID from the book read page HTML
    private fun extractLanguageIdFromBookPage(html: String): Int? {
        try {
            android.util.Log.d(
                "MainActivity",
                "Parsing HTML to extract language ID, HTML length: ${html.length}"
            )

            // Look for the LANG_ID in the JavaScript
            // Search for pattern like: LookupButton.LANG_ID = 2;
            val langIdPattern = Regex("LookupButton\\.LANG_ID\\s*=\\s*(\\d+)")
            val matchResult = langIdPattern.find(html)
            if (matchResult != null) {
                val langId = matchResult.groupValues[1].toIntOrNull()
                android.util.Log.d("MainActivity", "Found language ID from LookupButton: $langId")
                return langId
            }

            // Try alternative pattern that might be used
            val altPattern = Regex("var\\s+LANG_ID\\s*=\\s*(\\d+)")
            val altMatch = altPattern.find(html)
            if (altMatch != null) {
                val langId = altMatch.groupValues[1].toIntOrNull()
                android.util.Log.d("MainActivity", "Found language ID from var LANG_ID: $langId")
                return langId
            }

            // Try to find it in a script tag or other patterns
            val scriptPattern = Regex("LANG_ID[\\s=]+(\\d+)")
            val scriptMatch = scriptPattern.find(html)
            if (scriptMatch != null) {
                val langId = scriptMatch.groupValues[1].toIntOrNull()
                android.util.Log.d("MainActivity", "Found language ID from script pattern: $langId")
                return langId
            }

            // Additional pattern that might be used in the LUTE_USER_SETTINGS
            val settingsPattern = Regex("\"current_language_id\":\\s*\"(\\d+)\"")
            val settingsMatch = settingsPattern.find(html)
            if (settingsMatch != null) {
                val langId = settingsMatch.groupValues[1].toIntOrNull()
                android.util.Log.d(
                    "MainActivity",
                    "Found language ID from LUTE_USER_SETTINGS: $langId"
                )
                return langId
            }

            android.util.Log.d("MainActivity", "Could not find language ID in HTML")
            return null
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing HTML to extract language ID", e)
            return null
        }
    }

    // Cache for language ID to name mappings
    private val languageIdToNameCache = mutableMapOf<Int, String>()
    private var languageCacheTimestamp: Long = 0
    private val LANGUAGE_CACHE_EXPIRATION = 30 * 60 * 1000 // 30 minutes

    // Method to map language ID to language name by fetching from server
    private fun getLanguageNameFromId(langId: Int?): String? {
        if (langId == null) return null

        // Check if we have a cached mapping
        val currentTime = System.currentTimeMillis()
        if (languageIdToNameCache.containsKey(langId) &&
            (currentTime - languageCacheTimestamp) < LANGUAGE_CACHE_EXPIRATION
        ) {
            android.util.Log.d(
                "MainActivity",
                "Using cached language name for ID $langId: ${languageIdToNameCache[langId]}"
            )
            return languageIdToNameCache[langId]
        }

        // We need to fetch language mappings from the server
        android.util.Log.d("MainActivity", "Language ID $langId needs to be fetched from server")

        // Try to fetch language mappings asynchronously
        fetchLanguageMappings { languageMap ->
            if (languageMap.isNotEmpty()) {
                // Update cache
                languageIdToNameCache.clear()
                languageIdToNameCache.putAll(languageMap)
                languageCacheTimestamp = System.currentTimeMillis()

                android.util.Log.d(
                    "MainActivity",
                    "Updated language mapping cache with ${languageMap.size} entries"
                )
            }
        }

        // Return null for now, but the cache will be populated for future calls
        return null
    }

    // Method to fetch language mappings from the server
    private fun fetchLanguageMappings(callback: (Map<Int, String>) -> Unit) {
        android.util.Log.d("MainActivity", "Fetching language mappings from server")

        val serverSettingsManager = ServerSettingsManager.getInstance(this)
        if (!serverSettingsManager.isServerUrlConfigured()) {
            android.util.Log.d(
                "MainActivity",
                "Server not configured, cannot fetch language mappings"
            )
            callback(emptyMap())
            return
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        val url = "$serverUrl/language/index"

        android.util.Log.d("MainActivity", "Fetching languages from URL: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        android.util.Log.e(
                            "MainActivity",
                            "Failed to fetch language mappings",
                            e
                        )
                        callback(emptyMap())
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to fetch language mappings, response code: ${response.code}"
                                )
                                callback(emptyMap())
                                return
                            }

                            val responseData = response.body?.string()
                            android.util.Log.d(
                                "MainActivity",
                                "Received language mappings response, length: ${responseData?.length}"
                            )

                            try {
                                // Parse the HTML to extract language mappings
                                val languageMap = parseLanguageMappings(responseData ?: "")
                                android.util.Log.d(
                                    "MainActivity",
                                    "Parsed ${languageMap.size} language mappings"
                                )
                                callback(languageMap)
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Error parsing language mappings",
                                    e
                                )
                                callback(emptyMap())
                            }
                        }
                    }
                }
            )
    }

    // Method to parse language mappings from the language index page
    private fun parseLanguageMappings(html: String): Map<Int, String> {
        try {
            android.util.Log.d(
                "MainActivity",
                "Parsing language mappings from HTML, length: ${html.length}"
            )

            // Parse the HTML using Jsoup
            val document = Jsoup.parse(html)

            // Find the language table
            val languageTable = document.selectFirst("table#languagetable")
            if (languageTable == null) {
                android.util.Log.d("MainActivity", "Could not find language table")
                return emptyMap()
            }

            val languageMap = mutableMapOf<Int, String>()

            // Parse rows in the table
            val rows = languageTable.select("tbody tr")
            android.util.Log.d("MainActivity", "Found ${rows.size} rows in language table")

            for (row in rows) {
                val cells = row.select("td")
                android.util.Log.d("MainActivity", "Row has ${cells.size} cells")

                if (cells.size >= 1) {
                    // First cell contains the language name in an anchor tag with href containing
                    // the ID
                    val languageNameElement = cells[0].selectFirst("a")
                    val languageName = languageNameElement?.text()?.trim()
                    android.util.Log.d(
                        "MainActivity",
                        "Language name element: $languageNameElement, text: $languageName"
                    )

                    if (!languageName.isNullOrEmpty()) {
                        // Extract language ID from the href attribute
                        // href format: "/language/edit/3" where 3 is the language ID
                        val href = languageNameElement.attr("href")
                        android.util.Log.d("MainActivity", "Language href: '$href'")

                        // Extract the ID from the href using regex
                        val langIdPattern = Regex("/language/edit/(\\d+)")
                        val matchResult = langIdPattern.find(href)
                        val langIdText =
                            if (matchResult != null) {
                                matchResult.groupValues[1]
                            } else {
                                // Fallback: try to extract any number from the href
                                val numberPattern = Regex("\\d+")
                                val numberMatch = numberPattern.find(href)
                                numberMatch?.value ?: ""
                            }

                        android.util.Log.d("MainActivity", "Language ID text: '$langIdText'")

                        val langId = langIdText.toIntOrNull()
                        if (langId != null) {
                            languageMap[langId] = languageName
                            android.util.Log.d(
                                "MainActivity",
                                "Found language mapping: ID $langId -> $languageName"
                            )
                        } else {
                            android.util.Log.d(
                                "MainActivity",
                                "Could not parse language ID from text: '$langIdText'"
                            )
                        }
                    } else {
                        android.util.Log.d("MainActivity", "Language name is null or empty")
                    }
                } else {
                    android.util.Log.d(
                        "MainActivity",
                        "Row doesn't have enough cells (need 1, has ${cells.size})"
                    )
                }
            }

            android.util.Log.d(
                "MainActivity",
                "Parsed ${languageMap.size} language mappings: $languageMap"
            )
            return languageMap
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing language mappings", e)
            return emptyMap()
        }
    }

    // Method to detect the language of the current book being read
    private fun detectCurrentBookLanguage(): String {
        try {
            android.util.Log.d("MainActivity", "Starting detectCurrentBookLanguage")

            // Get the current fragment from the nav controller
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentDestination?.id

            android.util.Log.d("MainActivity", "Current fragment ID: $currentFragment")

            // Check if we're on the read screen
            if (currentFragment == R.id.nav_read) {
                android.util.Log.d("MainActivity", "We are on the read screen")

                // Get the actual fragment instance
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readFragment = fragment.childFragmentManager.fragments[0]
                    if (readFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        android.util.Log.d("MainActivity", "Found ReadFragment instance")

                        // Try to get the book ID from the ReadFragment
                        val bookId = readFragment.getBookId()
                        android.util.Log.d("MainActivity", "Book ID from ReadFragment: $bookId")

                        if (!bookId.isNullOrEmpty()) {
                            android.util.Log.d("MainActivity", "Found book ID: $bookId")

                            // Check if we have a cached language for this book in memory
                            val cachedEntry = bookLanguageCache[bookId]
                            val currentTime = System.currentTimeMillis()

                            if (cachedEntry != null &&
                                (currentTime - cachedEntry.timestamp) <
                                CACHE_EXPIRATION_TIME
                            ) {
                                android.util.Log.d(
                                    "MainActivity",
                                    "Using memory cached language for book $bookId: ${cachedEntry.language}"
                                )
                                return cachedEntry.language
                            }

                            // Check if we have a cached language for this book in SharedPreferences
                            val cachedLanguage =
                                bookLanguagePrefs.getString("language_$bookId", null)
                            val cachedTimestamp = bookLanguagePrefs.getLong("timestamp_$bookId", 0)

                            if (cachedLanguage != null &&
                                (currentTime - cachedTimestamp) < CACHE_EXPIRATION_TIME
                            ) {
                                android.util.Log.d(
                                    "MainActivity",
                                    "Using persistent cached language for book $bookId: $cachedLanguage"
                                )
                                // Add to memory cache for faster access next time
                                bookLanguageCache[bookId] =
                                    BookLanguageCacheEntry(cachedLanguage, cachedTimestamp)
                                return cachedLanguage
                            }

                            // Try to get the language from the server using the book ID
                            var languageFromServer: String? = null
                            val latch = java.util.concurrent.CountDownLatch(1)

                            fetchBookLanguage(bookId) { language ->
                                android.util.Log.d(
                                    "MainActivity",
                                    "fetchBookLanguage callback received: $language"
                                )
                                languageFromServer = language
                                latch.countDown()
                            }

                            // Wait for the async call to complete (with timeout)
                            try {
                                if (latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                                    android.util.Log.d(
                                        "MainActivity",
                                        "fetchBookLanguage completed with result: $languageFromServer"
                                    )
                                    if (!languageFromServer.isNullOrEmpty()) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Detected book language from server: $languageFromServer"
                                        )

                                        // Cache the language for this book in both memory and
                                        // SharedPreferences
                                        val cacheEntry =
                                            BookLanguageCacheEntry(
                                                languageFromServer!!,
                                                currentTime
                                            )
                                        bookLanguageCache[bookId] = cacheEntry

                                        // Save to SharedPreferences for persistence
                                        with(bookLanguagePrefs.edit()) {
                                            putString("language_$bookId", languageFromServer)
                                            putLong("timestamp_$bookId", currentTime)
                                            apply()
                                        }

                                        return languageFromServer!!
                                    }
                                } else {
                                    android.util.Log.w(
                                        "MainActivity",
                                        "Timeout waiting for language fetch for book $bookId"
                                    )
                                }
                            } catch (e: InterruptedException) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Interrupted while waiting for language fetch",
                                    e
                                )
                            }
                        }

                        // Try to get the book language from the WebView
                        val bookLanguage = readFragment.getBookLanguage()
                        android.util.Log.d(
                            "MainActivity",
                            "Book language from WebView: $bookLanguage"
                        )

                        if (!bookLanguage.isNullOrEmpty()) {
                            android.util.Log.d(
                                "MainActivity",
                                "Detected book language: $bookLanguage"
                            )
                            return bookLanguage
                        }
                    } else {
                        android.util.Log.d("MainActivity", "ReadFragment is not the expected type")
                    }
                } else {
                    android.util.Log.d(
                        "MainActivity",
                        "Could not get fragment or child fragments are empty"
                    )
                }
            } else {
                android.util.Log.d(
                    "MainActivity",
                    "We are NOT on the read screen, current fragment: $currentFragment"
                )

                // We're not in the ReadFragment, try to get the language for the last book ID
                // Get the last book ID from reader settings
                val readerSettings = getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
                val lastBookId = readerSettings.getString("last_book_id", null)

                android.util.Log.d("MainActivity", "Last book ID from reader settings: $lastBookId")

                if (!lastBookId.isNullOrEmpty()) {
                    android.util.Log.d("MainActivity", "Found last book ID: $lastBookId")

                    // Check if we have a cached language for this book in memory
                    val cachedEntry = bookLanguageCache[lastBookId]
                    val currentTime = System.currentTimeMillis()

                    if (cachedEntry != null &&
                        (currentTime - cachedEntry.timestamp) < CACHE_EXPIRATION_TIME
                    ) {
                        android.util.Log.d(
                            "MainActivity",
                            "Using memory cached language for last book $lastBookId: ${cachedEntry.language}"
                        )
                        return cachedEntry.language
                    }

                    // Check if we have a cached language for this book in SharedPreferences
                    val cachedLanguage = bookLanguagePrefs.getString("language_$lastBookId", null)
                    val cachedTimestamp = bookLanguagePrefs.getLong("timestamp_$lastBookId", 0)

                    if (cachedLanguage != null &&
                        (currentTime - cachedTimestamp) < CACHE_EXPIRATION_TIME
                    ) {
                        android.util.Log.d(
                            "MainActivity",
                            "Using persistent cached language for last book $lastBookId: $cachedLanguage"
                        )
                        // Add to memory cache for faster access next time
                        bookLanguageCache[lastBookId] =
                            BookLanguageCacheEntry(cachedLanguage, cachedTimestamp)
                        return cachedLanguage
                    }

                    // Try to get the language from the server using the last book ID
                    var languageFromServer: String? = null
                    val latch = java.util.concurrent.CountDownLatch(1)

                    fetchBookLanguage(lastBookId) { language ->
                        android.util.Log.d(
                            "MainActivity",
                            "fetchBookLanguage callback for last book received: $language"
                        )
                        languageFromServer = language
                        latch.countDown()
                    }

                    // Wait for the async call to complete (with timeout)
                    try {
                        if (latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            android.util.Log.d(
                                "MainActivity",
                                "fetchBookLanguage for last book completed with result: $languageFromServer"
                            )
                            if (!languageFromServer.isNullOrEmpty()) {
                                android.util.Log.d(
                                    "MainActivity",
                                    "Detected book language from server for last book: $languageFromServer"
                                )

                                // Cache the language for this book in both memory and
                                // SharedPreferences
                                val cacheEntry =
                                    BookLanguageCacheEntry(languageFromServer!!, currentTime)
                                bookLanguageCache[lastBookId] = cacheEntry

                                // Save to SharedPreferences for persistence
                                with(bookLanguagePrefs.edit()) {
                                    putString("language_$lastBookId", languageFromServer)
                                    putLong("timestamp_$lastBookId", currentTime)
                                    apply()
                                }

                                return languageFromServer!!
                            }
                        } else {
                            android.util.Log.w(
                                "MainActivity",
                                "Timeout waiting for language fetch for last book $lastBookId"
                            )
                        }
                    } catch (e: InterruptedException) {
                        android.util.Log.e(
                            "MainActivity",
                            "Interrupted while waiting for language fetch",
                            e
                        )
                    }
                }

                // If we couldn't get the language for the last book, try other cache methods
                // First check if we have a cached language for any book in memory
                if (bookLanguageCache.isNotEmpty()) {
                    val currentTime = System.currentTimeMillis()
                    for ((bookId, cachedEntry) in bookLanguageCache) {
                        if ((currentTime - cachedEntry.timestamp) < CACHE_EXPIRATION_TIME) {
                            android.util.Log.d(
                                "MainActivity",
                                "Using memory cached language for book $bookId: ${cachedEntry.language}"
                            )
                            return cachedEntry.language
                        }
                    }
                }

                // Check persistent cache for any book
                val keys = bookLanguagePrefs.all.keys
                val currentTime = System.currentTimeMillis()
                for (key in keys) {
                    if (key.startsWith("language_")) {
                        val bookId = key.substring(9) // Remove "language_" prefix
                        val cachedLanguage = bookLanguagePrefs.getString(key, null)
                        val cachedTimestamp = bookLanguagePrefs.getLong("timestamp_$bookId", 0)

                        if (cachedLanguage != null &&
                            (currentTime - cachedTimestamp) < CACHE_EXPIRATION_TIME
                        ) {
                            android.util.Log.d(
                                "MainActivity",
                                "Using persistent cached language for book $bookId: $cachedLanguage"
                            )
                            // Add to memory cache for faster access next time
                            bookLanguageCache[bookId] =
                                BookLanguageCacheEntry(cachedLanguage, cachedTimestamp)
                            return cachedLanguage
                        }
                    }
                }

                // Try to get the last book language from reader settings
                val lastBookLanguage = readerSettings.getString("last_book_language", null)
                android.util.Log.d(
                    "MainActivity",
                    "Last book language from reader settings: $lastBookLanguage"
                )

                if (!lastBookLanguage.isNullOrEmpty()) {
                    android.util.Log.d(
                        "MainActivity",
                        "Using last book language from reader settings: $lastBookLanguage"
                    )
                    return lastBookLanguage
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error detecting current book language", e)
        }

        // Instead of falling back to a hardcoded language, try to fetch language mappings
        // and then return an appropriate fallback
        android.util.Log.d(
            "MainActivity",
            "Could not detect book language, trying to fetch language mappings"
        )

        // Try to fetch language mappings to see if we can get a better fallback
        fetchLanguageMappings { languageMap ->
            if (languageMap.isNotEmpty()) {
                // Update cache
                languageIdToNameCache.clear()
                languageIdToNameCache.putAll(languageMap)
                languageCacheTimestamp = System.currentTimeMillis()

                android.util.Log.d(
                    "MainActivity",
                    "Updated language mapping cache with ${languageMap.size} entries"
                )
            }
        }

        // Return empty string to indicate no language detected
        return ""
    }

    // Method to refresh the current WebView content
    fun refreshCurrentWebView() {
        try {
            // Get the current fragment from the nav controller
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentBackStackEntry?.destination

            when (currentFragment?.id) {
                R.id.nav_read -> {
                    // Refresh the reader view
                    val fragment =
                        supportFragmentManager.findFragmentById(
                            R.id.nav_host_fragment_content_main
                        )
                    if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                        val readFragment = fragment.childFragmentManager.fragments[0]
                        if (readFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                            // Reload the current page with the new server URL
                            val serverSettingsManager =
                                com.example.luteforandroidv2.ui.settings.ServerSettingsManager
                                    .getInstance(this)
                            if (serverSettingsManager.isServerUrlConfigured()) {
                                val serverUrl = serverSettingsManager.getServerUrl()
                                // We need to get the current book ID to reload the correct page
                                // For now, we'll just trigger a general refresh
                                readFragment.executeJavaScript("location.reload();") { success ->
                                    if (!success) {
                                        android.util.Log.e(
                                            "MainActivity",
                                            "Failed to refresh reader view"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                R.id.nav_books -> {
                    // Refresh the books view
                    val fragment =
                        supportFragmentManager.findFragmentById(
                            R.id.nav_host_fragment_content_main
                        )
                    if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                        val booksFragment = fragment.childFragmentManager.fragments[0]
                        if (booksFragment is com.example.luteforandroidv2.ui.books.BooksFragment) {
                            booksFragment.refreshWebView()
                        }
                    }
                }

                R.id.nav_terms -> {
                    // Refresh the terms view
                    val fragment =
                        supportFragmentManager.findFragmentById(
                            R.id.nav_host_fragment_content_main
                        )
                    if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                        val termsFragment = fragment.childFragmentManager.fragments[0]
                        if (termsFragment is com.example.luteforandroidv2.ui.terms.TermsFragment) {
                            termsFragment.refreshWebView()
                        }
                    }
                }

                R.id.nav_stats -> {
                    // Refresh the stats view
                    val fragment =
                        supportFragmentManager.findFragmentById(
                            R.id.nav_host_fragment_content_main
                        )
                    if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                        val statsFragment = fragment.childFragmentManager.fragments[0]
                        if (statsFragment is com.example.luteforandroidv2.ui.stats.StatsFragment) {
                            statsFragment.refreshData()
                        }
                    }
                }

                R.id.nav_about -> {
                    // Refresh the about view
                    val fragment =
                        supportFragmentManager.findFragmentById(
                            R.id.nav_host_fragment_content_main
                        )
                    if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                        val aboutFragment = fragment.childFragmentManager.fragments[0]
                        if (aboutFragment is com.example.luteforandroidv2.ui.about.AboutFragment) {
                            aboutFragment.refreshWebView()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error refreshing current WebView", e)
        }
    }

    // Method to refresh the Lute version info
    fun refreshLuteVersionInfo() {
        val navView: NavigationView = binding.navView
        updateLuteVersionInfo(navView)
    }

    fun sendAndroidCustomStyles() {
        // Check if CSS injection is disabled in settings
        val serverSettingsManager = ServerSettingsManager.getInstance(this)
        if (serverSettingsManager.isCssInjectionDisabled()) {
            android.util.Log.d("MainActivity", "CSS injection is disabled in settings, skipping custom styles update")
            return
        }

        // Update the server's custom styles setting using minimal CSS
        // This only updates what we need to update (the CSS) and doesn't send any other settings
        CoroutineScope(Dispatchers.Main).launch {
            val success =
                withContext(Dispatchers.IO) { luteSettingsService.updateAndroidCustomStyles() }

            if (success) {
                android.util.Log.d(
                    "MainActivity",
                    "Successfully updated Android custom styles on server"
                )
            } else {
                android.util.Log.e(
                    "MainActivity",
                    "Failed to update Android custom styles on server"
                )
            }
        }
    }

    // Method to update the visibility of the word count text based on settings
    fun updateWordCountVisibility() {
        val sharedPref = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val visibilitySetting = sharedPref.getString("word_count_visibility", "Visible")

        runOnUiThread {
            if (visibilitySetting == "Visible") {
                wordCountText.visibility = View.VISIBLE
            } else {
                wordCountText.visibility = View.GONE
            }
        }
    }

    // NativeReadFragmentListener implementation for Anki features
    override fun onCreateAnkiCardsForSelection(text: String) {
        // Show a message that we're creating Anki cards
        showSnackbar("Creating Anki cards for: $text")

        // Make a network call to create Anki cards
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apiClient = LuteApiClient.getInstance(this@MainActivity)
                val result =
                    withContext(Dispatchers.IO) {
                        apiClient.apiService.createBulkAnkiCards(text)
                    }

                if (result.isSuccessful) {
                    showSnackbar("Anki cards created successfully")
                } else {
                    showSnackbar("Failed to create Anki cards: ${result.code()}")
                }
            } catch (e: Exception) {
                showSnackbar("Error creating Anki cards: ${e.message}")
            }
        }
    }

    override fun onCreateAnkiCardForTerm(termId: String) {
        // Show a message that we're creating an Anki card
        showSnackbar("Creating Anki card for term ID: $termId")

        // Make a network call to create an Anki card
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val apiClient = LuteApiClient.getInstance(this@MainActivity)
                val result =
                    withContext(Dispatchers.IO) { apiClient.apiService.createAnkiCard(termId) }

                if (result.isSuccessful) {
                    showSnackbar("Anki card created successfully")
                } else {
                    showSnackbar("Failed to create Anki card: ${result.code()}")
                }
            } catch (e: Exception) {
                showSnackbar("Error creating Anki card: ${e.message}")
            }
        }
    }

    // NativeReadFragmentListener implementation for dictionary features
    override fun onDictionaryLookup(term: String) {
        // Show a message that we're looking up the term
        showSnackbar("Looking up term: $term")

        // In a full implementation, this would show the dictionary view
        // For now, we'll just show a success message after a delay
        android.os.Handler().postDelayed({ showSnackbar("Dictionary lookup complete") }, 1000)
    }

    override fun onDictionaryTextSelected(text: String) {
        // Show a message with the selected text
        showSnackbar("Selected text: $text")
    }

    override fun onTranslateSentence() {
        // Execute JavaScript to translate the current sentence
        executeJavaScriptInWebView("Lute.util.translateSentence()")
    }

    override fun onTranslatePage() {
        // Execute JavaScript to translate the current page
        executeJavaScriptInWebView("Lute.util.translatePage()")
    }

    override fun onShowTextFormatting() {
        // Execute JavaScript to show text formatting options
        executeJavaScriptInWebView("Lute.util.showTextFormatting()")
    }

    override fun onAddBookmark() {
        // Execute JavaScript to add a bookmark
        executeJavaScriptInWebView("Lute.util.addBookmark()")
    }

    override fun onListBookmarks() {
        // Execute JavaScript to list bookmarks
        executeJavaScriptInWebView("Lute.util.listBookmarks()")
    }

    override fun onEditCurrentPage() {
        // Execute JavaScript to edit the current page
        executeJavaScriptInWebView("Lute.util.editCurrentPage()")
    }

    override fun onDictionaryClosed() {
        // Show a message that the dictionary is closed
        showSnackbar("Dictionary closed")
    }

    override fun onFetchSentenceReaderPageContent(
        bookId: String,
        pageNum: Int,
        callback: (Result<String>) -> Unit
    ) {
        // This method name is misleading now - it's actually for marking the current page as done
        // and navigating to the next page

        // For now, just return a success result - the sentence reader will handle navigation
        callback(Result.success(""))
    }

    override fun onMarkSentenceReaderPageDone(
        bookId: String,
        currentPageNum: Int,
        callback: (Result<Unit>) -> Unit
    ) {
        // This method is used to mark the current page as done and navigate to the next page
        // in the sentence reader mode

        // Use the existing markPageAsRead API which requires a PageDoneRequest body
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val requestBody =
                    PageDoneRequest(
                        bookid = bookId,
                        pagenum = currentPageNum,
                        restknown = false
                    )
                val result =
                    withContext(Dispatchers.IO) {
                        val apiClient = LuteApiClient.getInstance(this@MainActivity)
                        val service = apiClient.apiService
                        service.markPageAsRead(requestBody)
                    }

                if (result.isSuccessful) {
                    callback(Result.success(Unit))
                } else {
                    callback(
                        Result.failure(
                            Exception("Failed to mark page as done: ${result.code()}")
                        )
                    )
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    /** Show a text formatting dialog for WebView-based reader */
    private fun showTextFormattingDialog() {
        try {
            // Inflate the text formatting dialog layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_text_formatting, null)

            // Get UI elements
            val fontSizeSlider =
                dialogView.findViewById<android.widget.SeekBar>(R.id.fontSizeSlider)
            val lineSpacingSlider =
                dialogView.findViewById<android.widget.SeekBar>(R.id.lineSpacingSlider)
            val nightModeToggle =
                dialogView.findViewById<android.widget.Switch>(R.id.nightModeToggle)

            // Set up font size slider
            fontSizeSlider?.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: android.widget.SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            // Update font size in WebView - scale from 50% to 300%
                            val fontSizePercent = 50 + (progress * 2)
                            executeJavaScriptInWebView(
                                "document.body.style.fontSize = '${fontSizePercent}%';"
                            )
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                }
            )

            // Set up line spacing slider
            lineSpacingSlider?.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: android.widget.SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            // Update line spacing in WebView - scale from 1.0 to 3.0
                            val lineSpacing = 1.0f + (progress * 0.02f)
                            executeJavaScriptInWebView(
                                "document.body.style.lineHeight = '$lineSpacing';"
                            )
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                }
            )

            // Set up night mode toggle
            nightModeToggle?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Enable night mode
                    executeJavaScriptInWebView(
                        """
                        document.body.style.backgroundColor = '#000000';
                        document.body.style.color = '#ffffff';
                    """.trimIndent()
                    )
                } else {
                    // Disable night mode
                    executeJavaScriptInWebView(
                        """
                        document.body.style.backgroundColor = '';
                        document.body.style.color = '';
                    """.trimIndent()
                    )
                }
            }

            // Create and show the dialog
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Text Formatting")
                .setView(dialogView)
                .setPositiveButton("Apply") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error showing text formatting dialog", e)
            showSnackbar("Error showing text formatting dialog: ${e.message}")
        }
    }

    /** Show a popup menu with the 4 text editing options */
    fun showTextFormattingPopupMenu(view: android.view.View) {
        val popup = android.widget.PopupMenu(this, view)
        val menu = popup.menu

        // Add the 4 text editing options
        menu.add(0, 1, 0, "Increase Text Size")
        menu.add(0, 2, 0, "Decrease Text Size")
        menu.add(0, 3, 0, "Increase Line Spacing")
        menu.add(0, 4, 0, "Decrease Line Spacing")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    // Increase Text Size
                    executeJavaScriptInWebView("Lute.util.increaseTextSize();")
                    true
                }

                2 -> {
                    // Decrease Text Size
                    executeJavaScriptInWebView("Lute.util.decreaseTextSize();")
                    true
                }

                3 -> {
                    // Increase Line Spacing
                    executeJavaScriptInWebView("Lute.util.increaseLineSpacing();")
                    true
                }

                4 -> {
                    // Decrease Line Spacing
                    executeJavaScriptInWebView("Lute.util.decreaseLineSpacing();")
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    /** Handle text formatting for the native reader */

    /** Toggle fullscreen mode for the app */
    private fun toggleFullscreenMode() {
        val decorView = window.decorView
        val isFullscreen = isImmersiveModeEnabled()

        if (isFullscreen) {
            // Exit fullscreen mode
            exitImmersiveMode()
            // Contract WebView to normal size
            contractWebView()
        } else {
            // Enter fullscreen mode
            enterImmersiveMode()
            // Expand WebView to fill screen
            expandWebView()
        }
    }

    /** Check if immersive mode is currently enabled */
    private fun isImmersiveModeEnabled(): Boolean {
        val decorView = window.decorView
        val uiOptions = decorView.systemUiVisibility
        return (uiOptions and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0
    }

    /** Enter immersive fullscreen mode */
    private fun enterImmersiveMode() {
        val decorView = window.decorView
        decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN)

        // Hide the AppBarLayout
        binding.appBarMain.appBarLayout.visibility = View.GONE

        // Adjust window insets for fullscreen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            // For older versions, we need to adjust the padding manually
            binding.appBarMain.root.setPadding(0, 0, 0, 0)
        }
    }

    /** Exit immersive fullscreen mode */
    private fun exitImmersiveMode() {
        // Reset window insets
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        }

        val decorView = window.decorView
        decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        // Show the AppBarLayout
        binding.appBarMain.appBarLayout.visibility = View.VISIBLE

        // Reset padding if needed
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            // Reset padding to default values if we modified them
            binding.appBarMain.root.setPadding(0, 0, 0, 0)
        }
    }

    /** Expand the WebView to fill the entire screen */
    private fun expandWebView() {
        try {
            // Get the current fragment from the nav controller
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentBackStackEntry?.destination

            // Check if we're in any of the read fragments
            if (currentFragment?.id == R.id.nav_read || currentFragment?.id == R.id.nav_native_read
            ) {
                // Get the actual fragment instance
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readerFragment = fragment.childFragmentManager.fragments[0]
                    // Execute JavaScript in either ReadFragment or NativeReadFragment
                    if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        // Simply notify the web content that we've entered fullscreen mode
                        // The web content can handle its own layout adjustments if needed
                        readerFragment.executeJavaScript(
                            """
                            (function() {
                                // Add a class to the body to indicate fullscreen mode
                                document.body.classList.add('lute-android-fullscreen');

                                // Dispatch a custom event that the web content can listen for
                                if (typeof Event === 'function') {
                                    window.dispatchEvent(new CustomEvent('luteAndroidFullscreenChange', {
                                        detail: { isFullscreen: true }
                                    }));
                                }
                            })();
                            """.trimIndent()
                        ) { success ->
                            if (!success) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to execute expand WebView JavaScript"
                                )
                            }
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error expanding WebView", e)
        }
    }

    /** Contract the WebView back to normal size */
    private fun contractWebView() {
        try {
            // Get the current fragment from the nav controller
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = navController.currentBackStackEntry?.destination

            // Check if we're in any of the read fragments
            if (currentFragment?.id == R.id.nav_read || currentFragment?.id == R.id.nav_native_read
            ) {
                // Get the actual fragment instance
                val fragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (fragment?.childFragmentManager?.fragments?.isNotEmpty() == true) {
                    val readerFragment = fragment.childFragmentManager.fragments[0]
                    // Execute JavaScript in either ReadFragment or NativeReadFragment
                    if (readerFragment is com.example.luteforandroidv2.ui.read.ReadFragment) {
                        // Simply notify the web content that we've exited fullscreen mode
                        readerFragment.executeJavaScript(
                            """
                            (function() {
                                // Remove the fullscreen class from the body
                                document.body.classList.remove('lute-android-fullscreen');

                                // Dispatch a custom event that the web content can listen for
                                if (typeof Event === 'function') {
                                    window.dispatchEvent(new CustomEvent('luteAndroidFullscreenChange', {
                                        detail: { isFullscreen: false }
                                    }));
                                }
                            })();
                            """.trimIndent()
                        ) { success ->
                            if (!success) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to execute contract WebView JavaScript"
                                )
                            }
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error contracting WebView", e)
        }
    }
}
