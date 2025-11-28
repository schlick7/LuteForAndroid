package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorRegexTest {

    private val htmlContent =
            """
        <div><input class="form-control" id="parentslist" name="parentslist" type="text" value="[{&#34;value&#34;: &#34;mm&#34;}]"></div>
    """.trimIndent()

    @Test
    fun testParentsRegex() {
        println("HTML content: '$htmlContent'")

        // Test the actual method that's working
        val termData = TermDataExtractor.parseTermDataFromHtml(htmlContent, 95, "click")
        println("Extracted term data: $termData")

        // Verify the extracted data
        assertEquals("click", termData.termText)
        assertEquals(
                "",
                termData.translation
        ) // Empty because there's no translation textarea in this HTML
        assertEquals(
                1,
                termData.status
        ) // Default status because there are no radio buttons in this HTML
        assertEquals(
                1,
                termData.languageId
        ) // Default language ID because there's no language_id input in this HTML
        assertEquals(1, termData.parents.size)
        assertEquals("mm", termData.parents[0])
    }
}
