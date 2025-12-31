package com.example.luteforandroidv2.ui.nativeread.Term

import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.example.luteforandroidv2.ui.nativeread.NonScrollingScrollView

/** Data class to store segment position information */

/**
 * Text renderer for the native reading view Handles parsing and rendering of text content
 *
 * Fix for spacing issues:
 * - Reduced paragraph separators from double newlines to single newlines
 * - Maintained proper line spacing through NativeTextView configuration
 * - Adjusted line spacing multiplier range for better control
 */
class TextRenderer {
    // Store paragraph information for term interaction
    private var paragraphInfo: List<Paragraph> = emptyList()
    private var segmentInfoList: List<SegmentInfo> = emptyList()
    private var customTextColor: Int? = null
    
    fun setCustomTextColor(color: Int?) {
        customTextColor = color
    }

    /** Render text content into the provided container */
    fun renderTextContent(
            container: ViewGroup,
            content: TextContent,
            termInteractionListener: NativeTextView.TermInteractionListener? = null
    ) {
        // Clear existing content
        container.removeAllViews()

        Log.d("TextRenderer", "=== RENDER TEXT CONTENT CALLED ===")
        Log.d("TextRenderer", "Container: ${container.javaClass.simpleName}")
        Log.d("TextRenderer", "Content paragraphs: ${content.paragraphs.size}")
        Log.d(
                "TextRenderer",
                "Term interaction listener provided: ${termInteractionListener != null}"
        )
        if (termInteractionListener != null) {
            Log.d(
                    "TextRenderer",
                    "Term interaction listener class: ${termInteractionListener.javaClass.name}"
            )
        }

        // Set RTL layout direction if needed
        if (content.pageMetadata.isRTL) {
            container.layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else {
            container.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        // Create a single view for all paragraphs
        val contentView =
                createContentView(
                        container.context,
                        content.paragraphs,
                        content.pageMetadata.isRTL,
                        termInteractionListener,
                        content.pageMetadata
                )
        container.addView(contentView)

        Log.d("TextRenderer", "Rendered ${content.paragraphs.size} paragraphs in single view")
        Log.d("TextRenderer", "=== RENDER TEXT CONTENT COMPLETED ===")
    }

    /** Create a single view for all content */
    private fun createContentView(
            context: android.content.Context,
            paragraphs: List<Paragraph>,
            isRTL: Boolean,
            termInteractionListener: NativeTextView.TermInteractionListener?,
            pageMetadata: PageMetadata
    ): View {
        // Combine all paragraphs into one text string with proper paragraph spacing
        val combinedText = StringBuilder()
        val segmentInfo = mutableListOf<SegmentInfo>()
        var currentPosition = 0

        // Store paragraph information for term interaction
        paragraphInfo = paragraphs

        // Combine all paragraphs into one text string with proper paragraph spacing
        paragraphs.forEachIndexed { index, paragraph ->
            // Add paragraph separator before each paragraph except the first one
            if (index > 0) {
                combinedText.append("\n") // Double newline for paragraph separation
                currentPosition += 1
            }

            Log.d(
                    "TextRenderer",
                    "Processing paragraph ${paragraph.id} with ${paragraph.segments.size} segments"
            )

            // Combine all segments in this paragraph
            paragraph.segments.forEachIndexed { segmentIndex, segment ->
                Log.d(
                        "TextRenderer",
                        "  Segment $segmentIndex: '${segment.text}' (length: ${segment.text.length})"
                )

                // Add the segment text, converting tab characters to spaces for proper rendering
                val segmentStart = currentPosition
                // Convert tab characters to spaces since tabs don't always render as visible spaces
                // in Android TextViews
                val textToAdd = segment.text.replace('\t', ' ')
                combinedText.append(textToAdd)

                // Update position based on actual text length (after tab replacement)
                currentPosition = segmentStart + textToAdd.length
                val segmentEnd = currentPosition

                // Store segment information for later interaction handling
                segmentInfo.add(SegmentInfo(segmentStart, segmentEnd, segment, paragraph.id))
            }
        }

        // Store segment information for term interaction
        segmentInfoList = segmentInfo

        val textView =
                NativeTextView(context).apply {
                    // Set the combined text
                    text = combinedText.toString()
                    textSize = 16f // Default size, can be overridden

                    // Enable text selection to improve touch handling
                    setTextIsSelectable(true)

                    // Set text alignment based on RTL setting
                    if (isRTL) {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                    } else {
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }

                    // Set the language ID for this text view
                    Log.d(
                            "TextRenderer",
                            "Setting language ID on NativeTextView to: ${pageMetadata.languageId}"
                    )
                    Log.d(
                            "TextRenderer",
                            "Page metadata language ID type: ${pageMetadata.languageId::class.simpleName}"
                    )
                    setLanguageId(pageMetadata.languageId)

                    // Log segment info count
                    Log.d("TextRenderer", "Setting segment info count: ${segmentInfo.size}")
                    // Set segment information for term lookup
                    setSegmentInfo(segmentInfo)

                    // Apply styling and status-based background colors
                    val spannableString = android.text.SpannableString(text)
                    var needsSpannable = false

                    segmentInfo.forEach { info ->
                        val segment = info.segment
                        val style = segment.style

                        // Check if we need spannable for styling or status highlighting
                        if (style.fontWeight > 400 ||
                                        style.isItalic ||
                                        (style.color != 0 && style.color != 0xFFEBEBEB.toInt()) ||
                                        segment.status > 0
                        ) {
                            needsSpannable = true
                        }

                        // Apply font weight and italic styling
                        val spanFlags = android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

                        when {
                            style.isItalic && style.fontWeight > 500 -> {
                                spannableString.setSpan(
                                        android.text.style.StyleSpan(
                                                android.graphics.Typeface.BOLD_ITALIC
                                        ),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                            }
                            style.isItalic -> {
                                spannableString.setSpan(
                                        android.text.style.StyleSpan(
                                                android.graphics.Typeface.ITALIC
                                        ),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                            }
                            style.fontWeight > 500 -> {
                                spannableString.setSpan(
                                        android.text.style.StyleSpan(
                                                android.graphics.Typeface.BOLD
                                        ),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                            }
                        }

                        // Apply default color if explicitly set
                        if (style.color != 0 && style.color != 0xFFEBEBEB.toInt()) {
                            spannableString.setSpan(
                                    android.text.style.ForegroundColorSpan(style.color),
                                    info.start,
                                    info.end,
                                    spanFlags
                            )
                        }

                        // Apply status-based styling for interactive segments
                        Log.d(
                                "TextRenderer",
                                "Checking segment for status styling: '${segment.text}' (interactive: ${segment.isInteractive}, status: ${segment.status})"
                        )
                        if (segment.isInteractive && !isPunctuationOnly(segment.text)) {
                            Log.d("TextRenderer", "Segment is interactive: '${segment.text}'")
                            if (segment.status > 0) {
                                // Highlight terms with actual status (learned terms) with
                                // background colors
                                val backgroundColor = getStatusColor(segment.status)
                                Log.d(
                                        "TextRenderer",
                                        "Applying status ${segment.status} with color $backgroundColor to segment: ${segment.text}"
                                )
                                if (backgroundColor != Color.TRANSPARENT) {
                                    spannableString.setSpan(
                                            WordBackgroundSpan(backgroundColor),
                                            info.start,
                                            info.end,
                                            spanFlags
                                    )
                                }
                                
                                val textColor = customTextColor
                                if (textColor != null) {
                                    spannableString.setSpan(
                                            android.text.style.ForegroundColorSpan(textColor),
                                            info.start,
                                            info.end,
                                            spanFlags
                                    )
                                    Log.d(
                                            "TextRenderer",
                                            "Applied custom text color ($textColor) to status ${segment.status} segment: ${segment.text}"
                                    )
                                }
                            } else {
                                // Status 0 terms are interactive but get solid blue text color
                                // This overrides any default text color to clearly indicate they
                                // are clickable terms
                                val statusZeroTextColor = Color.parseColor("#8095FF") // Solid blue
                                Log.d(
                                        "TextRenderer",
                                        "Applying solid blue text color ($statusZeroTextColor) to status 0 interactive segment: '${segment.text}' at positions ${info.start}-${info.end}"
                                )
                                spannableString.setSpan(
                                        android.text.style.ForegroundColorSpan(statusZeroTextColor),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                                Log.d(
                                        "TextRenderer",
                                        "Applied blue text color to: '${segment.text}'"
                                )
                            }
                        } else if (!segment.isInteractive &&
                                        segment.status > 0 &&
                                        !isPunctuationOnly(segment.text)
                        ) {
                            // Non-interactive segments with status (rare case)
                            val backgroundColor = getStatusColor(segment.status)
                            Log.d(
                                    "TextRenderer",
                                    "Applying status ${segment.status} with color $backgroundColor to non-interactive segment: ${segment.text}"
                            )
                            if (backgroundColor != Color.TRANSPARENT) {
                                spannableString.setSpan(
                                        WordBackgroundSpan(backgroundColor),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                            }
                            
                            val textColor2 = customTextColor
                            if (textColor2 != null) {
                                spannableString.setSpan(
                                        android.text.style.ForegroundColorSpan(textColor2),
                                        info.start,
                                        info.end,
                                        spanFlags
                                )
                                Log.d(
                                        "TextRenderer",
                                        "Applied custom text color ($textColor2) to non-interactive status ${segment.status} segment: ${segment.text}"
                                )
                            }
                        }
                    }

                    // Only use SpannableString if styling is actually needed
                    if (needsSpannable) {
                        setText(spannableString)
                    }

                    // Set the term interaction listener
                    if (termInteractionListener != null) {
                        setTermInteractionListener(termInteractionListener)
                    }
                }

        // Configure layout parameters for the single content view
        val layoutParams =
                textView.layoutParams
                        ?: ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
        // Check if layoutParams is already MarginLayoutParams before casting
        if (layoutParams is ViewGroup.MarginLayoutParams) {
            layoutParams.setMargins(0, 8, 0, 8)
        } else {
            // Create new MarginLayoutParams if it's not already one
            val marginLayoutParams = ViewGroup.MarginLayoutParams(layoutParams)
            marginLayoutParams.setMargins(0, 8, 0, 8)
            textView.layoutParams = marginLayoutParams
        }

        return textView
    }

    /** Get background color based on term status */
    private fun getStatusColor(status: Int): Int {
        return when (status) {
            0 -> Color.parseColor("#8095FF") //  light blue - Unknown (status 0)
            1 -> Color.parseColor("#b46b7a") //  light red - Learning (status 1)
            2 -> Color.parseColor("#BA8050") //  light orange - Learning (status 2)
            3 -> Color.parseColor("#BD9C7B") //  light yellow/tan - Learning (status 3)
            4 -> Color.parseColor("#756D6B") //  light grey - Learning (status 4)
            5 -> Color.parseColor("#40756D6B") // 25% transparent light grey - Known (status 5)
            98 -> Color.TRANSPARENT // No background for ignored terms (status 98)
            99 -> Color.TRANSPARENT // No background for well-known terms (status 99)
            else -> Color.TRANSPARENT // Default - no background
        }
    }

    /** Parse HTML content into TextContent */
    fun parseHtmlContent(html: String, pageMetadata: PageMetadata): TextContent {
        try {
            Log.d("TextRenderer", "=== PARSING HTML CONTENT ===")
            Log.d("TextRenderer", "HTML length: ${html.length}")
            Log.d("TextRenderer", "HTML preview (first 1000 chars): ${html.take(1000)}")

            // Use JSoup to parse the HTML and extract only the book content
            val doc = org.jsoup.Jsoup.parse(html)

            // Look for the "thetext" div first (from regular read endpoint)
            val textDiv = doc.getElementById("thetext")
            if (textDiv != null) {
                Log.d("TextRenderer", "Found 'thetext' div")
                // Get the inner HTML of the text div
                val contentHtml = textDiv.html()
                Log.d("TextRenderer", "'thetext' div content length: ${contentHtml.length}")
                Log.d("TextRenderer", "'thetext' div content preview: ${contentHtml.take(500)}")

                // If contentHtml is just "...", it means we need to parse the actual content
                // which would have been loaded via JavaScript in a web browser
                if (contentHtml.trim() == "..." || contentHtml.trim().isEmpty()) {
                    Log.d(
                            "TextRenderer",
                            "'thetext' div contains '...' or is empty, parsing actual content"
                    )
                    // Parse the full HTML to extract actual content
                    return parseActualContent(doc, pageMetadata)
                }

                // Split into paragraphs based on <p> tags or double line breaks
                val paragraphs = mutableListOf<Paragraph>()
                var paragraphCounter = 0

                // Parse the content to extract paragraphs
                val contentDoc = org.jsoup.Jsoup.parse(contentHtml)
                val paragraphElements = contentDoc.select("p")

                if (paragraphElements.isNotEmpty()) {
                    Log.d("TextRenderer", "Found ${paragraphElements.size} <p> elements in content")
                    // If we have <p> tags, use them as paragraphs
                    paragraphElements.forEach { pElement ->
                        // Get the text content, preserving special characters
                        val text = pElement.text()
                        Log.d("TextRenderer", "Paragraph element text: '${text}'")
                        if (text.isNotEmpty()) {
                            val segment =
                                    TextSegment(
                                            id = "seg_$paragraphCounter",
                                            text = text,
                                            style = TextStyle(),
                                            isInteractive = false,
                                            languageId = pageMetadata.languageId
                                    )
                            paragraphs.add(
                                    Paragraph(
                                            id = "p_$paragraphCounter",
                                            segments = listOf(segment)
                                    )
                            )
                            paragraphCounter++
                        }
                    }
                } else {
                    Log.d("TextRenderer", "No <p> elements found, splitting on double line breaks")
                    // If no <p> tags, split on double line breaks
                    // Use JSoup's text() method which properly handles HTML entities
                    val cleanText = contentDoc.body().text()
                    Log.d("TextRenderer", "Clean text: '${cleanText}'")
                    val textSegments = cleanText.split("\n").filter { it.isNotBlank() }
                    textSegments.forEach { segmentText ->
                        if (segmentText.isNotEmpty()) {
                            val segment =
                                    TextSegment(
                                            id = "seg_$paragraphCounter",
                                            text = segmentText,
                                            style = TextStyle(),
                                            isInteractive = false,
                                            languageId = pageMetadata.languageId
                                    )
                            paragraphs.add(
                                    Paragraph(
                                            id = "p_$paragraphCounter",
                                            segments = listOf(segment)
                                    )
                            )
                            paragraphCounter++
                        }
                    }
                }

                Log.d("TextRenderer", "Created ${paragraphs.size} paragraphs from 'thetext' div")
                return TextContent(paragraphs = paragraphs, pageMetadata = pageMetadata)
            } else {
                Log.d("TextRenderer", "No 'thetext' div found, parsing actual content directly")
                // If no "thetext" div, parse the actual content directly (from start_reading
                // endpoint)
                return parseActualContent(doc, pageMetadata)
            }
        } catch (e: Exception) {
            Log.e("TextRenderer", "Error parsing HTML content", e)
            // Return empty content if parsing fails
            return TextContent(paragraphs = emptyList(), pageMetadata = pageMetadata)
        }
    }

    /** Parse actual content from HTML document */
    private fun parseActualContent(
            doc: org.jsoup.nodes.Document,
            pageMetadata: PageMetadata
    ): TextContent {
        Log.d("TextRenderer", "Parsing actual content from HTML document")

        // Extract paragraphs from the document
        val paragraphs = mutableListOf<Paragraph>()
        var paragraphCounter = 0

        // Select all <p> elements in the document
        val paragraphElements = doc.select("p")

        paragraphElements.forEach { pElement ->
            // Extract text items (spans with class "textitem")
            val textItems = pElement.select("span.textitem")

            // Always create a paragraph, even if it might be empty
            // This preserves the paragraph structure from the server
            if (textItems.isNotEmpty()) {
                // Parse text items as segments
                val segments =
                        textItems.map { textItem ->
                            // Extract status from data-status-class attribute
                            val statusClass = textItem.attr("data-status-class")
                            val status = extractStatusFromClassNames(setOf(statusClass))

                            // Extract term ID from data-wid attribute
                            val termIdStr = textItem.attr("data-wid")
                            val termId = termIdStr.toIntOrNull() ?: 0
                            Log.d(
                                    "TextRenderer",
                                    "Extracted term ID: $termId from data-wid: '$termIdStr'"
                            )

                            // Get the actual text content from data-text attribute to preserve
                            // spacing
                            val text = textItem.attr("data-text")
                            Log.d(
                                    "TextRenderer",
                                    "Parsed segment: '$text' with status: $status, statusClass: $statusClass, termId: $termId"
                            )

                            TextSegment(
                                    id = textItem.id(),
                                    text = text,
                                    style =
                                            TextStyle(
                                                    fontSize = 16f,
                                                    fontWeight =
                                                            if (textItem.hasClass("wordhover") ||
                                                                            textItem.hasClass(
                                                                                    "kwordmarked"
                                                                            )
                                                            )
                                                                    700
                                                            else 400,
                                                    isItalic = false,
                                                    color = 0xFFEBEBEB.toInt()
                                            ),
                                    isInteractive = textItem.hasClass("click"),
                                    status = status,
                                    termId = termId,
                                    languageId = pageMetadata.languageId
                            )
                        }

                // Only add paragraph if it has segments (even spaces count as segments)
                if (segments.isNotEmpty()) {
                    paragraphs.add(Paragraph(id = "p_$paragraphCounter", segments = segments))
                    paragraphCounter++
                }
            } else {
                // Check if this is a paragraph with text content but no textitem spans
                val text = pElement.text()
                if (text.isNotEmpty() && text != "\u200B"
                ) { // Not empty and not just zero-width space
                    val segment =
                            TextSegment(
                                    id = "seg_$paragraphCounter",
                                    text = text,
                                    style = TextStyle(),
                                    isInteractive = false,
                                    status = 0,
                                    languageId = pageMetadata.languageId
                            )
                    paragraphs.add(
                            Paragraph(id = "p_$paragraphCounter", segments = listOf(segment))
                    )
                    paragraphCounter++
                }
                // For completely empty paragraphs, we still want to preserve the structure
                // so we add an empty paragraph to maintain spacing
                else if (paragraphs.isNotEmpty()
                ) { // Only add empty paragraph if we already have content
                    paragraphs.add(Paragraph(id = "p_$paragraphCounter", segments = emptyList()))
                    paragraphCounter++
                }
            }
        }

        Log.d("TextRenderer", "Parsed ${paragraphs.size} paragraphs from actual content")
        return TextContent(paragraphs = paragraphs, pageMetadata = pageMetadata)
    }

    /** Extract status from class names */
    private fun extractStatusFromClassNames(classNames: Set<String>): Int {
        // Look for classes that match the pattern "statusX" where X is a number
        for (className in classNames) {
            if (className.startsWith("status")) {
                val statusStr = className.substring(6) // Remove "status" prefix
                try {
                    val status = statusStr.toInt()
                    Log.d("TextRenderer", "Extracted status $status from class name: $className")
                    return status
                } catch (e: NumberFormatException) {
                    // If parsing fails, continue to next class
                    Log.d("TextRenderer", "Failed to parse status from class name: $className")
                }
            }
        }
        // Default to status 0 if no status class is found
        Log.d("TextRenderer", "No status class found, defaulting to status 0")
        return 0
    }

    /** Update the status of a specific segment */
    fun updateSegmentStatus(container: ViewGroup, segmentId: String, status: Int) {
        // Find the NativeTextView within the container
        val textView = findNativeTextView(container)
        if (textView == null) {
            Log.d("TextRenderer", "No NativeTextView found in container")
            return
        }

        // Find the segment info for the given segmentId to get the termId
        val segmentInfo = segmentInfoList.find { it.segment.id == segmentId }
        if (segmentInfo == null) {
            Log.d("TextRenderer", "No segment found with id: $segmentId")
            return
        }

        // Get the termId from the segment and update all segments with that termId
        val termId = segmentInfo.segment.termId
        updateSegmentsWithTermId(textView, termId, status)
    }

    /** Update all segments with a specific termId */
    fun updateSegmentsWithTermId(container: ViewGroup, termId: Int, status: Int) {
        // Find the NativeTextView within the container
        val textView = findNativeTextView(container)
        if (textView == null) {
            Log.d("TextRenderer", "No NativeTextView found in container")
            return
        }

        updateSegmentsWithTermId(textView, termId, status)
    }

    /** Update all segments with a specific termId */
    private fun updateSegmentsWithTermId(textView: NativeTextView, termId: Int, status: Int) {
        Log.d("TextRenderer", "Updating all segments with termId: $termId to status: $status")

        // Get the current text as SpannableString
        val spannableText = textView.text as? android.text.Spannable
        if (spannableText == null) {
            Log.d("TextRenderer", "Text is not spannable")
            return
        }

        // Find all segments with the same termId and update their status
        var updatedCount = 0
        for (info in segmentInfoList) {
            if (info.segment.termId == termId) {
                // Remove existing spans for this segment (both background and text color)
                val existingBackgroundSpans =
                        spannableText.getSpans(info.start, info.end, WordBackgroundSpan::class.java)
                for (span in existingBackgroundSpans) {
                    spannableText.removeSpan(span)
                }

                val existingColorSpans =
                        spannableText.getSpans(
                                info.start,
                                info.end,
                                android.text.style.ForegroundColorSpan::class.java
                        )
                for (span in existingColorSpans) {
                    spannableText.removeSpan(span)
                }

                // Apply new styling based on status
                if (status > 0 || !info.segment.isInteractive) {
                    // Apply background color for statuses > 0 or non-interactive terms with status
                    val backgroundColor = getStatusColor(status)
                    if (backgroundColor != Color.TRANSPARENT) {
                        val newSpan = WordBackgroundSpan(backgroundColor)
                        spannableText.setSpan(
                                newSpan,
                                info.start,
                                info.end,
                                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        Log.d(
                                "TextRenderer",
                                "Applied new background color $backgroundColor to segment ${info.segment.id}"
                        )
                    } else {
                        Log.d(
                                "TextRenderer",
                                "No background color for status $status, removed existing spans for segment ${info.segment.id}"
                        )
                    }
                } else if (status == 0 && info.segment.isInteractive) {
                    // For status 0 interactive terms, apply blue text color instead of background
                    val statusZeroTextColor = Color.parseColor("#8095FF") // Solid blue
                    Log.d(
                            "TextRenderer",
                            "Applying solid blue text color ($statusZeroTextColor) to status 0 interactive segment: '${info.segment.text}' at positions ${info.start}-${info.end}"
                    )
                    spannableText.setSpan(
                            android.text.style.ForegroundColorSpan(statusZeroTextColor),
                            info.start,
                            info.end,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    Log.d("TextRenderer", "Applied blue text color to: '${info.segment.text}'")
                }
                updatedCount++
            }
        }

        Log.d("TextRenderer", "Updated $updatedCount segments with termId: $termId")

        // Update the text view with the modified spannable text (without scroll preservation)
        textView.setText(spannableText)

        // Find parent ScrollView and briefly scroll to force a redraw that updates spans
        // Since we know scrolling triggers the visual update, we'll programmatically scroll
        // slightly
        val parentScrollView = findScrollView(textView)
        if (parentScrollView != null) {
            parentScrollView.post {
                val currentScrollY = parentScrollView.scrollY
                // Scroll down 1 pixel and back to trigger redraw
                parentScrollView.scrollTo(0, currentScrollY + 1)
                parentScrollView.scrollTo(0, currentScrollY) // Return to original position
            }
        }
        // Reassigning text is the most reliable method to ensure spans update visually
        textView.post {
            val currentText = textView.text
            textView.text = currentText

            // Additional methods to force visual refresh for non-scrollable views
            textView.invalidate() // Force redraw
            textView.requestLayout() // Request layout update
            textView.refreshDrawableState() // Refresh drawable states

            // For NonScrollingScrollView, temporarily enable auto-scrolling to allow refresh
            val parentScrollView = findScrollView(textView)
            if (parentScrollView is NonScrollingScrollView) {
                val wasAutoScrollBlocked = parentScrollView.isAutoScrollBlocked
                // Temporarily enable auto-scrolling to allow our refresh scroll
                parentScrollView.setAutoScrollBlocked(false)
                parentScrollView.post {
                    val currentScrollY = parentScrollView.scrollY
                    // Scroll down 1 pixel and back to trigger redraw
                    parentScrollView.scrollTo(0, currentScrollY + 1)
                    parentScrollView.scrollTo(0, currentScrollY) // Return to original position

                    // Restore original auto-scrolling state
                    parentScrollView.setAutoScrollBlocked(wasAutoScrollBlocked)
                }
            }
        }
    }

    /**
     * Update multiple segments with specific term IDs in one operation without scroll preservation
     */
    fun updateMultipleSegmentsWithTermIds(textView: NativeTextView, termUpdates: Map<Int, Int>) {
        Log.d("TextRenderer", "Updating multiple segments with termId-status pairs: $termUpdates")

        // Get the current text as SpannableString
        val spannableText = textView.text as? android.text.Spannable
        if (spannableText == null) {
            Log.d("TextRenderer", "Text is not spannable")
            return
        }

        // Find all segments that match the termIds and update their status
        var totalUpdated = 0
        for ((termId, status) in termUpdates) {
            var updatedCount = 0
            for (info in segmentInfoList) {
                if (info.segment.termId == termId) {
                    // Remove existing spans for this segment (both background and text color)
                    val existingBackgroundSpans =
                            spannableText.getSpans(
                                    info.start,
                                    info.end,
                                    WordBackgroundSpan::class.java
                            )
                    for (span in existingBackgroundSpans) {
                        spannableText.removeSpan(span)
                    }

                    val existingColorSpans =
                            spannableText.getSpans(
                                    info.start,
                                    info.end,
                                    android.text.style.ForegroundColorSpan::class.java
                            )
                    for (span in existingColorSpans) {
                        spannableText.removeSpan(span)
                    }

                    // Apply new styling based on status
                    if (status > 0 || !info.segment.isInteractive) {
                        // Apply background color for statuses > 0 or non-interactive terms with
                        // status
                        val backgroundColor = getStatusColor(status)
                        if (backgroundColor != Color.TRANSPARENT) {
                            val newSpan = WordBackgroundSpan(backgroundColor)
                            spannableText.setSpan(
                                    newSpan,
                                    info.start,
                                    info.end,
                                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            Log.d(
                                    "TextRenderer",
                                    "Applied new background color $backgroundColor to segment ${info.segment.id}"
                            )
                        } else {
                            Log.d(
                                    "TextRenderer",
                                    "No background color for status $status, removed existing spans for segment ${info.segment.id}"
                            )
                        }
                    } else if (status == 0 && info.segment.isInteractive) {
                        // For status 0 interactive terms, apply blue text color instead of
                        // background
                        val statusZeroTextColor = Color.parseColor("#8095FF") // Solid blue
                        Log.d(
                                "TextRenderer",
                                "Applying solid blue text color ($statusZeroTextColor) to status 0 interactive segment: '${info.segment.text}' at positions ${info.start}-${info.end}"
                        )
                        spannableText.setSpan(
                                android.text.style.ForegroundColorSpan(statusZeroTextColor),
                                info.start,
                                info.end,
                                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        Log.d("TextRenderer", "Applied blue text color to: '${info.segment.text}'")
                    }
                    updatedCount++
                }
            }
            Log.d("TextRenderer", "Updated $updatedCount segments with termId: $termId")
            totalUpdated += updatedCount
        }

        Log.d("TextRenderer", "Total updated segments: $totalUpdated")

        // Update the segment status in the NativeTextView as well to ensure getSegmentInfo returns
        // updated statuses
        textView.updateSegmentStatusByTermIds(termUpdates)

        // Find parent ScrollView and briefly scroll to force a redraw that updates spans
        // Since we know scrolling triggers the visual update, we'll programmatically scroll
        // slightly
        val parentScrollView = findScrollView(textView)
        if (parentScrollView != null) {
            parentScrollView.post {
                val currentScrollY = parentScrollView.scrollY
                // Scroll down 1 pixel and back to trigger redraw
                parentScrollView.scrollTo(0, currentScrollY + 1)
                parentScrollView.scrollTo(0, currentScrollY) // Return to original position
            }
        }
        // Reassigning text is the most reliable method to ensure spans update visually
        textView.post {
            val currentText = textView.text
            textView.text = currentText

            // Additional methods to force visual refresh for non-scrollable views
            textView.invalidate() // Force redraw
            textView.requestLayout() // Request layout update
            textView.refreshDrawableState() // Refresh drawable states

            // For NonScrollingScrollView, temporarily enable auto-scrolling to allow refresh
            val parentScrollView = findScrollView(textView)
            if (parentScrollView is NonScrollingScrollView) {
                val wasAutoScrollBlocked = parentScrollView.isAutoScrollBlocked
                // Temporarily enable auto-scrolling to allow our refresh scroll
                parentScrollView.setAutoScrollBlocked(false)
                parentScrollView.post {
                    val currentScrollY = parentScrollView.scrollY
                    // Scroll down 1 pixel and back to trigger redraw
                    parentScrollView.scrollTo(0, currentScrollY + 1)
                    parentScrollView.scrollTo(0, currentScrollY) // Return to original position

                    // Restore original auto-scrolling state
                    parentScrollView.setAutoScrollBlocked(wasAutoScrollBlocked)
                }
            }
        }
    }

    /** Find the ScrollView containing a view */
    private fun findScrollView(view: View): ScrollView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is ScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    /** Find the NativeTextView within a container */
    private fun findNativeTextView(container: ViewGroup): NativeTextView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is NativeTextView) {
                return child
            } else if (child is ViewGroup) {
                val result = findNativeTextView(child)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    /** Check if a string contains only punctuation characters */
    private fun isPunctuationOnly(text: String): Boolean {
        return text.isNotEmpty() && text.all { !it.isLetterOrDigit() && !it.isWhitespace() }
    }

    /** Find the paragraph and segment that contains the specified text offset */
    fun findSegmentAtOffset(offset: Int): SegmentInfo? {
        // Search through our stored segment information to find which segment contains the
        // specified offset
        for (segmentInfo in segmentInfoList) {
            if (offset >= segmentInfo.start && offset < segmentInfo.end) {
                return segmentInfo
            }
        }
        return null
    }
}
