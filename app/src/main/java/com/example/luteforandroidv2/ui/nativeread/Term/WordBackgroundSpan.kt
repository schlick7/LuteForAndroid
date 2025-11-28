package com.example.luteforandroidv2.ui.nativeread.Term

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.style.ReplacementSpan

/**
 * Custom background span that draws background only around individual words, with proper vertical
 * alignment and transparency
 */
class WordBackgroundSpan(private val backgroundColor: Int) : ReplacementSpan() {
    private val verticalPadding = -2f // Padding above and below text (reduced from 4f)
    private val leftPadding = 2f // Left padding reduced from 6f
    private val rightPadding = 6f // Right padding remains 6f
    private val cornerRadius = 6f // Rounded corners for the background

    override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
    ): Int {
        if (text == null) return 0
        val rect = Rect()
        paint.getTextBounds(text.toString(), start, end, rect)
        return (rect.width() + leftPadding + rightPadding).toInt()
    }

    override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
    ) {
        // Only draw if we have a valid background color
        if (backgroundColor != android.graphics.Color.TRANSPARENT) {
            val originalColor = paint.color

            // Get font metrics to properly position the background
            val fm = paint.fontMetrics

            // Calculate the bounds for the background
            // Position the background around the text with padding
            val rect = Rect()
            paint.getTextBounds(text.toString(), start, end, rect)

            val backgroundRect =
                    android.graphics.RectF(
                            x - leftPadding,
                            y + fm.ascent - verticalPadding,
                            x + rect.width() + rightPadding,
                            y + fm.descent + verticalPadding
                    )

            // Set the background color
            paint.color = backgroundColor

            // Draw rounded rectangle background
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, paint)

            // Restore original paint color
            paint.color = originalColor
        }

        // Draw the text on top of the background
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}
