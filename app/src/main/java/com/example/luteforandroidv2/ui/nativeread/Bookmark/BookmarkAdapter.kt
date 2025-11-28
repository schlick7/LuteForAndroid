package com.example.luteforandroidv2.ui.nativeread.Bookmark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.luteforandroidv2.R

/**
 * Adapter for displaying bookmarks in a RecyclerView
 */
class BookmarkAdapter(
    private val onItemClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {
    private var bookmarks: List<Bookmark> = emptyList()

    /** Update the bookmarks list */
    fun updateBookmarks(newBookmarks: List<Bookmark>) {
        this.bookmarks = newBookmarks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(bookmarks[position])
    }

    override fun getItemCount(): Int = bookmarks.size

    inner class BookmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val positionText: TextView = itemView.findViewById(R.id.bookmark_position)
        private val labelText: TextView = itemView.findViewById(R.id.bookmark_label)

        fun bind(bookmark: Bookmark) {
            // Format position as time (mm:ss)
            val minutes = (bookmark.position / 1000 / 60).toInt()
            val seconds = (bookmark.position / 1000 % 60).toInt()
            positionText.text = String.format("%02d:%02d", minutes, seconds)

            // Set label or default text
            labelText.text = if (bookmark.label.isNotBlank()) {
                bookmark.label
            } else {
                "Bookmark at ${minutes}:${seconds}"
            }

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(bookmark)
            }
        }
    }
}
