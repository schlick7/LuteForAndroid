package com.example.luteforandroidv2.ui.books

import android.webkit.JavascriptInterface

class BookSelectionInterface(private val listener: BookSelectionListener) {
    @JavascriptInterface
    fun onBookSelected(bookId: String) {
        // Notify the listener that a book was selected
        listener.onBookSelected(bookId)
    }
    
    @JavascriptInterface
    fun testConnection() {
        // Test function to verify the interface is working
        android.util.Log.d("BookSelectionInterface", "JavaScript interface is working")
    }
    
    interface BookSelectionListener {
        fun onBookSelected(bookId: String)
    }
}