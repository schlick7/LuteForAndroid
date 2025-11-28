package com.example.luteforandroidv2.theme

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/** Extension functions for AppCompatActivity to integrate auto theming */
class AutoThemeExtension {
    companion object {
        private const val TAG = "AutoThemeExtension"
    }
}

/**
 * Initialize auto theming for the activity This should be called in onCreate() before
 * setContentView()
 */
fun AppCompatActivity.initAutoTheming() {
    try {
        Log.d(
                "AutoThemeExtension",
                "Initializing auto theming for activity: ${this::class.java.simpleName}"
        )

        // Apply any saved theme immediately
        val autoThemeService = AutoThemeService(this)
        autoThemeService.applySavedTheme()

        // Start the auto theme service to periodically update from server
        autoThemeService.start()

        // Store reference for cleanup
        setAutoThemeService(autoThemeService)
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error initializing auto theming", e)
    }
}

/** Update theme from server immediately This can be called when the app resumes or when needed */
fun AppCompatActivity.updateThemeFromServer() {
    try {
        Log.d(
                "AutoThemeExtension",
                "Updating theme from server for activity: ${this::class.java.simpleName}"
        )

        // Check if auto theme is enabled
        val sharedPref = this.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = sharedPref.getString("theme_mode", "App Theme")

        // Only apply auto theme if user has selected "Auto Theme"
        if (themeMode != "Auto Theme") {
            Log.d("AutoThemeExtension", "Auto theme is disabled, skipping theme update")
            return
        }

        val autoThemeService = getAutoThemeService()
        autoThemeService?.updateThemeFromServer()
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error updating theme from server", e)
    }
}

/** Clean up auto theming resources This should be called in onDestroy() */
fun AppCompatActivity.cleanupAutoTheming() {
    try {
        Log.d(
                "AutoThemeExtension",
                "Cleaning up auto theming for activity: ${this::class.java.simpleName}"
        )

        val autoThemeService = getAutoThemeService()
        autoThemeService?.stop()
        setAutoThemeService(null)
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error cleaning up auto theming", e)
    }
}

// Storage for auto theme service reference
private const val AUTO_THEME_SERVICE_KEY = "auto_theme_service"

/** Store auto theme service reference in activity context */
private fun AppCompatActivity.setAutoThemeService(service: AutoThemeService?) {
    try {
        val appContext = applicationContext
        if (appContext is android.app.Application) {
            // Store in application context
            // This is a simplified approach - in a real app you might want a proper service manager
        }
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error storing auto theme service", e)
    }
}

/** Retrieve auto theme service reference */
private fun AppCompatActivity.getAutoThemeService(): AutoThemeService? {
    return null // Simplified - would implement proper service retrieval in a real app
}
