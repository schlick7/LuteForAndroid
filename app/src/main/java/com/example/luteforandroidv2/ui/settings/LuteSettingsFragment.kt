package com.example.luteforandroidv2.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.databinding.FragmentLuteSettingsBinding
import com.example.luteforandroidv2.ui.settings.ServerSettingsManager
import com.example.luteforandroidv2.util.WebViewClassHelper

class LuteSettingsFragment : Fragment() {

    private var _binding: FragmentLuteSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLuteSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWebView()
    }

    private fun setupWebView() {
        // Keep WebView transparent during loading to prevent FOUC (Opacity Control approach)
        binding.webviewLuteSettings.alpha = 0f
        
        binding.webviewLuteSettings.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // Add Android app identification class as early as possible
                WebViewClassHelper.ensureAndroidAppClassAdded(view)
                
                // Simple delayed approach to set height
                view?.postDelayed({
                    updateWebViewHeight(view)
                }, 1000)
                
                // Additional delayed updates to catch any dynamic content
                view?.postDelayed({
                    updateWebViewHeight(view)
                }, 3000)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Add Android app identification class again to ensure it's applied
                WebViewClassHelper.addAndroidAppClass(view)
                
                // Fade in WebView now that everything is loaded (Opacity Control approach)
                view?.animate()?.alpha(1f)?.setDuration(100)?.start()

                // Add a 5px padding div to the bottom of the page
                view?.evaluateJavascript("javascript:(function() { " +
                    "var padder = document.getElementById('gemini-padder');" +
                    "if (padder === null) {" +
                    "    padder = document.createElement('div');" +
                    "    padder.id = 'gemini-padder';" +
                    "    padder.style.height = '10px';" +
                    "    document.body.appendChild(padder);" +
                    "}" +
                    "})();", null)
            }
        }
        
        // Configure WebView settings
        binding.webviewLuteSettings.settings.javaScriptEnabled = true
        binding.webviewLuteSettings.settings.domStorageEnabled = true
        binding.webviewLuteSettings.settings.useWideViewPort = false
        binding.webviewLuteSettings.settings.loadWithOverviewMode = true
        binding.webviewLuteSettings.settings.builtInZoomControls = false
        binding.webviewLuteSettings.settings.displayZoomControls = false
        binding.webviewLuteSettings.settings.setSupportZoom(false)
        binding.webviewLuteSettings.settings.defaultTextEncodingName = "UTF-8"
        
        // Hide scrollbars in the WebView
        binding.webviewLuteSettings.scrollBarStyle = android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
        binding.webviewLuteSettings.isHorizontalScrollBarEnabled = false
        binding.webviewLuteSettings.isVerticalScrollBarEnabled = false
        
        val serverSettingsManager = ServerSettingsManager.getInstance(requireContext())
        if (serverSettingsManager.isServerUrlConfigured()) {
            val serverUrl = serverSettingsManager.getServerUrl()
            binding.webviewLuteSettings.loadUrl("$serverUrl/settings/index")
        } else {
            // Load a local HTML page or show an error message
            binding.webviewLuteSettings.loadData("<html><body><h2>Server not configured</h2><p>Please configure your server URL in App Settings.</p><p>Go to Settings > App Settings to enter your server URL.</p></body></html>", "text/html", "UTF-8")
        }
    }

    private fun updateWebViewHeight(view: WebView?) {
        view?.evaluateJavascript(
            """
            (function() {
                // Force reflow to ensure all content is rendered
                document.body.style.display = 'none';
                document.body.offsetHeight; // Trigger reflow
                document.body.style.display = 'block';
                
                var body = document.body;
                var html = document.documentElement;
                
                // Get the maximum possible height
                var height = Math.max(
                    body.scrollHeight,
                    body.offsetHeight,
                    html.clientHeight,
                    html.scrollHeight,
                    html.offsetHeight
                );
                
                // Add a generous buffer to ensure all content is visible
                return height + 300;
            })()
            """.trimIndent()) { result ->
            try {
                val height = result?.replace("\"", "")?.toIntOrNull() ?: 4000
                if (height > 0) {
                    val layoutParams = view?.layoutParams
                    // Set a minimum height to ensure the WebView doesn't shrink too much
                    val finalHeight = Math.max(height, 2000)
                    layoutParams?.height = Math.min(finalHeight, 8000)
                    view?.layoutParams = layoutParams
                    view?.requestLayout()
                    
                    // Force another layout pass to ensure the height is applied
                    view?.post {
                        view.layoutParams = view.layoutParams
                        view.requestLayout()
                    }
                }
            } catch (e: Exception) {
                val layoutParams = view?.layoutParams
                layoutParams?.height = 4000
                view?.layoutParams = layoutParams
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
