package com.example.luteforandroidv2.ui.terms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentTermsBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class TermsFragment : Fragment() {

    private var _binding: FragmentTermsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTermsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWebView()
    }

    private fun setupWebView() {
        // Keep WebView transparent during loading to prevent FOUC (Opacity Control approach)
        binding.webview.alpha = 0f
        
        binding.webview.webViewClient = object : WebViewClient() {
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
        
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.settings.domStorageEnabled = true
        
        // Hide scrollbars in the WebView
        binding.webview.scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
        binding.webview.isHorizontalScrollBarEnabled = false
        binding.webview.isVerticalScrollBarEnabled = false
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            binding.webview.loadUrl("$serverUrl/term/index")
        } else {
            // Load a local HTML page or show an error message
            binding.webview.loadData("<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>", "text/html", "UTF-8")
        }
    }
    
    
    
    fun refreshWebView() {
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            binding.webview.loadUrl("$serverUrl/term/index")
        } else {
            // Load a local HTML page or show an error message
            binding.webview.loadData("<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>", "text/html", "UTF-8")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}