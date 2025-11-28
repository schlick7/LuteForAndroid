package com.example.luteforandroidv2.ui.nativeread.Term

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.example.luteforandroidv2.R

/**
 * Custom TextView that selects words on single tap, similar to long-press selection Handles touch
 * events for term selection and interaction
 */
class NativeTextView : AppCompatTextView, View.OnTouchListener {
    private var termInteractionListener: TermInteractionListener? = null
    private var languageId: Int = 1 // Default language ID
    private var segmentInfoList: List<SegmentInfo> =
            emptyList() // Store segment information for term lookup

    // For handling gestures
    private var gestureDetector: GestureDetector? = null

    constructor(context: Context) : super(context) {
        init()
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    constructor(
            context: Context,
            attrs: AttributeSet?,
            defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        // Set default text color to lute_on_background
        setTextColor(context.getColor(R.color.lute_on_background))

        // Disable text selection to prevent native Android selection popup
        setTextIsSelectable(false)

        // Disable text cursor and selection handles
        isCursorVisible = false
        customSelectionActionModeCallback =
                object : android.view.ActionMode.Callback {
                    override fun onCreateActionMode(
                            mode: android.view.ActionMode?,
                            menu: android.view.Menu?
                    ): Boolean {
                        return false // Return false to prevent action mode from being created
                    }

                    override fun onPrepareActionMode(
                            mode: android.view.ActionMode?,
                            menu: android.view.Menu?
                    ): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(
                            mode: android.view.ActionMode?,
                            item: android.view.MenuItem?
                    ): Boolean {
                        return false
                    }

                    override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                        // No action needed
                    }
                }

        // Enable clickability for long press detection
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Set highlight color to transparent to prevent any highlighting
        highlightColor = android.graphics.Color.TRANSPARENT

        // Set up touch listener for word selection on tap
        setOnTouchListener(this)

        // Set up long click listener for sentence translation
        setOnLongClickListener { view ->
            Log.d("NativeTextView", "Long press detected!")
            // Extract sentence at the last known tap position
            val sentence = extractCurrentSentence()
            Log.d("NativeTextView", "Extracted sentence: '$sentence'")
            if (sentence.isNotEmpty()) {
                Log.d("NativeTextView", "Sending sentence to listener: $sentence")
                termInteractionListener?.onSentenceLongPressed(
                        sentence,
                        languageId,
                        currentTapX,
                        currentTapY
                )
            } else {
                Log.d("NativeTextView", "No sentence extracted, text length: ${text?.length}")
            }
            true // Consume the event
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                // Store the touch coordinates for potential long press
                event?.let { e ->
                    val x = e.x.toInt()
                    val y = e.y.toInt()

                    // Store coordinates for long press processing, but only if layout is ready
                    val textViewLayout: Layout? = layout
                    if (textViewLayout != null) {
                        try {
                            val line = textViewLayout.getLineForVertical(y)
                            val offset = textViewLayout.getOffsetForHorizontal(line, x.toFloat())
                            currentTapOffset = offset

                            // Calculate screen coordinates similar to handleWordSelection
                            val lineTop = textViewLayout.getLineTop(line)
                            val horizontalPos = textViewLayout.getPrimaryHorizontal(offset)

                            // Get absolute position of the TextView on screen
                            val location = IntArray(2)
                            getLocationOnScreen(location)

                            currentTapX = (location[0] + horizontalPos).toFloat()
                            currentTapY = (location[1] + lineTop).toFloat()
                        } catch (ex: Exception) {
                            Log.e("NativeTextView", "Error calculating tap coordinates", ex)
                            // Continue with defaults if calculation fails
                        }
                    }
                }
                return false // Allow other gestures like long press to be detected
            }
            MotionEvent.ACTION_UP -> {
                event?.let { e ->
                    val x = e.x.toInt()
                    val y = e.y.toInt()

                    val textViewLayout: Layout? = layout
                    if (textViewLayout == null) {
                        Log.w("NativeTextView", "Layout is null, cannot process tap event")
                        return false
                    }

                    val line = textViewLayout.getLineForVertical(y)
                    val offset = textViewLayout.getOffsetForHorizontal(line, x.toFloat())

                    // Get word boundaries without using Spannable Selection
                    val wordRange = getWordBoundaries(text as Spannable, offset)

                    // Notify listener about the selected word
                    handleWordSelection(wordRange.first, wordRange.second)
                }

                // Return false to allow other gestures like long press to be detected
                return false
            }
        }
        // Let default behavior continue (for scrolling) in other cases
        return false
    }

    private fun getWordBoundaries(spannable: Spannable, offset: Int): Pair<Int, Int> {
        val text = spannable.toString()
        var start = offset
        var end = offset

        // Move backward to find word start
        while (start > 0 && isWordCharacter(text[start - 1])) {
            start--
        }

        // Move forward to find word end
        while (end < text.length && isWordCharacter(text[end])) {
            end++
        }

        return Pair(start, end)
    }

    /** Check if a character is part of a word */
    private fun isWordCharacter(char: Char): Boolean {
        return char.isLetter()
    }

    /** Extract the sentence at the position of the tap */
    private fun extractSentenceAtPosition(event: MotionEvent): String {
        val x = event.x.toInt()
        val y = event.y.toInt()

        val textViewLayout: Layout? = layout
        if (textViewLayout == null) {
            Log.w("NativeTextView", "Layout is null, cannot extract sentence at position")
            return ""
        }

        val line = textViewLayout.getLineForVertical(y)
        val offset = textViewLayout.getOffsetForHorizontal(line, x.toFloat())

        // Find sentence boundaries
        val textString = text.toString()
        var start = offset
        var end = offset

        // Move backward to find sentence start (look for sentence-ending punctuation)
        while (start > 0) {
            val char = textString[start - 1]
            if (char == '.' || char == '!' || char == '?' || char == '\n' || char == '\r') {
                // After finding sentence punctuation, skip any whitespace to find the actual start
                start-- // Move to the punctuation position
                while (start > 0 && textString[start - 1].isWhitespace()) {
                    start--
                }
                break
            }
            start--
        }

        // Move forward to find sentence end
        while (end < textString.length) {
            val char = textString[end]
            if (char == '.' || char == '!' || char == '?' || char == '\n' || char == '\r') {
                end++ // Include the punctuation mark
                // Skip any whitespace after the punctuation
                while (end < textString.length && textString[end].isWhitespace()) {
                    end++
                }
                break
            }
            end++
        }

        // Extract the sentence
        var sentence = textString.substring(start, end).trim()

        // Remove any extra whitespace while preserving single spaces between words
        sentence = sentence.replace(Regex("\\s+"), " ")

        return sentence
    }

    private var currentTapX = 0f
    private var currentTapY = 0f
    private var currentTapOffset = 0 // Store the text offset of the current tap

    /**
     * Extract sentence at specific coordinates This method allows external classes to extract
     * sentences using the same logic as long-press
     */
    fun extractSentenceAtXY(x: Float, y: Float): String {
        val layout = this.layout
        if (layout != null) {
            // Calculate the offset in the text at the given coordinates, same as touch events
            val line = layout.getLineForVertical(y.toInt())
            val offset = layout.getOffsetForHorizontal(line, x)
            currentTapOffset = offset
            currentTapX = x
            currentTapY = y
        }
        return extractCurrentSentence()
    }

    /** Extract the sentence at the stored tap position */
    fun extractCurrentSentence(): String {
        val textString = text.toString()
        if (currentTapOffset < 0 || currentTapOffset >= textString.length) {
            return ""
        }

        var start = currentTapOffset
        var end = currentTapOffset

        // Move backward to find sentence start (look for sentence-ending punctuation)
        while (start > 0) {
            val char = textString[start - 1]
            if (char == '.' || char == '!' || char == '?' || char == '\n' || char == '\r') {
                // After finding sentence punctuation, skip any whitespace to find the actual start
                start-- // Move to the punctuation position
                while (start > 0 && textString[start - 1].isWhitespace()) {
                    start--
                }
                break
            }
            start--
        }

        // Move forward to find sentence end
        while (end < textString.length) {
            val char = textString[end]
            if (char == '.' || char == '!' || char == '?' || char == '\n' || char == '\r') {
                end++ // Include the punctuation mark
                // Skip any whitespace after the punctuation
                while (end < textString.length && textString[end].isWhitespace()) {
                    end++
                }
                break
            }
            end++
        }

        // Extract the sentence
        var sentence = textString.substring(start, end).trim()

        // Remove any extra whitespace while preserving single spaces between words
        sentence = sentence.replace(Regex("\\s+"), " ")

        return sentence
    }

    /** Handle word selection to notify listeners */
    private fun handleWordSelection(start: Int, end: Int) {
        val word = text.subSequence(start, end).toString()
        Log.d("NativeTextView", "Word selected: '$word' from $start to $end")

        // Find the corresponding segment for this word
        val segmentInfo = findSegmentForWord(word, start, end)

        if (segmentInfo != null) {
            // Get the layout for positioning calculations
            val layout = this.layout
            if (layout != null) {
                val line = layout.getLineForOffset(start)

                // Calculate the exact screen position of the tapped word
                val (startOffset, endOffset) = getWordBoundaries(text as Spannable, start)
                val startX = layout.getPrimaryHorizontal(startOffset)
                val endX = layout.getPrimaryHorizontal(endOffset)
                val centerX = (startX + endX) / 2
                val lineTop = layout.getLineTop(line)

                // Get absolute position of the TextView on screen
                val location = IntArray(2)
                getLocationOnScreen(location)

                val screenX = location[0] + centerX.toInt()
                val screenY = location[1] + lineTop

                // Store the tap positions for potential long press
                currentTapX = screenX.toFloat()
                currentTapY = screenY.toFloat()
                currentTapOffset = start // Store the text offset for sentence extraction

                // Notify listener about the selected word with correct positioning
                Log.d(
                        "NativeTextView",
                        "Creating TermData with language ID: ${segmentInfo.segment.languageId}, termId: ${segmentInfo.segment.termId}"
                )
                val term =
                        TermData(
                                termId = segmentInfo.segment.termId,
                                term = word,
                                languageId = segmentInfo.segment.languageId,
                                status = segmentInfo.segment.status,
                                tapX = screenX.toFloat(), // Correct screen X position
                                tapY = screenY.toFloat(), // Correct screen Y position
                                segmentId = segmentInfo.segment.id
                        )

                Log.d("NativeTextView", "Notifying listener of term selection: $word")
                termInteractionListener?.onTermTapped(term)
            } else {
                Log.w(
                        "NativeTextView",
                        "Layout is null, cannot calculate tap position for word: $word"
                )
            }
        }
    }

    /** Find the segment that corresponds to a selected word */
    private fun findSegmentForWord(word: String, start: Int, end: Int): SegmentInfo? {
        // Search through segment info to find matching segment
        Log.d(
                "NativeTextView",
                "Searching through ${segmentInfoList.size} segments for word: '$word' at position: $start-$end"
        )

        // First, try to find a segment that exactly matches the word and position
        for (segmentInfo in segmentInfoList) {
            // Check if the segment text matches and the position is within the segment
            if (segmentInfo.segment.text == word &&
                            start >= segmentInfo.start &&
                            end <= segmentInfo.end
            ) {
                Log.d(
                        "NativeTextView",
                        "Found exact matching segment - termId: ${segmentInfo.segment.termId}, text: '${segmentInfo.segment.text}'"
                )
                return segmentInfo
            }
        }

        // If we didn't find an exact match, try to find a segment that contains the word
        for (segmentInfo in segmentInfoList) {
            // Check if the segment contains the word at approximately the right position
            if (segmentInfo.segment.text.contains(word) &&
                            start >= segmentInfo.start &&
                            start <= segmentInfo.end
            ) {
                Log.d(
                        "NativeTextView",
                        "Found containing segment - termId: ${segmentInfo.segment.termId}, text: '${segmentInfo.segment.text}'"
                )
                return segmentInfo
            }
        }

        return null
    }

    /** Set the language ID for this text view */
    fun setLanguageId(langId: Int) {
        Log.d("NativeTextView", "Setting language ID to: $langId (was: $languageId)")
        this.languageId = langId
    }

    /** Set segment information for term lookup */
    fun setSegmentInfo(segmentInfo: List<SegmentInfo>) {
        Log.d("NativeTextView", "Setting segment info count: ${segmentInfo.size}")
        this.segmentInfoList = segmentInfo
    }

    /** Get segment information for term lookup */
    fun getSegmentInfo(): List<SegmentInfo> {
        return this.segmentInfoList
    }

    /** Update the status of segments by term ID */
    fun updateSegmentStatusByTermIds(termUpdates: Map<Int, Int>) {
        // Update the segment info list by creating new SegmentInfo objects with updated TextSegment
        // objects
        this.segmentInfoList =
                this.segmentInfoList.map { segmentInfo ->
                    if (termUpdates.containsKey(segmentInfo.segment.termId)) {
                        // Create a new TextSegment with updated status
                        val updatedSegment =
                                segmentInfo.segment.copy(
                                        status = termUpdates[segmentInfo.segment.termId]!!
                                )
                        // Create a new SegmentInfo with the updated TextSegment
                        segmentInfo.copy(segment = updatedSegment)
                    } else {
                        segmentInfo // Keep the original if no update is needed
                    }
                }
    }

    /** Set listener for term interaction events */
    fun setTermInteractionListener(listener: TermInteractionListener) {
        this.termInteractionListener = listener
    }

    /** Set the font family for this text view */
    fun setFontFamily(fontName: String?, customTypeface: Typeface? = null) {
        try {
            Log.d("NativeTextView", "Setting font family to: $fontName")

            // If we have a custom typeface, use it directly
            if (customTypeface != null) {
                this.typeface = customTypeface
                Log.d("NativeTextView", "Successfully applied custom typeface")
                return
            }

            // Handle null or "Default" font
            if (fontName == null || fontName == "Default") {
                // Reset to default typeface
                this.typeface = android.graphics.Typeface.DEFAULT
                Log.d(
                        "NativeTextView",
                        "Reset to default typeface: ${android.graphics.Typeface.DEFAULT}"
                )
                return
            }

            // Try to create typeface from font name
            val typeface =
                    android.graphics.Typeface.create(fontName, android.graphics.Typeface.NORMAL)
            Log.d("NativeTextView", "Created typeface for '$fontName': $typeface")

            if (typeface != null) {
                this.typeface = typeface
                Log.d(
                        "NativeTextView",
                        "Successfully applied font '$fontName', actual typeface: $typeface"
                )
            } else {
                Log.w("NativeTextView", "Failed to create typeface for font: $fontName")
                // Fall back to default
                this.typeface = android.graphics.Typeface.DEFAULT
            }
        } catch (e: Exception) {
            Log.e("NativeTextView", "Error setting font family: $fontName", e)
            // Fall back to default
            this.typeface = android.graphics.Typeface.DEFAULT
        }
    }

    /** Interface for term interaction events */
    interface TermInteractionListener {
        fun onTermTapped(termData: TermData)
        fun onSentenceLongPressed(sentence: String, languageId: Int, tapX: Float, tapY: Float)
    }
}
