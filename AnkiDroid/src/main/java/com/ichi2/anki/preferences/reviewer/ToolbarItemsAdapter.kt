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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView
import java.util.Collections

class ToolbarItemsAdapter(private val items: List<ToolbarItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var onDragListener: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ACTION_VIEW_TYPE -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.reviewer_settings_action_item, parent, false)
                ActionViewHolder(itemView)
            }
            DISPLAY_TYPE_VIEW_TYPE -> {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.reviewer_settings_action_category, parent, false)
                DisplayTypeViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("Unexpected viewType")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ToolbarItem.Action -> ACTION_VIEW_TYPE
            is ToolbarItem.DisplayType -> DISPLAY_TYPE_VIEW_TYPE
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ActionViewHolder -> holder.bind((item as ToolbarItem.Action).action)
            is DisplayTypeViewHolder -> holder.bind((item as ToolbarItem.DisplayType).menuDisplayType)
        }
    }

    private inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        /** @see [R.layout.reviewer_settings_action_item] */
        fun bind(action: ReviewerAction) {
            itemView.findViewById<FixedTextView>(R.id.title).setText(action.title)
            itemView.findViewById<AppCompatImageView>(R.id.icon).setBackgroundResource(action.drawable)

            itemView.findViewById<AppCompatImageView>(R.id.drag_handle).setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragListener?.invoke(this)
                }
                return@setOnTouchListener false
            }
        }
    }

    private class DisplayTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(displayCategory: MenuDisplayType) {
            itemView.findViewById<FixedTextView>(R.id.title).setText(displayCategory.title)
        }
    }

    companion object {
        private const val ACTION_VIEW_TYPE = 0
        const val DISPLAY_TYPE_VIEW_TYPE = 1
    }
}

sealed class ToolbarItem {
    data class Action(val action: ReviewerAction) : ToolbarItem()
    data class DisplayType(val menuDisplayType: MenuDisplayType) : ToolbarItem()
}

class ToolbarItemsTouchHelperCallback<T : ToolbarItem>(private val items: List<T>) : ItemTouchHelper.Callback() {
    private val movementFlags = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    private var onClearViewListener: ((List<T>) -> Unit)? = null

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (viewHolder.itemViewType == ToolbarItemsAdapter.DISPLAY_TYPE_VIEW_TYPE) {
            0
        } else {
            movementFlags
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.absoluteAdapterPosition
        val toPosition = target.absoluteAdapterPosition

        // `Always show` is always the first element, so don't allow moving above it
        if (toPosition == 0) return false

        Collections.swap(items, fromPosition, toPosition)
        recyclerView.adapter?.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // do nothing
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        super.clearView(recyclerView, viewHolder)
        onClearViewListener?.invoke(items)
    }

    override fun isLongPressDragEnabled(): Boolean = false

    fun setOnClearViewListener(listener: (List<T>) -> Unit) {
        onClearViewListener = listener
    }
}
