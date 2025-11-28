package com.example.luteforandroidv2.ui.nativeread

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.ui.nativeread.Bookmark.BookStateManager
import kotlinx.coroutines.launch

class NavigationManager(
        private val fragment: androidx.fragment.app.Fragment,
        private val viewModel: NativeReadViewModel,
        private val bookStateManager: BookStateManager,
        private val navigationController: NavigationController,
        private var savedBookId: String?,
        private var savedBookLanguage: String?
) {

    // Listener for communication with MainActivity
    var fragmentListener: NativeReadFragmentListener? = null

    /** Update the book information in the NavigationManager */
    fun updateBookInfo(bookId: String?, bookLanguage: String?) {
        Log.d(
                "NavigationManager",
                "updateBookInfo called with bookId: $bookId, bookLanguage: $bookLanguage"
        )
        this.savedBookId = bookId
        this.savedBookLanguage = bookLanguage
        Log.d("NavigationManager", "NavigationManager book info updated")
    }

    /** Navigate to the previous page */
    fun navigateToPreviousPageInternal() {
        Log.d("NavigationManager", "navigateToPreviousPageInternal called")
        Log.d("NavigationManager", "Navigate to previous page requested")
        val pageInfo = navigationController.getCurrentPageInfo()
        Log.d(
                "NavigationManager",
                "Current page info: ${pageInfo.currentPage}/${pageInfo.totalPages}"
        )

        // Calculate the target page without modifying the navigation controller state yet
        val targetPage = pageInfo.currentPage - 1
        if (targetPage >= 1) {
            Log.d("NavigationManager", "Navigating to page $targetPage")
            savedBookId?.let { bookId ->
                bookStateManager.saveCurrentBookState(bookId, targetPage)
                Log.d(
                        "NavigationManager",
                        "Saved book state for book ID: $bookId, page: $targetPage"
                )
                // Load the new page - the viewModel will update the navigation controller when
                // content loads
                Log.d("NavigationManager", "Calling viewModel.loadBookPage($bookId, $targetPage)")
                viewModel.loadBookPage(bookId, targetPage)
                Log.d("NavigationManager", "viewModel.loadBookPage called")
            }
        } else {
            Log.d("NavigationManager", "Already at first page")
        }
        Log.d("NavigationManager", "navigateToPreviousPageInternal completed")
    }

    /** Navigate to the next page without marking current page as done */
    fun navigateToNextPageWithoutMarkingDone() {
        Log.d("NavigationManager", "navigateToNextPageWithoutMarkingDone called")
        Log.d("NavigationManager", "Navigate to next page requested (without marking done)")
        val pageInfo = navigationController.getCurrentPageInfo()
        Log.d(
                "NavigationManager",
                "Current page info: ${pageInfo.currentPage}/${pageInfo.totalPages}"
        )

        // Calculate the target page without modifying the navigation controller state yet
        val targetPage = pageInfo.currentPage + 1
        if (targetPage <= pageInfo.totalPages) {
            Log.d("NavigationManager", "Navigating to page $targetPage")
            savedBookId?.let { bookId ->
                bookStateManager.saveCurrentBookState(bookId, targetPage)
                Log.d(
                        "NavigationManager",
                        "Saved book state for book ID: $bookId, page: $targetPage"
                )
                // Load the new page - the viewModel will update the navigation controller when
                // content loads
                Log.d("NavigationManager", "Calling viewModel.loadBookPage($bookId, $targetPage)")
                viewModel.loadBookPage(bookId, targetPage)
                Log.d("NavigationManager", "viewModel.loadBookPage called")
            }
        } else {
            Log.d("NavigationManager", "Already at last page")
        }
        Log.d("NavigationManager", "navigateToNextPageWithoutMarkingDone completed")
    }

    /** Mark current page as done and navigate to the next page */
    fun markPageAsDoneAndNavigateToNext() {
        Log.d("NavigationManager", "markPageAsDoneAndNavigateToNext called")
        Log.d("NavigationManager", "Mark page as done and navigate to next page requested")
        val pageInfo = navigationController.getCurrentPageInfo()
        Log.d(
                "NavigationManager",
                "Current page info: ${pageInfo.currentPage}/${pageInfo.totalPages}"
        )

        // Check if we're on the last page
        if (pageInfo.currentPage == pageInfo.totalPages) {
            // On the last page, mark it as done and navigate to books view
            Log.d("NavigationManager", "On last page, marking as done and navigating to books view")
            savedBookId?.let { bookId ->
                // Save reading progress for the CURRENT page to server
                fragment.lifecycleScope.launch {
                    try {
                        // First mark the current page as done (not marking rest as known)
                        val markResult =
                                viewModel.saveReadingProgressAndWait(
                                        bookId,
                                        pageInfo.currentPage,
                                        false
                                )
                        if (markResult.isSuccess) {
                            Log.d(
                                    "NavigationManager",
                                    "Successfully marked last page ${pageInfo.currentPage} as done"
                            )

                            // Update the word count display in the header by forcing a refresh
                            fragment.activity?.let { activity ->
                                if (activity is com.example.luteforandroidv2.MainActivity) {
                                    // Clear the cache to force a fresh network request
                                    activity.clearWordCountCache()
                                    activity.updateWordCount()
                                }
                            }

                            // Navigate to books view
                            try {
                                fragment.findNavController().navigate(R.id.nav_books)
                            } catch (e: Exception) {
                                Log.e("NavigationManager", "Error navigating to books view", e)
                            }
                        } else {
                            Log.e(
                                    "NavigationManager",
                                    "Failed to mark page as done: ${markResult.exceptionOrNull()?.message}"
                            )
                            // Show error to user
                            Toast.makeText(
                                            fragment.requireContext(),
                                            "Failed to mark page as done: ${markResult.exceptionOrNull()?.message}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    } catch (e: Exception) {
                        Log.e("NavigationManager", "Exception while marking page as done", e)
                        Toast.makeText(
                                        fragment.requireContext(),
                                        "Error: ${e.message}",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            }
        } else {
            // Not on the last page, navigate to next page
            Log.d("NavigationManager", "Not on last page, navigating to next page")
            // Calculate the target page (next page)
            val targetPage = pageInfo.currentPage + 1
            Log.d("NavigationManager", "Navigating to page $targetPage")
            savedBookId?.let { bookId ->
                // Save reading progress for the CURRENT page to server and then load the next page
                fragment.lifecycleScope.launch {
                    try {
                        // First mark the current page as done (not marking rest as known)
                        val markResult =
                                viewModel.saveReadingProgressAndWait(
                                        bookId,
                                        pageInfo.currentPage,
                                        false
                                )
                        if (markResult.isSuccess) {
                            Log.d(
                                    "NavigationManager",
                                    "Successfully marked page ${pageInfo.currentPage} as done"
                            )

                            // Update the word count display in the header by forcing a refresh
                            fragment.activity?.let { activity ->
                                if (activity is com.example.luteforandroidv2.MainActivity) {
                                    // Clear the cache to force a fresh network request
                                    activity.clearWordCountCache()
                                    activity.updateWordCount()
                                }
                            }

                            // Then load the NEXT page directly
                            bookStateManager.saveCurrentBookState(bookId, targetPage)
                            Log.d(
                                    "NavigationManager",
                                    "Saved book state for book ID: $bookId, page: $targetPage"
                            )
                            viewModel.loadBookPage(bookId, targetPage)
                        } else {
                            Log.e(
                                    "NavigationManager",
                                    "Failed to mark page as done: ${markResult.exceptionOrNull()?.message}"
                            )
                            // Show error to user
                            Toast.makeText(
                                            fragment.requireContext(),
                                            "Failed to mark page as done: ${markResult.exceptionOrNull()?.message}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    } catch (e: Exception) {
                        Log.e("NavigationManager", "Exception while marking page as done", e)
                        Toast.makeText(
                                        fragment.requireContext(),
                                        "Error: ${e.message}",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            }
        }
        Log.d("NavigationManager", "markPageAsDoneAndNavigateToNext completed")
    }

    /** Mark current page as all known */
    fun markPageAsAllKnown() {
        Log.d("NavigationManager", "markPageAsAllKnown called")
        Log.d("NavigationManager", "Mark page as all known requested")
        val pageInfo = navigationController.getCurrentPageInfo()
        Log.d(
                "NavigationManager",
                "Current page info: ${pageInfo.currentPage}/${pageInfo.totalPages}"
        )

        savedBookId?.let { bookId ->
            // Mark the current page as done AND mark all words as known (restKnown = true)
            fragment.lifecycleScope.launch {
                try {
                    val markResult =
                            viewModel.saveReadingProgressAndWait(
                                    bookId,
                                    pageInfo.currentPage,
                                    true // This marks all terms as known (restKnown = true)
                            )
                    if (markResult.isSuccess) {
                        Log.d(
                                "NavigationManager",
                                "Successfully marked page ${pageInfo.currentPage} as done and all words as known"
                        )

                        // Update the word count display in the header by forcing a refresh
                        fragment.activity?.let { activity ->
                            if (activity is com.example.luteforandroidv2.MainActivity) {
                                // Clear the cache to force a fresh network request
                                activity.clearWordCountCache()
                                activity.updateWordCount()
                            }
                        }

                        // Navigate to the next page after marking all words as known
                        navigateToNextPageWithoutMarkingDone()
                    } else {
                        Log.e(
                                "NavigationManager",
                                "Failed to mark page as done with all known: ${markResult.exceptionOrNull()?.message}"
                        )
                        // Show error to user
                        Toast.makeText(
                                        fragment.requireContext(),
                                        "Failed to mark page as done: ${markResult.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                } catch (e: Exception) {
                    Log.e("NavigationManager", "Exception while marking page as all known", e)
                    Toast.makeText(
                                    fragment.requireContext(),
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }
        }
        Log.d("NavigationManager", "markPageAsAllKnown completed")
    }

    fun onAddBookmark() {
        fragmentListener?.onAddBookmark()
    }

    fun onListBookmarks() {
        fragmentListener?.onListBookmarks()
    }

    fun onEditCurrentPage() {
        fragmentListener?.onEditCurrentPage()
    }

    // Method to get the ID of the current book
    fun getBookId(): String? {
        return savedBookId
    }

    // Method to get the language of the current book
    fun getBookLanguage(): String? {
        return savedBookLanguage
    }

    // Method to handle back navigation (similar to WebView-based fragments)
    fun goBackInWebView(callback: (Boolean) -> Unit) {
        // For native reader, we'll check if we can navigate to a previous page
        val pageInfo = navigationController.getCurrentPageInfo()
        if (pageInfo.currentPage > 1) {
            // We can go back to a previous page
            callback(true)
        } else {
            // We're at the first page, can't go back further
            callback(false)
        }
    }
}
