package com.example.luteforandroidv2.util

import java.util.concurrent.CopyOnWriteArrayList

class LanguageManager private constructor() {
    private val availableLanguages = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<LanguageUpdateListener>()
    
    interface LanguageUpdateListener {
        fun onLanguagesUpdated(languages: List<String>)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: LanguageManager? = null
        
        fun getInstance(): LanguageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanguageManager().also { INSTANCE = it }
            }
        }
    }
    
    fun setAvailableLanguages(languages: List<String>) {
        android.util.Log.d("LanguageManager", "setAvailableLanguages called with ${languages.size} languages: $languages")
        availableLanguages.clear()
        availableLanguages.addAll(languages)
        
        // Notify all listeners
        listeners.forEach { listener ->
            try {
                android.util.Log.d("LanguageManager", "Notifying listener: $listener")
                listener.onLanguagesUpdated(availableLanguages.toList())
            } catch (e: Exception) {
                android.util.Log.e("LanguageManager", "Error notifying listener", e)
            }
        }
    }
    
    fun getAvailableLanguages(): List<String> {
        return availableLanguages.toList()
    }
    
    fun clearLanguages() {
        availableLanguages.clear()
    }
    
    fun addListener(listener: LanguageUpdateListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: LanguageUpdateListener) {
        listeners.remove(listener)
    }
}