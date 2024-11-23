/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences.reviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView

class ToolbarItemsAdapter(private val items: List<ToolbarItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ACTION_VIEW_TYPE -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.reviewer_settings_action_item, parent, false)
                ActionViewHolder(itemView)
            }
            DISPLAY_CATEGORY_VIEW_TYPE -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.reviewer_settings_action_category, parent, false)
                CategoryViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("Unexpected viewType")
        }
    }

    override fun getItemCount(): Int =
        items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ToolbarItem.Action -> ACTION_VIEW_TYPE
            is ToolbarItem.DisplayCategory -> DISPLAY_CATEGORY_VIEW_TYPE
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> holder.bind((item as ToolbarItem.Action).action)
            is CategoryViewHolder -> holder.bind((item as ToolbarItem.DisplayCategory).displayCategory)
        }
    }

    private class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(action: ToolbarAction) {
            itemView.findViewById<FixedTextView>(R.id.title).setText(action.title)
            itemView.findViewById<AppCompatImageView>(R.id.icon).setBackgroundResource(action.drawable)
        }
    }

    private class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(displayCategory: ToolbarDisplayCategory) {
            itemView.findViewById<FixedTextView>(R.id.title).setText(displayCategory.title)
        }
    }

    companion object {
        private const val ACTION_VIEW_TYPE = 0
        private const val DISPLAY_CATEGORY_VIEW_TYPE = 1
    }
}

sealed class ToolbarItem {
    data class Action(val action: ToolbarAction) : ToolbarItem()
    data class DisplayCategory(val displayCategory: ToolbarDisplayCategory) : ToolbarItem()
}
