package com.example.luteforandroidv2.ui.nativeread.Dictionary

import android.content.Context
import android.util.Log
import java.util.Date

class DictionaryCacheManager private constructor(context: Context) {
    private val cache = mutableMapOf<String, DictionaryCacheEntry>()
    private val cacheTimeout: Long = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

    companion object {
        private var instance: DictionaryCacheManager? = null

        fun getInstance(context: Context): DictionaryCacheManager {
            if (instance == null) {
                instance = DictionaryCacheManager(context.applicationContext)
            }
            return instance!!
        }
    }

    fun getCachedContent(term: String, languageId: Int, dictionaryUrl: String): String? {
        val key = generateKey(term, languageId, dictionaryUrl)
        val entry = cache[key]

        if (entry != null) {
            val now = Date()
            val diff = now.time - entry.timestamp.time

            if (diff < cacheTimeout) {
                Log.d("DictionaryCache", "Cache hit for key: $key")
                return entry.content
            } else {
                Log.d("DictionaryCache", "Cache expired for key: $key")
                cache.remove(key)
            }
        }

        Log.d("DictionaryCache", "Cache miss for key: $key")
        return null
    }

    fun cacheContent(term: String, languageId: Int, dictionaryUrl: String, content: String) {
        val key = generateKey(term, languageId, dictionaryUrl)
        val entry = DictionaryCacheEntry(term, languageId, dictionaryUrl, content, Date())
        cache[key] = entry
        Log.d("DictionaryCache", "Cached content for key: $key")
    }

    fun clearCache() {
        cache.clear()
        Log.d("DictionaryCache", "Cache cleared")
    }

    private fun generateKey(term: String, languageId: Int, dictionaryUrl: String): String {
        return "$term:$languageId:$dictionaryUrl"
    }
}
