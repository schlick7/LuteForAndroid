package com.example.luteforandroidv2.ui.nativebooks

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.luteforandroidv2.lute.Book
import com.example.luteforandroidv2.lute.LuteRepository
import kotlinx.coroutines.launch

class NativeBooksViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "NativeBooksViewModel"

    private val repository = LuteRepository(application)

    // State management
    private val _books = MutableLiveData<List<Book>>()
    val books: LiveData<List<Book>> = _books

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isEmpty = MutableLiveData<Boolean>()
    val isEmpty: LiveData<Boolean> = _isEmpty

    // Refresh books data
    fun refreshBooks() {
        viewModelScope.launch { fetchBooks() }
    }

    // Initial load of books data
    fun loadBooks() {
        // Only load if we don't already have data
        if (_books.value == null || _books.value!!.isEmpty()) {
            refreshBooks()
        }
    }

    // Private method to fetch books from repository
    private suspend fun fetchBooks() {
        try {
            _isLoading.value = true
            _errorMessage.value = null

            Log.d(TAG, "Fetching books from repository")
            val books = repository.getBooksFromHtml()

            // Use the elvis operator to ensure we never assign null to _books.value
            _books.value = books ?: emptyList()
            if (books != null) {
                Log.d(TAG, "Successfully fetched ${books.size} books")
                _isEmpty.value = books.isEmpty()
            } else {
                Log.e(TAG, "Failed to fetch books, repository returned null")
                _errorMessage.value = "Failed to load books"
                _isEmpty.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while fetching books", e)
            _errorMessage.value = "Error loading books: ${e.message}"
            _isEmpty.value = true
        } finally {
            _isLoading.value = false
        }
    }
}
