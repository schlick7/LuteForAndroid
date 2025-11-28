package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorSpecificDebugTest {

    private val htmlFragment =
            """
        <div><input class="form-control" id="parentslist" name="parentslist" type="text" value="[{&#34;value&#34;: &#34;mm&#34;}]"></div>
    """.trimIndent()

    @Test
    fun testExtractParentsFromFragment() {
        println("HTML fragment: '$htmlFragment'")

        // Test the extractParents method directly
        val parents = TermDataExtractor.extractParents(htmlFragment)
        println("Extracted parents: $parents")

        // For now, just verify it doesn't crash
        assertNotNull(parents)
    }

    @Test
    fun testRegexPattern() {
        println("Testing regex pattern on HTML fragment")

        // Test the exact regex from TermDataExtractor
        val parentsRegex = Regex("""<input[^>]*id="parentslist"[^>]*value="(.*?)"[^>]*/>""")
        val parentsMatch = parentsRegex.find(htmlFragment)
        if (parentsMatch != null) {
            println("Found match: ${parentsMatch.value}")
            println("Group 1: '${parentsMatch.groupValues[1]}'")
        } else {
            println("No match found with primary pattern")

            // Try alternative patterns
            val altPatterns =
                    listOf(
                            Regex("""<input[^>]*id="parentslist"[^>]*value="([^"]*)"[^>]*/>"""),
                            Regex("""<input[^>]*id="parentslist".*?value="(.*?)"[^>]*/>"""),
                            Regex("""<input[^>]*id="parentslist"[^>]*value="(.*)"[^>]*/>""")
                    )

            for ((index, pattern) in altPatterns.withIndex()) {
                val match = pattern.find(htmlFragment)
                if (match != null) {
                    println("Found match with alternative pattern $index: ${match.value}")
                    println("Group 1: '${match.groupValues[1]}'")
                    break
                } else {
                    println("No match found with alternative pattern $index")
                }
            }
        }
    }
}
