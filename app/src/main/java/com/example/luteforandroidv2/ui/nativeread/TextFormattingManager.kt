package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.DialogEnhancedTextFormattingBinding
import com.example.luteforandroidv2.databinding.DialogFontSelectionBinding
import com.example.luteforandroidv2.ui.nativeread.Term.NativeTextView

class TextFormattingManager(private val context: Context) {

    // SharedPreferences for text formatting settings
    private val textFormattingPrefs: SharedPreferences =
            context.getSharedPreferences("text_formatting", Context.MODE_PRIVATE)

    // Callback for status terms font size changes
    private var statusTermsFontSizeChangeListener: (() -> Unit)? = null

    // Current text formatting settings
    var currentFontSize: Int = 35 // Default progress value for seekbar
    var currentLineSpacing: Int = 45 // Default progress value for seekbar (1.25 multiplier)
    var currentFontName: String = "Default"
    var currentStatusTermsFontSize: Int = 14 // Default font size for status terms

    init {
        // Load saved formatting settings
        currentFontSize = textFormattingPrefs.getInt("font_size", 35)
        currentLineSpacing = textFormattingPrefs.getInt("line_spacing", 45)
        currentFontName = textFormattingPrefs.getString("font_name", "Default") ?: "Default"
        currentStatusTermsFontSize = textFormattingPrefs.getInt("status_terms_font_size", 14)
    }

    /** Set a listener for status terms font size changes */
    fun setStatusTermsFontSizeChangeListener(listener: () -> Unit) {
        statusTermsFontSizeChangeListener = listener
    }

    fun saveTextFormattingSettings() {
        with(textFormattingPrefs.edit()) {
            putInt("font_size", currentFontSize)
            putInt("line_spacing", currentLineSpacing)
            putString("font_name", currentFontName)
            putInt("status_terms_font_size", currentStatusTermsFontSize)
            apply()
        }
    }

