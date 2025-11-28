package com.example.luteforandroidv2.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.luteforandroidv2.R

/**
 * Auto theme manager that extracts colors from the Lute CSS theme and applies them to the Android
 * app's UI elements.
 */
class AutoThemeProvider(private val context: Context) {

    companion object {
        private const val TAG = "AutoThemeProvider"
        private const val PREFS_NAME = "auto_theme_prefs"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_SECONDARY_COLOR = "secondary_color"
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Theme information extracted from CSS */
    data class ThemeColors(
            val backgroundColor: Int,
            val textColor: Int,
            val primaryColor: Int,
            val secondaryColor: Int
    )

    /**
     * Extract theme colors from CSS content This parses the custom CSS to extract color variables
     */
    fun extractThemeColorsFromCss(cssContent: String): ThemeColors? {
        return try {
            Log.d(TAG, "Extracting theme colors from CSS, content length: ${cssContent.length}")

            // Look for CSS variables in the custom styles
            // Pattern: --variable-name: #value;
            val backgroundColor =
                    extractCssVariable(cssContent, "--app-background")
                            ?: extractCssVariable(cssContent, "--background-color")
                                    ?: ContextCompat.getColor(context, R.color.default_background)

            val textColor =
                    extractCssVariable(cssContent, "--app-on-background")
                            ?: extractCssVariable(cssContent, "--font-color")
                                    ?: ContextCompat.getColor(context, R.color.default_text)

            val primaryColor =
                    extractCssVariable(cssContent, "--app-primary")
                            ?: extractCssVariable(cssContent, "--primary-color")
                                    ?: ContextCompat.getColor(context, R.color.default_primary)

            val secondaryColor =
                    extractCssVariable(cssContent, "--app-secondary")
                            ?: extractCssVariable(cssContent, "--secondary-color")
                                    ?: ContextCompat.getColor(context, R.color.default_secondary)

            Log.d(
                    TAG,
                    "Extracted colors - Background: #${
                Integer.toHexString(backgroundColor).substring(2)}, " +
                            "Text: #${Integer.toHexString(textColor).substring(2)}, " +
                            "Primary: #${Integer.toHexString(primaryColor).substring(2)}, " +
                            "Secondary: #${Integer.toHexString(secondaryColor).substring(2)}"
            )

            ThemeColors(backgroundColor, textColor, primaryColor, secondaryColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting theme colors from CSS", e)
            null
        }
    }

    /** Extract a CSS variable value from CSS content */
    private fun extractCssVariable(cssContent: String, variableName: String): Int? {
        try {
            // Look for pattern: --variable-name: #value;
            val regex = Regex("$variableName\\s*:\\s*([^;]+)")
            val matchResult = regex.find(cssContent)

            if (matchResult != null) {
                val value = matchResult.groupValues[1].trim()
                Log.d(TAG, "Found CSS variable $variableName = $value")
                return parseCssColor(value)
            }

            // Also try RGB/A values
            val rgbRegex = Regex("$variableName\\s*:\\s*rgb(?:a)?\\s*\\([^)]+\\)")
            val rgbMatch = rgbRegex.find(cssContent)

            if (rgbMatch != null) {
                val rgbValue = rgbMatch.groupValues[0].trim()
                Log.d(TAG, "Found CSS RGB variable $variableName = $rgbValue")
                return parseRgbColor(rgbValue)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting CSS variable: $variableName", e)
        }

        return null
    }

    /** Parse a CSS color value (#RRGGBB, #RGB, rgb(), rgba()) */
    private fun parseCssColor(colorValue: String): Int? {
        return try {
            when {
                colorValue.startsWith("#") -> {
                    // Hex color
                    parseHexColor(colorValue)
                }
                colorValue.startsWith("rgb") -> {
                    // RGB or RGBA color
                    parseRgbColor(colorValue)
                }
                else -> {
                    // Named color or other format
                    parseNamedColor(colorValue)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSS color: $colorValue", e)
            null
        }
    }

    /** Parse hex color (#RRGGBB or #RGB) */
    private fun parseHexColor(hexValue: String): Int? {
        return try {
            val cleanHex = hexValue.replace("#", "").trim()

            when (cleanHex.length) {
                3 -> {
                    // #RGB format - expand to #RRGGBB
                    val r = cleanHex[0].toString().repeat(2)
                    val g = cleanHex[1].toString().repeat(2)
                    val b = cleanHex[2].toString().repeat(2)
                    Color.parseColor("#$r$g$b")
                }
                6 -> {
                    // #RRGGBB format
                    Color.parseColor("#$cleanHex")
                }
                8 -> {
                    // #AARRGGBB format - ignore alpha for now
                    Color.parseColor("#${cleanHex.substring(2)}")
                }
                else -> {
                    Log.w(TAG, "Unsupported hex color format: $hexValue")
                    null
                }
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid hex color: $hexValue", e)
            null
        }
    }

    /** Parse RGB/RGBA color */
    private fun parseRgbColor(rgbValue: String): Int? {
        return try {
            // Pattern: rgb(255, 255, 255) or rgba(255, 255, 255, 1.0)
            val regex = Regex("rgba?\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)")
            val matchResult = regex.find(rgbValue)

            if (matchResult != null) {
                val r = matchResult.groupValues[1].toInt()
                val g = matchResult.groupValues[2].toInt()
                val b = matchResult.groupValues[3].toInt()

                Color.rgb(r, g, b)
            } else {
                Log.w(TAG, "Could not parse RGB color: $rgbValue")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RGB color: $rgbValue", e)
            null
        }
    }

    /** Parse named CSS colors */
    private fun parseNamedColor(namedColor: String): Int? {
        return when (namedColor.lowercase().trim()) {
            "black" -> Color.BLACK
            "white" -> Color.WHITE
            "red" -> Color.RED
            "green" -> Color.GREEN
            "blue" -> Color.BLUE
            "yellow" -> Color.YELLOW
            "cyan" -> Color.CYAN
            "magenta" -> Color.MAGENTA
            "gray", "grey" -> Color.GRAY
            "darkgray", "darkgrey" -> Color.DKGRAY
            "lightgray", "lightgrey" -> Color.LTGRAY
            else -> {
                Log.w(TAG, "Unknown named color: $namedColor")
                null
            }
        }
    }

    /** Apply theme colors to the app */
    fun applyTheme(themeColors: ThemeColors) {
        try {
            Log.d(TAG, "Applying auto theme colors")

            // Save the colors to preferences for persistence
            with(prefs.edit()) {
                putInt(KEY_BACKGROUND_COLOR, themeColors.backgroundColor)
                putInt(KEY_TEXT_COLOR, themeColors.textColor)
                putInt(KEY_PRIMARY_COLOR, themeColors.primaryColor)
                putInt(KEY_SECONDARY_COLOR, themeColors.secondaryColor)
                apply()
            }

            // Actually apply colors to app UI elements
            val themeApplier = AutoThemeApplier(context)

            // If we're in an activity context, apply to the current activity
            if (context is AppCompatActivity) {
                themeApplier.applyThemeToActivity(context, themeColors)
            }

            Log.d(TAG, "Successfully applied auto theme colors")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme", e)
        }
    }

    /** Get saved theme colors */
    fun getSavedThemeColors(): ThemeColors? {
        return try {
            if (!prefs.contains(KEY_BACKGROUND_COLOR)) {
                return null
            }

            val backgroundColor =
                    prefs.getInt(
                            KEY_BACKGROUND_COLOR,
                            ContextCompat.getColor(context, R.color.default_background)
                    )
            val textColor =
                    prefs.getInt(
                            KEY_TEXT_COLOR,
                            ContextCompat.getColor(context, R.color.default_text)
                    )
            val primaryColor =
                    prefs.getInt(
                            KEY_PRIMARY_COLOR,
                            ContextCompat.getColor(context, R.color.default_primary)
                    )
            val secondaryColor =
                    prefs.getInt(
                            KEY_SECONDARY_COLOR,
                            ContextCompat.getColor(context, R.color.default_secondary)
                    )

            ThemeColors(backgroundColor, textColor, primaryColor, secondaryColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting saved theme colors", e)
            null
        }
    }

    /** Clear saved theme */
    fun clearSavedTheme() {
        prefs.edit().clear().apply()
    }
}
