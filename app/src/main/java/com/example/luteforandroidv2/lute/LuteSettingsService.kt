package com.example.luteforandroidv2.lute

import android.util.Log

class LuteSettingsService(private val apiService: LuteApiService) {

    companion object {
        // Minimal CSS that only hides necessary UI elements and enables mobile term selection
        // This is much shorter than the full CSS and fits within URL limits
        private const val MINIMAL_ANDROID_CSS =
                // Hide unnecessary UI elements in Android app
                ".lute-android-app .header," +
                ".lute-android-app .menu-bar," +
                ".lute-android-app .home-link," +
                ".lute-android-app .menu," +
                ".lute-android-app .menu-item," +
                ".lute-android-app .sub-menu," +
                ".lute-android-app .bug_report," +
                ".lute-android-app .flash-notice," +
                ".lute-android-app .lutelogo_small," +
                ".lute-android-app .lutelogo," +
                ".lute-android-app .reading_menu_logo_container," +
                ".lute-android-app .hamburger-btn," +
                ".lute-android-app.hide-title-progress #thetexttitle," +
                ".lute-android-app.hide-title-progress #headertexttitle," +
                ".lute-android-app.hide-title-progress .read-slide-container," +
                ".lute-android-app.hide-title-progress .reading_header_page," +
                ".lute-android-app.hide-title-progress #page_indicator{" +
                "display:none!important;" +
                "visibility:hidden!important;" +
                "opacity:0!important;" +
                "width:0!important;" +
                "height:0!important;" +
                "margin:0!important;" +
                "padding:0!important;" +
                "position:absolute!important;" +
                "top:-9999px!important;" +
                "left:-9999px!important;" +
                "}" +
                // Keep header blue as requested
                ".lute-android-app .header{" +
                "background-color:#8095FF!important;" +
                "color:#EBEBEB!important;" +
                "}" +
                // Enable mobile-friendly term interaction
                ".lute-android-app .textitem{" +
                "cursor:pointer!important;" +
                "user-select:none!important;" +
                "-webkit-user-select:none!important;" +
                "}" +
                // Ensure proper hover and click states for mobile
                ".lute-android-app .wordhover,.lute-android-app .kwordmarked{" +
                "pointer-events:auto!important;" +
                "z-index:1000!important;" +
                "}" +
                // Make sure terms are clickable on mobile
                ".lute-android-app .textitem.click_enabled{" +
                "pointer-events:auto!important;" +
                "}" +
                // Ensure popup behavior works correctly
                ".lute-android-app .textarea-style{" +
                "touch-action:manipulation!important;" +
                "}"
    }

    /**
     * Update Android custom styles using the simple endpoint
     *
     * FIXED TO USE YOUR CORRECT APPROACH:
     * - Only sends what we need to update (the CSS)
     * - Doesn't send ANY other settings
     * - Uses minimal CSS that fits within URL length limits
     */
    suspend fun updateAndroidCustomStyles(): Boolean {
        return try {
            Log.d("LuteSettingsService", "Updating Android custom styles using minimal CSS")

            // Use the simple endpoint with minimal CSS
            // This follows the correct approach of only updating what's needed
            val response = apiService.updateCustomStyles(MINIMAL_ANDROID_CSS)

            Log.d("LuteSettingsService", "Update custom styles response: ${response.code()}")

            if (response.isSuccessful) {
                Log.d(
                        "LuteSettingsService",
                        "Successfully updated Android custom styles with minimal CSS"
                )
                return true
            } else {
                Log.e(
                        "LuteSettingsService",
                        "Failed to update custom styles. Status: ${response.code()}"
                )
                return false
            }
        } catch (e: Exception) {
            Log.e("LuteSettingsService", "Error updating Android custom styles", e)
            return false
        }
    }
}
