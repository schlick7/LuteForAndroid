package com.example.luteforandroidv2.network

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class KokoroTTSClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var ttsCallback: TTSClientCallback? = null

    interface TTSClientCallback {
        fun onTTSStateChanged(isPlaying: Boolean)
        fun onError(error: String)
    }

    fun setTTSClientCallback(callback: TTSClientCallback?) {
        this.ttsCallback = callback
    }

    fun synthesizeSpeech(
        serverUrl: String,
        text: String,
        voice: String = "af_bella", // Default voice
        speed: Float = 1.0f,
        responseFormat: String = "mp3"
    ) {
        // Make sure to stop any existing audio
        stop()

        // Construct the API URL
        val apiUrl = "$serverUrl/v1/audio/speech"

        val requestBody = """
            {
                "model": "kokoro",
                "input": "$text",
                "voice": "$voice",
                "response_format": "$responseFormat",
                "speed": $speed
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(apiUrl)
            .post(RequestBody.create("application/json".toMediaType(), requestBody))
            .build()

        ttsCallback?.onTTSStateChanged(true)
        isPlaying = true

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("KokoroTTSClient", "Request failed", e)
                Handler(Looper.getMainLooper()).post {
                    ttsCallback?.onError("Network request failed: ${e.message}")
                    ttsCallback?.onTTSStateChanged(false)
                    isPlaying = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.byteStream()?.let { audioStream ->
                        try {
                            // Copy the audio stream to a temporary file that can be used by MediaPlayer
                            val tempFile = createTempFile("kokoro_tts", ".tmp", context.cacheDir)
                            tempFile.outputStream().use { outputStream ->
                                audioStream.copyTo(outputStream)
                            }
                            // Now play the temporary file
                            playAudio(tempFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e("KokoroTTSClient", "Error processing audio stream", e)
                            Handler(Looper.getMainLooper()).post {
                                ttsCallback?.onError("Error processing audio: ${e.message}")
                                ttsCallback?.onTTSStateChanged(false)
                                isPlaying = false
                            }
                        } finally {
                            audioStream.close()
                        }
                    }
                } else {
                    Log.e("KokoroTTSClient", "Request failed with code: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        ttsCallback?.onError("Request failed with code: ${response.code}")
                        ttsCallback?.onTTSStateChanged(false)
                        isPlaying = false
                    }
                }
                response.close()
            }
        })
    }

    private fun playAudio(filePath: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT) // Suitable for TTS
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                setDataSource(filePath)
                prepare()

                setOnCompletionListener {
                    this@KokoroTTSClient.isPlaying = false
                    ttsCallback?.onTTSStateChanged(false)
                    releaseMediaPlayer()
                    // Delete the temporary file after playback
                    try {
                        File(filePath).delete()
                    } catch (e: Exception) {
                        Log.e("KokoroTTSClient", "Error deleting temp file: ${e.message}")
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e("KokoroTTSClient", "MediaPlayer error: what=$what, extra=$extra")
                    ttsCallback?.onError("MediaPlayer error: what=$what, extra=$extra")
                    ttsCallback?.onTTSStateChanged(false)
                    this@KokoroTTSClient.isPlaying = false
                    releaseMediaPlayer()
                    // Delete the temporary file if there's an error
                    try {
                        File(filePath).delete()
                    } catch (e: Exception) {
                        Log.e("KokoroTTSClient", "Error deleting temp file: ${e.message}")
                    }
                    true
                }

                start()
            }
        } catch (e: Exception) {
            Log.e("KokoroTTSClient", "Error initializing MediaPlayer", e)
            ttsCallback?.onError("Error initializing MediaPlayer: ${e.message}")
            ttsCallback?.onTTSStateChanged(false)
            isPlaying = false
            // Delete the temporary file if there's an error during initialization
            try {
                File(filePath).delete()
            } catch (e: Exception) {
                Log.e("KokoroTTSClient", "Error deleting temp file: ${e.message}")
            }
        }
    }

    fun isSpeaking(): Boolean {
        return isPlaying
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        ttsCallback?.onTTSStateChanged(false)
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        isPlaying = false
        ttsCallback?.onTTSStateChanged(false)
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }

    fun shutdown() {
        stop()
    }
}
