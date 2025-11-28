package com.example.luteforandroidv2.ui.nativeread.Term

import android.util.Log
import java.net.URLDecoder
import org.json.JSONArray

/** Utility class for extracting term data from Lute server HTML responses using regex patterns. */
class TermDataExtractor {
    companion object {
        private const val TAG = "TermDataExtractor"

        /** Extract term data from HTML content using regex patterns. */
        fun parseTermDataFromHtml(htmlContent: String, termId: Int, parent: String): TermFormData {
            try {
                // Extract term text
                var termText = extractTermText(htmlContent)

                // If still empty, use the parent as fallback
                if (termText.isEmpty()) {
                    termText = parent
                    logDebug("Using parent text as fallback: '$termText'")
                }

                // Extract translation
                val translation = extractTranslation(htmlContent)

                // Extract parents
                val parents = extractParents(htmlContent)

                // Extract status
                val status = extractStatus(htmlContent)

                // Extract language ID
                val languageId = extractLanguageId(htmlContent)

                // Extract sync_status (linking status)
                val isLinked = extractSyncStatus(htmlContent)

                logDebug(
                        "Parsed term data - Text: '$termText', Translation: '$translation', " +
                                "Status: $status, Parents: $parents, Language ID: $languageId, Is Linked: $isLinked"
                )

                return TermFormData(
                        termId = termId, // Use the actual termId
                        termText = termText,
                        languageId = languageId ?: 1, // Default to 1 if not found
                        context = "", // Context is not available in term edit form
                        translation = translation,
                        status = status,
                        parents = parents,
                        tags = listOf(), // Tags are not available in term edit form
                        isLinked = isLinked // Extracted sync status
                )
            } catch (e: Exception) {
                logError("Error parsing term data from HTML", e)
                // Use parent text as fallback
                return TermFormData(
                        termId = termId,
                        termText = parent,
                        languageId = 1, // Default language ID
                        context = "", // Context is not available in term edit form
                        translation = "",
                        status = 1, // Default status
                        parents = listOf(),
                        tags = listOf(), // Tags are not available in term edit form
                        isLinked = false // Default to unlinked
                )
            }
        }

        /** Extract term text from HTML content. */
        internal fun extractTermText(htmlContent: String): String {
            // Try to extract from the text input field with value attribute
            // More robust pattern that handles various attribute orders and whitespace
            // Also handles both self-closing and regular input tags
            val textRegex =
                    Regex(
                            """<input[^>]*id\s*=\s*["']text["'][^>]*value\s*=\s*["']([^"']*)["'][^>]*/?>"""
                    )
            val textMatch = textRegex.find(htmlContent)
            if (textMatch != null) {
                val termText = textMatch.groupValues[1].replace("\\\"", "\"")
                logDebug("Found term text in value attribute: '$termText'")
                return termText
            } else {
                logDebug("No value attribute found in text input")
            }

            // If still empty, try to get from original_text hidden field
            val originalTextRegex =
                    Regex(
                            """<input[^>]*id\s*=\s*["']original_text["'][^>]*value\s*=\s*["']([^"']*)["'][^>]*/?>"""
                    )
            val originalTextMatch = originalTextRegex.find(htmlContent)
            if (originalTextMatch != null) {
                val termText = originalTextMatch.groupValues[1].replace("\\\"", "\"")
                logDebug("Found term text in original_text field: '$termText'")
                return termText
            } else {
                logDebug("No original_text field found")
            }

            // Try to extract from a textarea with id="text" (alternative structure)
            val textareaRegex =
                    Regex(
                            """<textarea[^>]*id\s*=\s*["']text["'][^>]*>(.*?)</textarea>""",
                            RegexOption.DOT_MATCHES_ALL
                    )
            val textareaMatch = textareaRegex.find(htmlContent)
            if (textareaMatch != null) {
                val termText = textareaMatch.groupValues[1].trim()
                logDebug("Found term text in textarea: '$termText'")
                return termText
            } else {
                logDebug("No textarea found for text input")
            }

            // Try to extract from the text input field with inner text (for textarea-like
            // structures)
            val inputWithContentRegex =
                    Regex(
                            """<input[^>]*id\s*=\s*["']text["'][^>]*>(.*?)</input>""",
                            RegexOption.DOT_MATCHES_ALL
                    )
            val inputWithContentMatch = inputWithContentRegex.find(htmlContent)
            if (inputWithContentMatch != null) {
                val termText = inputWithContentMatch.groupValues[1].trim()
                logDebug("Found term text in input with content: '$termText'")
                return termText
            } else {
                logDebug("No input with content found for text input")
            }

            return ""
        }

        /** Extract translation from HTML content. */
        internal fun extractTranslation(htmlContent: String): String {
            val translationRegex =
                    Regex("""<textarea[^>]*id="translation"[^>]*>([^<]*)</textarea>""")
            val translationMatch = translationRegex.find(htmlContent)
            val translation = translationMatch?.groupValues?.get(1)?.trim() ?: ""
            logDebug("Found translation: '$translation'")
            return translation
        }

        /** Extract parents from HTML content. */
        internal fun extractParents(htmlContent: String): List<String> {
            var parentsValue = ""

            // Try to find parentslist input field
            // This pattern handles escaped quotes in the value attribute
            // More flexible pattern that matches various attribute orders
            // Allow both self-closing (/>) and regular (>) input tags
            val parentsRegex = Regex("""<input[^>]*id="parentslist"[^>]*value="(.*?)"[^>]*[/]?>""")
            val parentsMatch = parentsRegex.find(htmlContent)
            if (parentsMatch != null) {
                parentsValue = parentsMatch.groupValues[1]
                logDebug("Found parentslist value: '$parentsValue'")
            }

            return if (parentsValue.isNotEmpty()) {
                try {
                    // First decode HTML entities
                    var decodedParentsValue = parentsValue
                    decodedParentsValue = decodedParentsValue.replace("&#34;", "\"")
                    decodedParentsValue = decodedParentsValue.replace("&#39;", "'")
                    decodedParentsValue = decodedParentsValue.replace("&quot;", "\"")
                    decodedParentsValue = decodedParentsValue.replace("&apos;", "'")
                    logDebug("HTML decoded parents value: '$decodedParentsValue'")

                    // Then decode URL encoded characters
                    decodedParentsValue = URLDecoder.decode(decodedParentsValue, "UTF-8")
                    logDebug("URL decoded parents value: '$decodedParentsValue'")

                    // Parse JSON array of parents: [{"value": "parent1"}, {"value": "parent2"}]
                    val parentList = mutableListOf<String>()
                    try {
                        val parentsJson = JSONArray(decodedParentsValue)
                        for (i in 0 until parentsJson.length()) {
                            val parentObj = parentsJson.getJSONObject(i)
                            val parentValue = parentObj.getString("value")
                            parentList.add(parentValue)
                            logDebug("Found parent: '$parentValue'")
                        }
                    } catch (jsonException: Exception) {
                        logError(
                                "Error parsing parents JSON. Raw value: '$decodedParentsValue'",
                                jsonException
                        )
                        // In unit tests, we might not have access to the Android JSON library
                        // Try to parse manually as a fallback
                        parentList.addAll(parseParentsManually(decodedParentsValue))
                    }
                    parentList
                } catch (e: Exception) {
                    logError("Error parsing parents JSON. Raw value: '$parentsValue'", e)
                    listOf()
                }
            } else {
                logDebug("No parents value found in HTML")
                listOf()
            }
        }

        /** Extract status from HTML content. */
        internal fun extractStatus(htmlContent: String): Int {
            var status = 1 // Default status

            logDebug("Attempting to extract status from HTML")

            // Primary pattern - look for the checked radio button with name="status"
            val statusRegex =
                    Regex(
                            """<input\s+[^>]*name\s*=\s*["']status["'][^>]*checked[^>]*type\s*=\s*["']radio["'][^>]*value\s*=\s*["'](\d+)["'][^>]*/?>"""
                    )
            val statusMatch = statusRegex.find(htmlContent)
            if (statusMatch != null) {
                status = statusMatch.groupValues[1].toIntOrNull() ?: 1
                logDebug("Found status with primary pattern: $status, match: ${statusMatch.value}")
            } else {
                logDebug("No status found with primary pattern, trying fallback")

                // Fallback: find all radio buttons with name="status" and check each one
                val allStatusRegex =
                        Regex(
                                """<input\s+[^>]*name\s*=\s*["']status["'][^>]*type\s*=\s*["']radio["'][^>]*value\s*=\s*["'](\d+)["'][^>]*/?>"""
                        )
                val allStatusMatches = allStatusRegex.findAll(htmlContent)

                logDebug("Found ${allStatusMatches.count()} radio buttons with name='status'")

                for (match in allStatusMatches) {
                    val fullMatch = match.value
                    val value = match.groupValues[1]
                    logDebug("Checking radio button: value=$value, HTML=$fullMatch")

                    // Check if this radio button has the 'checked' attribute
                    if (fullMatch.contains("checked")) {
                        status = value.toIntOrNull() ?: 1
                        logDebug("Found checked radio button with status: $status")
                        break
                    }
                }
            }

            logDebug("Final extracted status: $status")
            return status
        }

        /** Extract language ID from HTML content. */
        internal fun extractLanguageId(htmlContent: String): Int? {
            // Pattern for language_id select element with selected option
            val languageIdRegex =
                    Regex(
                            """<select[^>]*id="language_id"[^>]*>.*?<option[^>]*selected[^>]*value="(\d+)"[^>]*>.*?</select>"""
                    )
            val languageIdMatch = languageIdRegex.find(htmlContent)
            if (languageIdMatch != null) {
                val languageId = languageIdMatch.groupValues[1].toIntOrNull()
                logDebug("Found language ID: $languageId")
                return languageId
            } else {
                // Fallback pattern for input field (in case the HTML structure changes)
                val fallbackRegex =
                        Regex("""<input[^>]*id="language_id"[^>]*value="(\d+)"[^>]*/>""")
                val fallbackMatch = fallbackRegex.find(htmlContent)
                if (fallbackMatch != null) {
                    val languageId = fallbackMatch.groupValues[1].toIntOrNull()
                    logDebug("Found language ID (fallback): $languageId")
                    return languageId
                } else {
                    logDebug("No language ID found in HTML")
                    return null
                }
            }
        }

        /** Extract sync_status (linking status) from HTML content. */
        internal fun extractSyncStatus(htmlContent: String): Boolean {
            logDebug("Attempting to extract sync_status from HTML")
            logDebug("HTML content length: ${htmlContent.length}")

            // Look for the sync_status checkbox element
            // Pattern to match: <input ... id="sync_status" ... checked ... />
            // or: <input ... checked ... id="sync_status" ... />
            val syncStatusPattern = Regex("""<input[^>]*id\s*=\s*["']sync_status["'][^>]*/?>""")
            val syncStatusMatch = syncStatusPattern.find(htmlContent)

            if (syncStatusMatch != null) {
                val element = syncStatusMatch.value
                logDebug("Found sync_status element: $element")

                // Check if it's checked
                val isChecked = element.contains("checked", ignoreCase = true)
                logDebug("Sync_status element is checked: $isChecked")
                return isChecked
            } else {
                logDebug("No element with id='sync_status' found")
                // Log a larger sample of the HTML to see what we're working with
                val sample = if (htmlContent.length > 3000) htmlContent.take(3000) else htmlContent
                logDebug("HTML sample: $sample")
                return false
            }
        }

        // Simple logging methods using Android Log utilities
        private fun logDebug(message: String) {
            Log.d(TAG, message)
        }

        private fun logError(message: String, throwable: Throwable? = null) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }

