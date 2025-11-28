package com.example.luteforandroidv2.ui.nativeread

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.luteforandroidv2.ui.nativeread.Term.TextContent
import com.example.luteforandroidv2.ui.nativeread.Term.TextRenderer
import kotlinx.coroutines.launch

class NativeReadViewModel(application: Application) : AndroidViewModel(application) {
    private val _currentPageContent = MutableLiveData<TextContent>()
    val currentPageContent: LiveData<TextContent> = _currentPageContent

    private val _loadingState = MutableLiveData<LoadingState>()
    val loadingState: LiveData<LoadingState> = _loadingState

    private val _errorState = MutableLiveData<String?>()
    val errorState: LiveData<String?> = _errorState

    private val _translationResult = MutableLiveData<String>()
    val translationResult: LiveData<String> = _translationResult

    private val _translationSettings = MutableLiveData<String>()
    val translationSettings: LiveData<String> = _translationSettings

    private val _ankiCardCreated = MutableLiveData<Boolean>()
    val ankiCardCreated: LiveData<Boolean> = _ankiCardCreated

    val repository = NativeReadRepository(application)

    // Store the total page count from the initial probe
    private var totalPagesFromProbe: Int = 0

    /** Load a specific page of a book */
    fun loadBookPage(bookId: String, pageNum: Int) {
        Log.d("NativeReadViewModel", "loadBookPage called with bookId: $bookId, pageNum: $pageNum")
        viewModelScope.launch {
            _loadingState.value = LoadingState.LOADING
            try {
                Log.d(
                        "NativeReadViewModel",
                        "Calling repository.fetchPageContent($bookId, $pageNum)"
                )
                val result = repository.fetchPageContent(bookId, pageNum)
                Log.d("NativeReadViewModel", "Repository result isSuccess: ${result.isSuccess}")
                if (result.isSuccess) {
                    val textContentAndHtml = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Received HTML content, length: ${textContentAndHtml.htmlContent.length}"
                    )
                    // Parse HTML content using TextRenderer
                    val textRenderer = TextRenderer()
                    // Use the stored total page count if we have it
                    val pageMetadata =
                            if (totalPagesFromProbe > 0) {
                                textContentAndHtml.pageMetadata.copy(
                                        pageCount = totalPagesFromProbe
                                )
                            } else {
                                textContentAndHtml.pageMetadata
                            }
                    val textContent =
                            textRenderer.parseHtmlContent(
                                    textContentAndHtml.htmlContent,
                                    pageMetadata
                            )
                    _currentPageContent.value = textContent
                    _loadingState.value = LoadingState.LOADED
                    Log.d("NativeReadViewModel", "Successfully loaded and parsed content")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to fetch page content", error)
                    _errorState.value = error?.message
                    _loadingState.value = LoadingState.ERROR
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception while loading book page", e)
                _errorState.value = e.message
                _loadingState.value = LoadingState.ERROR
            }
        }
    }

    /** Fetch updated term data for highlights without changing the text */
    fun fetchUpdatedTermData(bookId: String, pageNum: Int, callback: (TextContent?) -> Unit) {
        Log.d(
                "NativeReadViewModel",
                "fetchUpdatedTermData called with bookId: $bookId, pageNum: $pageNum"
        )
        viewModelScope.launch {
            try {
                Log.d(
                        "NativeReadViewModel",
                        "Calling repository.fetchPageContent($bookId, $pageNum) for term data"
                )
                val result = repository.fetchPageContent(bookId, pageNum)
                Log.d("NativeReadViewModel", "Repository result isSuccess: ${result.isSuccess}")
                if (result.isSuccess) {
                    val textContentAndHtml = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Received HTML content for term data, length: ${textContentAndHtml.htmlContent.length}"
                    )
                    // Parse HTML content using TextRenderer to get updated term statuses
                    val textRenderer = TextRenderer()
                    // Use the stored total page count if we have it
                    val pageMetadata =
                            if (totalPagesFromProbe > 0) {
                                textContentAndHtml.pageMetadata.copy(
                                        pageCount = totalPagesFromProbe
                                )
                            } else {
                                textContentAndHtml.pageMetadata
                            }
                    val textContent =
                            textRenderer.parseHtmlContent(
                                    textContentAndHtml.htmlContent,
                                    pageMetadata
                            )
                    callback(textContent)
                    Log.d("NativeReadViewModel", "Successfully fetched and parsed term data")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to fetch term data", error)
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception while fetching term data", e)
                callback(null)
            }
        }
    }

