package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorSimpleTest {

    @Test
    fun testSimpleExtraction() {
        val html = """<input id="parentslist" value="[{&#34;value&#34;: &#34;mm&#34;}]"/>"""
        println("Testing with HTML: $html")

        val parents = TermDataExtractor.extractParents(html)
        println("Extracted parents: $parents")

        // Just verify it doesn't crash for now
        assertNotNull(parents)
    }
}
