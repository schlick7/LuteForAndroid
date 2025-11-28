package com.example.luteforandroidv2.lute

// Data class for editable book information
data class EditableBook(
        val id: Int,
        var title: String,
        var text: String,
        var languageId: Int,
        var languageName: String,
        var tags: String // Comma-separated tags
)
