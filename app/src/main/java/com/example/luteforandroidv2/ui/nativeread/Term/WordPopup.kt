package com.example.luteforandroidv2.ui.nativeread.Term

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

/** Simple word popup for displaying term information */
class WordPopup(context: Context) {
    private val popupWindow: PopupWindow
    private val popupView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private val dismissDelay: Long = 5000 // 5 seconds

    init {
        // Create the popup view
        popupView =
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    setTextSize(14f)
                    setPadding(16, 12, 16, 12)

                    // Create background drawable
                    val background =
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#CC000000")) // Semi-transparent black
                                cornerRadius = 8f
                            }
                    setBackground(background)

                    // Add click listener to dismiss the popup when tapped
                    setOnClickListener { dismiss() }
                }

        // Create the popup window
        popupWindow =
                PopupWindow(
                                popupView,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                false // Don't make it focusable
                        )
                        .apply {
                            elevation = 8f
                            isOutsideTouchable = true
                            isFocusable = false // Explicitly set to not focusable
                        }
    }

    /** Show the word popup with the given text at specific screen coordinates */
    fun show(anchor: View, text: String, screenX: Float, screenY: Float) {
        Log.d("WordPopup", "Showing word popup with text: $text")
        Log.d("WordPopup", "Screen coordinates: ($screenX, $screenY)")

        // Don't show popup if there's no text to display
        if (text.isBlank()) {
            Log.d("WordPopup", "No text to display, not showing popup")
            return
        }

        popupView.text = text

        // Measure the popup to get its dimensions
        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        // Calculate position to center popup above the tap point
        val x = (screenX - popupWidth / 2).toInt()
        val y = (screenY - popupHeight - 30).toInt() // 30px above the word for better visibility

        // Show the popup
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)

        // Schedule automatic dismissal
        scheduleDismissal()
    }

    /** Dismiss the popup */
    fun dismiss() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        popupWindow.dismiss()
    }

    /** Schedule automatic dismissal of the popup */
    private fun scheduleDismissal() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = Runnable {
            dismiss()
            dismissRunnable = null
        }
        mainHandler.postDelayed(dismissRunnable!!, dismissDelay)
    }

    /** Check if the popup is currently showing */
    fun isShowing(): Boolean {
        return popupWindow.isShowing
    }
}