        /**
         * Manual parsing of parents JSON as a fallback for unit tests where the Android JSON
         * library might not be available.
         */
        private fun parseParentsManually(jsonString: String): List<String> {
            logDebug("Attempting manual JSON parsing of parents: '$jsonString'")

            val parents = mutableListOf<String>()
            try {
                // Very basic JSON parsing - this is just a fallback for unit tests
                // Remove the outer brackets
                val content = jsonString.trim()
                if (content.startsWith("[") && content.endsWith("]")) {
                    val innerContent = content.substring(1, content.length - 1).trim()
                    if (innerContent.isNotEmpty()) {
                        // Split by }{ to separate multiple objects
                        val objects =
                                innerContent
                                        .split(Regex("\\}\\s*,\\s*\\{"))
                                        .map { obj -> if (!obj.startsWith("{")) "{$obj" else obj }
                                        .map { obj -> if (!obj.endsWith("}")) "$obj}" else obj }

                        for (obj in objects) {
                            // Extract the value field
                            val valueRegex = Regex("\"value\"\\s*:\\s*\"([^\"]*)\"")
                            val valueMatch = valueRegex.find(obj)
                            if (valueMatch != null) {
                                val value = valueMatch.groupValues[1]
                                parents.add(value)
                                logDebug("Found parent (manual parsing): '$value'")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logError("Error in manual JSON parsing of parents: '$jsonString'", e)
            }

            return parents
        }
    }
}
