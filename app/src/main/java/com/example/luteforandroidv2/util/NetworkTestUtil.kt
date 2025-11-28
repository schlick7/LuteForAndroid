package com.example.luteforandroidv2.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Utility class for testing network connectivity and server accessibility. */
class NetworkTestUtil private constructor() {

    companion object {
        private const val TAG = "NetworkTestUtil"
        private const val TIMEOUT_MS = 5000 // 5 seconds timeout

        @Volatile private var INSTANCE: NetworkTestUtil? = null

        fun getInstance(): NetworkTestUtil {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: NetworkTestUtil().also { INSTANCE = it } }
        }
    }

    /** Test if the device has basic network connectivity (WiFi/mobile data). */
    fun hasNetworkConnectivity(context: Context): Boolean {
        return try {
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION") val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            false
        }
    }

    /**
     * Test if a specific server URL is accessible. This performs an actual HTTP request to test
     * server accessibility.
     */
    fun isServerAccessible(serverUrl: String, callback: (Boolean) -> Unit) {
        Executors.newSingleThreadExecutor().execute {
            try {
                // Only test the base URL, not specific endpoints
                val baseUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                val url = URL(baseUrl)

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD" // HEAD request is lighter than GET
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS

                val responseCode = connection.responseCode
                connection.disconnect()

                // Consider 2xx and 3xx response codes as successful
                val isAccessible = responseCode in 200..399
                Log.d(
                        TAG,
                        "Server test for $serverUrl returned response code: $responseCode, accessible: $isAccessible"
                )

                callback(isAccessible)
            } catch (e: Exception) {
                Log.e(TAG, "Error testing server accessibility for URL: $serverUrl", e)
                callback(false)
            }
        }
    }

    /**
     * Test if a specific server URL is accessible synchronously. This should only be used in
     * background threads.
     */
    fun isServerAccessibleSync(serverUrl: String): Boolean {
        return try {
            // Only test the base URL, not specific endpoints
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
            val url = URL(baseUrl)

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD" // HEAD request is lighter than GET
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            val responseCode = connection.responseCode
            connection.disconnect()

            // Consider 2xx and 3xx response codes as successful
            val isAccessible = responseCode in 200..399
            Log.d(
                    TAG,
                    "Server test for $serverUrl returned response code: $responseCode, accessible: $isAccessible"
            )

            isAccessible
        } catch (e: Exception) {
            Log.e(TAG, "Error testing server accessibility for URL: $serverUrl", e)
            false
        }
    }
}
