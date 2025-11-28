package com.example.luteforandroidv2.ui.nativeread.Bookmark

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Book state manager for the native reading view Handles persistence of book reading state (book
 * ID, page number)
 */
class BookStateManager(private val context: Context) {
    private val prefs: SharedPreferences =
            context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_BOOK_ID = "last_book_id"
        private const val KEY_LAST_PAGE_NUMBER = "last_page_number"
        private const val KEY_LAST_READ_TIMESTAMP = "last_read_timestamp"
    }

    /** Save current book state */
    fun saveCurrentBookState(bookId: String, pageNum: Int) {
        with(prefs.edit()) {
            putString(KEY_LAST_BOOK_ID, bookId)
            putInt(KEY_LAST_PAGE_NUMBER, pageNum)
            putLong(KEY_LAST_READ_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        Log.d("BookStateManager", "Saved book state: ID=$bookId, Page=$pageNum")
    }

    /** Load last book state */
    fun loadLastBookState(): BookState? {
        val bookId = prefs.getString(KEY_LAST_BOOK_ID, null)
        val pageNum = prefs.getInt(KEY_LAST_PAGE_NUMBER, 1)
        val timestamp = prefs.getLong(KEY_LAST_READ_TIMESTAMP, 0)

        return if (bookId != null) {
            BookState(bookId, pageNum, timestamp)
        } else {
            null
        }
    }

    /** Clear book state */
    fun clearBookState() {
        with(prefs.edit()) {
            remove(KEY_LAST_BOOK_ID)
            remove(KEY_LAST_PAGE_NUMBER)
            remove(KEY_LAST_READ_TIMESTAMP)
            apply()
        }
        Log.d("BookStateManager", "Cleared book state")
    }

    /** Update only the page number in the current book state */
    fun updateCurrentPageNumber(pageNum: Int) {
        val bookId = prefs.getString(KEY_LAST_BOOK_ID, null)
        if (bookId != null) {
            with(prefs.edit()) {
                putInt(KEY_LAST_PAGE_NUMBER, pageNum)
                putLong(KEY_LAST_READ_TIMESTAMP, System.currentTimeMillis())
                apply()
            }
            Log.d("BookStateManager", "Updated page number to $pageNum")
        }
    }

    /** Get the current page for a specific book, defaulting to 1 if not found */
    fun getCurrentPage(bookId: String): Int {
        val lastBookId = prefs.getString(KEY_LAST_BOOK_ID, null)
        return if (lastBookId == bookId) {
            prefs.getInt(KEY_LAST_PAGE_NUMBER, 1)
        } else {
            1
        }
    }
}

/** Data class for book state information */
data class BookState(val bookId: String, val pageNumber: Int, val timestamp: Long)
