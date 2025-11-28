package com.example.luteforandroidv2.ui.nativeread.Dictionary

data class LanguageInfo(val langId: Int, val name: String)

data class DictionaryInfo(
        val dictUri: String,
        val useFor: String,
        val isActive: Boolean,
        val displayName: String = extractDisplayName(dictUri),
        val sourceType: DictionarySourceType = DictionarySourceType.INTERNAL
) {
    companion object {
        private fun extractDisplayName(uri: String): String {
            return try {
                val url = java.net.URL(uri)
                val host = url.host
                if (host != null) {
                    val parts = host.split(".")
                    if (parts.size >= 2) {
                        parts[parts.size - 2].replaceFirstChar(Char::titlecase)
                    } else {
                        host
                    }
                } else {
                    // Fallback for file-based URLs or other cases
                    val parts = uri.split("/")
                    parts.lastOrNull()?.split(".")?.firstOrNull() ?: "Dictionary"
                }
            } catch (e: Exception) {
                "Dictionary"
            }
        }
    }
}

enum class DictionarySourceType {
    INTERNAL, // Lute's built-in dictionaries
    EXTERNAL_WEB, // External web-based dictionaries
    OFFLINE // Offline dictionaries stored locally
}
