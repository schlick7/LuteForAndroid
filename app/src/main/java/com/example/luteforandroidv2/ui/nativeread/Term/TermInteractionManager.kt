package com.example.luteforandroidv2.ui.nativeread.Term

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Term interaction manager for the native reading view Handles term selection and interaction logic
 */
class TermInteractionManager {
    private var selectedTerm: TermData? = null
    private var lastTapTime: Long = 0
    private val doubleTapTimeout: Long = 200 // milliseconds
    private var pendingSingleTap: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var termInteractionListener: TermInteractionListener? = null

    /** Set listener for term interaction events */
    fun setTermInteractionListener(listener: TermInteractionListener) {
        this.termInteractionListener = listener
    }

    /** Handle term tap event */
    fun onTermTapped(term: TermData) {
        val currentTime = System.currentTimeMillis()

        // Check if this is a double tap on the same term
        if (selectedTerm?.termId == term.termId && (currentTime - lastTapTime) < doubleTapTimeout) {
            // Cancel the pending single tap if it exists
            pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
            pendingSingleTap = null

            // Double tap detected
            onTermDoubleTap(term)
        } else {
            // Cancel any pending single tap for a different term
            pendingSingleTap?.let { mainHandler.removeCallbacks(it) }

            // Schedule a single tap event
            pendingSingleTap = Runnable {
                onTermSingleTap(term)
                pendingSingleTap = null
            }
            mainHandler.postDelayed(pendingSingleTap!!, doubleTapTimeout)
        }

        lastTapTime = currentTime
        selectedTerm = term
    }

    /** Handle single tap on term */
    private fun onTermSingleTap(term: TermData) {
        Log.d(
                "TermInteractionManager",
                "Single tap on term: ${term.term} at (${term.tapX}, ${term.tapY})"
        )
        termInteractionListener?.onTermSingleTap(term)
    }

    /** Handle double tap on term */
    private fun onTermDoubleTap(term: TermData) {
        Log.d(
                "TermInteractionManager",
                "Double tap on term: ${term.term} at (${term.tapX}, ${term.tapY})"
        )
        termInteractionListener?.onTermDoubleTap(term)
    }

    /** Clear current term selection */
    fun clearSelection() {
        selectedTerm = null
        // Cancel any pending single tap
        pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTap = null
        Log.d("TermInteractionManager", "Term selection cleared")
    }

    /** Check if a term is currently selected */
    fun isTermCurrentlySelected(term: TermData): Boolean {
        return selectedTerm?.termId == term.termId
    }

    /** Interface for term interaction events */
    interface TermInteractionListener {
        fun onTermSingleTap(term: TermData)
        fun onTermDoubleTap(term: TermData)
    }
}
