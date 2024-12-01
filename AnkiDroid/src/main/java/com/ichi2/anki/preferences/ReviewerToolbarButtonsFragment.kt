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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ToolbarItem
import com.ichi2.anki.preferences.reviewer.ToolbarItemsAdapter
import com.ichi2.anki.preferences.reviewer.ToolbarItemsTouchHelperCallback
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.utils.ext.sharedPrefs

class ReviewerToolbarButtonsFragment : Fragment(R.layout.preferences_reviewer_toolbar_buttons) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sharedPreferences = sharedPrefs()
        val alwaysItems = MenuDisplayType.ALWAYS.getToolbarActions(sharedPreferences)
        val menuOnlyItems = MenuDisplayType.MENU_ONLY.getToolbarActions(sharedPreferences)
        val disabledItems = MenuDisplayType.DISABLED.getToolbarActions(sharedPreferences)

        val items: List<ToolbarItem> = listOf(
            ToolbarItem.DisplayType(MenuDisplayType.ALWAYS),
            *alwaysItems.toTypedArray(),
            ToolbarItem.DisplayType(MenuDisplayType.MENU_ONLY),
            *menuOnlyItems.toTypedArray(),
            ToolbarItem.DisplayType(MenuDisplayType.DISABLED),
            *disabledItems.toTypedArray()
        )

        val menu = view.findViewById<MaterialToolbar>(R.id.toolbar).menu
        val callback = ToolbarItemsTouchHelperCallback(items).apply {
            setOnClearViewListener { items ->
                val menuOnlyItemsIndex = items.indexOfFirst {
                    it is ToolbarItem.DisplayType && it.menuDisplayType == MenuDisplayType.MENU_ONLY
                }
                val disabledItemsIndex = items.indexOfFirst {
                    it is ToolbarItem.DisplayType && it.menuDisplayType == MenuDisplayType.DISABLED
                }

                val alwaysShowItems = items.subList(1, menuOnlyItemsIndex).filterIsInstance<ToolbarItem.Action>()
                val onMenuItems = items.subList(menuOnlyItemsIndex, disabledItemsIndex).filterIsInstance<ToolbarItem.Action>()
                val disabledItems2 = items.subList(disabledItemsIndex, items.lastIndex).filterIsInstance<ToolbarItem.Action>()

                val prefs = sharedPrefs()
                MenuDisplayType.ALWAYS.setPreferenceValue(prefs, alwaysShowItems)
                MenuDisplayType.MENU_ONLY.setPreferenceValue(prefs, onMenuItems)
                MenuDisplayType.DISABLED.setPreferenceValue(prefs, disabledItems2)

                menu.clear()
                addItemsToMenu(menu, alwaysShowItems, MenuItem.SHOW_AS_ACTION_ALWAYS)
                addItemsToMenu(menu, onMenuItems, MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
        val itemTouchHelper = ItemTouchHelper(callback)
        val adapter = ToolbarItemsAdapter(items).apply {
            onDragListener = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        }
        with(view.findViewById<RecyclerView>(R.id.recycler_view)) {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            itemTouchHelper.attachToRecyclerView(this)
        }

        addItemsToMenu(menu, alwaysItems, MenuItem.SHOW_AS_ACTION_ALWAYS)
        addItemsToMenu(menu, menuOnlyItems, MenuItem.SHOW_AS_ACTION_NEVER)

        super.onViewCreated(view, savedInstanceState)
    }

    private fun addItemsToMenu(menu: Menu, items: List<ToolbarItem.Action>, menuActionType: Int) {
        for (item in items) {
            val action = item.action
            menu.add(0, action.id, Menu.NONE, action.title).apply {
                icon = ContextCompat.getDrawable(requireContext(), action.drawable)
                setShowAsAction(menuActionType)
                setOnMenuItemClickListener {
                    val title = getString(action.title)
                    showThemedToast(requireContext(), title, true)
                    true
                }
            }
        }
    }
}
