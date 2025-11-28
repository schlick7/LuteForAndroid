package com.example.luteforandroidv2.ui.nativeread.Dictionary

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DictionaryPagerAdapter(
        fragment: Fragment,
        private val dictionaries: List<DictionaryInfo>,
        private val term: String,
        private val isSentenceMode: Boolean = false,
        private val closeListener: DictionaryPageFragment.CloseButtonListener? = null
) : FragmentStateAdapter(fragment) {

    private val fragments = mutableMapOf<Int, DictionaryPageFragment>()

    override fun getItemCount(): Int {
        return dictionaries.size
    }

    override fun createFragment(position: Int): DictionaryPageFragment {
        val dictionary = dictionaries[position]
        val fragment = DictionaryPageFragment.newInstance(dictionary.dictUri, term, isSentenceMode)
        // Set the close button listener to allow the page to notify when close button is clicked
        fragment.setCloseButtonListener(closeListener)
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): DictionaryPageFragment? {
        return fragments[position]
    }
}