    /** Open book to current page with initial probe */
    fun openBookToCurrentPageWithProbe(bookId: String) {
        Log.d("NativeReadViewModel", "openBookToCurrentPageWithProbe called with bookId: $bookId")
        viewModelScope.launch {
            _loadingState.value = LoadingState.LOADING
            try {
                Log.d(
                        "NativeReadViewModel",
                        "Calling repository.openBookToCurrentPage($bookId) for probe"
                )
                val result = repository.openBookToCurrentPage(bookId)
                Log.d(
                        "NativeReadViewModel",
                        "Repository probe result isSuccess: ${result.isSuccess}"
                )
                if (result.isSuccess) {
                    val textContentAndHtml = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Received HTML content for probe, length: ${textContentAndHtml.htmlContent.length}"
                    )

                    // Parse page metadata from HTML content to get the current page number and
                    // total pages
                    val pageMetadata =
                            repository.parsePageMetadataFromHtmlPublic(
                                    textContentAndHtml.htmlContent,
                                    bookId,
                                    1
                            )

                    // Store the total page count for subsequent page loads
                    totalPagesFromProbe = pageMetadata.pageCount
                    Log.d(
                            "NativeReadViewModel",
                            "Stored total page count from probe: $totalPagesFromProbe"
                    )

                    // Now load the actual content for the current page
                    Log.d(
                            "NativeReadViewModel",
                            "Loading actual content for page ${pageMetadata.pageNum}"
                    )
                    loadBookPage(bookId, pageMetadata.pageNum)
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to probe book to current page", error)
                    _errorState.value = error?.message
                    _loadingState.value = LoadingState.ERROR
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception while probing book to current page", e)
                _errorState.value = e.message
                _loadingState.value = LoadingState.ERROR
            }
        }
    }

    /** Get term popup information */
    fun getTermPopup(termId: Int) {
        Log.d("NativeReadViewModel", "getTermPopup called with termId: $termId")
        viewModelScope.launch {
            try {
                Log.d("NativeReadViewModel", "Calling repository.getTermPopup($termId)")
                val result = repository.getTermPopup(termId)
                if (result.isSuccess) {
                    val popupContent = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Term popup retrieved successfully, length: ${popupContent.length}"
                    )
                    _translationResult.value = popupContent
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to get term popup: ${error?.message}")
                    _errorState.value = error?.message
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception during term popup retrieval", e)
                _errorState.value = e.message
            }
        }
    }

