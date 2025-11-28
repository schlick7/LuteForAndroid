package com.example.luteforandroidv2.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentHomeBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var webView: WebView? = null
    private var startX = 0f
    private var startY = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWebView()
    }

    private fun setupWebView() {
        webView = binding.webview
        
        // Keep WebView transparent during loading to prevent FOUC (Opacity Control approach)
        webView?.alpha = 0f
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Add Android app identification class as early as possible
                WebViewClassHelper.ensureAndroidAppClassAdded(view)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Add Android app identification class again to ensure it's applied
                WebViewClassHelper.addAndroidAppClass(view)
                
                // Fade in WebView now that everything is loaded (Opacity Control approach)
                view?.animate()?.alpha(1f)?.setDuration(200)?.start()
            }
        }
        
        // Disable horizontal scrolling at the WebView level
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.useWideViewPort = false
        webView?.settings?.loadWithOverviewMode = true
        
        // Add touch listener to prevent horizontal scrolling
        webView?.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = motionEvent.x
                    startY = motionEvent.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(motionEvent.x - startX)
                    val deltaY = Math.abs(motionEvent.y - startY)
                    
                    // If horizontal movement is greater than vertical, consume the touch event
                    // to prevent horizontal scrolling
                    if (deltaX > deltaY) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            // Check if a book ID was passed from the books view
            val sharedPref = requireActivity().getSharedPreferences("book_selection", Context.MODE_PRIVATE)
            val bookId = sharedPref.getString("selected_book_id", null)
            
            if (bookId != null) {
                android.util.Log.d("HomeFragment", "Loading book with ID: $bookId")
                webView?.loadUrl("$serverUrl/read/$bookId")
                // Clear the book ID so it's not used again
                with (sharedPref.edit()) {
                    remove("selected_book_id")
                    apply()
                }
            } else {
                android.util.Log.d("HomeFragment", "Loading main page")
                webView?.loadUrl("$serverUrl/")
            }
        } else {
            // Show an error message in the WebView
            webView?.loadData("<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>", "text/html", "UTF-8")
        }
    }

    fun executeJavaScript(jsCode: String, callback: (Boolean) -> Unit) {
        webView?.evaluateJavascript(jsCode) { result ->
            // JavaScript execution completed successfully
            callback(true)
        } ?: run {
            // WebView is null, execution failed
            callback(false)
        }
    }
    
    fun goBackInWebView(callback: (Boolean) -> Unit) {
        webView?.let { webView ->
            if (webView.canGoBack()) {
                webView.goBack()
                callback(true)
            } else {
                callback(false)
            }
        } ?: run {
            callback(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        webView = null
    }
}