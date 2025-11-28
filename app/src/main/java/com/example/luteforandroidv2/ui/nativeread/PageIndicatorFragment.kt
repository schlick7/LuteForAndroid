package com.example.luteforandroidv2.ui.nativeread

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentPageIndicatorBinding

/**
 * Page indicator fragment for the native reading view Shows current page number and navigation
 * controls
 */
class PageIndicatorFragment : Fragment() {
    private var _binding: FragmentPageIndicatorBinding? = null
    private val binding
        get() = _binding!!

    // Listener for page navigation events
    private var pageNavigationListener: PageNavigationListener? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPageIndicatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup all known button
        binding.allKnownButton.setOnClickListener { pageNavigationListener?.onAllKnown() }

        // Setup previous page button
        binding.previousPageButton.setOnClickListener { pageNavigationListener?.onPreviousPage() }

        // Setup lute logo button
        binding.luteLogoButton.setOnClickListener { pageNavigationListener?.onLuteMenu() }

        // Setup next page button (arrow)
        binding.nextPageButton.setOnClickListener { pageNavigationListener?.onNextPage() }

        // Setup next page button (text)
        binding.nextPageTextButton.setOnClickListener { pageNavigationListener?.onMarkPageDone() }
    }

    /** Update the page counter display */
    fun updatePageCounter(currentPage: Int, totalPages: Int) {
        binding.pageCounterText.text = "$currentPage/$totalPages"
        Log.d("PageIndicatorFragment", "Updated page counter to $currentPage/$totalPages")

        // Update the button text based on current page position
        if (currentPage == totalPages) {
            // On the last page, show "Done" button
            binding.nextPageTextButton.text = "Done"
        } else {
            // Not on the last page, show "Next" button
            binding.nextPageTextButton.text = "Next"
        }
    }

    /** Set the page navigation listener */
    fun setPageNavigationListener(listener: PageNavigationListener) {
        this.pageNavigationListener = listener
    }

    /** Update text color for page indicator elements */
    fun updateTextColor(color: Int) {
        try {
            // Update the page counter text color
            binding.pageCounterText.setTextColor(color)

            // Update the next page text button color
            binding.nextPageTextButton.setTextColor(color)
        } catch (e: Exception) {
            Log.e("PageIndicatorFragment", "Error updating text color", e)
        }
    }

    /** Hide navigation arrows to show only the page counter and other controls */
    fun hideNavigationButtons() {
        try {
            binding.previousPageButton.visibility = View.GONE
            binding.nextPageButton.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("PageIndicatorFragment", "Error hiding navigation buttons", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        pageNavigationListener = null
    }

    /** Toggle visibility of the all known button */
    fun toggleAllKnownButtonVisibility(show: Boolean) {
        binding.allKnownButton.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Interface for page navigation events */
    interface PageNavigationListener {
        fun onAllKnown()
        fun onPreviousPage()
        fun onLuteMenu()
        fun onNextPage()
        fun onMarkPageDone()
    }
}
