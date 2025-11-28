package com.example.luteforandroidv2.ui.nativeread.Term

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.luteforandroidv2.R

class ParentTermSuggestionsAdapter(
        private val suggestions: List<String>,
        private val onSuggestionSelected: (String) -> Unit
) : RecyclerView.Adapter<ParentTermSuggestionsAdapter.SuggestionViewHolder>() {

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.suggestion_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view =
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_parent_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.textView.text = suggestion
        holder.itemView.setOnClickListener { onSuggestionSelected(suggestion) }
    }

    override fun getItemCount(): Int = suggestions.size
}
