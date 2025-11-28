package com.example.luteforandroidv2.ui.nativeread.Dictionary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.DialogSentenceTranslationBinding
import com.example.luteforandroidv2.ui.nativeread.LuteServerService
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SentenceTranslationDialogFragment :
        DialogFragment(), DictionaryPageFragment.CloseButtonListener {

    private var _binding: DialogSentenceTranslationBinding? = null
    private val binding
        get() = _binding ?: throw IllegalStateException("Binding is not initialized")

    private var sentenceText: String? = null
    private var languageId: Int? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    companion object {
        private const val ARG_SENTENCE_TEXT = "sentence_text"
        private const val ARG_LANGUAGE_ID = "language_id"

        fun newInstance(sentenceText: String, languageId: Int): SentenceTranslationDialogFragment {
            return SentenceTranslationDialogFragment().apply {
                arguments =
                        Bundle().apply {
                            putString(ARG_SENTENCE_TEXT, sentenceText)
                            putInt(ARG_LANGUAGE_ID, languageId)
                        }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sentenceText = it.getString(ARG_SENTENCE_TEXT)
            languageId = it.getInt(ARG_LANGUAGE_ID)
        }
        // Make the dialog full-screen
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = DialogSentenceTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make the dialog background transparent and dismiss on touch outside
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.setCanceledOnTouchOutside(true)

        viewPager = binding.viewPager
        tabLayout = binding.tabLayout

        setupViewsWithSentence()
    }

    private fun setupViewsWithSentence() {
        // Set the sentence text at the top
        binding.sentenceText.text = sentenceText

        loadSentenceDictionaries()
    }

    private fun loadSentenceDictionaries() {
        val sentence = sentenceText ?: return
        val langId = languageId ?: return
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())

        if (!serverSettingsManager.isServerUrlConfigured()) {
            Log.e("SentenceTranslationDialog", "Server URL not configured")
            return
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        Log.d("SentenceTranslationDialog", "Using server URL: $serverUrl for sentence: $sentence")

        lifecycleScope.launch(Dispatchers.IO) {
            val service = LuteServerService()
            val allDictionaries = service.fetchDictionaries(serverUrl, langId)

            // Filter to only include dictionaries marked for sentences
            val sentenceDictionaries =
                    allDictionaries.filter {
                        it.isActive && (it.useFor == "sentences" || it.useFor == "sentence")
                    }

            withContext(Dispatchers.Main) {
                if (sentenceDictionaries.isEmpty()) {
                    // If no sentence-specific dictionaries, use all active term dictionaries as
                    // fallback
                    val fallbackDictionaries =
                            allDictionaries.filter { it.isActive && it.useFor == "terms" }
                    val adapter =
                            SentenceTranslationPagerAdapter(
                                    this@SentenceTranslationDialogFragment,
                                    fallbackDictionaries,
                                    sentence,
                                    this@SentenceTranslationDialogFragment
                            )
                    viewPager.adapter = adapter

                    TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                                val urlString = fallbackDictionaries[position].dictUri
                                val host =
                                        try {
                                            URL(urlString).host
                                        } catch (e: Exception) {
                                            null
                                        }

                                tab.text =
                                        if (host != null) {
                                            val parts = host.split(".")
                                            if (parts.size >= 2) {
                                                parts[parts.size - 2].replaceFirstChar(
                                                        Char::titlecase
                                                )
                                            } else {
                                                host
                                            }
                                        } else {
                                            val parts = urlString.split("/")
                                            parts.lastOrNull()?.split(".")?.firstOrNull() ?: "Dict"
                                        }
                            }
                            .attach()
                } else {
                    // Use sentence dictionaries
                    val adapter =
                            SentenceTranslationPagerAdapter(
                                    this@SentenceTranslationDialogFragment,
                                    sentenceDictionaries,
                                    sentence,
                                    this@SentenceTranslationDialogFragment
                            )
                    viewPager.adapter = adapter

                    TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                                val urlString = sentenceDictionaries[position].dictUri
                                val host =
                                        try {
                                            URL(urlString).host
                                        } catch (e: Exception) {
                                            null
                                        }

                                tab.text =
                                        if (host != null) {
                                            val parts = host.split(".")
                                            if (parts.size >= 2) {
                                                parts[parts.size - 2].replaceFirstChar(
                                                        Char::titlecase
                                                )
                                            } else {
                                                host
                                            }
                                        } else {
                                            val parts = urlString.split("/")
                                            parts.lastOrNull()?.split(".")?.firstOrNull() ?: "Dict"
                                        }
                            }
                            .attach()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCloseButtonClicked() {
        dismiss() // Dismiss the entire sentence translation dialog
    }

    // Implementation for the close button listener
    private inner class SentenceTranslationPagerAdapter(
            fragment: androidx.fragment.app.Fragment,
            private val dictionaries: List<DictionaryInfo>,
            private val term: String,
            private val closeListener: DictionaryPageFragment.CloseButtonListener
    ) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

        private val fragments = mutableMapOf<Int, DictionaryPageFragment>()

        override fun getItemCount(): Int {
            return dictionaries.size
        }

        override fun createFragment(position: Int): DictionaryPageFragment {
            val dictionary = dictionaries[position]
            val fragment = DictionaryPageFragment.newInstance(dictionary.dictUri, term, true)
            // Set the close button listener so the page can notify the dialog to close
            fragment.setCloseButtonListener(closeListener)
            fragments[position] = fragment
            return fragment
        }

        fun getFragment(position: Int): DictionaryPageFragment? {
            return fragments[position]
        }
    }
}
