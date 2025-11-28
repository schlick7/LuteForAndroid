package com.example.luteforandroidv2.ui.nativeread.Bookmark

/** Data class for Bookmark */
data class Bookmark(
        val id: String,
        val bookId: String,
        val position: Long,
        val title: String,
        val label: String = "",
        val timestamp: Long = System.currentTimeMillis()
)
