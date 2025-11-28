package com.example.luteforandroidv2.ui.nativeread.Translation

/**
 * Singleton manager for temporary translation text caching. Ensures consistent text synchronization
 * between NativeTermForm and Dictionary popup.
 */
class TranslationCacheManager private constructor() {
    private var temporaryTranslation: String? = null
    private var isCacheValid: Boolean = false
    private val observers = mutableSetOf<TranslationObserver>()

    interface TranslationObserver {
        fun onTranslationTextChanged(text: String)
    }

    companion object {
        @Volatile private var INSTANCE: TranslationCacheManager? = null

        fun getInstance(): TranslationCacheManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: TranslationCacheManager().also { INSTANCE = it }
                    }
        }
    }

    /** Set temporary translation text in cache */
    fun setTemporaryTranslation(text: String) {
        temporaryTranslation = text
        isCacheValid = true
        // Notify all observers of the change
        notifyObservers(text)
    }

    /** Get temporary translation text from cache */
    fun getTemporaryTranslation(): String {
        return if (isCacheValid) temporaryTranslation ?: "" else ""
    }

    /** Check if cache has valid data */
    fun hasValidCache(): Boolean {
        return isCacheValid
    }

    /** Clear temporary translation cache */
    fun clearTemporaryTranslation() {
        temporaryTranslation = null
        isCacheValid = false
        // Notify all observers of the change
        notifyObservers("")
    }

    /** Add an observer to be notified of translation text changes */
    fun addObserver(observer: TranslationObserver) {
        observers.add(observer)
    }

    /** Remove an observer from being notified of translation text changes */
    fun removeObserver(observer: TranslationObserver) {
        observers.remove(observer)
    }

    /** Notify all observers of a translation text change */
    private fun notifyObservers(text: String) {
        // Make a copy of the set to avoid ConcurrentModificationException
        val observersCopy = observers.toSet()
        for (observer in observersCopy) {
            observer.onTranslationTextChanged(text)
        }
    }
}
