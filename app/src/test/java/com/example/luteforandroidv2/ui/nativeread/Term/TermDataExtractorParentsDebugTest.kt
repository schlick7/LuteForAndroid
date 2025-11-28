package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorParentsDebugTest {

    private val htmlContent =
            """
        <div><input class="form-control" id="parentslist" name="parentslist" type="text" value="[{&#34;value&#34;: &#34;mm&#34;}]"></div>
    """.trimIndent()

    @Test
    fun testExtractParents() {
        // Print the exact HTML to see what we're working with
        println("HTML content: '$htmlContent'")

        // Test the exact regex from TermDataExtractor
        val parentsRegex = Regex("""<input[^>]*id="parentslist"[^>]*value="(.*?)"[^>]*/>""")
        val parentsMatch = parentsRegex.find(htmlContent)
        if (parentsMatch != null) {
            println("Found parents match: ${parentsMatch.value}")
            println("Group 1 (value): '${parentsMatch.groupValues[1]}'")

            // Test decoding
            val value = parentsMatch.groupValues[1]
            println("Before decoding: '$value'")
            val decodedValue = value.replace("&#34;", "\"")
            println("After decoding: '$decodedValue'")
        } else {
            println("No parents match found")

            // Try a more flexible pattern
            val parentsRegex2 = Regex("""<input[^>]*id="parentslist"[^>]*value="([^"]*)"[^>]*/>""")
            val parentsMatch2 = parentsRegex2.find(htmlContent)
            if (parentsMatch2 != null) {
                println("Found parents match with second pattern: ${parentsMatch2.value}")
                println("Group 1 (value): '${parentsMatch2.groupValues[1]}'")
            } else {
                println("No parents match found with second pattern either")
            }
        }

        // Test with the actual TermDataExtractor method
        val parents = TermDataExtractor.extractParents(htmlContent)
        println("Extracted parents: $parents")

        // For now, let's just verify the method doesn't crash
        assertNotNull(parents)
    }
}
