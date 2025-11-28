package com.example.luteforandroidv2.ui.nativeread.Dictionary

import android.webkit.WebView

/**
 * Apply security measures to dictionary WebViews to block context menus and other unwanted
 * interactions
 */
fun WebView.applyDictionarySecurity() {
    // Android-level blocking
    this.setOnLongClickListener { true }
    this.isLongClickable = false
}

/** JavaScript code to block context menus in dictionary WebViews */
const val CONTEXT_MENU_BLOCKING_JS =
        """
        (function() {
            // Block context menus
            window.oncontextmenu = function(event) {
                event.preventDefault();
                event.stopPropagation();
                return false;
            };

            document.addEventListener('contextmenu', function(e) {
                e.preventDefault();
                e.stopPropagation();
                return false;
            }, true);

            // CSS for additional protection
            if (!document.getElementById('dict-security-style')) {
                var style = document.createElement('style');
                style.id = 'dict-security-style';
                style.textContent = `
                    * {
                        -webkit-touch-callout: none !important;
                        -webkit-user-select: none !important;
                        -khtml-user-select: none !important;
                        -moz-user-select: none !important;
                        -ms-user-select: none !important;
                        user-select: none !important;
                        -webkit-tap-highlight-color: transparent !important;
                    }
                `;
                document.head.appendChild(style);
            }
        })();
    """
