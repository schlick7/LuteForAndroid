package com.example.luteforandroidv2.ui.nativeread.Term

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Test utility to verify the POST data generation for term saving */
object TermPostDataTest {

    @JvmStatic
    fun main(args: Array<String>) {
        // Test case 1: Linked term
        val linkedTermData =
                TermFormData(
                        termId = 123,
                        termText = "test",
                        languageId = 1,
                        context = "",
                        translation = "testing",
                        status = 3,
                        parents = listOf("testparent"),
                        tags = emptyList(),
                        isLinked = true
                )

        val linkedPostData = generatePostData(linkedTermData)
        println("Linked term POST data: $linkedPostData")

        // Test case 2: Unlinked term
        val unlinkedTermData =
                TermFormData(
                        termId = 123,
                        termText = "test",
                        languageId = 1,
                        context = "",
                        translation = "testing",
                        status = 3,
                        parents = listOf("testparent"),
                        tags = emptyList(),
                        isLinked = false
                )

        val unlinkedPostData = generatePostData(unlinkedTermData)
        println("Unlinked term POST data: $unlinkedPostData")
    }

    private fun generatePostData(termData: TermFormData): String {
        // Format parents as JSON array
        val parentsJson = JSONArray()
        termData.parents.forEach { parent ->
            val parentObj = JSONObject()
            parentObj.put("value", parent)
            parentsJson.put(parentObj)
        }

        // For Flask-WTF BooleanField, we only send the sync_status parameter when the term is
        // linked
        // If not linked, we don't send the parameter at all
        val syncStatusParam = if (termData.isLinked) "&sync_status=on" else ""

        return "text=${URLEncoder.encode(termData.termText, "UTF-8")}" +
                "&translation=${URLEncoder.encode(termData.translation, "UTF-8")}" +
                "&status=${termData.status}" +
                "&parentslist=${URLEncoder.encode(parentsJson.toString(), "UTF-8")}" +
                syncStatusParam
    }
}
