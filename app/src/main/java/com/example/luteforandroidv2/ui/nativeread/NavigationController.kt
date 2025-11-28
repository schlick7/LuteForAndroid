package com.example.luteforandroidv2.ui.nativeread

import android.util.Log

/** Navigation controller for the native reading view Handles page navigation logic */
class NavigationController {
    private var currentPage: Int = 1
    private var totalPages: Int = 1

    /** Navigate to a specific page */
    fun goToPage(pageNum: Int): Boolean {
        if (pageNum >= 1 && pageNum <= totalPages) {
            currentPage = pageNum
            Log.d("NavigationController", "Navigated to page $currentPage")
            return true
        }
        Log.w("NavigationController", "Invalid page number: $pageNum")
        return false
    }

    /** Navigate to the next page */
    fun nextPage(): Boolean {
        return if (currentPage < totalPages) {
            currentPage++
            Log.d("NavigationController", "Navigated to next page: $currentPage")
            true
        } else {
            Log.d("NavigationController", "Already at last page")
            false
        }
    }

    /** Navigate to the previous page */
    fun previousPage(): Boolean {
        return if (currentPage > 1) {
            currentPage--
            Log.d("NavigationController", "Navigated to previous page: $currentPage")
            true
        } else {
            Log.d("NavigationController", "Already at first page")
            false
        }
    }

    /** Get current page information */
    fun getCurrentPageInfo(): PageInfo {
        return PageInfo(currentPage, totalPages)
    }

    /** Get total page count */
    fun getTotalPageCount(): Int {
        return totalPages
    }

    /** Set total page count */
    fun setTotalPageCount(count: Int) {
        totalPages = count
        // Ensure current page is within valid range
        if (currentPage > totalPages) {
            currentPage = totalPages
        }
        Log.d("NavigationController", "Total pages set to $totalPages")
    }

    /** Set current page */
    fun setCurrentPage(page: Int) {
        if (page >= 1 && page <= totalPages) {
            currentPage = page
            Log.d("NavigationController", "Current page set to $currentPage")
        } else {
            Log.w("NavigationController", "Invalid page number: $page, totalPages: $totalPages")
        }
    }
}

/** Data class for page information */
data class PageInfo(val currentPage: Int, val totalPages: Int)
