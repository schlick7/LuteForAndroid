package com.example.luteforandroidv2.ui.nativeread.Audio

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.R

/** Audio player fragment for the native reading view Displays audio playback controls and status */
class AudioPlayerFragment : Fragment() {
    private var _binding: com.example.luteforandroidv2.databinding.FragmentAudioPlayerBinding? =
            null
    private val binding
        get() = _binding!!

    private var audioPlayerManager: AudioPlayerManager? = null
    private var audioPlayerListener: AudioPlayerManager.AudioPlayerListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updatePositionRunnable = Runnable { updatePosition() }
    private var isUpdatingPosition = false

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding =
                com.example.luteforandroidv2.databinding.FragmentAudioPlayerBinding.inflate(
                        inflater,
                        container,
                        false
                )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    /** Setup the UI components */
    private fun setupUI() {
        // Setup play/pause button
        binding.playPauseButton.setOnClickListener { audioPlayerManager?.togglePlayPause() }

        // Setup skip forward button
        binding.skipForwardButton.setOnClickListener {
            audioPlayerManager?.skipForward(10) // Skip forward 10 seconds
        }

        // Setup skip backward button
        binding.skipBackwardButton.setOnClickListener {
            audioPlayerManager?.skipBackward(10) // Skip backward 10 seconds
        }

        // Setup seek bar
        binding.audioSeekBar.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: android.widget.SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (fromUser) {
                            audioPlayerManager?.seekTo(progress.toLong())
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                        // Not needed
                    }

                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        // Not needed
                    }
                }
        )

        // Setup playback rate spinner
        val playbackRates =
                arrayOf(
                        0.5f,
                        0.6f,
                        0.7f,
                        0.8f,
                        0.9f,
                        1.0f,
                        1.1f,
                        1.2f,
                        1.3f,
                        1.4f,
                        1.5f,
                        1.6f,
                        1.7f,
                        1.8f,
                        1.9f,
                        2.0f
                )
        val playbackRateStrings =
                arrayOf(
                        "0.5x",
                        "0.6x",
                        "0.7x",
                        "0.8x",
                        "0.9x",
                        "1.0x",
                        "1.1x",
                        "1.2x",
                        "1.3x",
                        "1.4x",
                        "1.5x",
                        "1.6x",
                        "1.7x",
                        "1.8x",
                        "1.9x",
                        "2.0x"
                )

        binding.playbackRateSpinner.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                    ) {
                        val selectedRate = playbackRates[position]
                        audioPlayerManager?.setPlaybackRate(selectedRate)
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                        // Not needed
                    }
                }

        // Set default selection to 1.0x (position 5)
        binding.playbackRateSpinner.setSelection(5)
    }

    /** Set the audio player manager */
    fun setAudioPlayerManager(playerManager: AudioPlayerManager) {
        this.audioPlayerManager = playerManager
        // Set up the audio player listener
        audioPlayerListener =
                object : AudioPlayerManager.AudioPlayerListener {
                    override fun onAudioPrepared(duration: Long) {
                        activity?.runOnUiThread {
                            binding.audioSeekBar.max = duration.toInt()
                            updateDurationText(duration)
                            startPositionUpdates()
                            // Set default playback rate to 1.0x when audio is prepared
                            binding.playbackRateSpinner.setSelection(5) // 1.0x is at index 5
                        }
                    }

                    override fun onAudioCompleted() {
                        activity?.runOnUiThread {
                            binding.playPauseButton.setImageResource(R.drawable.ic_play)
                            binding.currentTimeText.text = formatTime(0)
                            stopPositionUpdates()
                        }
                    }

                    override fun onAudioError(error: String) {
                        activity?.runOnUiThread {
                            // TODO: Display error to user
                            stopPositionUpdates()
                        }
                    }

                    override fun onSeekCompleted() {
                        // Not needed
                    }

                    override fun onPlaybackStateChanged(state: AudioPlayerManager.PlaybackState) {
                        activity?.runOnUiThread {
                            when (state) {
                                AudioPlayerManager.PlaybackState.PLAYING -> {
                                    binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                                    startPositionUpdates()
                                }
                                AudioPlayerManager.PlaybackState.PAUSED -> {
                                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                                    stopPositionUpdates()
                                }
                                AudioPlayerManager.PlaybackState.STOPPED -> {
                                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                                    binding.currentTimeText.text = formatTime(0)
                                    stopPositionUpdates()
                                }
                                AudioPlayerManager.PlaybackState.PREPARING -> {
                                    // Not needed
                                }
                            }
                        }
                    }

                    override fun onPositionSaved(position: Long) {
                        activity?.runOnUiThread {
                            // TODO: Display confirmation to user
                        }
                    }
                }
        playerManager.setAudioPlayerListener(audioPlayerListener!!)
    }

    /** Start updating the position display */
    private fun startPositionUpdates() {
        if (!isUpdatingPosition) {
            isUpdatingPosition = true
            updatePosition()
        }
    }

    /** Stop updating the position display */
    private fun stopPositionUpdates() {
        isUpdatingPosition = false
        mainHandler.removeCallbacks(updatePositionRunnable)
    }

    /** Update the position display */
    private fun updatePosition() {
        if (isUpdatingPosition) {
            audioPlayerManager?.let { player ->
                val position = player.getCurrentPosition()
                val duration = player.getDuration()

                activity?.runOnUiThread {
                    updateSeekBarPosition(position)
                    updateCurrentTimeText(position)
                    updateDurationText(duration)
                }

                // Schedule next update
                mainHandler.postDelayed(updatePositionRunnable, 1000)
            }
        }
    }

    /** Update the current time text */
    private fun updateCurrentTimeText(position: Long) {
        binding.currentTimeText.text = formatTime(position)
    }

    /** Update the duration text */
    private fun updateDurationText(duration: Long) {
        binding.durationText.text = formatTime(duration)
    }

    /** Format time in mm:ss format */
    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    /** Update the seek bar position */
    fun updateSeekBarPosition(position: Long) {
        binding.audioSeekBar.progress = position.toInt()
        updateCurrentTimeText(position)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPositionUpdates()
        _binding = null
    }
}
