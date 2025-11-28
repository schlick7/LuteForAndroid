package com.example.luteforandroidv2.lute

import android.content.Context
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LuteApiClient private constructor(context: Context) {
    private val serverSettingsManager = ServerSettingsManager.getInstance(context)

    private val loggingInterceptor =
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    private val okHttpClient =
            OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS) // Increased connection timeout
                    .readTimeout(30, TimeUnit.SECONDS) // Increased read timeout
                    .writeTimeout(30, TimeUnit.SECONDS) // Increased write timeout
                    .build()

    val apiService: LuteApiService by lazy {
        val baseUrl =
                if (serverSettingsManager.isServerUrlConfigured()) {
                    serverSettingsManager.getServerUrl() + "/"
                } else {
                    // Use a placeholder URL when no server is configured
                    "http://localhost:5001/"
                }

        android.util.Log.d("LuteApiClient", "Using base URL: $baseUrl")

        Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LuteApiService::class.java)
    }

    companion object {
        @Volatile private var INSTANCE: LuteApiClient? = null

        fun getInstance(context: Context): LuteApiClient {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: LuteApiClient(context).also { INSTANCE = it }
                    }
        }
    }
}
