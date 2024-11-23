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
package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ToolbarAction
import com.ichi2.anki.preferences.reviewer.ToolbarDisplayCategory
import com.ichi2.anki.preferences.reviewer.ToolbarItem
import com.ichi2.anki.preferences.reviewer.ToolbarItemsAdapter

class ReviewerToolbarButtonsFragment : Fragment(R.layout.preferences_reviewer_toolbar_buttons) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val items = ToolbarAction.entries.map { ToolbarItem.Action(it) } + ToolbarDisplayCategory.entries.map { ToolbarItem.DisplayCategory(it) }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.adapter = ToolbarItemsAdapter(items)
        super.onViewCreated(view, savedInstanceState)
    }
}
