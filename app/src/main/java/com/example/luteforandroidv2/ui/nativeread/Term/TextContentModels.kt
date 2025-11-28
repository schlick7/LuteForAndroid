package com.example.luteforandroidv2.ui.nativeread.Term

/** Data class to store segment position information */
data class SegmentInfo(
        val start: Int,
        val end: Int,
        val segment: TextSegment,
        val paragraphId: String = "" // Add paragraph ID for better tracking
)

/** Represents the text content of a page */
data class TextContent(val paragraphs: List<Paragraph>, val pageMetadata: PageMetadata)

/** Represents a paragraph of text */
data class Paragraph(val id: String, val segments: List<TextSegment>)

/** Represents a text segment within a paragraph */
data class TextSegment(
        val id: String,
        val text: String,
        val style: TextStyle,
        val isInteractive: Boolean = false,
        val status: Int = 0, // Add status field with default value of 0 (unknown)
        val termId: Int = 0, // Add term ID field with default value of 0 (unknown)
        val languageId: Int = 1, // Add language ID field with default value of 1 (unknown)
        val translation: String = "", // Add translation field
        val parentList: List<String> = emptyList(), // Add parent list field
        val parentTranslations: List<String> = emptyList() // Add parent translations field
)

/** Represents the styling information for a text segment */
data class TextStyle(
        val fontSize: Float = 16f,
        val fontWeight: Int = 400,
        val isItalic: Boolean = false,
        val color: Int = 0 // Transparent/default color to allow theme to take precedence
)

/** Represents metadata for a page */
data class PageMetadata(
        val bookId: String,
        val pageNum: Int,
        val pageCount: Int,
        val hasAudio: Boolean = false,
        val isRTL: Boolean = false,
        val languageId: Int = 1, // Default to 1, but should be set from book data
        val languageName: String = "" // Language name for reference
)
