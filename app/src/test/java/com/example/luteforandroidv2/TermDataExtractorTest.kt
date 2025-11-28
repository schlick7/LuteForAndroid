package com.example.luteforandroidv2

import com.example.luteforandroidv2.ui.nativeread.Term.TermDataExtractor
import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorTest {

    @Test
    fun extractSyncStatus_whenCheckboxIsChecked_returnsTrue() {
        val htmlContent =
                """
            <input id="sync_status" name="sync_status" type="checkbox" checked value="y">
        """.trimIndent()

        val result = TermDataExtractor.extractSyncStatus(htmlContent)
        assertTrue("Should return true when checkbox is checked", result)
    }

    @Test
    fun extractSyncStatus_whenCheckboxIsNotChecked_returnsFalse() {
        val htmlContent =
                """
            <input id="sync_status" name="sync_status" type="checkbox" value="y">
        """.trimIndent()

        val result = TermDataExtractor.extractSyncStatus(htmlContent)
        assertFalse("Should return false when checkbox is not checked", result)
    }

    @Test
    fun extractSyncStatus_whenCheckboxIsMissing_returnsFalse() {
        val htmlContent =
                """
            <input id="other_field" name="other_field" type="text" value="test">
        """.trimIndent()

        val result = TermDataExtractor.extractSyncStatus(htmlContent)
        assertFalse("Should return false when sync_status checkbox is missing", result)
    }
}
