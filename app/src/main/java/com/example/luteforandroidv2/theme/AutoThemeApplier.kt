package com.example.luteforandroidv2.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.example.luteforandroidv2.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Service that applies auto theme colors to UI elements throughout the app */
class AutoThemeApplier(private val context: Context) {

    companion object {
        private const val TAG = "AutoThemeApplier"
    }

    /** Apply theme colors to an activity */
    fun applyThemeToActivity(
            activity: AppCompatActivity,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            Log.d(TAG, "Applying theme to activity: ${activity::class.java.simpleName}")

            // Apply theme to the main content view
            val rootView = activity.findViewById<View>(android.R.id.content)
            if (rootView != null) {
                applyThemeToView(rootView, themeColors)
            }

            // Apply theme to the action bar/toolbar if it exists
            val actionBar = activity.supportActionBar
            if (actionBar != null) {
                actionBar.setBackgroundDrawable(ColorDrawable(themeColors.backgroundColor))
                // Note: Text color is harder to change for ActionBar
            }

            Log.d(TAG, "Successfully applied theme to activity")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to activity", e)
        }
    }

    /** Apply theme colors to a fragment */
    fun applyThemeToFragment(fragment: Fragment, themeColors: AutoThemeProvider.ThemeColors) {
        try {
            Log.d(TAG, "Applying theme to fragment: ${fragment::class.java.simpleName}")

            // Apply theme to the fragment's view
            fragment.view?.let { fragmentView -> applyThemeToView(fragmentView, themeColors) }

            Log.d(TAG, "Successfully applied theme to fragment")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to fragment", e)
        }
    }

    /** Recursively apply theme colors to a view and all its children */
    fun applyThemeToView(view: View, themeColors: AutoThemeProvider.ThemeColors) {
        try {
            // Apply background color to all views
            view.setBackgroundColor(themeColors.backgroundColor)

            // Apply specific styling based on view type
            when (view) {
                is TextView -> {
                    applyThemeToTextView(view, themeColors)
                }
                is Button -> {
                    applyThemeToButton(view, themeColors)
                }
                is MaterialButton -> {
                    applyThemeToMaterialButton(view, themeColors)
                }
                is EditText -> {
                    applyThemeToEditText(view, themeColors)
                }
                is TextInputEditText -> {
                    applyThemeToTextInputEditText(view, themeColors)
                }
                is TextInputLayout -> {
                    applyThemeToTextInputLayout(view, themeColors)
                }
                is MaterialCardView -> {
                    applyThemeToCardView(view, themeColors)
                }
                is Toolbar -> {
                    applyThemeToToolbar(view, themeColors)
                }
                is ViewGroup -> {
                    // Recursively apply theme to all children
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        if (child != null) {
                            applyThemeToView(child, themeColors)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to view", e)
        }
    }

    /** Apply theme to TextView */
    private fun applyThemeToTextView(
            textView: TextView,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            textView.setTextColor(themeColors.textColor)
            textView.setBackgroundColor(themeColors.backgroundColor)

            // Handle hint text color if it's an EditText
            if (textView is EditText) {
                textView.setHintTextColor(adjustAlpha(themeColors.textColor, 0.7f))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to TextView", e)
        }
    }

    /** Apply theme to regular Button */
    private fun applyThemeToButton(button: Button, themeColors: AutoThemeProvider.ThemeColors) {
        try {
            button.setTextColor(themeColors.textColor)
            button.setBackgroundColor(themeColors.primaryColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to Button", e)
        }
    }

    /** Apply theme to MaterialButton */
    private fun applyThemeToMaterialButton(
            button: MaterialButton,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            button.setTextColor(themeColors.textColor)
            button.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(themeColors.primaryColor)

            // Set ripple color
            button.rippleColor =
                    android.content.res.ColorStateList.valueOf(
                            adjustAlpha(themeColors.primaryColor, 0.3f)
                    )
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to MaterialButton", e)
        }
    }

    /** Apply theme to EditText */
    private fun applyThemeToEditText(
            editText: EditText,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            editText.setTextColor(themeColors.textColor)
            editText.setBackgroundColor(themeColors.backgroundColor)
            editText.setHintTextColor(adjustAlpha(themeColors.textColor, 0.7f))
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to EditText", e)
        }
    }

    /** Apply theme to TextInputEditText */
    private fun applyThemeToTextInputEditText(
            editText: TextInputEditText,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            editText.setTextColor(themeColors.textColor)
            editText.setBackgroundColor(themeColors.backgroundColor)
            editText.setHintTextColor(adjustAlpha(themeColors.textColor, 0.7f))
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to TextInputEditText", e)
        }
    }

    /** Apply theme to TextInputLayout */
    private fun applyThemeToTextInputLayout(
            layout: TextInputLayout,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            // TextInputLayout styling is more complex and often handled by themes
            // For now, we'll just ensure the background is set
            layout.setBackgroundColor(themeColors.backgroundColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to TextInputLayout", e)
        }
    }

    /** Apply theme to CardView */
    private fun applyThemeToCardView(
            cardView: MaterialCardView,
            themeColors: AutoThemeProvider.ThemeColors
    ) {
        try {
            cardView.setCardBackgroundColor(themeColors.backgroundColor)

            // Apply stroke color using primary color
            cardView.strokeColor = themeColors.primaryColor
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to CardView", e)
        }
    }

    /** Apply theme to Toolbar */
    private fun applyThemeToToolbar(toolbar: Toolbar, themeColors: AutoThemeProvider.ThemeColors) {
        try {
            toolbar.setBackgroundColor(themeColors.backgroundColor)

            // Toolbar text color is tricky to change directly
            // It's usually controlled by the app theme
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme to Toolbar", e)
        }
    }

    /** Adjust alpha of a color */
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
