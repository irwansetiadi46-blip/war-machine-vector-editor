package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView

class KeywordCheckboxAdapter(
    private val keywords: List<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<KeywordCheckboxAdapter.ViewHolder>() {

    private val selectedStates = BooleanArray(keywords.size) { false }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbKeyword: CheckBox = view.findViewById(R.id.cbKeyword)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keyword_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val keyword = keywords[position]
        holder.cbKeyword.text = keyword
        
        // Temporarily nullify to avoid listener calls during binding
        holder.cbKeyword.setOnCheckedChangeListener(null)
        holder.cbKeyword.isChecked = selectedStates[position]
        
        holder.cbKeyword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != selectedStates[position]) {
                // To support checking limits on the activity level, the caller handles verification.
                // We will inform the listener of the change.
                selectedStates[position] = isChecked
                onSelectionChanged()
            }
        }
    }

    override fun getItemCount(): Int = keywords.size

    fun getSelectedKeywords(): List<String> {
        return keywords.filterIndexed { index, _ -> selectedStates[index] }
    }

    fun getSelectedCount(): Int {
        return selectedStates.count { it }
    }

    fun isChecked(position: Int): Boolean {
        if (position in selectedStates.indices) {
            return selectedStates[position]
        }
        return false
    }

    fun setCheckedSilently(position: Int, checked: Boolean) {
        if (position in selectedStates.indices) {
            selectedStates[position] = checked
            notifyItemChanged(position)
        }
    }

    fun setDefaultSelection(indices: List<Int>) {
        for (i in selectedStates.indices) {
            selectedStates[i] = indices.contains(i)
        }
        notifyDataSetChanged()
    }

    fun setAllCheckedState(checked: Boolean, limit: Int = keywords.size) {
        var countChecked = 0
        for (i in selectedStates.indices) {
            if (checked) {
                if (countChecked < limit) {
                    selectedStates[i] = true
                    countChecked++
                } else {
                    selectedStates[i] = false
                }
            } else {
                selectedStates[i] = false
            }
        }
        notifyDataSetChanged()
    }
}
