package com.example.luteforandroidv2.ui.nativeread.Audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.luteforandroidv2.ui.nativeread.NativeReadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Audio player manager for the native reading view Handles audio playback functionality and
 * coordination with the server
 */
class AudioPlayerManager(
        private val context: Context,
        private val repository: NativeReadRepository
) {
    private val mediaPlayer = MediaPlayer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioPlayerListener: AudioPlayerListener? = null
    private var isPrepared = false
    private var currentPosition: Long = 0
    private var duration: Long = 0
    private var playbackRate: Float = 1.0f
    private var bookId: String = ""
    private var isPlayingInBackground = false
    private var isCurrentlyPlaying = false

    // Audio focus management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    init {
        setupMediaPlayer()
        setupAudioFocus()
    }

    /** Set listener for audio player events */
    fun setAudioPlayerListener(listener: AudioPlayerListener) {
        this.audioPlayerListener = listener
    }

    /** Setup the media player with event listeners */
    private fun setupMediaPlayer() {
        mediaPlayer.setOnPreparedListener { mp ->
            isPrepared = true
            duration = mp.duration.toLong()
            audioPlayerListener?.onAudioPrepared(duration)
            Log.d("AudioPlayerManager", "Audio prepared, duration: $duration")
        }

        mediaPlayer.setOnCompletionListener {
            isCurrentlyPlaying = false
            audioPlayerListener?.onAudioCompleted()
            abandonAudioFocus()
            Log.d("AudioPlayerManager", "Audio playback completed")
        }

        mediaPlayer.setOnErrorListener { _, what, extra ->
            audioPlayerListener?.onAudioError("MediaPlayer error: what=$what, extra=$extra")
            abandonAudioFocus()
            Log.e("AudioPlayerManager", "MediaPlayer error: what=$what, extra=$extra")
            false
        }

        mediaPlayer.setOnSeekCompleteListener {
            audioPlayerListener?.onSeekCompleted()
            Log.d("AudioPlayerManager", "Seek completed")
        }
    }

    /** Setup audio focus management */
    private fun setupAudioFocus() {
        // Create audio attributes for media playback
        val audioAttributes =
                AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()

        // Create audio focus change listener
        audioFocusChangeListener =
                AudioManager.OnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            // Permanent loss of audio focus - pause playback and abandon focus
                            Log.d("AudioPlayerManager", "Audio focus lost permanently")
                            pause()
                            abandonAudioFocus()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            // Temporary loss of audio focus - pause playback but keep focus
                            Log.d("AudioPlayerManager", "Audio focus lost temporarily")
                            pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // Temporary loss of audio focus - reduce volume but keep playing
                            Log.d("AudioPlayerManager", "Audio focus lost temporarily, can duck")
                            mediaPlayer.setVolume(0.3f, 0.3f)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // Regained audio focus - resume playback or increase volume
                            Log.d("AudioPlayerManager", "Audio focus regained")
                            if (!mediaPlayer.isPlaying) {
                                play()
                            } else {
                                mediaPlayer.setVolume(1.0f, 1.0f)
                            }
                        }
                    }
                }

        // Create audio focus request (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                            .build()
        }
    }

    /** Request audio focus */
    private fun requestAudioFocus(): Boolean {
        return try {
            val result =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.requestAudioFocus(audioFocusRequest!!)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.requestAudioFocus(
                                audioFocusChangeListener,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN
                        )
                    }

            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.d("AudioPlayerManager", "Audio focus granted")
                    true
                }
                else -> {
                    Log.d("AudioPlayerManager", "Audio focus request denied")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error requesting audio focus", e)
            false
        }
    }

    /** Abandon audio focus */
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION") audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            Log.d("AudioPlayerManager", "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error abandoning audio focus", e)
        }
    }

    /** Initialize player with audio file */
    fun initializePlayer(audioFile: String, bookId: String) {
        this.bookId = bookId
        try {
            resetPlayer()
            mediaPlayer.setDataSource(audioFile)
            mediaPlayer.prepareAsync() // Prepare asynchronously to avoid blocking
            Log.d("AudioPlayerManager", "Initializing player with audio file: $audioFile")
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error initializing player", e)
            audioPlayerListener?.onAudioError("Error initializing player: ${e.message}")
        }
    }

    /** Toggle play/pause */
    fun togglePlayPause() {
        if (isCurrentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    /** Play audio */
    fun play() {
        if (isPrepared) {
            // Request audio focus before playing
            if (requestAudioFocus()) {
                mediaPlayer.start()
                isCurrentlyPlaying = true
                audioPlayerListener?.onPlaybackStateChanged(PlaybackState.PLAYING)
                Log.d("AudioPlayerManager", "Audio playback started")

                // Start foreground service for background playback
                startForegroundService()
            } else {
                Log.e("AudioPlayerManager", "Failed to request audio focus")
                audioPlayerListener?.onAudioError("Failed to request audio focus")
            }
        } else {
            Log.w("AudioPlayerManager", "Cannot play - player not prepared")
        }
    }

    /** Pause audio */
    fun pause() {
        if (isPrepared && isCurrentlyPlaying) {
            mediaPlayer.pause()
            currentPosition = mediaPlayer.currentPosition.toLong()
            isCurrentlyPlaying = false
            audioPlayerListener?.onPlaybackStateChanged(PlaybackState.PAUSED)
            Log.d("AudioPlayerManager", "Audio playback paused at position: $currentPosition")

            // Stop foreground service when pausing
            stopForegroundService()
        }
    }

    /** Stop audio */
    fun stop() {
        if (isPrepared) {
            mediaPlayer.stop()
            currentPosition = 0
            isCurrentlyPlaying = false
            audioPlayerListener?.onPlaybackStateChanged(PlaybackState.STOPPED)
            Log.d("AudioPlayerManager", "Audio playback stopped")

            // Stop foreground service when stopping
            stopForegroundService()

            // Abandon audio focus when stopping
            abandonAudioFocus()
        }
    }

    /** Seek to position */
    fun seekTo(position: Long) {
        if (isPrepared) {
            mediaPlayer.seekTo(position.toInt())
            currentPosition = position
            audioPlayerListener?.onSeekCompleted()
            Log.d("AudioPlayerManager", "Seeking to position: $position")
        }
    }

    /** Get current position */
    fun getCurrentPosition(): Long {
        return if (mediaPlayer.isPlaying) {
            mediaPlayer.currentPosition.toLong()
        } else {
            currentPosition
        }
    }

    /** Get duration */
    fun getDuration(): Long {
        return duration
    }

    /** Set playback rate */
    fun setPlaybackRate(rate: Float) {
        this.playbackRate = rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Only set the playback rate if the media player is prepared
                if (isPrepared) {
                    // Check if player is currently playing before changing the rate
                    val wasPlaying = mediaPlayer.isPlaying
                    val currentPosition = mediaPlayer.currentPosition

                    val playbackParams = mediaPlayer.playbackParams
                    playbackParams.speed = rate
                    mediaPlayer.playbackParams = playbackParams
                    Log.d("AudioPlayerManager", "Playback rate set to: $rate")

                    // If it wasn't playing, we might need to pause it again after setting the rate
                    // (This is a known issue with some Android versions where setting
                    // playbackParams starts playback)
                    if (!wasPlaying) {
                        // If the player started playing after setting the speed, pause it
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.seekTo(currentPosition) // Seek to maintain position
                            mediaPlayer.pause()
                        }
                    }
                } else {
                    Log.w("AudioPlayerManager", "Cannot set playback rate - player not prepared")
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error setting playback rate: ${e.message}")
                // Fall back to just updating the property if setting on MediaPlayer fails
                this.playbackRate = rate
            }
        } else {
            Log.w("AudioPlayerManager", "Playback rate control requires API 23+")
            // For older APIs, just update the property
            this.playbackRate = rate
        }
    }

    /** Skip forward by seconds */
    fun skipForward(seconds: Int) {
        val newPosition = getCurrentPosition() + (seconds * 1000)
        val clampedPosition = newPosition.coerceAtMost(duration)
        seekTo(clampedPosition)
        Log.d(
                "AudioPlayerManager",
                "Skipping forward by $seconds seconds to position: $clampedPosition"
        )
    }

    /** Skip backward by seconds */
    fun skipBackward(seconds: Int) {
        val newPosition = getCurrentPosition() - (seconds * 1000)
        val clampedPosition = newPosition.coerceAtLeast(0)
        seekTo(clampedPosition)
        Log.d(
                "AudioPlayerManager",
                "Skipping backward by $seconds seconds to position: $clampedPosition"
        )
    }

    /** Save current position to server */
    fun saveCurrentPosition() {
        val position = getCurrentPosition()
        // Make a network call to save the position
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.savePlayerData(bookId, position, playbackRate)
                if (result.isSuccess) {
                    Log.d(
                            "AudioPlayerManager",
                            "Successfully saved position $position for book $bookId"
                    )
                    // Notify the listener on the main thread
                    Handler(Looper.getMainLooper()).post {
                        audioPlayerListener?.onPositionSaved(position)
                    }
                } else {
                    Log.e(
                            "AudioPlayerManager",
                            "Failed to save position: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error saving position", e)
            }
        }
    }

    /** Start foreground service for background playback */
    private fun startForegroundService() {
        if (!isPlayingInBackground) {
            val serviceIntent = Intent(context, AudioPlaybackService::class.java)
            context.startForegroundService(serviceIntent)
            isPlayingInBackground = true
            Log.d("AudioPlayerManager", "Started foreground service for background playback")
        }
    }

    /** Stop foreground service */
    private fun stopForegroundService() {
        if (isPlayingInBackground) {
            val serviceIntent = Intent(context, AudioPlaybackService::class.java)
            context.stopService(serviceIntent)
            isPlayingInBackground = false
            Log.d("AudioPlayerManager", "Stopped foreground service")
        }
    }

    /** Reset the player */
    private fun resetPlayer() {
        if (isPrepared) {
            mediaPlayer.reset()
            isPrepared = false
            currentPosition = 0
            duration = 0
        }
    }

    /** Release resources */
    fun release() {
        mediaPlayer.release()
        mainHandler.removeCallbacksAndMessages(null)

        // Stop foreground service when releasing
        stopForegroundService()

        // Abandon audio focus when releasing
        abandonAudioFocus()

        Log.d("AudioPlayerManager", "Audio player released")
    }

    /** Interface for audio player events */
    interface AudioPlayerListener {
        fun onAudioPrepared(duration: Long)
        fun onAudioCompleted()
        fun onAudioError(error: String)
        fun onSeekCompleted()
        fun onPlaybackStateChanged(state: PlaybackState)
        fun onPositionSaved(position: Long)
    }

    /** Enum for playback states */
    enum class PlaybackState {
        PLAYING,
        PAUSED,
        STOPPED,
        PREPARING
    }
}