    /** Get term popup data directly for batch processing */
    suspend fun getTermPopupData(termId: Int): Result<String> {
        Log.d("NativeReadViewModel", "getTermPopupData called with termId: $termId")
        return try {
            Log.d("NativeReadViewModel", "Calling repository.getTermPopup($termId)")
            val result = repository.getTermPopup(termId)
            if (result.isSuccess) {
                val popupContent = result.getOrNull()!!
                Log.d(
                        "NativeReadViewModel",
                        "Term popup data retrieved successfully, length: ${popupContent.length}"
                )
                Result.success(popupContent)
            } else {
                val error = result.exceptionOrNull()
                Log.e("NativeReadViewModel", "Failed to get term popup data: ${error?.message}")
                Result.failure(error ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("NativeReadViewModel", "Exception during term popup data retrieval", e)
            Result.failure(e)
        }
    }

    /** Get term edit page information */
    fun getTermEditPage(termId: Int) {
        Log.d("NativeReadViewModel", "getTermEditPage called with termId: $termId")
        viewModelScope.launch {
            try {
                Log.d("NativeReadViewModel", "Calling repository.getTermEditPage($termId)")
                val result = repository.getTermEditPage(termId)
                if (result.isSuccess) {
                    val editPageContent = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Term edit page retrieved successfully, length: ${editPageContent.length}"
                    )
                    _translationResult.value = editPageContent
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to get term edit page: ${error?.message}")
                    _errorState.value = error?.message
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception during term edit page retrieval", e)
                _errorState.value = e.message
            }
        }
    }

    /** Translate a page */
    fun translatePage(bookId: String, pageNum: Int, langId: Int) {
        viewModelScope.launch {
            try {
                val result = repository.translatePage(bookId, pageNum, langId)
                if (result.isSuccess) {
                    _translationResult.value = result.getOrNull()!!
                } else {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Get translation settings */
    fun getTranslationSettings() {
        viewModelScope.launch {
            try {
                val result = repository.getTranslationSettings()
                if (result.isSuccess) {
                    _translationSettings.value = result.getOrNull()!!
                } else {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Create Anki card for a term */
    fun createAnkiCard(termId: String) {
        viewModelScope.launch {
            try {
                val result = repository.createAnkiCard(termId)
                if (result.isSuccess) {
                    _ankiCardCreated.value = true
                } else {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Create bulk Anki cards for text */
    fun createBulkAnkiCards(text: String) {
        viewModelScope.launch {
            try {
                val result = repository.createBulkAnkiCards(text)
                if (result.isSuccess) {
                    _ankiCardCreated.value = true
                } else {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Update the status of a term */
    fun updateTermStatus(termId: String, status: Int) {
        viewModelScope.launch {
            try {
                val result = repository.updateTermStatus(termId, status)
                if (result.isFailure) {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Save reading progress */
    fun saveReadingProgress(bookId: String, pageNum: Int, restKnown: Boolean = false) {
        viewModelScope.launch {
            try {
                val result = repository.savePageProgress(bookId, pageNum, restKnown)
                if (result.isFailure) {
                    _errorState.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }

    /** Save reading progress and return the result */
    suspend fun saveReadingProgressAndWait(
            bookId: String,
            pageNum: Int,
            restKnown: Boolean = false
    ): Result<Boolean> {
        return try {
            val result = repository.savePageProgress(bookId, pageNum, restKnown)
            if (result.isFailure) {
                _errorState.value = result.exceptionOrNull()?.message
            }
            result
        } catch (e: Exception) {
            _errorState.value = e.message
            Result.failure(e)
        }
    }

    /** Test if a book has audio by checking the audio stream endpoint */
    suspend fun hasAudioForBook(bookId: String): Result<Boolean> {
        return repository.hasAudioForBook(bookId)
    }

    /** Get a specific book by ID */
    suspend fun getBook(bookId: String): Result<com.example.luteforandroidv2.lute.Book> {
        return repository.getBook(bookId)
    }

    /** Get book title by ID for display in UI */
    suspend fun getBookTitle(bookId: String): Result<String> {
        return repository.getBookTitle(bookId)
    }

    /**
     * Fetch language name from server using language ID This function can be called when the
     * language name is not available in page metadata
     */
    fun fetchLanguageName(languageId: Int, callback: (Result<String>) -> Unit) {
        Log.d("NativeReadViewModel", "fetchLanguageName called with languageId: $languageId")
        viewModelScope.launch {
            try {
                Log.d("NativeReadViewModel", "Calling repository.fetchLanguageName($languageId)")
                val result = repository.fetchLanguageName(languageId)
                if (result.isSuccess) {
                    val languageName = result.getOrNull()!!
                    Log.d(
                            "NativeReadViewModel",
                            "Language name retrieved successfully: $languageName"
                    )
                    callback(Result.success(languageName))
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("NativeReadViewModel", "Failed to get language name: ${error?.message}")
                    callback(Result.failure(error ?: Exception("Unknown error")))
                }
            } catch (e: Exception) {
                Log.e("NativeReadViewModel", "Exception during language name retrieval", e)
                callback(Result.failure(e))
            }
        }
    }
}

/** Represents the loading state of the view */
enum class LoadingState {
    LOADING,
    LOADED,
    ERROR
}
