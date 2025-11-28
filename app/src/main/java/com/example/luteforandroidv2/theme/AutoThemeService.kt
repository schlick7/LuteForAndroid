package com.example.luteforandroidv2.theme

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.*
import okhttp3.*

/**
 * Service that periodically fetches custom styles from the server and applies the auto theme based
 * on the CSS theme colors.
 */
class AutoThemeService(private val context: Context) {

    companion object {
        private const val TAG = "AutoThemeService"
        private const val CUSTOM_STYLES_ENDPOINT = "/theme/custom_styles"
        private const val UPDATE_INTERVAL_MS = 300000L // 5 minutes
    }

    private val autoThemeProvider = AutoThemeProvider(context)
    private val client = OkHttpClient()
    private var updateJob: Job? = null
    private var isRunning = false
    private val globalThemeManager =
            GlobalAutoThemeManager.getInstance(context.applicationContext as Application)

    /** Start the auto theme service */
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Auto theme service already running")
            return
        }

        Log.d(TAG, "Starting auto theme service")
        isRunning = true

        // Start periodic updates
        updateJob =
                CoroutineScope(Dispatchers.IO).launch {
                    while (isActive && isRunning) {
                        try {
                            updateThemeFromServer()
                            delay(UPDATE_INTERVAL_MS)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in auto theme service", e)
                            delay(UPDATE_INTERVAL_MS) // Still delay even on error
                        }
                    }
                }
    }

    /** Stop the auto theme service */
    fun stop() {
        Log.d(TAG, "Stopping auto theme service")
        isRunning = false
        updateJob?.cancel()
        updateJob = null
    }

    /** Force an immediate update from the server */
    fun updateThemeFromServer() {
        try {
            Log.d(TAG, "Updating theme from server")

            // Check if auto theme is enabled
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val themeMode = sharedPref.getString("theme_mode", "App Theme")

            // Only apply auto theme if user has selected "Auto Theme"
            if (themeMode != "Auto Theme") {
                Log.d(TAG, "Auto theme is disabled, skipping theme update")
                return
            }

            // Get the server URL from settings
            val serverSettingsManager =
                    com.example.luteforandroidv2.ui.settings.ServerSettingsManager.getInstance(
                            context
                    )
            if (!serverSettingsManager.isServerUrlConfigured()) {
                Log.d(TAG, "Server URL not configured, skipping theme update")
                return
            }

            val serverUrl = serverSettingsManager.getServerUrl()
            val customStylesUrl = "$serverUrl$CUSTOM_STYLES_ENDPOINT"

            Log.d(TAG, "Fetching custom styles from: $customStylesUrl")

            val request = Request.Builder().url(customStylesUrl).build()

            client.newCall(request)
                    .enqueue(
                            object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    Log.e(TAG, "Failed to fetch custom styles", e)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    response.use {
                                        try {
                                            if (!response.isSuccessful) {
                                                Log.e(
                                                        TAG,
                                                        "Failed to fetch custom styles, response code: ${response.code}"
                                                )
                                                return
                                            }

                                            val cssContent = response.body?.string()
                                            if (cssContent.isNullOrEmpty()) {
                                                Log.d(
                                                        TAG,
                                                        "Custom styles are empty, using default theme"
                                                )
                                                // Clear the saved theme to use defaults
                                                autoThemeProvider.clearSavedTheme()
                                                return
                                            }

                                            Log.d(
                                                    TAG,
                                                    "Received custom styles, length: ${cssContent.length}"
                                            )

                                            // Extract theme colors from CSS
                                            val themeColors =
                                                    autoThemeProvider.extractThemeColorsFromCss(
                                                            cssContent
                                                    )
                                            if (themeColors != null) {
                                                Log.d(
                                                        TAG,
                                                        "Successfully extracted theme colors, applying theme"
                                                )
                                                // Apply the theme on main thread
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    autoThemeProvider.applyTheme(themeColors)
                                                    // Also apply globally
                                                    globalThemeManager.setCurrentThemeColors(
                                                            themeColors
                                                    )
                                                }
                                            } else {
                                                Log.w(
                                                        TAG,
                                                        "Failed to extract theme colors from CSS"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing custom styles response", e)
                                        }
                                    }
                                }
                            }
                    )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating theme from server", e)
        }
    }

    /** Apply the saved theme to the app */
    fun applySavedTheme() {
        try {
            // Check if auto theme is enabled
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val themeMode = sharedPref.getString("theme_mode", "App Theme")

            // Only apply auto theme if user has selected "Auto Theme"
            if (themeMode != "Auto Theme") {
                Log.d(TAG, "Auto theme is disabled, skipping saved theme application")
                return
            }

            val savedTheme = autoThemeProvider.getSavedThemeColors()
            if (savedTheme != null) {
                Log.d(TAG, "Applying saved theme colors")
                autoThemeProvider.applyTheme(savedTheme)
                // Also apply globally
                globalThemeManager.setCurrentThemeColors(savedTheme)
            } else {
                Log.d(TAG, "No saved theme found, using default theme")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying saved theme", e)
        }
    }
}