    /** Apply the selected font to the text content */
    fun applySelectedFont(
            fontName: String,
            container: ViewGroup,
            customTypeface: Typeface? = null
    ): Typeface? {
        Log.d("TextFormattingManager", "applySelectedFont called with font: $fontName")

        // Handle special case for "Default" font
        val actualFontName = if (fontName == "Default") null else fontName

        // Handle custom fonts with variants
        val typeface =
                customTypeface
                        ?: when {
                            fontName == "Atkinson Hyperlegible Bold" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-Bold.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Atkinson Hyperlegible Bold font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Atkinson Hyperlegible Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-RegularItalic.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Atkinson Hyperlegible Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Atkinson Hyperlegible Bold Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-BoldItalic.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Atkinson Hyperlegible Bold Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Atkinson Hyperlegible" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-Regular.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Atkinson Hyperlegible font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Linux Biolinum Bold" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/linux-biolinum/LinBiolinum_RB.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Linux Biolinum Bold font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Linux Biolinum Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/linux-biolinum/LinBiolinum_RI.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Linux Biolinum Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Linux Biolinum" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/linux-biolinum/LinBiolinum_R.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Linux Biolinum font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Literata Bold" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/literata/Literata-Bold.ttf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Literata Bold font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Literata Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/literata/Literata-Italic.ttf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Literata Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Literata Bold Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/literata/Literata-BoldItalic.ttf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Literata Bold Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Literata" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/literata/Literata-Regular.ttf"
                                    )
                                } catch (e: Exception) {
                                    Log.e("TextFormattingManager", "Error loading Literata font", e)
                                    null
                                }
                            }
                            fontName == "Vollkorn Bold" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Vollkorn PS-OTF/Vollkorn-Bold.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Vollkorn Bold font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Vollkorn Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Vollkorn PS-OTF/Vollkorn-Italic.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Vollkorn Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Vollkorn Bold Italic" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Vollkorn PS-OTF/Vollkorn-BoldItalic.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                            "TextFormattingManager",
                                            "Error loading Vollkorn Bold Italic font",
                                            e
                                    )
                                    null
                                }
                            }
                            fontName == "Vollkorn" -> {
                                try {
                                    Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/Vollkorn PS-OTF/Vollkorn-Regular.otf"
                                    )
                                } catch (e: Exception) {
                                    Log.e("TextFormattingManager", "Error loading Vollkorn font", e)
                                    null
                                }
                            }
                            else -> null
                        }

        // Apply font to all NativeTextViews in the content container
        applyFontToContainer(container, actualFontName, typeface)
        return typeface
    }

    /** Apply font to all NativeTextViews within a container */
    private fun applyFontToContainer(
            container: ViewGroup,
            fontName: String?,
            customTypeface: Typeface? = null
    ) {
        Log.d("TextFormattingManager", "applyFontToContainer called with font: $fontName")
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")
        val childCount = container.childCount
        Log.d("TextFormattingManager", "Container has $childCount children")
        var textViewCount = 0
        var groupCount = 0

        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            Log.d("TextFormattingManager", "Child #$i: ${child.javaClass.simpleName}")

            when (child) {
                is NativeTextView -> {
                    textViewCount++
                    Log.d(
                            "TextFormattingManager",
                            "Applying font '$fontName' to NativeTextView #$textViewCount"
                    )
                    Log.d("TextFormattingManager", "NativeTextView text: '${child.text}'")
                    Log.d(
                            "TextFormattingManager",
                            "NativeTextView current typeface: ${child.typeface}"
                    )

                    // Apply the selected font
                    if (customTypeface != null) {
                        child.setFontFamily(fontName, customTypeface)
                    } else {
                        child.setFontFamily(fontName)
                    }

                    // Force a layout update to ensure the font is applied
                    child.requestLayout()
                    // Force a redraw to ensure the text is rendered with the new font
                    child.invalidate()

                    // Also trigger a refresh of the text spans to ensure visual update
                    refreshTextSpans(child)

                    // Log the result
                    Log.d("TextFormattingManager", "NativeTextView new typeface: ${child.typeface}")
                }
                is ViewGroup -> {
                    groupCount++
                    Log.d("TextFormattingManager", "Recursing into ViewGroup #$groupCount")
                    applyFontToContainer(child, fontName, customTypeface)
                }
                else -> {
                    Log.d(
                            "TextFormattingManager",
                            "Child #$i is neither NativeTextView nor ViewGroup: ${child.javaClass.simpleName}"
                    )
                }
            }
        }
        Log.d(
                "TextFormattingManager",
                "Applied font to $textViewCount NativeTextViews and processed $groupCount ViewGroups"
        )
    }

    /** Update the font size of the text content */
    fun updateFontSize(progress: Int, container: ViewGroup) {
        Log.d("TextFormattingManager", "updateFontSize called with progress: $progress")
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")

        // Convert progress to font size (e.g., 10-30sp)
        val fontSize = 10f + (progress * 0.2f)
        Log.d("TextFormattingManager", "Updating font size to: $fontSize (progress: $progress)")

        // Update font size in all text views
        updateTextSizeInContainer(container, fontSize)
        Log.d("TextFormattingManager", "updateFontSize completed")
    }

    /** Update the line spacing of the text content */
    fun updateLineSpacing(progress: Int, container: ViewGroup) {
        Log.d("TextFormattingManager", "updateLineSpacing called with progress: $progress")
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")

        // Convert progress to line spacing multiplier (0.5-2.0)
        // This provides a more intuitive range where 1.0 is single spacing, 1.5 is 1.5 line
        // spacing, 2.0 is double spacing
        val lineSpacingMultiplier = 0.5f + (progress * 0.015f) // This gives us 0.5 to 2.0 range
        Log.d(
                "TextFormattingManager",
                "Updating line spacing multiplier to: $lineSpacingMultiplier (progress: $progress)"
        )

        // Update line spacing in all text views
        updateLineSpacingInContainer(container, lineSpacingMultiplier)
        Log.d("TextFormattingManager", "updateLineSpacing completed")
    }

    /** Update the font size of the status terms */
    fun updateStatusTermsFontSize(progress: Int) {
        Log.d("TextFormattingManager", "updateStatusTermsFontSize called with progress: $progress")
        // Convert progress to font size (e.g., 8-24sp)
        val fontSize = 8f + (progress * 0.2f)
        Log.d(
                "TextFormattingManager",
                "Updating status terms font size to: $fontSize (progress: $progress)"
        )

        // Update the current status terms font size
        currentStatusTermsFontSize = progress

        // Notify listener if set
        statusTermsFontSizeChangeListener?.invoke()

        // Save the setting immediately
        saveTextFormattingSettings()
        Log.d(
                "TextFormattingManager",
                "Updated status terms font size to: $fontSize (progress: $progress)"
        )
    }

    /** Helper method to update text size in all text views within a container */
    private fun updateTextSizeInContainer(container: ViewGroup, fontSize: Float) {
        Log.d("TextFormattingManager", "updateTextSizeInContainer called with fontSize: $fontSize")
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")

        val childCount = container.childCount
        Log.d("TextFormattingManager", "Container has $childCount children")
        var textViewCount = 0
        var groupCount = 0

        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            Log.d("TextFormattingManager", "Processing child #$i: ${child.javaClass.simpleName}")

            when (child) {
                is NativeTextView -> {
                    textViewCount++
                    Log.d(
                            "TextFormattingManager",
                            "Updating font size for NativeTextView #$textViewCount to: $fontSize"
                    )
                    Log.d(
                            "TextFormattingManager",
                            "NativeTextView text: '${child.text.take(50)}...'"
                    )
                    child.textSize = fontSize
                    // Force a layout update to ensure the font size is applied
                    child.requestLayout()
                    // Force a redraw to ensure the text is rendered with the new size
                    child.invalidate()
                    // Also trigger a refresh of the text spans to ensure visual update
                    refreshTextSpans(child)
                    Log.d(
                            "TextFormattingManager",
                            "Font size updated for NativeTextView #$textViewCount"
                    )
                }
                is ViewGroup -> {
                    groupCount++
                    Log.d(
                            "TextFormattingManager",
                            "Recursing into ViewGroup #$groupCount: ${child.javaClass.simpleName}"
                    )
                    updateTextSizeInContainer(child, fontSize)
                }
                else -> {
                    Log.d(
                            "TextFormattingManager",
                            "Skipping non-NativeTextView/non-ViewGroup child: ${child.javaClass.simpleName}"
                    )
                }
            }
        }
        Log.d(
                "TextFormattingManager",
                "Processed $textViewCount NativeTextViews and $groupCount ViewGroups"
        )
    }

    /** Helper method to update line spacing in all text views within a container */
    private fun updateLineSpacingInContainer(container: ViewGroup, lineSpacingMultiplier: Float) {
        Log.d(
                "TextFormattingManager",
                "updateLineSpacingInContainer called with multiplier: $lineSpacingMultiplier"
        )
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")

        val childCount = container.childCount
        Log.d("TextFormattingManager", "Container has $childCount children")
        var textViewCount = 0
        var groupCount = 0

        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            Log.d("TextFormattingManager", "Processing child #$i: ${child.javaClass.simpleName}")

            when (child) {
                is NativeTextView -> {
                    textViewCount++
                    Log.d(
                            "TextFormattingManager",
                            "Updating line spacing for NativeTextView #$textViewCount to multiplier: $lineSpacingMultiplier"
                    )
                    Log.d(
                            "TextFormattingManager",
                            "NativeTextView text: '${child.text.take(50)}...'"
                    )
                    // Set line spacing directly using the multiplier
                    // Add value is 0f to only use the multiplier
                    // Ensure the line spacing is applied consistently for all line breaks
                    child.setLineSpacing(0f, lineSpacingMultiplier)
                    // Force a layout update to ensure the line spacing is applied
                    child.requestLayout()
                    // Force a redraw to ensure the text is rendered with the new line spacing
                    child.invalidate()
                    // Also trigger a refresh of the text spans to ensure visual update
                    refreshTextSpans(child)
                    Log.d(
                            "TextFormattingManager",
                            "Line spacing updated for NativeTextView #$textViewCount"
                    )
                }
                is ViewGroup -> {
                    groupCount++
                    Log.d(
                            "TextFormattingManager",
                            "Recursing into ViewGroup #$groupCount: ${child.javaClass.simpleName}"
                    )
                    updateLineSpacingInContainer(child, lineSpacingMultiplier)
                }
                else -> {
                    Log.d(
                            "TextFormattingManager",
                            "Skipping non-NativeTextView/non-ViewGroup child: ${child.javaClass.simpleName}"
                    )
                }
            }
        }
        Log.d(
                "TextFormattingManager",
                "Processed $textViewCount NativeTextViews and $groupCount ViewGroups"
        )
    }

    /** Update text color in all NativeTextViews within a container */
    fun updateTextColorInContainer(container: ViewGroup, color: Int) {
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

    // Font family and variant selection methods

    /** Data class representing a font family with its available variants */
    data class FontFamily(val name: String, val variants: Map<String, String>)

    /** Get a list of available font families with their variants */
    fun getFontFamilies(): List<FontFamily> {
        return listOf(
                FontFamily("Default", mapOf("Regular" to "")),
                FontFamily(
                        "Atkinson Hyperlegible",
                        mapOf(
                                "Regular" to
                                        "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-Regular.otf",
                                "Bold" to
                                        "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-Bold.otf",
                                "Italic" to
                                        "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-RegularItalic.otf",
                                "Bold Italic" to
                                        "fonts/Atkinson Hyperlegible Next/AtkinsonHyperlegibleNext-BoldItalic.otf"
                        )
                ),
                FontFamily(
                        "Linux Biolinum",
                        mapOf(
                                "Regular" to "fonts/linux-biolinum/LinBiolinum_R.otf",
                                "Bold" to "fonts/linux-biolinum/LinBiolinum_RB.otf",
                                "Italic" to "fonts/linux-biolinum/LinBiolinum_RI.otf"
                        )
                ),
                FontFamily(
                        "Literata",
                        mapOf(
                                "Regular" to "fonts/literata/Literata-Regular.ttf",
                                "Bold" to "fonts/literata/Literata-Bold.ttf",
                                "Italic" to "fonts/literata/Literata-Italic.ttf",
                                "Bold Italic" to "fonts/literata/Literata-BoldItalic.ttf"
                        )
                ),
                FontFamily(
                        "Vollkorn",
                        mapOf(
                                "Regular" to "fonts/Vollkorn PS-OTF/Vollkorn-Regular.otf",
                                "Bold" to "fonts/Vollkorn PS-OTF/Vollkorn-Bold.otf",
                                "Italic" to "fonts/Vollkorn PS-OTF/Vollkorn-Italic.otf",
                                "Bold Italic" to "fonts/Vollkorn PS-OTF/Vollkorn-BoldItalic.otf"
                        )
                )
        )
    }

    /** Get the current font family name from the full font name */
    fun getCurrentFontFamilyName(): String {
        // For flattened font list, we need to extract the family name from the full name
        // Special handling for multi-word family names like "Atkinson Hyperlegible"
        return when {
            currentFontName.startsWith("Atkinson Hyperlegible ") ||
                    currentFontName == "Atkinson Hyperlegible" -> "Atkinson Hyperlegible"
            currentFontName.startsWith("Linux Biolinum ") || currentFontName == "Linux Biolinum" ->
                    "Linux Biolinum"
            currentFontName.startsWith("Literata ") || currentFontName == "Literata" -> "Literata"
            currentFontName.startsWith("Vollkorn ") || currentFontName == "Vollkorn" -> "Vollkorn"
            else -> currentFontName // Default or single word font names
        }
    }

    /** Get the current font variant name from the full font name */
    fun getCurrentFontVariantName(): String {
        // For flattened font list, we need to extract the variant name from the full name
        return when {
            currentFontName.startsWith("Atkinson Hyperlegible ") ->
                    currentFontName.substringAfter(" ")
            currentFontName.startsWith("Linux Biolinum ") -> currentFontName.substringAfter(" ")
            currentFontName.startsWith("Literata ") -> currentFontName.substringAfter(" ")
            currentFontName.startsWith("Vollkorn ") -> currentFontName.substringAfter(" ")
            currentFontName == "Atkinson Hyperlegible" ||
                    currentFontName == "Linux Biolinum" ||
                    currentFontName == "Literata" ||
                    currentFontName == "Vollkorn" -> "Regular"
            else -> "Regular" // Default variant
        }
    }

    fun showEnhancedTextFormattingDialogWithPersistence(
            container: ViewGroup,
            isSentenceReader: Boolean = false
    ) {
        Log.d("TextFormattingManager", "showEnhancedTextFormattingDialogWithPersistence called")
        Log.d("TextFormattingManager", "Container class: ${container.javaClass.simpleName}")
        Log.d("TextFormattingManager", "Is sentence reader: $isSentenceReader")

        // Create and show a dialog with enhanced text formatting options
        val dialogView =
                DialogEnhancedTextFormattingBinding.inflate(LayoutInflater.from(context)).root

        // Set up font button
        val fontButton = dialogView.findViewById<Button>(R.id.fontButton)
        fontButton.text = currentFontName

        // Set up font size slider
        val fontSizeSlider = dialogView.findViewById<android.widget.SeekBar>(R.id.fontSizeSlider)
        fontSizeSlider.progress = currentFontSize
        fontSizeSlider?.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: android.widget.SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (fromUser) {
                            Log.d("TextFormattingManager", "Font size slider changed to: $progress")
                            // Update font size
                            currentFontSize = progress
                            updateFontSize(progress, container)
                            // Save the setting immediately
                            saveTextFormattingSettings()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                }
        )

        // Set up line spacing slider
        val lineSpacingSlider =
                dialogView.findViewById<android.widget.SeekBar>(R.id.lineSpacingSlider)
        lineSpacingSlider.progress = currentLineSpacing
        lineSpacingSlider?.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: android.widget.SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (fromUser) {
                            Log.d(
                                    "TextFormattingManager",
                                    "Line spacing slider changed to: $progress"
                            )
                            // Update line spacing
                            currentLineSpacing = progress
                            updateLineSpacing(progress, container)
                            // Save the setting immediately
                            saveTextFormattingSettings()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                }
        )

        // Conditionally set up status terms font size slider only in Sentence Reader
        val statusTermsFontSizeSlider =
                dialogView.findViewById<android.widget.SeekBar>(R.id.statusTermsFontSizeSlider)

        if (isSentenceReader) {
            // Show and set up the status terms font size slider in Sentence Reader
            statusTermsFontSizeSlider.visibility = View.VISIBLE
            statusTermsFontSizeSlider.progress = currentStatusTermsFontSize
            statusTermsFontSizeSlider?.setOnSeekBarChangeListener(
                    object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                                seekBar: android.widget.SeekBar?,
                                progress: Int,
                                fromUser: Boolean
                        ) {
                            if (fromUser) {
                                Log.d(
                                        "TextFormattingManager",
                                        "Status terms font size slider changed to: $progress"
                                )
                                // Update status terms font size
                                currentStatusTermsFontSize = progress
                                updateStatusTermsFontSize(progress)
                                // Save the setting immediately
                                saveTextFormattingSettings()
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    }
            )
        } else {
            // Hide the status terms font size slider in Native Reader
            statusTermsFontSizeSlider.visibility = View.GONE

            // Also hide the label if it exists
            val statusTermsFontSizeLabel =
                    dialogView.findViewById<TextView>(R.id.statusTermsFontSizeLabel)
            statusTermsFontSizeLabel?.visibility = View.GONE
        }

        // Set up font button click listener
        fontButton.setOnClickListener { showFontSelectionDialog(fontButton, container) }

        // Create and show the dialog
        AlertDialog.Builder(context)
                .setTitle("Text Formatting")
                .setView(dialogView)
                .setPositiveButton("Close") { dialog, _ ->
                    Log.d("TextFormattingManager", "Dialog closing, saving settings")
                    // Save all settings when closing
                    saveTextFormattingSettings()
                    dialog.dismiss()
                }
                .show()

        Log.d("TextFormattingManager", "Dialog shown")
    }

    /** Show a custom font selection dialog that stays open after selection */
    private fun showFontSelectionDialog(fontButton: Button, container: ViewGroup) {
        // Define font families with their variants
        val fontFamilies = getFontFamilies()

        // Create a flattened list of all font options (family + variants)
        val fontOptions = mutableListOf<String>()
        for (fontFamily in fontFamilies) {
            if (fontFamily.name == "Default") {
                fontOptions.add("Default")
            } else {
                for ((variantName, _) in fontFamily.variants) {
                    val fullName =
                            if (variantName == "Regular") {
                                fontFamily.name
                            } else {
                                "${fontFamily.name} $variantName"
                            }
                    fontOptions.add(fullName)
                }
            }
        }

        val dialogView = DialogFontSelectionBinding.inflate(LayoutInflater.from(context)).root
        val listView = dialogView.findViewById<ListView>(R.id.fontListView)

        // Create adapter for the font list
        val adapter =
                object :
                        android.widget.ArrayAdapter<String>(
                                context,
                                android.R.layout.simple_list_item_single_choice,
                                fontOptions
                        ) {
                    override fun getView(
                            position: Int,
                            convertView: android.view.View?,
                            parent: android.view.ViewGroup
                    ): android.view.View {
                        val view = super.getView(position, convertView, parent)
                        if (view is android.widget.TextView) {
                            val fontName = getItem(position)
                            // Highlight the currently selected font
                            if (fontName == currentFontName) {
                                view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
                            } else {
                                view.setTypeface(view.typeface, android.graphics.Typeface.NORMAL)
                            }
                        }
                        return view
                    }
                }

        listView.adapter = adapter

        // Set the currently selected item
        val currentFontIndex = fontOptions.indexOf(currentFontName)
        if (currentFontIndex >= 0) {
            listView.setItemChecked(currentFontIndex, true)
        }

        // Handle font selection
        listView.onItemClickListener =
                android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                    val selectedFont = fontOptions[position]
                    currentFontName = selectedFont
                    fontButton.text = selectedFont
                    applySelectedFont(selectedFont, container)
                    // Save the setting immediately
                    saveTextFormattingSettings()

                    // Update the adapter to refresh highlighting
                    adapter.notifyDataSetChanged()
                }

        // Create and show the dialog
        AlertDialog.Builder(context)
                .setTitle("Select Font")
                .setView(dialogView)
                .setPositiveButton("Close") { dialog, _ ->
                    // Save all settings when closing
                    saveTextFormattingSettings()
                    dialog.dismiss()
                }
                .show()
    }

    /** Refresh text spans to ensure visual updates happen */
    private fun refreshTextSpans(textView: NativeTextView) {
        // Get the current text and reassign it to force span refresh
        val currentText = textView.text
        if (currentText != null) {
            textView.post {
                // Reassign the text to force a visual refresh of spans
                textView.text = currentText

                // Additional methods to force visual refresh
                textView.invalidate()
                textView.requestLayout()
                textView.refreshDrawableState()

                // For some cases, a small scroll can help trigger visual update
                val parentScrollView = findParentScrollView(textView)
                if (parentScrollView != null) {
                    parentScrollView.post {
                        val currentScrollY = parentScrollView.scrollY
                        // Scroll down 1 pixel and back to trigger redraw
                        parentScrollView.scrollTo(0, currentScrollY + 1)
                        parentScrollView.post {
                            parentScrollView.scrollTo(
                                    0,
                                    currentScrollY
                            ) // Return to original position
                        }
                    }
                }
            }
        }
    }

    /** Find the parent ScrollView containing a view */
    private fun findParentScrollView(view: View): android.widget.ScrollView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is android.widget.ScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }
}
