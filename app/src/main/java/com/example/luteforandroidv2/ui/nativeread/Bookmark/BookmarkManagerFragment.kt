package com.example.luteforandroidv2.ui.nativeread.Bookmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentBookmarkManagerBinding
import com.example.luteforandroidv2.ui.nativeread.Audio.AudioPlayerManager

/**
 * Bookmark manager fragment for the native reading view Displays a list of bookmarks and allows
 * management operations
 */
class BookmarkManagerFragment : Fragment() {
    private var _binding: FragmentBookmarkManagerBinding? = null
    private val binding
        get() = _binding!!

    private var audioPlayerManager: AudioPlayerManager? = null
    private var bookmarkAdapter: BookmarkAdapter? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarkManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    /** Setup the UI components */
    private fun setupUI() {
        // Setup bookmark list adapter
        bookmarkAdapter = BookmarkAdapter { bookmark ->
            // Handle bookmark click - seek to bookmark position
            audioPlayerManager?.seekTo(bookmark.position)
        }

        binding.bookmarkList.adapter = bookmarkAdapter

        // Setup refresh button
        binding.refreshButton.setOnClickListener { loadBookmarks() }

        // Setup close button
        binding.closeButton.setOnClickListener { parentFragmentManager.popBackStack() }

        // Load bookmarks
        loadBookmarks()
    }

    /** Set the audio player manager */
    fun setAudioPlayerManager(playerManager: AudioPlayerManager) {
        this.audioPlayerManager = playerManager

        // Set up the audio player listener
        playerManager.setAudioPlayerListener(
                object : AudioPlayerManager.AudioPlayerListener {
                    override fun onAudioPrepared(duration: Long) {
                        // Not applicable for bookmark manager
                    }

                    override fun onAudioCompleted() {
                        // Not applicable for bookmark manager
                    }

                    override fun onAudioError(error: String) {
                        activity?.runOnUiThread {
                            // TODO: Display error to user
                        }
                    }

                    override fun onSeekCompleted() {
                        // Not needed
                    }

                    override fun onPlaybackStateChanged(state: AudioPlayerManager.PlaybackState) {
                        // Not needed
                    }

                    override fun onPositionSaved(position: Long) {
                        // Not needed
                    }
                }
        )
    }

    /** Load bookmarks - TODO: Implement proper bookmark loading */
    private fun loadBookmarks() {
        // TODO: Implement bookmark loading using ReadRepository
        // This should load bookmarks from the server using the repository
        // For now, we'll just clear the list
        activity?.runOnUiThread { bookmarkAdapter?.updateBookmarks(emptyList()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        bookmarkAdapter = null
    }
}
