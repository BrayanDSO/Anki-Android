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

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ToolbarItem
import com.ichi2.anki.preferences.reviewer.ToolbarItemsAdapter
import com.ichi2.anki.preferences.reviewer.ToolbarItemsTouchHelperCallback
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.ViewerActionMenu
import com.ichi2.anki.snackbar.showSnackbar
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

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            item.title?.let { showSnackbar(it, Snackbar.LENGTH_SHORT) }
            true
        }
        val menu = toolbar.menu
        val callback = ToolbarItemsTouchHelperCallback(items).apply {
            setOnClearViewListener { items ->
                val menuOnlyItemsIndex = items.indexOfFirst {
                    it is ToolbarItem.DisplayType && it.menuDisplayType == MenuDisplayType.MENU_ONLY
                }
                val disabledItemsIndex = items.indexOfFirst {
                    it is ToolbarItem.DisplayType && it.menuDisplayType == MenuDisplayType.DISABLED
                }

                val alwaysShowItems = items.subList(1, menuOnlyItemsIndex).filterIsInstance<ToolbarItem.ViewerItem>()
                val onMenuItems = items.subList(menuOnlyItemsIndex, disabledItemsIndex).filterIsInstance<ToolbarItem.ViewerItem>()
                val disabledItems2 = items.subList(disabledItemsIndex, items.lastIndex).filterIsInstance<ToolbarItem.ViewerItem>()

                val prefs = sharedPrefs()
                MenuDisplayType.ALWAYS.setPreferenceValue(prefs, alwaysShowItems)
                MenuDisplayType.MENU_ONLY.setPreferenceValue(prefs, onMenuItems)
                MenuDisplayType.DISABLED.setPreferenceValue(prefs, disabledItems2)

                menu.clear()
                addItemsToMenu(menu, alwaysShowItems, MenuItem.SHOW_AS_ACTION_ALWAYS, requireContext())
                addItemsToMenu(menu, onMenuItems, MenuItem.SHOW_AS_ACTION_NEVER, requireContext())
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

        addItemsToMenu(menu, alwaysItems, MenuItem.SHOW_AS_ACTION_ALWAYS, requireContext())
        addItemsToMenu(menu, menuOnlyItems, MenuItem.SHOW_AS_ACTION_NEVER, requireContext())

        super.onViewCreated(view, savedInstanceState)
    }

    // TODO mostrar ícones no menu
    companion object {
        private fun addItemsToMenu(
            menu: Menu,
            items: List<ToolbarItem.ViewerItem>,
            menuActionType: Int,
            context: Context // TODO possivelmente converter para Resources
        ) {
            for (item in items) {
                val action = item.viewerItem
                val title = action.titleRes?.let { context.getString(it) } ?: ""
                val menuItem = if (action is ViewerActionMenu) {
                    menu.addSubMenu(Menu.NONE, action.id, Menu.NONE, action.titleRes)
                    menu.findItem(action.id)
                } else {
                    menu.add(Menu.NONE, action.id, Menu.NONE, title)
                }
                with(menuItem) {
                    action.drawableRes?.let { setIcon(it) }
                    setShowAsAction(menuActionType)
                }
            }
        }

        fun setConfiguredMenu(menu: Menu, context: Context) {
            val preferences = context.sharedPrefs()

            val alwaysItems = MenuDisplayType.ALWAYS.getToolbarActions(preferences)
            val menuOnlyItems = MenuDisplayType.MENU_ONLY.getToolbarActions(preferences)

            addItemsToMenu(menu, alwaysItems, MenuItem.SHOW_AS_ACTION_ALWAYS, context)
            addItemsToMenu(menu, menuOnlyItems, MenuItem.SHOW_AS_ACTION_NEVER, context)

            for (action in ViewerAction.entries) {
                if (action.parentMenu == null) continue
                val subMenu = menu.findItem(action.parentMenu.id).subMenu
                val title = action.titleRes?.let { context.getString(it) } ?: ""
                subMenu?.add(Menu.NONE, action.id, Menu.NONE, title)?.apply {
                    action.drawableRes?.let { setIcon(it) }
                }
            }
        }
    }
}
