package com.example.luteforandroidv2.lute

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LuteRepository(private val context: Context) {
    private val apiService by lazy { LuteApiClient.getInstance(context).apiService }
    private val TAG = "LuteRepository"

    /*
    suspend fun getBooks(): List<Book>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API call to fetch books")
                val response = apiService.getBooks()
                Log.d(TAG, "API response received, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val books = response.body()
                    Log.d(TAG, "Books body parsed, count: ${books?.size}")
                    books
                } else {
                    Log.e(
                            TAG,
                            "API response not successful, code: ${response.code()}, error: ${response.errorBody()?.string()}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call", e)
                null
            }
        }
    }
    */

    suspend fun getBook(bookId: String): Book? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API call to fetch book with ID: $bookId")
                val response = apiService.getBook(bookId)
                Log.d(TAG, "API response received, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val htmlContent = response.body()?.string()
                    Log.d(TAG, "Book HTML content received, length: ${htmlContent?.length}")

                    // Parse the book title from the HTML content
                    htmlContent?.let { parseBookFromHtml(it, bookId) }
                } else {
                    Log.e(
                            TAG,
                            "API response not successful, code: ${response.code()}, error: ${response.errorBody()?.string()}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call to fetch single book", e)
                null
            }
        }
    }

    private fun parseBookFromHtml(htmlContent: String, bookId: String): Book? {
        try {
            val document = org.jsoup.Jsoup.parse(htmlContent)

            // Find the book title in the HTML
            // Try multiple approaches to find the book title

            var title = ""

            // Approach 1: Look for title in the h2 tag with "Book: " prefix
            val h2Elements = document.select("h2")
            for (element in h2Elements) {
                val text = element.text().trim()
                if (text.startsWith("Book: ")) {
                    title = text.substring("Book: ".length).trim()
                    break
                }
            }

            // Approach 2: If not found, look for h2 tags within containers
            if (title.isEmpty()) {
                val titleElements = document.select("div.container h2")
                for (element in titleElements) {
                    val text = element.text().trim()
                    if (text.startsWith("Book: ")) {
                        title = text.substring("Book: ".length).trim()
                        break
                    } else if (text.isNotEmpty()) {
                        // If it's just an h2 with text (no "Book: " prefix), use it as title
                        title = text
                        break
                    }
                }
            }

            // Approach 3: Look for any h2 element that might contain the title directly
            if (title.isEmpty()) {
                for (element in h2Elements) {
                    val text = element.text().trim()
                    if (text.isNotEmpty()) {
                        title = text
                        break
                    }
                }
            }

            // Approach 4: Look for title in the <title> tag
            if (title.isEmpty()) {
                val titleElement = document.select("title").first()
                if (titleElement != null) {
                    val text = titleElement.text().trim()
                    // If the title contains "Lute" and "Book", extract the book part
                    if (text.contains("Lute") && text.contains("Book")) {
                        title = text.replace("Lute", "").replace("Book", "").trim()
                    } else if (text.isNotEmpty()) {
                        title = text
                    }
                }
            }

            // Approach 5: Look for form inputs with book title
            if (title.isEmpty()) {
                val titleInput = document.select("input[name=title]").first()
                if (titleInput != null) {
                    title = titleInput.attr("value").trim()
                }
            }

            // NEW APPROACH: Look for the actual book title as it appears in the reading page
            // Based on our testing, the book title is visible directly in the HTML content
            if (title.isEmpty()) {
                // Look for distinctive patterns in the text that match book titles
                val bodyText = document.body().text()

                // Split into lines to examine each line individually
                val lines = bodyText.lines()
                for (line in lines) {
                    val trimmedLine = line.trim()
                    // Look for lines that match the pattern of a book title
                    // Based on our sample: "HOLA LOLA A1 -- JUAN FERNÁNDEZ"
                    if (trimmedLine.isNotEmpty() &&
                                    trimmedLine.length > 10 && // Book titles are reasonably long
                                    !trimmedLine.equals("Page Menu", ignoreCase = true) &&
                                    !trimmedLine.equals("p", ignoreCase = true) &&
                                    !trimmedLine.contains("span", ignoreCase = true) &&
                                    !trimmedLine.contains("javascript", ignoreCase = true) &&
                                    (trimmedLine.contains("--") ||
                                            trimmedLine.contains(":") ||
                                            trimmedLine.any { it.isUpperCase() })
                    ) { // Titles often have uppercase letters
                        title = trimmedLine
                        break
                    }
                }
            }

            // If still no title found, use the book ID as fallback
            if (title.isEmpty()) {
                title = "Book $bookId"
                Log.w(
                        TAG,
                        "Could not parse book title from HTML for book ID: $bookId, using fallback"
                )
            }

            // For now, return a Book with minimal info, since we don't have language/word count
            // from this endpoint
            return Book(
                    id = bookId.toIntOrNull() ?: 0,
                    title = title,
                    language = "", // We don't have language from this page
                    wordCount = 0 // We don't have word count from this page
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing book from HTML", e)
            // Return a fallback book with the ID as title
            return Book(
                    id = bookId.toIntOrNull() ?: 0,
                    title = "Book $bookId",
                    language = "",
                    wordCount = 0
            )
        }
    }

    suspend fun getBookTitle(bookId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Making API call to fetch book reading page with ID: $bookId")
                val response = apiService.getBookReadingPage(bookId)
                Log.d(
                        TAG,
                        "API response received for book reading page, isSuccessful: ${response.isSuccessful}"
                )

                if (response.isSuccessful) {
                    val htmlContent = response.body()?.string()
                    Log.d(
                            TAG,
                            "Book reading page HTML content received, length: ${htmlContent?.length}"
                    )

                    // Parse the book title from the HTML content
                    htmlContent?.let { parseBookTitleFromHtml(it, bookId) }
                } else {
                    Log.e(
                            TAG,
                            "API response not successful for book reading page, code: ${response.code()}, error: ${response.errorBody()?.string()}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during API call to fetch book reading page", e)
                null
            }
        }
    }

    private fun parseBookTitleFromHtml(htmlContent: String, bookId: String): String? {
        try {
            val document = org.jsoup.Jsoup.parse(htmlContent)

            // Find the book title in the HTML from the reading page
            var title = ""

            // Approach 1: Look for h3 tag with class "m-0" or other common title classes
            val titleH3 = document.select("h3.m-0").first()
            if (titleH3 != null) {
                title = titleH3.text().trim()
            }

            // Approach 2: If not found, look for the first h3 tag which often contains the book
            // title
            if (title.isEmpty()) {
                val h3Elements = document.select("h3")
                for (element in h3Elements) {
                    val text = element.text().trim()
                    if (text.isNotEmpty()) {
                        title = text
                        break
                    }
                }
            }

            // Approach 3: Look in the page header area
            if (title.isEmpty()) {
                val headerElements =
                        document.select("div.page-header h2, div.card-header h2, header h2")
                for (element in headerElements) {
                    val text = element.text().trim()
                    if (text.isNotEmpty()) {
                        title = text
                        break
                    }
                }
            }

            // Approach 4: Look for title in the <title> tag
            if (title.isEmpty()) {
                val titleElement = document.select("title").first()
                if (titleElement != null) {
                    val text = titleElement.text().trim()
                    // Extract book title from page title like "Lute - Book Title - Page X/Y"
                    if (text.contains(" - ")) {
                        val parts = text.split(" - ")
                        if (parts.size >= 2) {
                            title = parts[1].trim() // Second part is usually the book title
                        } else {
                            title = text.replace("Lute", "").replace("|", "").trim()
                        }
                    } else {
                        title = text.replace("Lute", "").trim()
                    }
                }
            }

            // If still no title found, use the book ID as fallback
            if (title.isEmpty()) {
                Log.w(
                        TAG,
                        "Could not parse book title from reading page HTML for book ID: $bookId, using book ID"
                )
                return bookId
            }

            return title
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing book title from reading page HTML", e)
            // Return the book ID as fallback
            return bookId
        }
    }

    /**
     * Fetch and parse books from the main page HTML This replaces the non-existent API endpoint by
     * parsing the HTML table
     */
    suspend fun getBooksFromHtml(): List<Book>? {
        return withContext(Dispatchers.IO) {
            // First try the main page HTML approach
            var books: List<Book>? = null

            try {
                Log.d(TAG, "Making API call to fetch main page HTML")
                val response = apiService.getMainPage()
                Log.d(
                        TAG,
                        "Main page HTML response received, isSuccessful: ${response.isSuccessful}"
                )

                if (response.isSuccessful) {
                    val htmlContent = response.body()?.string()
                    Log.d(TAG, "Main page HTML content received, length: ${htmlContent?.length}")

                    // Parse the books from the HTML content
                    books = htmlContent?.let { parseBooksFromHtml(it) }

                    // If we found books in the HTML, return them
                    if (books != null && books.isNotEmpty()) {
                        Log.d(TAG, "Successfully fetched ${books.size} books from main page HTML")
                        return@withContext books
                    }
                } else {
                    Log.e(
                            TAG,
                            "Main page HTML response not successful, code: ${response.code()}, error: ${response.errorBody()?.string()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during main page HTML API call", e)
            }

            // Try to get books using the DataTables AJAX call directly
            // This mimics what the browser does, but with safe parameters to avoid the SQL bug
            try {
                Log.d(TAG, "Attempting to fetch books via DataTables AJAX API")
                val dataTableResponse = apiService.getActiveBooksDataTables()

                Log.d(
                        TAG,
                        "DataTables AJAX API response received, isSuccessful: ${dataTableResponse.isSuccessful}"
                )

                if (dataTableResponse.isSuccessful) {
                    val dataTableContent = dataTableResponse.body()?.string()
                    Log.d(TAG, "DataTables content received, length: ${dataTableContent?.length}")

                    // Try to parse DataTables response which should be in JSON format
                    val parsedBooks = parseBooksFromDataTablesResponse(dataTableContent)
                    if (parsedBooks != null && parsedBooks.isNotEmpty()) {
                        Log.d(
                                TAG,
                                "Successfully fetched ${parsedBooks.size} books from DataTables AJAX response"
                        )
                        return@withContext parsedBooks
                    } else {
                        Log.d(TAG, "DataTables AJAX response had no books or failed to parse")
                    }
                } else {
                    Log.e(
                            TAG,
                            "DataTables AJAX API response not successful, code: ${dataTableResponse.code()}, error: ${dataTableResponse.errorBody()?.string()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during DataTables AJAX API call", e)
            }

            // If both approaches failed, return the result from the main page (which might be empty
            // list)
            Log.d(
                    TAG,
                    "All book fetching methods exhausted, returning what was found (likely empty)"
            )
            return@withContext books
        }
    }

    /**
     * Parse books from the main page HTML content Extracts book data from the DataTable on the main
     * page
     */
    private fun parseBooksFromHtml(htmlContent: String): List<Book>? {
        try {
            val document = org.jsoup.Jsoup.parse(htmlContent)

            // Find the book table
            val bookTable = document.select("#booktable").first()

            // If the table exists but has no tbody, the books might be loaded dynamically
            // So let's try to extract the books from JavaScript code or use alternative methods
            if (bookTable != null) {
                // Check if there are already rows in the table (might be rendered statically in
                // some cases)
                val rows = bookTable.select("tbody tr")
                if (rows.isNotEmpty()) {
                    // Process books from static HTML if they exist
                    val books = mutableListOf<Book>()

                    for (row in rows) {
                        try {
                            val cells = row.select("td")
                            if (cells.size >= 4) {
                                // Extract book information from cells
                                val titleCell = cells[0]
                                val languageCell = cells[1]
                                val wordCountCell = cells[3]

                                // Parse title and ID from the link - but the links might not be <a
                                // class="book-title">
                                // Let's look for any links in the first cell that go to book
                                // reading pages
                                val titleLink = titleCell.select("a[href^='/read']").first()
                                val title = titleLink?.text() ?: titleCell.text().trim()
                                val href = titleLink?.attr("href") ?: ""

                                // Extract book ID from href (format: "/read/{bookId}")
                                val bookId = extractBookIdFromHref(href)

                                // Parse language
                                val language = languageCell.text().trim()

                                // Parse word count
                                val wordCountText = wordCountCell.text().trim()
                                val wordCount = wordCountText.toIntOrNull() ?: 0

                                // Create Book object
                                val book =
                                        Book(
                                                id = bookId,
                                                title = title,
                                                language = language,
                                                wordCount = wordCount
                                        )

                                books.add(book)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing individual book row", e)
                            // Continue with next row
                        }
                    }

                    Log.d(TAG, "Parsed ${books.size} books from static HTML")
                    return books
                }
            }

            // Since the main page might not have the books loaded statically due to DataTables,
            // we need to try alternative approaches.
            // First, let's try to see if we can get the count from the "empty table" message
            val emptyTableMessage = document.select(".dataTables_empty, [data-i18n='emptyTable']")
            if (!emptyTableMessage.isEmpty()) {
                val messageText = emptyTableMessage.text()
                if (messageText.contains("No books available") || messageText.contains("Create one")
                ) {
                    Log.d(TAG, "No books available according to empty table message")
                    return emptyList()
                }
            }

            // If table is empty in HTML but the UI shows books via JS, we need to make a separate
            // API call
            // However, since the direct datatables endpoint has issues, let's try parsing the
            // script tags
            // to see if we can extract any information
            val scripts = document.select("script")
            for (script in scripts) {
                val scriptContent = script.html()
                if (scriptContent.contains("book_listing_table") ||
                                scriptContent.contains("DataTable")
                ) {
                    // Look for any data that might indicate book count or status
                    if (scriptContent.contains("No books available")) {
                        Log.d(TAG, "Found 'No books available' in script content")
                        return emptyList()
                    }
                }
            }

            // If we still can't find books, return an empty list
            // This is expected when the server uses DataTables for dynamic loading
            Log.d(TAG, "No books found in HTML (likely using dynamic DataTables loading)")
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing books from HTML", e)
            return null
        }
    }

    /** Extract book ID from href attribute Expected format: "/read/{bookId}" */
    private fun extractBookIdFromHref(href: String): Int {
        try {
            // Match pattern /read/{number}
            val regex = Regex("/read/(\\d+)")
            val matchResult = regex.find(href)
            if (matchResult != null) {
                val bookIdStr = matchResult.groupValues[1]
                return bookIdStr.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting book ID from href: $href", e)
        }
        return 0
    }

    /**
     * Parse books from the DataTables API response The response is likely to be in JSON format
     * based on DataTables requirements
     */
    private fun parseBooksFromDataTablesResponse(responseContent: String?): List<Book>? {
        if (responseContent.isNullOrBlank()) {
            Log.d(TAG, "DataTables response content is null or blank")
            return null
        }

        try {
            // The DataTables endpoint returns JSON response with the following structure:
            // {
            //   "draw": 1,
            //   "recordsTotal": 10,
            //   "recordsFiltered": 10,
            //   "data": [...]
            // }
            // The data array contains the actual book records

            val json = org.json.JSONObject(responseContent)

            // Extract the draw, recordsTotal, and recordsFiltered values
            val draw = json.optInt("draw", 0)
            val recordsTotal = json.optInt("recordsTotal", 0)
            val recordsFiltered = json.optInt("recordsFiltered", 0)

            Log.d(
                    TAG,
                    "DataTables response - draw: $draw, total: $recordsTotal, filtered: $recordsFiltered"
            )

            // Get the data array
            val dataArray = json.optJSONArray("data")
            if (dataArray == null || dataArray.length() == 0) {
                Log.d(TAG, "No data array found in DataTables response or it's empty")
                return emptyList()
            }

            val books = mutableListOf<Book>()

            // Process each item in the data array
            for (i in 0 until dataArray.length()) {
                try {
                    // Each book record is a JSONObject with named fields, not an array
                    val item = dataArray.optJSONObject(i)

                    if (item != null) {
                        // Extract book data from the JSON object fields
                        val bookId = item.optInt("BkID", 0)
                        val title = item.optString("BkTitle", "Unknown Title")
                        val language = item.optString("LgName", "")
                        val wordCount = item.optInt("WordCount", 0)
                        val statusDistribution = item.optString("StatusDistribution")
                        val unknownPercent = item.optInt("UnknownPercent", -1)
                        val distinctUnknowns = item.optInt("UnknownCount", -1)
                        val distinctTerms = item.optInt("DistinctCount", -1)
                        val pageNum = item.optInt("PageNum", -1)
                        val pageCount = item.optInt("PageCount", -1)

                        // The data might also contain HTML for the title with a link
                        // But since we have the direct fields, we can use them directly
                        val book =
                                Book(
                                        id = bookId,
                                        title = title,
                                        language = language,
                                        wordCount = wordCount,
                                        statusDistribution = statusDistribution,
                                        unknownPercent =
                                                if (unknownPercent >= 0) unknownPercent else null,
                                        distinctUnknowns =
                                                if (distinctUnknowns >= 0) distinctUnknowns
                                                else null,
                                        distinctTerms =
                                                if (distinctTerms >= 0) distinctTerms else null,
                                        pageNum = if (pageNum >= 0) pageNum else null,
                                        pageCount = if (pageCount >= 0) pageCount else null,
                                        lastOpenedDate = item.optString("LastOpenedDate", null)
                                )

                        books.add(book)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing individual book from DataTables data", e)
                    // Continue with next item
                }
            }

            Log.d(TAG, "Parsed ${books.size} books from DataTables response")
            return books
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing DataTables response", e)
            return null
        }
    }

    /** Extract title and book ID from the HTML containing the book link */
    private fun extractTitleAndBookIdFromHtml(html: String): Pair<String, Int> {
        try {
            val doc = org.jsoup.Jsoup.parse(html)
            val link = doc.select("a").first()

            if (link != null) {
                val title = link.text().trim()
                val href = link.attr("href")
                val bookId = extractBookIdFromHref(href)
                return Pair(title, bookId)
            } else {
                // If no link found, try to get the text directly
                val title = doc.text().trim()
                return Pair(title, 0) // Return 0 as ID if no link found
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting title and ID from HTML", e)
            return Pair("Unknown Title", 0)
        }
    }
}
