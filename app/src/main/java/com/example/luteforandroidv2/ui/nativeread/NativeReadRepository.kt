package com.example.luteforandroidv2.ui.nativeread

import android.content.Context
import android.util.Log
import com.example.luteforandroidv2.lute.Book
import com.example.luteforandroidv2.lute.LuteApiClient
import com.example.luteforandroidv2.ui.nativeread.Bookmark.Bookmark
import com.example.luteforandroidv2.ui.nativeread.Term.HtmlTextContent
import com.example.luteforandroidv2.ui.nativeread.Term.PageMetadata
import java.io.IOException
import kotlinx.coroutines.delay
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Repository for the native reading view Handles data access for book content, term management, and
 * reading progress
 */
class NativeReadRepository(context: Context) {
    private val apiService by lazy { LuteApiClient.getInstance(context).apiService }
    private val TAG = "NativeReadRepository"

    /** Fetch page content from the server */
    suspend fun fetchPageContent(bookId: String, pageNum: Int): Result<HtmlTextContent> {
        return try {
            Log.d(TAG, "Fetching page content for book $bookId, page $pageNum")

            // First try the start_reading endpoint to get actual text content
            Log.d(TAG, "Calling apiService.startReading($bookId, $pageNum)")
            val response: Response<ResponseBody> = apiService.startReading(bookId, pageNum)

            if (response.isSuccessful) {
                val htmlContent = response.body()?.string() ?: ""
                Log.d(
                        TAG,
                        "Successfully fetched page content via startReading, length: ${htmlContent.length}"
                )

                // Parse page metadata from HTML content
                val pageMetadata = parsePageMetadataFromHtml(htmlContent, bookId, pageNum)

                Result.success(HtmlTextContent(htmlContent, pageMetadata))
            } else {
                Log.e(
                        TAG,
                        "Failed to fetch page content via startReading, code: ${response.code()}"
                )
                // Fall back to the regular read page endpoint
                Log.d(TAG, "Falling back to getReadPage($bookId, $pageNum)")
                val fallbackResponse: Response<ResponseBody> =
                        apiService.getReadPage(bookId, pageNum)

                if (fallbackResponse.isSuccessful) {
                    val htmlContent = fallbackResponse.body()?.string() ?: ""
                    Log.d(
                            TAG,
                            "Successfully fetched page content via getReadPage, length: ${htmlContent.length}"
                    )

                    // Parse page metadata from HTML content
                    val pageMetadata = parsePageMetadataFromHtml(htmlContent, bookId, pageNum)

                    Result.success(HtmlTextContent(htmlContent, pageMetadata))
                } else {
                    Log.e(
                            TAG,
                            "Failed to fetch page content via getReadPage, code: ${fallbackResponse.code()}"
                    )
                    Result.failure(
                            Exception(
                                    "Failed to fetch page content: ${fallbackResponse.code()} - ${fallbackResponse.message()}"
                            )
                    )
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching page content", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching page content", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Open book to current page */
    suspend fun openBookToCurrentPage(bookId: String): Result<HtmlTextContent> {
        return try {
            Log.d(TAG, "Opening book $bookId to current page")

            val response: Response<ResponseBody> = apiService.openBookToCurrentPage(bookId)

            if (response.isSuccessful) {
                val htmlContent = response.body()?.string() ?: ""
                Log.d(
                        TAG,
                        "Successfully opened book to current page, length: ${htmlContent.length}"
                )

                // Parse page metadata from HTML content to get the current page number
                val pageMetadata =
                        parsePageMetadataFromHtml(
                                htmlContent,
                                bookId,
                                1
                        ) // Default to page 1, will be updated by parsePageMetadataFromHtml

                Result.success(HtmlTextContent(htmlContent, pageMetadata))
            } else {
                Log.e(TAG, "Failed to open book to current page, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to open book to current page: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while opening book to current page", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while opening book to current page", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Parse page metadata from HTML content */
    fun parsePageMetadataFromHtmlPublic(
            htmlContent: String,
            bookId: String,
            pageNum: Int
    ): PageMetadata {
        return parsePageMetadataFromHtml(htmlContent, bookId, pageNum)
    }

    /** Parse page metadata from HTML content */
    private fun parsePageMetadataFromHtml(
            htmlContent: String,
            bookId: String,
            pageNum: Int
    ): PageMetadata {
        return try {
            Log.d(TAG, "Parsing page metadata from HTML content, length: ${htmlContent.length}")
            // Log the first 1000 characters of HTML for debugging
            val htmlPreview = htmlContent.take(1000)
            Log.d(TAG, "HTML content preview: $htmlPreview")

            // Parse page number from hidden input field (more flexible pattern)
            val pageNumPattern =
                    Regex(
                            """<input[^>]*id=(?:["']page_num["'])[^>]*value=(?:["'](\d+)["'])[^>]*/?>"""
                    )
            val pageNumMatch = pageNumPattern.find(htmlContent)
            val actualPageNum =
                    if (pageNumMatch != null) {
                        Log.d(TAG, "Found page_num match: ${pageNumMatch.groupValues[1]}")
                        pageNumMatch.groupValues[1].toIntOrNull() ?: pageNum
                    } else {
                        Log.d(TAG, "No page_num match found, using default: $pageNum")
                        pageNum
                    }

            // Parse page count from hidden input field (more flexible pattern)
            val pageCountPattern =
                    Regex(
                            """<input[^>]*id=(?:["']page_count["'])[^>]*value=(?:["'](\d+)["'])[^>]*/?>"""
                    )
            val pageCountMatch = pageCountPattern.find(htmlContent)
            val pageCount =
                    if (pageCountMatch != null) {
                        Log.d(TAG, "Found page_count match: ${pageCountMatch.groupValues[1]}")
                        pageCountMatch.groupValues[1].toIntOrNull() ?: 10
                    } else {
                        Log.d(TAG, "No page_count match found, trying JavaScript variable")
                        // Try to find page count in JavaScript variables as fallback
                        val jsPattern = Regex("""var\s+page_count\s*=\s*(\d+);""")
                        val jsMatch = jsPattern.find(htmlContent)
                        if (jsMatch != null) {
                            Log.d(TAG, "Found page_count in JavaScript: ${jsMatch.groupValues[1]}")
                            jsMatch.groupValues[1].toIntOrNull() ?: 10
                        } else {
                            Log.d(TAG, "No page_count found in JavaScript, using default: 10")
                            // Default to 10 if not found
                            10
                        }
                    }

            // Look for audio availability
            val hasAudio =
                    htmlContent.contains("audio-player-container") ||
                            htmlContent.contains("book_audio_file")

            // Look for RTL (right-to-left) indicator
            val isRTL = htmlContent.contains("dir=\"rtl\"") || htmlContent.contains("class=\"rtl\"")

            Log.d(
                    TAG,
                    "Parsed page metadata - pageNum: $actualPageNum, pageCount: $pageCount, hasAudio: $hasAudio, isRTL: $isRTL"
            )

            // Extract language ID and name from HTML if available
            var languageId = 1 // Default language ID
            var languageName = "" // Default language name

            // Try to extract language ID from form data or JavaScript
            val langIdPattern =
                    Regex(
                            """<input[^>]*name=(?:["']langid["'])[^>]*value=(?:["'](\d+)["'])[^>]*/?>"""
                    )
            val langIdMatch = langIdPattern.find(htmlContent)
            if (langIdMatch != null) {
                languageId = langIdMatch.groupValues[1].toIntOrNull() ?: 1
                Log.d(TAG, "Found language ID from input field: $languageId")
            } else {
                // Try alternative pattern for language ID extraction
                val altLangIdPattern = Regex("""var\\s+current_language_id\\s*=\\s*(\\d+);""")
                val altLangIdMatch = altLangIdPattern.find(htmlContent)
                if (altLangIdMatch != null) {
                    languageId = altLangIdMatch.groupValues[1].toIntOrNull() ?: 1
                    Log.d(TAG, "Found language ID from JavaScript variable: $languageId")
                } else {
                    // Try additional patterns for language ID extraction
                    Log.d(
                            TAG,
                            "No language ID found with JavaScript pattern, trying additional patterns"
                    )

                    // Try to find language ID in data attributes
                    val dataLangPattern = Regex("""data-lang(?:uage)?-?id=["'](\d+)["']""")
                    val dataLangMatch = dataLangPattern.find(htmlContent)
                    if (dataLangMatch != null) {
                        languageId = dataLangMatch.groupValues[1].toIntOrNull() ?: 1
                        Log.d(TAG, "Found language ID from data attribute: $languageId")
                    } else {
                        // Try to find language ID in form action
                        val formActionPattern = Regex("""action=["'][^"']*langid=(\d+)["']""")
                        val formActionMatch = formActionPattern.find(htmlContent)
                        if (formActionMatch != null) {
                            languageId = formActionMatch.groupValues[1].toIntOrNull() ?: 1
                            Log.d(TAG, "Found language ID from form action: $languageId")
                        } else {
                            // Try generic patterns for language ID
                            val genericLangIdPattern =
                                    Regex("""["']?(?:lang(?:uage)?_?id)["']?\s*[:=]\s*(\d+)""")
                            val genericMatch = genericLangIdPattern.find(htmlContent)
                            if (genericMatch != null) {
                                languageId = genericMatch.groupValues[1].toIntOrNull() ?: 1
                                Log.d(TAG, "Found language ID from generic pattern: $languageId")
                            } else {
                                Log.d(
                                        TAG,
                                        "No language ID found in HTML, using default: $languageId"
                                )
                                // Add debugging to see what the HTML actually contains
                                if (htmlContent.contains("langid") ||
                                                htmlContent.contains("language")
                                ) {
                                    Log.d(
                                            TAG,
                                            "HTML contains language references, logging snippet for analysis"
                                    )
                                    val htmlSnippet = htmlContent.take(3000)
                                    Log.d(TAG, "HTML snippet: $htmlSnippet")
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "FINAL PARSED LANGUAGE ID: $languageId")

            // Try to extract language name from form data or JavaScript
            val langNamePattern =
                    Regex(
                            """<input[^>]*name=(?:["']langname["'])[^>]*value=(?:["']([^"']*)["'])[^>]*/?>"""
                    )
            val langNameMatch = langNamePattern.find(htmlContent)
            if (langNameMatch != null) {
                languageName = langNameMatch.groupValues[1]
                Log.d(TAG, "Found language name: $languageName")
            } else {
                Log.d(TAG, "No language name found in HTML")
            }

            PageMetadata(
                            bookId = bookId,
                            pageNum = actualPageNum,
                            pageCount = pageCount,
                            hasAudio = hasAudio,
                            isRTL = isRTL,
                            languageId = languageId,
                            languageName = languageName
                    )
                    .also {
                        Log.d(
                                TAG,
                                "Created PageMetadata with language ID: $languageId, language name: '$languageName'"
                        )
                    }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing page metadata from HTML", e)
            // Return a default PageMetadata in case of error
            PageMetadata(
                    bookId = bookId,
                    pageNum = pageNum,
                    pageCount = 10, // Default to 10 pages
                    hasAudio = false,
                    isRTL = false,
                    languageId = 1, // Default language ID
                    languageName = ""
            )
        }
    }

    /**
     * Fetch language name from server using language ID This function can be called from the
     * ViewModel when needed
     */
    suspend fun fetchLanguageName(languageId: Int): Result<String> {
        return try {
            Log.d(TAG, "Fetching language name for language ID: $languageId")
            val response: Response<ResponseBody> = apiService.getLanguageById(languageId)

            if (response.isSuccessful) {
                val htmlContent = response.body()?.string()
                if (!htmlContent.isNullOrEmpty()) {
                    // Parse the language name from the HTML response
                    val langNameRegex =
                            Regex(
                                    """<input[^>]*id=["']lang_name["'][^>]*value=["']([^"']*)["'][^>]*/>"""
                            )
                    val langNameMatcher = langNameRegex.find(htmlContent)
                    if (langNameMatcher != null) {
                        val languageName = langNameMatcher.groupValues[1]
                        Log.d(TAG, "Successfully fetched language name: $languageName")
                        Result.success(languageName)
                    } else {
                        Log.d(TAG, "Could not parse language name from response")
                        Result.success("")
                    }
                } else {
                    Log.d(TAG, "Empty response from language endpoint")
                    Result.success("")
                }
            } else {
                Log.e(TAG, "Failed to fetch language info, code: ${response.code()}")
                Result.failure(Exception("Failed to fetch language info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching language name from server", e)
            Result.failure(e)
        }
    }

    /** Get term popup information */
    suspend fun getTermPopup(termId: Int): Result<String> {
        return try {
            Log.d(TAG, "Getting term popup for term ID: $termId")
            val response: Response<ResponseBody> = apiService.getTermPopup(termId)

            if (response.isSuccessful) {
                val popupContent = response.body()?.string() ?: ""
                Log.d(
                        TAG,
                        "Successfully retrieved term popup content, length: ${popupContent.length}"
                )
                Result.success(popupContent)
            } else {
                Log.e(
                        TAG,
                        "Failed to get term popup, code: ${response.code()}, message: ${response.message()}"
                )
                Result.failure(
                        Exception(
                                "Failed to get term popup: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while getting term popup", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while getting term popup", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Get term edit page information */
    suspend fun getTermEditPage(termId: Int): Result<String> {
        return try {
            Log.d(TAG, "Getting term edit page for term ID: $termId")
            val response: Response<ResponseBody> = apiService.getTermEditPage(termId)

            if (response.isSuccessful) {
                val editPageContent = response.body()?.string() ?: ""
                Log.d(
                        TAG,
                        "Successfully retrieved term edit page content, length: ${editPageContent.length}"
                )
                Result.success(editPageContent)
            } else {
                Log.e(
                        TAG,
                        "Failed to get term edit page, code: ${response.code()}, message: ${response.message()}"
                )
                Result.failure(
                        Exception(
                                "Failed to get term edit page: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while getting term edit page", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while getting term edit page", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Save page progress to the server */
    suspend fun savePageProgress(
            bookId: String,
            pageNum: Int,
            restKnown: Boolean = false
    ): Result<Boolean> {
        // Attempt with retry logic for better robustness
        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                Log.d(
                        TAG,
                        "Marking page $pageNum of book $bookId as read, restKnown: $restKnown (attempt $attempt)"
                )
                val requestBody =
                        com.example.luteforandroidv2.lute.PageDoneRequest(
                                bookid = bookId,
                                pagenum = pageNum,
                                restknown = restKnown
                        )
                val response: Response<ResponseBody> = apiService.markPageAsRead(requestBody)

                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully marked page as read on attempt $attempt")
                    return Result.success(true)
                } else {
                    Log.e(
                            TAG,
                            "Failed to mark page as read on attempt $attempt, code: ${response.code()}"
                    )
                    // Don't retry on HTTP error codes, just return failure
                    return Result.failure(
                            Exception(
                                    "Failed to mark page as read: ${response.code()} - ${response.message()}"
                            )
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error while marking page as read on attempt $attempt", e)
                lastException = Exception("Network error: ${e.message}")

                // If this isn't the last attempt, wait before retrying
                if (attempt < maxRetries) {
                    // Exponential backoff: wait 1s, then 2s, then 4s between attempts
                    val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                    kotlinx.coroutines.delay(delayMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while marking page as read on attempt $attempt", e)
                lastException = Exception("Unexpected error: ${e.message}")
                // Don't retry on general exceptions
                break
            }
        }

        // If we get here, all retries have failed
        return Result.failure(
                lastException ?: Exception("Unknown error after $maxRetries attempts")
        )
    }

    /** Save audio player data to the server */
    suspend fun savePlayerData(
            bookId: String,
            position: Long,
            playbackRate: Float
    ): Result<Boolean> {
        return try {
            Log.d(
                    TAG,
                    "Saving player data for book $bookId, position: $position, rate: $playbackRate"
            )
            val response: Response<ResponseBody> =
                    apiService.savePlayerData(bookId, position, playbackRate)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully saved player data")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to save player data, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to save player data: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while saving player data", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while saving player data", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Get bookmarks for a book */
    suspend fun getBookmarks(bookId: String): Result<List<Bookmark>> {
        return try {
            Log.d(TAG, "Fetching bookmarks for book $bookId")
            val response: Response<ResponseBody> = apiService.getBookmarks(bookId)

            if (response.isSuccessful) {
                // In a full implementation, we would parse the response into Bookmark objects
                Log.d(TAG, "Successfully fetched bookmarks")
                Result.success(emptyList()) // Return empty list for now
            } else {
                Log.e(TAG, "Failed to fetch bookmarks, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to fetch bookmarks: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching bookmarks", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching bookmarks", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Add a bookmark */
    suspend fun addBookmark(bookId: String, position: Long): Result<Boolean> {
        return try {
            Log.d(TAG, "Adding bookmark for book $bookId at position $position")
            val response: Response<ResponseBody> = apiService.addBookmark(bookId, position)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully added bookmark")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to add bookmark, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to add bookmark: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while adding bookmark", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while adding bookmark", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Delete a bookmark */
    suspend fun deleteBookmark(bookmarkId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Deleting bookmark $bookmarkId")
            val response: Response<ResponseBody> = apiService.deleteBookmark(bookmarkId)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully deleted bookmark")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete bookmark, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to delete bookmark: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while deleting bookmark", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while deleting bookmark", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Translate a page */
    suspend fun translatePage(bookId: String, pageNum: Int, langId: Int): Result<String> {
        return try {
            Log.d(TAG, "Translating page $pageNum of book $bookId, language ID: $langId")
            // In a full implementation, this would make a network call to translate the page
            // For now, we'll just return a mock translation
            Result.success("Translated page content for page $pageNum")
        } catch (e: Exception) {
            Log.e(TAG, "Error translating page", e)
            Result.failure(Exception("Error translating page: ${e.message}"))
        }
    }

    /** Get translation settings */
    suspend fun getTranslationSettings(): Result<String> {
        return try {
            Log.d(TAG, "Fetching translation settings")
            val response: Response<ResponseBody> = apiService.getTranslationSettings()

            if (response.isSuccessful) {
                val settings = response.body()?.string() ?: ""
                Log.d(TAG, "Successfully fetched translation settings")
                Result.success(settings)
            } else {
                Log.e(TAG, "Failed to fetch translation settings, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to fetch translation settings: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching translation settings", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching translation settings", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Create Anki card for a term */
    suspend fun createAnkiCard(termId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Creating Anki card for term $termId")
            val response: Response<ResponseBody> = apiService.createAnkiCard(termId)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully created Anki card")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to create Anki card, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to create Anki card: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while creating Anki card", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while creating Anki card", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Create bulk Anki cards for text */
    suspend fun createBulkAnkiCards(text: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Creating bulk Anki cards for text: $text")
            val response: Response<ResponseBody> = apiService.createBulkAnkiCards(text)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully created bulk Anki cards")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to create bulk Anki cards, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to create bulk Anki cards: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while creating bulk Anki cards", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while creating bulk Anki cards", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /** Update term status on the server */
    suspend fun updateTermStatus(termId: String, status: Int): Result<Boolean> {
        // TODO: Implement updating term status
        // This will make a POST request to /term/update_status/{termid}/{status}
        return Result.failure(NotImplementedError("Not implemented yet"))
    }

    /** Test if a book has audio by checking the audio stream endpoint */
    suspend fun hasAudioForBook(bookId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Testing audio availability for book $bookId")
            val response = apiService.testAudioStream(bookId)

            // Consider the book has audio if we get a successful response (200)
            // A 404 would mean no audio file exists for this book
            // A 500 would mean there's an internal server error (e.g., null audio filename)
            val hasAudio = response.isSuccessful && response.code() == 200

            Log.d(
                    TAG,
                    "Audio endpoint for book $bookId returned ${response.code()}, hasAudio: $hasAudio"
            )

            Result.success(hasAudio)
        } catch (e: IOException) {
            Log.e(TAG, "Network error while testing audio availability", e)
            // On network error, return false so we don't show an audio player that can't work
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while testing audio availability", e)
            // On any error, return false as a safe default
            Result.success(false)
        }
    }

    /*
    /** Get list of all books */
    suspend fun getBooks(): Result<List<Book>> {
        return try {
            Log.d(TAG, "Fetching list of books")
            val response = apiService.getBooks()

            if (response.isSuccessful) {
                val books = response.body() ?: emptyList()
                Log.d(TAG, "Successfully fetched ${books.size} books")
                Result.success(books)
            } else {
                Log.e(TAG, "Failed to fetch books, code: ${response.code()}")
                Result.failure(Exception("Failed to fetch books: ${response.code()}"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching books", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching books", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }
    */

    /** Get a specific book by ID */
    suspend fun getBook(bookId: String): Result<Book> {
        return try {
            Log.d(TAG, "Fetching book with ID: $bookId")
            val response = apiService.getBook(bookId)

            if (response.isSuccessful) {
                val htmlContent = response.body()?.string()
                if (!htmlContent.isNullOrEmpty()) {
                    // Parse the book title from the HTML content
                    val document = org.jsoup.Jsoup.parse(htmlContent)

                    // Find the book title in the input field with id="title"
                    var title = ""
                    val titleInput = document.select("input#title[name=title]").first()
                    if (titleInput != null) {
                        title = titleInput.attr("value")
                    }

                    // If not found in input field, try other common patterns
                    if (title.isEmpty()) {
                        val h2Elements = document.select("h2")
                        for (element in h2Elements) {
                            val text = element.text()
                            if (text.startsWith("Book: ")) {
                                title = text.substring("Book: ".length)
                                break
                            }
                        }
                    }

                    // If still not found, try other common patterns
                    if (title.isEmpty()) {
                        val titleElement = document.select("div.container h2").first()
                        if (titleElement != null) {
                            title = titleElement.text()
                        }
                    }

                    if (title.isEmpty()) {
                        Log.e(TAG, "Could not parse book title from HTML for book ID: $bookId")
                        Result.failure(Exception("Could not parse book title from HTML"))
                    } else {
                        // Create a Book instance with the parsed title
                        val book =
                                Book(
                                        id = bookId.toIntOrNull() ?: 0,
                                        title = title,
                                        language = "", // Language not available from this endpoint
                                        wordCount = 0 // Word count not available from this endpoint
                                )
                        Log.d(TAG, "Successfully fetched book with title: $title")
                        Result.success(book)
                    }
                } else {
                    Log.e(TAG, "Received empty HTML content for book ID: $bookId")
                    Result.failure(Exception("Received empty HTML content"))
                }
            } else {
                Log.e(TAG, "Failed to fetch book, code: ${response.code()}")
                Result.failure(
                        Exception(
                                "Failed to fetch book: ${response.code()} - ${response.message()}"
                        )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error while fetching book", e)
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching book", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /**
     * Get book title from reading page for display in UI This uses the existing getBook method
     * which should reliably extract the title
     */
    suspend fun getBookTitle(bookId: String): Result<String> {
        return try {
            Log.d(TAG, "Fetching book title for book ID: $bookId")

            // Use the existing getBook method which should work for getting the book title
            val bookResult = getBook(bookId)
            if (bookResult.isSuccess) {
                val book = bookResult.getOrNull()
                if (book != null) {
                    val title = book.title
                    Log.d(TAG, "Successfully got book title: $title")
                    Result.success(title)
                } else {
                    Log.e(TAG, "getBook returned success but book was null for book ID: $bookId")
                    Result.failure(Exception("getBook returned success but book was null"))
                }
            } else {
                Log.e(
                        TAG,
                        "getBook failed for book ID: $bookId, error: ${bookResult.exceptionOrNull()?.message}"
                )
                Result.failure(
                        Exception("getBook failed: ${bookResult.exceptionOrNull()?.message}")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while fetching book title", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }
}
