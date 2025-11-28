package com.example.luteforandroidv2.theme

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/** Application-level theme manager that tracks activities and applies themes */
class GlobalAutoThemeManager private constructor(private val application: Application) :
        Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "GlobalAutoThemeManager"
        private var instance: GlobalAutoThemeManager? = null

        fun getInstance(application: Application): GlobalAutoThemeManager {
            if (instance == null) {
                instance = GlobalAutoThemeManager(application)
            }
            return instance!!
        }
    }

    private val themeApplier = AutoThemeApplier(application)
    private var currentThemeColors: AutoThemeProvider.ThemeColors? = null

    init {
        // Register for activity lifecycle callbacks
        application.registerActivityLifecycleCallbacks(this)
    }

    /** Set the current theme colors to be applied to all activities */
    fun setCurrentThemeColors(themeColors: AutoThemeProvider.ThemeColors) {
        Log.d(TAG, "Setting current theme colors")
        currentThemeColors = themeColors
        // Apply theme to all currently running activities
        applyThemeToAllActivities()
    }

    /** Apply current theme to all running activities */
    private fun applyThemeToAllActivities() {
        try {
            val themeColors = currentThemeColors ?: return
            Log.d(TAG, "Applying theme to all activities")

            // Note: We can't directly access all activities from Application
            // The lifecycle callbacks will handle new activities as they're created
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to all activities", e)
        }
    }

    // Activity lifecycle callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            Log.d(TAG, "Activity created: ${activity::class.java.simpleName}")
            val themeColors = currentThemeColors ?: return

            if (activity is AppCompatActivity) {
                themeApplier.applyThemeToActivity(activity, themeColors)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityCreated", e)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        // No action needed
    }

    override fun onActivityResumed(activity: Activity) {
        try {
            Log.d(TAG, "Activity resumed: ${activity::class.java.simpleName}")
            val themeColors = currentThemeColors ?: return

            if (activity is AppCompatActivity) {
                themeApplier.applyThemeToActivity(activity, themeColors)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onActivityResumed", e)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        // No action needed
    }

    override fun onActivityStopped(activity: Activity) {
        // No action needed
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No action needed
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No action needed
    }

    /** Apply theme to a specific activity */
    fun applyThemeToActivity(activity: Activity) {
        try {
            val themeColors = currentThemeColors ?: return
            Log.d(TAG, "Applying theme to activity: ${activity::class.java.simpleName}")

            if (activity is AppCompatActivity) {
                themeApplier.applyThemeToActivity(activity, themeColors)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to activity", e)
        }
    }

    /** Apply theme to a specific fragment */
    fun applyThemeToFragment(fragment: Fragment) {
        try {
            val themeColors = currentThemeColors ?: return
            Log.d(TAG, "Applying theme to fragment: ${fragment::class.java.simpleName}")
            themeApplier.applyThemeToFragment(fragment, themeColors)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to fragment", e)
        }
    }
}
