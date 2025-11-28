package com.example.luteforandroidv2.ui.nativeread.Term

import org.json.JSONObject

/** Data class for term information */
data class TermData(
        val termId: Int,
        val term: String,
        val languageId: Int,
        val translation: String = "",
        val status: Int = 0,
        val parentsList: List<String> = emptyList(),
        val parentTranslations: List<String> = emptyList(), // Add parent translations
        val tapX: Float = -1f,
        val tapY: Float = -1f,
        val segmentId: String = "", // Add segment ID to identify the specific segment
        val sentenceContext: String = "" // Add sentence context for AI processing
) {
    /** Serialize TermData to JSON string */
    fun toJson(): String {
        val jsonObject =
                JSONObject().apply {
                    put("termId", termId)
                    put("term", term)
                    put("languageId", languageId)
                    put("translation", translation)
                    put("status", status)
                    put("parentsList", parentsList.joinToString(","))
                    put(
                            "parentTranslations",
                            parentTranslations.joinToString(",")
                    ) // Add parent translations
                    put("tapX", tapX)
                    put("tapY", tapY)
                    put("segmentId", segmentId)
                    put("sentenceContext", sentenceContext)
                }
        return jsonObject.toString()
    }

    companion object {
        /** Deserialize TermData from JSON string */
        fun fromJson(json: String): TermData {
            val jsonObject = JSONObject(json)
            return TermData(
                    termId = jsonObject.getInt("termId"),
                    term = jsonObject.getString("term"),
                    languageId = jsonObject.getInt("languageId"),
                    translation = jsonObject.getString("translation"),
                    status = jsonObject.getInt("status"),
                    parentsList =
                            jsonObject.getString("parentsList").split(",").filter {
                                it.isNotEmpty()
                            },
                    parentTranslations =
                            jsonObject.getString("parentTranslations").split(",").filter {
                                it.isNotEmpty()
                            }, // Add parent translations
                    tapX = jsonObject.optDouble("tapX", -1.0).toFloat(),
                    tapY = jsonObject.optDouble("tapY", -1.0).toFloat(),
                    segmentId = jsonObject.optString("segmentId", ""),
                    sentenceContext = jsonObject.optString("sentenceContext", "")
            )
        }
    }
}
