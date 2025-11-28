package com.example.luteforandroidv2.ui.nativeread.Term

/**
 * Data class to hold raw HTML content along with page metadata Used for transporting data between
 * repository and ViewModel
 */
data class HtmlTextContent(val htmlContent: String, val pageMetadata: PageMetadata)
