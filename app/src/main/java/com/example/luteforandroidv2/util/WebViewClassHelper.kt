package com.example.luteforandroidv2.util

import android.webkit.WebView

/**
 * Utility class for adding the Android app class to WebViews as early as possible
 * to minimize FOUC (Flash of Unstyled Content).
 */
class WebViewClassHelper {
    companion object {
        /**
         * Add the lute-android-app class to the WebView as early as possible.
         * This should be called multiple times with delays to ensure the class
         * is added before initial rendering occurs.
         */
        @JvmStatic
        fun addAndroidAppClass(webView: WebView?) {
            webView?.evaluateJavascript(
                """
                (function() {
                    if (document.documentElement) {
                        document.documentElement.classList.add('lute-android-app');
                    }
                    if (document.body) {
                        document.body.classList.add('lute-android-app');
                    }
                })();
                """.trimIndent(), null)
        }
        
        /**
         * Add the class immediately and schedule additional attempts to ensure
         * it's applied before rendering begins.
         */
        @JvmStatic
        fun ensureAndroidAppClassAdded(webView: WebView?) {
            // Immediate attempt
            addAndroidAppClass(webView)
            
            // Additional attempts with small delays
            webView?.postDelayed({ addAndroidAppClass(webView) }, 10)
            webView?.postDelayed({ addAndroidAppClass(webView) }, 50)
            webView?.postDelayed({ addAndroidAppClass(webView) }, 100)
        }
    }
}