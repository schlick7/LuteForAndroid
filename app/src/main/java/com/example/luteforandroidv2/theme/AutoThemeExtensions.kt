package com.example.luteforandroidv2

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.luteforandroidv2.theme.AutoThemeService

/** Extension functions for AppCompatActivity to integrate auto theming */
fun AppCompatActivity.initAutoTheming() {
    try {
        Log.d(
                "AutoThemeExtension",
                "Initializing auto theming for activity: ${this::class.java.simpleName}"
        )

        // Apply any saved theme immediately
        val autoThemeService = AutoThemeService(this)
        autoThemeService.applySavedTheme()
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error initializing auto theming", e)
    }
}

fun AppCompatActivity.updateThemeFromServer() {
    try {
        Log.d(
                "AutoThemeExtension",
                "Updating theme from server for activity: ${this::class.java.simpleName}"
        )

        val autoThemeService = AutoThemeService(this)
        autoThemeService.updateThemeFromServer()
    } catch (e: Exception) {
        Log.e("AutoThemeExtension", "Error updating theme from server", e)
    }
}
