package com.example.luteforandroidv2.ui.nativeread.Term

import org.json.JSONObject

/** Data class for term form data */
data class TermFormData(
        val termId: Int?, // null for new terms
        val termText: String,
        val languageId: Int,
        val context: String,
        val translation: String,
        val status: Int,
        val parents: List<String>,
        val tags: List<String>,
        val isLinked: Boolean = false // Whether the term is linked to its parents
) {
    /** Serialize TermFormData to JSON string */
    fun toJson(): String {
        val jsonObject =
                JSONObject().apply {
                    put("termId", termId ?: JSONObject.NULL)
                    put("termText", termText)
                    put("languageId", languageId)
                    put("context", context)
                    put("translation", translation)
                    put("status", status)
                    put("parents", parents.joinToString(","))
                    put("tags", tags.joinToString(","))
                    put("isLinked", isLinked)
                }
        return jsonObject.toString()
    }

    companion object {
        /** Deserialize TermFormData from JSON string */
        fun fromJson(json: String): TermFormData {
            val jsonObject = JSONObject(json)
            return TermFormData(
                    termId = if (jsonObject.isNull("termId")) null else jsonObject.getInt("termId"),
                    termText = jsonObject.getString("termText"),
                    languageId = jsonObject.getInt("languageId"),
                    context = jsonObject.getString("context"),
                    translation = jsonObject.getString("translation"),
                    status = jsonObject.getInt("status"),
                    parents = jsonObject.getString("parents").split(",").filter { it.isNotEmpty() },
                    tags = jsonObject.getString("tags").split(",").filter { it.isNotEmpty() },
                    isLinked = jsonObject.optBoolean("isLinked", false)
            )
        }
    }
}
