package com.example.luteforandroidv2.ui.nativebooks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.luteforandroidv2.databinding.FragmentNativeBooksBinding
import com.example.luteforandroidv2.lute.Book
import com.example.luteforandroidv2.lute.LuteApiClient
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import kotlinx.coroutines.launch

class NativeBooksFragment : Fragment(), BooksAdapter.OnBookClickListener {
    private val TAG = "NativeBooksFragment"

    private var _binding: FragmentNativeBooksBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: NativeBooksViewModel by viewModels()

    private lateinit var booksAdapter: BooksAdapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNativeBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()
        viewModel.loadBooks()
    }

    private fun setupRecyclerView() {
        booksAdapter = BooksAdapter(this)
        binding.booksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = booksAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshBooks() }
    }

    private fun setupObservers() {
        viewModel.books.observe(viewLifecycleOwner) { books ->
            books?.let {
                booksAdapter.updateBooks(it)
                updateVisibility()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading ?: false
            binding.loadingIndicator.visibility = if (isLoading == true) View.VISIBLE else View.GONE
            updateVisibility()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            binding.errorMessage.text = error
            binding.errorMessage.visibility = if (error != null) View.VISIBLE else View.GONE
            updateVisibility()
        }

        viewModel.isEmpty.observe(viewLifecycleOwner) { isEmpty ->
            binding.emptyMessage.visibility = if (isEmpty == true) View.VISIBLE else View.GONE
            updateVisibility()
        }
    }

    private fun updateVisibility() {
        // Determine which view should be visible based on state
        val hasBooks = !viewModel.books.value.isNullOrEmpty()
        val isLoading = viewModel.isLoading.value == true
        val hasError = !viewModel.errorMessage.value.isNullOrEmpty()
        val isEmpty = viewModel.isEmpty.value == true

        // Show appropriate view
        when {
            isLoading -> {
                // Show loading indicator
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.booksRecyclerView.visibility = View.GONE
                binding.errorMessage.visibility = View.GONE
                binding.emptyMessage.visibility = View.GONE
            }
            hasError -> {
                // Show error message
                binding.loadingIndicator.visibility = View.GONE
                binding.booksRecyclerView.visibility = View.GONE
                binding.errorMessage.visibility = View.VISIBLE
                binding.emptyMessage.visibility = View.GONE
            }
            isEmpty || !hasBooks -> {
                // Show empty message
                binding.loadingIndicator.visibility = View.GONE
                binding.booksRecyclerView.visibility = View.GONE
                binding.errorMessage.visibility = View.GONE
                binding.emptyMessage.visibility = View.VISIBLE
            }
            else -> {
                // Show books list
                binding.loadingIndicator.visibility = View.GONE
                binding.booksRecyclerView.visibility = View.VISIBLE
                binding.errorMessage.visibility = View.GONE
                binding.emptyMessage.visibility = View.GONE
            }
        }
    }

    override fun onBookClick(book: Book) {
        try {
            Log.d(TAG, "Book selected with ID: ${book.id}")

            // Save the book ID as the last opened book
            val sharedPref =
                    requireContext()
                            .getSharedPreferences(
                                    "reader_settings",
                                    android.content.Context.MODE_PRIVATE
                            )
            with(sharedPref.edit()) {
                putString("last_book_id", book.id.toString())
                apply()
            }

            // Get the default reader setting
            val appSettingsPref =
                    requireContext()
                            .getSharedPreferences(
                                    "app_settings",
                                    android.content.Context.MODE_PRIVATE
                            )
            val defaultReader = appSettingsPref.getString("default_reader", "Native Reader")

            // Navigate based on the default reader setting
            if (defaultReader == "Native Reader") {
                // Navigate to the native reader view with the book ID
                val action =
                        NativeBooksFragmentDirections.actionNavNativeBooksToNavNativeRead(
                                book.id.toString()
                        )
                findNavController().navigate(action)
            } else {
                // Navigate to the webview reader view with the book ID
                val action =
                        NativeBooksFragmentDirections.actionNavNativeBooksToNavRead(
                                book.id.toString()
                        )
                findNavController().navigate(action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to reader: ${e.message}", e)
        }
    }

    override fun onBookEdit(book: Book) {
        try {
            Log.d(TAG, "Editing book with ID: ${book.id}")

            // Get the server URL using ServerSettingsManager
            val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
            val serverUrl = serverSettingsManager.getServerUrl()

            // For now, we'll open the book in the webview for editing since there's no native edit
            // UI
            // In a future update, we could implement a native edit book UI
            val url = "$serverUrl/book/edit/${book.id}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening book editor: ${e.message}", e)
        }
    }

    override fun onBookArchive(book: Book) {
        try {
            Log.d(TAG, "Archiving book with ID: ${book.id}")

            // Get the API service using LuteApiClient
            val client = LuteApiClient.getInstance(requireContext())
            val service = client.apiService

            lifecycleScope.launch {
                try {
                    val response = service.archiveBook(book.id)
                    if (response.isSuccessful) {
                        // Refresh the book list
                        viewModel.refreshBooks()
                        Toast.makeText(context, "Book archived successfully", Toast.LENGTH_SHORT)
                                .show()
                    } else {
                        Log.e(
                                TAG,
                                "Failed to archive book: ${response.code()} - ${response.message()}"
                        )
                        Toast.makeText(context, "Failed to archive book", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error archiving book: ${e.message}", e)
                    Toast.makeText(context, "Error archiving book", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in archiving book: ${e.message}", e)
            Toast.makeText(context, "Error archiving book", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBookDelete(book: Book) {
        try {
            Log.d(TAG, "Deleting book with ID: ${book.id}")

            // Show confirmation dialog
            AlertDialog.Builder(requireContext())
                    .setTitle("Delete Book")
                    .setMessage(
                            "Are you sure you want to delete '${book.title}'? This action cannot be undone."
                    )
                    .setPositiveButton("Delete") { _, _ ->
                        // Get the API service using LuteApiClient
                        val client = LuteApiClient.getInstance(requireContext())
                        val service = client.apiService

                        lifecycleScope.launch {
                            try {
                                val response = service.deleteBook(book.id)
                                if (response.isSuccessful) {
                                    // Refresh the book list
                                    viewModel.refreshBooks()
                                    Toast.makeText(
                                                    context,
                                                    "Book deleted successfully",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                } else {
                                    Log.e(
                                            TAG,
                                            "Failed to delete book: ${response.code()} - ${response.message()}"
                                    )
                                    Toast.makeText(
                                                    context,
                                                    "Failed to delete book",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting book: ${e.message}", e)
                                Toast.makeText(context, "Error deleting book", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleting book: ${e.message}", e)
            Toast.makeText(context, "Error deleting book", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
