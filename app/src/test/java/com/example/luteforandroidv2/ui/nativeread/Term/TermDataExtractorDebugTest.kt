package com.example.luteforandroidv2.ui.nativeread.Term

import org.junit.Assert.*
import org.junit.Test

class TermDataExtractorDebugTest {

    private val htmlContent =
            """
        <ul class="form-control" id="status"><li><input id="status-0" name="status" type="radio" value="1"> <label for="status-0">1</label></li><li><input checked id="status-1" name="status" type="radio" value="2"> <label for="status-1">2</label></li><li><input id="status-2" name="status" type="radio" value="3"> <label for="status-2">3</label></li><li><input id="status-3" name="status" type="radio" value="4"> <label for="status-3">4</label></li><li><input id="status-4" name="status" type="radio" value="5"> <label for="status-4">5</label></li><li><input id="status-5" name="status" type="radio" value="99"> <label for="status-5">Wkn</label></li><li><input id="status-6" name="status" type="radio" value="98"> <label for="status-6">Ign</label></li></ul>
    """.trimIndent()

    @Test
    fun testExtractStatus() {
        val status = TermDataExtractor.extractStatus(htmlContent)
        println("Extracted status: $status")
        assertEquals(2, status)
    }
}
