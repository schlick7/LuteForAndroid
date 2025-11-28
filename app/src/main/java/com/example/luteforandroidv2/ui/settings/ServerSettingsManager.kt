package com.example.luteforandroidv2.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ServerSettingsManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
    private val appContext: Context = context.applicationContext

    fun saveServerUrl(url: String) {
        sharedPreferences.edit()
            .putString(SERVER_URL_KEY, url)
            .apply()
    }

    fun getServerUrl(): String {
        // For release version, don't use a default server URL
        return sharedPreferences.getString(SERVER_URL_KEY, "") ?: ""
    }

    fun isServerUrlConfigured(): Boolean {
        val url = sharedPreferences.getString(SERVER_URL_KEY, "") ?: ""
        return url.isNotEmpty()
    }

    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /**
     * Inject Android app specific CSS into the Lute server's custom styles.
     * This should be called once during app launch.
     */
    fun injectAndroidAppCss() {
        // Check if CSS injection is disabled in settings
        if (isCssInjectionDisabled()) {
            Log.d("ServerSettingsManager", "CSS injection is disabled in settings, skipping injection")
            return
        }

        Thread {
            try {
                // Read CSS from assets
                val cssContent = readAssetFile("css/android-app-styles.css")
                Log.d("ServerSettingsManager", "Read CSS file, length: ${cssContent.length}")

                // URL encode the CSS content
                val encodedCss = URLEncoder.encode(cssContent, "UTF-8")
                Log.d("ServerSettingsManager", "Encoded CSS, length: ${encodedCss.length}")

                // Send CSS to server
                val serverUrl = getServerUrl()
                if (serverUrl.isNotEmpty()) {
                    val url = URL("$serverUrl/settings/set/custom_styles/$encodedCss")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    Log.d("ServerSettingsManager", "CSS injection response code: $responseCode")

                    if (responseCode == 200) {
                        Log.d("ServerSettingsManager", "Successfully injected Android app CSS")
                    } else {
                        Log.e("ServerSettingsManager", "Failed to inject CSS, response code: $responseCode")
                    }

                    connection.disconnect()
                } else {
                    Log.w("ServerSettingsManager", "Server URL not configured, skipping CSS injection")
                }
            } catch (e: Exception) {
                Log.e("ServerSettingsManager", "Error injecting Android app CSS", e)
            }
        }.start()
    }

    /**
     * Clear any existing custom CSS on the server for testing purposes.
     */
    fun clearServerCss() {
        Thread {
            try {
                val serverUrl = getServerUrl()
                if (serverUrl.isNotEmpty()) {
                    val url = URL("$serverUrl/settings/set/custom_styles/")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    Log.d("ServerSettingsManager", "CSS clear response code: $responseCode")

                    if (responseCode == 200) {
                        Log.d("ServerSettingsManager", "Successfully cleared server CSS")
                    } else {
                        Log.e("ServerSettingsManager", "Failed to clear CSS, response code: $responseCode")
                    }

                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e("ServerSettingsManager", "Error clearing server CSS", e)
            }
        }.start()
    }

    /**
     * Read a file from the app's assets folder.
     */
    private fun readAssetFile(fileName: String): String {
        val inputStream = appContext.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            stringBuilder.append(line).append("\n")
            line = reader.readLine()
        }
        reader.close()
        inputStream.close()
        return stringBuilder.toString()
    }

    /**
     * Check if CSS injection is disabled in app settings.
     */
    fun isCssInjectionDisabled(): Boolean {
        val sharedPref = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPref.getBoolean("disable_css_injection", false)
    }

    companion object {
        private const val SERVER_URL_KEY = "server_url"
        const val PLACEHOLDER_URL = "http://your-server-ip:5001"

        @Volatile
        private var INSTANCE: ServerSettingsManager? = null

        fun getInstance(context: Context): ServerSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerSettingsManager(context).also { INSTANCE = it }
            }
        }
    }
}
