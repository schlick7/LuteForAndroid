package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTextView

class ThemeManager(private val context: Context) {

    // SharedPreferences for theme settings
    private val themePrefs: SharedPreferences =
            context.getSharedPreferences("native_reader_theme", Context.MODE_PRIVATE)

    /** Apply Native Reader Theme to UI components */
    fun applyNativeReaderTheme(rootContainer: ViewGroup) {
        try {
            // Get the saved Native Reader Theme settings
            val themeMode = themePrefs.getString("native_reader_theme_mode", "App Settings")
            val backgroundColor: String?
            val textColor: String?

            // Determine which colors to use based on theme mode
            if (themeMode == "Custom") {
                backgroundColor = themePrefs.getString("background_color", null)
                textColor = themePrefs.getString("text_color", null)
            } else {
                // App Settings mode - use default values from resources
                backgroundColor = null // We'll set this from resources
                textColor = null // We'll set this from resources
            }

            // Apply background color to the main content container
            // In App Settings mode, use lute_background color from resources
            // In Custom mode, use the saved background color
            val backgroundColorToApply =
                    if (themeMode == "Custom" && backgroundColor != null) {
                        try {
                            android.graphics.Color.parseColor(backgroundColor)
                        } catch (e: IllegalArgumentException) {
                            Log.e(
                                    "ThemeManager",
                                    "Invalid background color format: $backgroundColor",
                                    e
                            )
                            // Fallback to lute_background color from resources
                            ContextCompat.getColor(context, R.color.lute_background)
                        }
                    } else {
                        // App Settings mode - use lute_background color from resources
                        ContextCompat.getColor(context, R.color.lute_background)
                    }

            rootContainer.setBackgroundColor(backgroundColorToApply)

            // Apply text color to all NativeTextViews in the content container
            // In App Settings mode, use lute_on_background color from resources
            // In Custom mode, use the saved text color
            val colorToApply =
                    if (themeMode == "Custom" && textColor != null) {
                        try {
                            android.graphics.Color.parseColor(textColor)
                        } catch (e: IllegalArgumentException) {
                            Log.e("ThemeManager", "Invalid text color format: $textColor", e)
                            // Fallback to lute_on_background color from resources
                            ContextCompat.getColor(context, R.color.lute_on_background)
                        }
                    } else {
                        // App Settings mode - use lute_on_background color from resources
                        ContextCompat.getColor(context, R.color.lute_on_background)
                    }

            updateTextColorInContainer(rootContainer, colorToApply)
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error applying Native Reader Theme", e)
        }
    }

    /** Get the current text color that would be applied */
    fun getCurrentTextColor(): Int {
        val themeMode = themePrefs.getString("native_reader_theme_mode", "App Settings")
        val textColor: String?
        
        if (themeMode == "Custom") {
            textColor = themePrefs.getString("text_color", null)
            return if (textColor != null) {
                try {
                    android.graphics.Color.parseColor(textColor)
                } catch (e: IllegalArgumentException) {
                    Log.e("ThemeManager", "Invalid text color format: $textColor", e)
                    ContextCompat.getColor(context, R.color.lute_on_background)
                }
            } else {
                ContextCompat.getColor(context, R.color.lute_on_background)
            }
        } else {
            // App Settings mode - use lute_on_background color from resources
            return ContextCompat.getColor(context, R.color.lute_on_background)
        }
    }

    /** Update text color in all NativeTextViews within a container */
    private fun updateTextColorInContainer(container: ViewGroup, color: Int) {
        val childCount = container.childCount
        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            when (child) {
                is NativeTextView -> {
                    child.setTextColor(color)
                }
                is ViewGroup -> {
                    updateTextColorInContainer(child, color)
                }
            }
        }
    }
}
