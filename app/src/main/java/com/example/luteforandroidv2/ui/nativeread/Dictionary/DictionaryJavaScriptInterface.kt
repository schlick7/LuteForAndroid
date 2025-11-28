package com.example.luteforandroidv2.ui.read

import android.webkit.JavascriptInterface
import android.util.Log

class DictionaryJavaScriptInterface(private val listener: DictionaryListener) {
    
    @JavascriptInterface
    fun onDictionaryLinkClicked(href: String, text: String) {
        Log.d("DictionaryJSInterface", "Link clicked: href=$href, text=$text")
        listener.onLinkClicked(href, text)
    }
    
    @JavascriptInterface
    fun onDictionaryContentCopy(content: String) {
        Log.d("DictionaryJSInterface", "Content copied: $content")
        listener.onContentCopy(content)
    }
    
    interface DictionaryListener {
        fun onLinkClicked(href: String, text: String)
        fun onContentCopy(content: String)
    }
}