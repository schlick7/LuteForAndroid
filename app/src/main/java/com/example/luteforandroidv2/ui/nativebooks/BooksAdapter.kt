package com.example.luteforandroidv2.ui.nativebooks

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.luteforandroidv2.R
import com.example.luteforandroidv2.lute.Book
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import org.json.JSONObject

class BooksAdapter(private val onBookClickListener: OnBookClickListener) :
        RecyclerView.Adapter<BooksAdapter.BookViewHolder>() {

    private var books: List<Book> = emptyList()

    interface OnBookClickListener {
        fun onBookClick(book: Book)
        fun onBookEdit(book: Book)
        fun onBookArchive(book: Book)
        fun onBookDelete(book: Book)
    }

    fun updateBooks(newBooks: List<Book>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position])
    }

    override fun getItemCount(): Int = books.size

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.book_title)
        private val languageTextView: TextView = itemView.findViewById(R.id.book_language)
        private val pageNumberTextView: TextView = itemView.findViewById(R.id.book_page_number)
        private val wordCountTextView: TextView = itemView.findViewById(R.id.book_word_count)
        private val statusBarContainer: LinearLayout =
                itemView.findViewById(R.id.status_bar_container)

        fun bind(book: Book) {
            // Set book title
            titleTextView.text = book.title

            // Set language
            languageTextView.text = book.language

            // Set page number if available and not on page 1
            setPageNumberDisplay(book)

            // Set word count
            wordCountTextView.text = "${book.wordCount} words"

            // Set status bar if available
            setupStatusBar(book)

            // Set click listener
            itemView.setOnClickListener { onBookClickListener.onBookClick(book) }

            // Set long click listener to show popup menu
            itemView.setOnLongClickListener {
                showPopupMenu(it, book)
                true // consume the long click
            }
        }

        private fun showPopupMenu(view: View, book: Book) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.book_actions_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onBookClickListener.onBookEdit(book)
                        true
                    }
                    R.id.action_archive -> {
                        onBookClickListener.onBookArchive(book)
                        true
                    }
                    R.id.action_delete -> {
                        onBookClickListener.onBookDelete(book)
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }

        private fun setPageNumberDisplay(book: Book) {
            // Hide page number by default
            pageNumberTextView.visibility = View.GONE

            // Check if we have page information
            val pageNum = book.pageNum
            val pageCount = book.pageCount

            // Only show page number if we have both values and page is not 1
            if (pageNum != null && pageCount != null && pageNum > 1) {
                pageNumberTextView.text = "($pageNum/$pageCount)"
                pageNumberTextView.visibility = View.VISIBLE
            }
        }

        private fun setupStatusBar(book: Book) {
            // Clear any existing views
            statusBarContainer.removeAllViews()

            // Hide status bar if no distribution data
            if (book.statusDistribution.isNullOrEmpty()) {
                statusBarContainer.visibility = View.GONE
                return
            }

            try {
                // Parse the status distribution JSON
                val statusJson = JSONObject(book.statusDistribution)

                // Calculate total for percentage calculations (exclude ignored words status 98)
                var total = 0
                for (key in statusJson.keys()) {
                    // Exclude ignored words (status 98) from total
                    if (key != "98") {
                        total += statusJson.getInt(key)
                    }
                }

                // If no total, hide the status bar
                if (total <= 0) {
                    statusBarContainer.visibility = View.GONE
                    return
                }

                // Show the status bar
                statusBarContainer.visibility = View.VISIBLE

                // Create colored bars for each status (excluding ignored words)
                for (status in arrayOf("0", "1", "2", "3", "4", "5", "99")) {
                    if (statusJson.has(status)) {
                        val count = statusJson.getInt(status)
                        val percentage = (count.toDouble() / total.toDouble() * 100).toInt()

                        // Only show bars with meaningful percentages
                        if (percentage > 0) {
                            val barView = View(itemView.context)
                            val layoutParams =
                                    LinearLayout.LayoutParams(
                                            0,
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            percentage.toFloat()
                                    )
                            layoutParams.setMargins(1, 0, 1, 0)
                            barView.layoutParams = layoutParams

                            // Status colors mapping (exact colors from Reader Theme)
                            val statusColors =
                                    mapOf(
                                            "0" to "#8095FF", // light blue - Unknown (status 0)
                                            "1" to "#b46b7a", // light red - Learning (status 1)
                                            "2" to "#BA8050", // light orange - Learning (status 2)
                                            "3" to "#BD9C7B", // light yellow/tan - Learning
                                            // (status 3)
                                            "4" to "#756D6B", // light grey - Learning (status 4)
                                            "5" to "#40756D6B", // 25% transparent light grey -
                                            // Known (status 5)
                                            "99" to "#00000000" // Transparent - Well-known terms
                                            // (status 99)
                                            )

                            // Set color based on status
                            val color = statusColors[status] ?: "#CCCCCC" // Default gray

                            // Create rounded drawable with the color and border
                            val drawable = GradientDrawable()
                            drawable.cornerRadius = 4f // 4dp radius
                            drawable.setColor(Color.parseColor(color))

                            // Add thin border with text color (luteonbackground)
                            val borderColor =
                                    titleTextView.currentTextColor // Use same color as text
                            drawable.setStroke(1, borderColor) // 1px border

                            barView.background = drawable

                            statusBarContainer.addView(barView)
                        }
                    }
                }

                // If no bars were added, hide the container
                if (statusBarContainer.childCount == 0) {
                    statusBarContainer.visibility = View.GONE
                }

                // Set click listener for the status bar to show book stats popup
                statusBarContainer.setOnClickListener { showBookStatsPopup(book) }
            } catch (e: Exception) {
                // If parsing fails, hide the status bar
                statusBarContainer.visibility = View.GONE
            }
        }

        private fun showBookStatsPopup(book: Book) {
            // Create a popup dialog with pie chart to show book stats
            val context = itemView.context
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.popup_book_stats, null)

            // Set up the dialog
            val dialog = android.app.AlertDialog.Builder(context).setView(view).create()

            // Set the book title
            val titleTextView = view.findViewById<TextView>(R.id.book_title)
            titleTextView.text = "Book Stats: ${book.title}"

            // Set up the stats info
            val statsInfoTextView = view.findViewById<TextView>(R.id.stats_info)

            // Format the last read date to remove seconds and milliseconds
            var formattedDate = book.lastOpenedDate ?: "Never"
            if (book.lastOpenedDate != null) {
                // Parse the date string and remove seconds/milliseconds
                // Assuming the date format is like "YYYY-MM-DD HH:MM:SS.mmm" or similar
                formattedDate =
                        when {
                            book.lastOpenedDate!!.contains("T") -> {
                                // ISO format: "YYYY-MM-DDTHH:MM:SS.mmmZ" or similar
                                val spaceIndex = book.lastOpenedDate!!.indexOf("T")
                                val datePart = book.lastOpenedDate!!.substring(0, spaceIndex)
                                val timePart = book.lastOpenedDate!!.substring(spaceIndex + 1)
                                val timeWithoutSecs =
                                        timePart.substring(0, Math.min(5, timePart.length)) // HH:MM
                                "$datePart $timeWithoutSecs"
                            }
                            book.lastOpenedDate!!.contains(":") -> {
                                // Format: "YYYY-MM-DD HH:MM:SS" or similar
                                val spaceIndex = book.lastOpenedDate!!.indexOf(" ")
                                val datePart = book.lastOpenedDate!!.substring(0, spaceIndex)
                                val timePart = book.lastOpenedDate!!.substring(spaceIndex + 1)
                                val timeWithoutSecs =
                                        timePart.substring(0, Math.min(5, timePart.length)) // HH:MM
                                "$datePart $timeWithoutSecs"
                            }
                            else -> book.lastOpenedDate!! // If no time part, use as is
                        }
            }

            statsInfoTextView.text = "Last Read: $formattedDate"

            // Set up the pie chart
            val pieChart =
                    view.findViewById<com.github.mikephil.charting.charts.PieChart>(R.id.pie_chart)

            // Parse the status distribution JSON from the book
            if (!book.statusDistribution.isNullOrEmpty()) {
                try {
                    val statusJson = JSONObject(book.statusDistribution)

                    // Calculate total for percentage calculations (exclude ignored words status 98)
                    var total = 0
                    for (key in statusJson.keys()) {
                        // Exclude ignored words (status 98) from total
                        if (key != "98") {
                            total += statusJson.getInt(key)
                        }
                    }

                    if (total > 0) {
                        // Prepare data for the pie chart
                        val entries = mutableListOf<com.github.mikephil.charting.data.PieEntry>()
                        val colors = mutableListOf<Int>()

                        // Status to label mapping (showing only numbers for statuses 1-5)
                        val statusLabels =
                                mapOf(
                                        "0" to "Unknown",
                                        "1" to "1",
                                        "2" to "2",
                                        "3" to "3",
                                        "4" to "4",
                                        "5" to "5",
                                        "99" to "Well Known"
                                )

                        // Status colors mapping (same as in setupStatusBar)
                        val statusColors =
                                mapOf(
                                        "0" to
                                                android.graphics.Color.parseColor(
                                                        "#8095FF"
                                                ), // light blue - Unknown
                                        "1" to
                                                android.graphics.Color.parseColor(
                                                        "#b46b7a"
                                                ), // light red - Learning (1)
                                        "2" to
                                                android.graphics.Color.parseColor(
                                                        "#BA8050"
                                                ), // light orange - Learning (2)
                                        "3" to
                                                android.graphics.Color.parseColor(
                                                        "#BD9C7B"
                                                ), // light yellow/tan - Learning (3)
                                        "4" to
                                                android.graphics.Color.parseColor(
                                                        "#756D6B"
                                                ), // light grey - Learning (4)
                                        "5" to
                                                android.graphics.Color.parseColor(
                                                        "#40756D6B"
                                                ), // 25% transparent light grey - Known
                                        "99" to
                                                android.graphics.Color.parseColor(
                                                        "#00000000"
                                                ) // Transparent - Well-known
                                )

                        // Add entries for each status with meaningful percentages
                        for (status in arrayOf("0", "1", "2", "3", "4", "5", "99")) {
                            if (statusJson.has(status)) {
                                val count = statusJson.getInt(status)
                                val percentage = (count.toDouble() / total.toDouble() * 100).toInt()

                                // Only add to chart if percentage is meaningful
                                if (percentage > 0) {
                                    // For pie chart, only show percentage (no labels)
                                    entries.add(
                                            com.github.mikephil.charting.data.PieEntry(
                                                    percentage.toFloat(),
                                                    "" // Empty label since we're showing
                                                    // percentages
                                                    )
                                    )
                                    colors.add(statusColors[status] ?: android.graphics.Color.GRAY)
                                }
                            }
                        }

                        // Create pie chart dataset
                        val dataSet =
                                com.github.mikephil.charting.data.PieDataSet(
                                        entries,
                                        "Status Distribution"
                                )
                        dataSet.colors = colors
                        dataSet.sliceSpace = 1f // Reduce slice spacing to create thin borders
                        dataSet.valueTextSize = 12f

                        // Create the data object and set it to the chart
                        val data = com.github.mikephil.charting.data.PieData(dataSet)
                        data.setValueTextColor(android.graphics.Color.BLACK)
                        data.setValueFormatter(
                                object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return "${value.toInt()}%" // Add percentage symbol
                                    }
                                }
                        )

                        // Set data to the chart
                        pieChart.data = data
                        pieChart.description.isEnabled = false
                        pieChart.legend.isEnabled =
                                false // Disable legend since we're showing detailed list

                        // Disable hole in the center to make it a solid pie chart (not doughnut)
                        pieChart.setDrawHoleEnabled(false)

                        pieChart.animateY(1000)
                        pieChart.invalidate() // refresh the chart

                        // Create the detailed status list
                        createDetailedStatusList(view, statusJson, total)
                    } else {
                        statsInfoTextView.text = "No statistics available for this book"
                    }
                } catch (e: Exception) {
                    statsInfoTextView.text = "Error loading statistics: ${e.message}"
                }
            } else {
                statsInfoTextView.text = "No status distribution data available"
            }

            // Set up close button
            val closeButton = view.findViewById<Button>(R.id.close_button)
            closeButton.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }

        private fun createDetailedStatusList(view: View, statusJson: JSONObject, total: Int) {
            val statusListContainer = view.findViewById<LinearLayout>(R.id.status_list_container)
            statusListContainer.removeAllViews()

            // Status to name mapping
            val statusNames =
                    mapOf(
                            "0" to "Unknown",
                            "1" to "Learning (1)",
                            "2" to "Learning (2)",
                            "3" to "Learning (3)",
                            "4" to "Learning (4)",
                            "5" to "Known",
                            "99" to "Well Known"
                    )

            // Add entries for each status (showing all including 0, 1-5, 99)
            for (status in arrayOf("0", "1", "2", "3", "4", "5", "99")) {
                if (statusJson.has(status)) {
                    val count = statusJson.getInt(status)
                    val percentage =
                            if (total > 0) (count.toDouble() / total.toDouble() * 100).toInt()
                            else 0

                    // Only show if count is greater than 0
                    if (count > 0) {
                        val statusName = statusNames[status] ?: "Status $status"

                        // Create a horizontal layout for each status entry
                        val statusEntryLayout =
                                LinearLayout(view.context).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                                    )
                                                    .apply {
                                                        setMargins(
                                                                0,
                                                                4,
                                                                0,
                                                                4
                                                        ) // Add some spacing between entries
                                                    }
                                }

                        // Status number
                        val statusNumberView =
                                TextView(view.context).apply {
                                    text = status
                                    textSize = 14f
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                    0,
                                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                                    1f
                                            )
                                    setTextColor(
                                            ContextCompat.getColor(
                                                    view.context,
                                                    R.color.lute_on_background
                                            )
                                    )
                                }

                        // Status name
                        val statusNameView =
                                TextView(view.context).apply {
                                    text = statusName
                                    textSize = 14f
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                    0,
                                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                                    2f
                                            )
                                    setTextColor(
                                            ContextCompat.getColor(
                                                    view.context,
                                                    R.color.lute_on_background
                                            )
                                    )
                                }

                        // Percentage
                        val percentageView =
                                TextView(view.context).apply {
                                    text = "$percentage%"
                                    textSize = 14f
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                    0,
                                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                                    1f
                                            )
                                    setTextColor(
                                            ContextCompat.getColor(
                                                    view.context,
                                                    R.color.lute_on_background
                                            )
                                    )
                                }

                        // Total terms
                        val totalTermsView =
                                TextView(view.context).apply {
                                    text = "$count"
                                    textSize = 14f
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                    0,
                                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                                    1f
                                            )
                                    setTextColor(
                                            ContextCompat.getColor(
                                                    view.context,
                                                    R.color.lute_on_background
                                            )
                                    )
                                }

                        statusEntryLayout.addView(statusNumberView)
                        statusEntryLayout.addView(statusNameView)
                        statusEntryLayout.addView(percentageView)
                        statusEntryLayout.addView(totalTermsView)

                        statusListContainer.addView(statusEntryLayout)

                        // Add a divider line after each entry for better readability
                        val divider =
                                View(view.context).apply {
                                    layoutParams =
                                            LinearLayout.LayoutParams(
                                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                                    1
                                            )
                                    setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                                }
                        statusListContainer.addView(divider)
                    }
                }
            }
        }
    }
}
