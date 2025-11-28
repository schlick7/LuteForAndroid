package com.example.luteforandroidv2.ui.read

data class DictionaryEntry(
    val term: String,
    val translation: String,
    val definition: String,
    val examples: List<String>,
    val pronunciation: String?,
    val partOfSpeech: String?,
    val synonyms: List<String>,
    val antonyms: List<String>
)