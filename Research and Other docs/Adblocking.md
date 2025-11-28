# Ad Blocking Implementation in LuteForAndroid Dictionary WebViews

This document describes the comprehensive ad blocking system implemented in the LuteForAndroid application's dictionary WebViews. The implementation uses a multi-layered approach to ensure maximum effectiveness in blocking ads, trackers, and unwanted content while maintaining the core functionality of displaying dictionary content.

**Version**: 0.3.701

## 1. CSS-Based Ad Blocking

### Implementation Location
`DictionaryPageFragment.kt` - Injected CSS in `setupWebView()` method

### Features
- Hides common ad elements by class and ID patterns:
  - `.ad`, `.ads`, `.advertisement`, `.banner`, `.sponsor`, `.sponsored`
  - Elements with IDs or classes containing ad-related terms
- Blocks ad iframes from known ad networks
- Hides ad images based on source URLs or alt text
- Uses `!important` declarations to override website styles
- Positions blocked elements off-screen as an additional safety measure

### CSS Rules
```css
.ad, .ads, .advertisement, .banner, .sponsor, .sponsored,
[class*="ad-"], [id*="ad-"], [class*="ads-"], [id*="ads-"],
[class*="advertisement"], [id*="advertisement"],
[class*="banner"], [id*="banner"],
[class*="sponsor"], [id*="sponsor"] {
    display: none !important;
    visibility: hidden !important;
    height: 0 !important;
    width: 0 !important;
    position: absolute !important;
    left: -10000px !important;
    top: -10000px !important;
}

iframe[src*="ads"], iframe[src*="doubleclick"], 
iframe[src*="googlesyndication"], iframe[src*="adnxs"],
iframe[name*="ad"], iframe[id*="ad"] {
    display: none !important;
    visibility: hidden !important;
    height: 0 !important;
    width: 0 !important;
    position: absolute !important;
    left: -10000px !important;
    top: -10000px !important;
}

img[src*="ads"], img[src*="doubleclick"], img[src*="googlesyndication"],
img[alt*="ad"], img[alt*="advertisement"], img[alt*="sponsor"] {
    display: none !important;
}
```

## 2. JavaScript Injection for Ad Function Blocking

### Implementation Location
`DictionaryPageFragment.kt` - Injected JavaScript in `setupWebView()` method

### Features
- Blocks common ad loading functions like `eval()` and `document.write()`
- Overrides XMLHttpRequest and fetch API to block requests to known ad networks
- Maintains a denylist of popular ad network domains
- Logs blocked activities to console for debugging

### JavaScript Functions Blocked
- `eval()` - Often used by ad scripts to dynamically execute code
- `document.write()` - Used to inject ad content into pages
- XMLHttpRequest requests to ad networks
- Fetch API requests to ad networks

### Ad Network Denylist
- doubleclick.net
- googlesyndication.com
- googleadservices.com
- adservice.google.com
- facebook.com/tr
- facebook.net
- ads.yahoo.com
- adnxs.com
- rubiconproject.com
- openx.net
- pubmatic.com
- taboola.com
- outbrain.com

## 3. Enhanced WebView Settings

### Implementation Location
`DictionaryPageFragment.kt` - WebView configuration in `setupWebView()` method

### Features
- Disables file access to prevent local file exploitation
- Disables content access for additional security
- Blocks file access from file URLs
- Blocks universal access from file URLs
- Disables multiple windows to reduce attack surface
- Disables geolocation to prevent tracking
- Disables form data and password saving
- Sets mixed content mode to never allow HTTP on HTTPS pages

### WebView Settings
```kotlin
webView.settings.setAllowFileAccess(false)
webView.settings.setAllowContentAccess(false)
webView.settings.setAllowFileAccessFromFileURLs(false)
webView.settings.setAllowUniversalAccessFromFileURLs(false)
webView.settings.setSupportMultipleWindows(false)
webView.settings.setGeolocationEnabled(false)
webView.settings.setSaveFormData(false)
webView.settings.setSavePassword(false)

// Block mixed content
webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
```

## 4. Content Filtering with JSoup

### Implementation Location
`DictionaryPageFragment.kt` - `filterContentWithJSoup()` method

### Features
- Parses HTML content before loading to remove unwanted elements
- Strips script tags that may contain ad or tracking code
- Removes style tags that may contain ad-related CSS
- Eliminates iframe elements commonly used for ads
- Filters elements by class or ID patterns associated with ads
- Blocks images and links from known ad networks
- Removes object and embed tags often used for ads
- Cleans up potentially malicious attributes

### Elements Removed
- `<script>` tags
- `<style>` tags
- `<iframe>` elements
- Elements with ad-related classes/IDs
- Images from ad networks
- `<object>` and `<embed>` tags
- `<noscript>` tags
- Tracking-related `<meta>` tags
- Elements with potentially malicious attributes (onclick, onerror, onload)

### Usage
- Processes cached content before loading
- Filters content before caching to improve subsequent load times
- Works in conjunction with other ad blocking methods for maximum effectiveness

## 5. Link Navigation Control

### Implementation Location
`DictionaryPageFragment.kt` - `WebViewClient.shouldOverrideUrlLoading()` method

### Features
- Blocks all external navigation to prevent opening of ad-related URLs
- Blocks internal navigation except for specific dictionary-related paths
- Maintains core dictionary functionality by allowing API calls and definition lookups
- Prevents users from navigating away from dictionary entries to other pages on the same site
- Logs blocked link attempts for debugging purposes

### Allowed Internal Paths
- `/api/` - Dictionary API calls
- `/definition/` - Word definition lookups
- `/word/` - Word-related endpoints
- `/search/` - Search functionality endpoints

## Summary

The ad blocking implementation uses a defense-in-depth approach with five complementary techniques:

1. **CSS Blocking** - Hides ad elements at render time
2. **JavaScript Blocking** - Prevents ad scripts from executing
3. **WebView Restrictions** - Limits WebView capabilities to reduce attack surface
4. **Content Filtering** - Removes ads from HTML before rendering
5. **Navigation Control** - Blocks external links that may lead to ads

This multi-layered approach ensures comprehensive ad blocking while maintaining the core functionality of displaying dictionary content. Each layer provides protection even if others fail, creating a robust ad blocking system.