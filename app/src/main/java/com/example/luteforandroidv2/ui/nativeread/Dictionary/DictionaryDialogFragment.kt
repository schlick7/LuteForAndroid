package com.example.luteforandroidv2.ui.nativeread.Dictionary

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.databinding.DialogDictionaryBinding
import com.example.luteforandroidv2.ui.nativeread.LuteServerService
import com.example.luteforandroidv2.ui.nativeread.Translation.TranslationCacheManager
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryDialogFragment :
        DialogFragment(),
        DictionaryPageFragment.CloseButtonListener,
        TranslationCacheManager.TranslationObserver {
    private var _binding: DialogDictionaryBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private var currentTerm: String? = null
    private var currentLanguageId: Int? = null
    private var dictionaryListener: DictionaryListener? = null

    // Flag to prevent infinite loop when updating text programmatically
    private var isProgrammaticUpdate = false

    companion object {
        private const val ARG_TERM = "term"
        private const val ARG_LANGUAGE_ID = "language_id"

        fun newInstance(term: String, languageId: Int): DictionaryDialogFragment {
            val fragment = DictionaryDialogFragment()
            val args =
                    Bundle().apply {
                        putString(ARG_TERM, term)
                        putInt(ARG_LANGUAGE_ID, languageId)
                    }
            fragment.arguments = args
            return fragment
        }
    }

    interface DictionaryListener {
        fun onDictionaryClosed()
        fun onDictionaryTextSelected(text: String)
    }

    fun setDictionaryListener(listener: DictionaryListener) {
        this.dictionaryListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentTerm = it.getString(ARG_TERM)
            currentLanguageId = it.getInt(ARG_LANGUAGE_ID)
        }
        // Make the dialog full-screen
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = DialogDictionaryBinding.inflate(inflater, container, false)

        // Register as observer for translation text changes
        TranslationCacheManager.getInstance().addObserver(this)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = binding.viewPager
        tabLayout = binding.tabLayout

        // Initialize translation field with cached data
        val cachedText = TranslationCacheManager.getInstance().getTemporaryTranslation()
        binding.translationField.setText(cachedText)

        // Add TextWatcher to sync changes from Dictionary translation field to NativeTermForm
        binding.translationField.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        // Only notify if this is not a programmatic update to avoid loops
                        if (!isProgrammaticUpdate && dictionaryListener != null) {
                            val currentText = s?.toString() ?: ""
                            // Update cache with current text
                            TranslationCacheManager.getInstance()
                                    .setTemporaryTranslation(currentText)
                            dictionaryListener?.onDictionaryTextSelected(currentText)
                        }
                    }
                }
        )

        // Set up ViewPager swipe sensitivity to make swiping harder
        setupViewPagerSwipeSensitivity()

        loadDictionaries()
    }

    private fun loadDictionaries() {
        val term = currentTerm ?: return
        val languageId = currentLanguageId ?: return
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())

        if (!serverSettingsManager.isServerUrlConfigured()) {
            Log.e("DictionaryDialogFragment", "Server URL not configured")
            // Handle error, maybe show a message to the user
            return
        }

        val serverUrl = serverSettingsManager.getServerUrl()
        Log.d("DictionaryDialogFragment", "Using server URL: $serverUrl")

        lifecycleScope.launch(Dispatchers.IO) {
            val service = LuteServerService()
            val dictionaries =
                    service.fetchDictionaries(serverUrl, languageId).filter {
                        it.isActive && it.useFor == "terms"
                    }

            withContext(Dispatchers.Main) {
                val adapter =
                        DictionaryPagerAdapter(
                                this@DictionaryDialogFragment,
                                dictionaries,
                                term,
                                false,
                                this@DictionaryDialogFragment
                        )
                viewPager.adapter = adapter

                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                            val urlString = dictionaries[position].dictUri
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
                                            parts[parts.size - 2].replaceFirstChar(Char::titlecase)
                                        } else {
                                            host
                                        }
                                    } else {
                                        // Fallback for file-based URLs or other cases
                                        val parts = urlString.split("/")
                                        parts.lastOrNull()?.split(".")?.firstOrNull() ?: "Dict"
                                    }
                        }
                        .attach()
            }
        }
    }

    // Method to set the text in the translation field (replaces existing text)
    fun setTranslationText(text: String) {
        try {
            activity?.runOnUiThread {
                isProgrammaticUpdate = true
                binding.translationField.setText(text)
                // Update cache with new text
                TranslationCacheManager.getInstance().setTemporaryTranslation(text)
                // Notify the listener to update the NativeTermFormFragment
                dictionaryListener?.onDictionaryTextSelected(text)
                isProgrammaticUpdate = false
            }
        } catch (e: Exception) {
            Log.e("DictionaryDialogFragment", "Error setting translation text", e)
            isProgrammaticUpdate = false
        }
    }

    // Method to update the translation text (appends to existing text with space separator)
    fun appendTranslationText(text: String) {
        try {
            activity?.runOnUiThread {
                isProgrammaticUpdate = true
                val currentText = binding.translationField.text.toString()
                val newText = if (currentText.isNotEmpty()) "$currentText $text" else text
                binding.translationField.setText(newText)
                // Move cursor to end of text
                binding.translationField.setSelection(newText.length)
                // Update cache with new text
                TranslationCacheManager.getInstance().setTemporaryTranslation(newText)
                // Notify the listener to update the NativeTermFormFragment
                dictionaryListener?.onDictionaryTextSelected(newText)
                isProgrammaticUpdate = false
            }
        } catch (e: Exception) {
            Log.e("DictionaryDialogFragment", "Error updating translation text", e)
            isProgrammaticUpdate = false
        }
    }

    // Method to automatically copy a word to the translation field
    fun autoCopyWord(word: String) {
        try {
            activity?.runOnUiThread {
                // Append the word to the translation text
                appendTranslationText(word)
            }
        } catch (e: Exception) {
            Log.e("DictionaryDialogFragment", "Error in autoCopyWord", e)
        }
    }

    // Method to get the current translation text from the dictionary field
    fun getCurrentTranslationText(): String {
        return binding.translationField.text.toString()
    }

    // CloseButtonListener implementation for handling close button clicks from
    // DictionaryPageFragment
    override fun onCloseButtonClicked() {
        // For the regular dictionary dialog, just dismiss it when the close button is clicked
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Ensure the current text is transferred back to the NativeTermFormFragment
        val currentText = binding.translationField.text.toString()
        dictionaryListener?.onDictionaryTextSelected(currentText)
        // Notify listener that dictionary is closed
        dictionaryListener?.onDictionaryClosed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister as observer for translation text changes
        TranslationCacheManager.getInstance().removeObserver(this)
        _binding = null
    }

    // TranslationCacheManager.TranslationObserver implementation
    override fun onTranslationTextChanged(text: String) {
        // Update the translation field with the new text if it's different
        activity?.runOnUiThread {
            val currentText = binding.translationField.text.toString()
            if (currentText != text) {
                isProgrammaticUpdate = true
                binding.translationField.setText(text)
                isProgrammaticUpdate = false
            }
        }
    }

    private fun setupViewPagerSwipeSensitivity() {
        // Increase the touch slop (minimum distance to trigger swipe) to make swiping harder
        try {
            val recyclerView = viewPager.getChildAt(0)
            if (recyclerView is androidx.recyclerview.widget.RecyclerView) {
                val touchSlopField =
                        androidx.recyclerview.widget.RecyclerView::class.java.getDeclaredField(
                                "mTouchSlop"
                        )
                touchSlopField.isAccessible = true
                val originalTouchSlop = touchSlopField.get(recyclerView) as Int
                // Increase the touch slop by 300% (4x) to make swiping significantly harder
                touchSlopField.set(recyclerView, (originalTouchSlop * 4).toInt())
            }
        } catch (e: Exception) {
            Log.e("DictionaryDialogFragment", "Error adjusting ViewPager swipe sensitivity", e)
        }
    }
}
