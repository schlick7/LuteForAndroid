package com.example.luteforandroidv2.ui.nativeread

/**
 * Interface for communication between NativeReadFragment and MainActivity for handling FAB menu
 * actions and other interactions
 */
interface NativeReadFragmentListener {
    /** Called when the user wants to create Anki cards for the current selection */
    fun onCreateAnkiCardsForSelection(text: String)

    /** Called when the user wants to create an Anki card for a specific term */
    fun onCreateAnkiCardForTerm(termId: String)

    /** Called when the user wants to translate the current sentence */
    fun onTranslateSentence()

    /** Called when the user wants to translate the current page */
    fun onTranslatePage()

    /** Called when the user wants to show text formatting options */
    fun onShowTextFormatting()

    /** Called when the user wants to add a bookmark */
    fun onAddBookmark()

    /** Called when the user wants to list bookmarks */
    fun onListBookmarks()

    /** Called when the user wants to edit the current page */
    fun onEditCurrentPage()

    /** Called when the user wants to lookup a term in the dictionary */
    fun onDictionaryLookup(term: String)

    /** Called when the user selects text in the dictionary */
    fun onDictionaryTextSelected(text: String)

    /** Called when the dictionary is closed */
    fun onDictionaryClosed()

    /** Called when the sentence reader needs to fetch content for a specific page */
    fun onFetchSentenceReaderPageContent(
            bookId: String,
            pageNum: Int,
            callback: (Result<String>) -> Unit
    )

    /**
     * Called when the sentence reader wants to mark the current page as done and turn to next page
     */
    fun onMarkSentenceReaderPageDone(
            bookId: String,
            currentPageNum: Int,
            callback: (Result<Unit>) -> Unit
    )
}
