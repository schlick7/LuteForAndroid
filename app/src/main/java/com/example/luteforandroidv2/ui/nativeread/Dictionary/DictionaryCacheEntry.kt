package com.example.luteforandroidv2.ui.nativeread.Dictionary

import java.util.Date

data class DictionaryCacheEntry(
        val term: String,
        val languageId: Int,
        val dictionaryUrl: String,
        val content: String,
        val timestamp: Date
)
