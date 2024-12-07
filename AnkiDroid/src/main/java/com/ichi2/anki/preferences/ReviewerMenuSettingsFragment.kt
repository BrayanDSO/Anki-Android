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
import androidx.appcompat.view.menu.MenuBuilder
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.OnClearViewListener
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsAdapter
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsRecyclerItem
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsTouchHelperCallback
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.utils.ext.sharedPrefs

class ReviewerMenuSettingsFragment :
    Fragment(R.layout.preferences_reviewer_menu),
    OnClearViewListener {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuItems = MenuDisplayType.getMenuItems(sharedPrefs())
        setupRecyclerView(view, menuItems)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val menu = toolbar.menu
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        setupMenu(toolbar.menu, menuItems)
    }

    private fun setupRecyclerView(view: View, menuItems: MenuDisplayType.Items) {
        fun toRecyclerItems(items: List<ViewerAction>): Array<ReviewerMenuSettingsRecyclerItem> =
            items.map { ReviewerMenuSettingsRecyclerItem.Action(it) }.toTypedArray()

        val recyclerViewItems = listOf(
            ReviewerMenuSettingsRecyclerItem.DisplayType(MenuDisplayType.ALWAYS),
            *toRecyclerItems(menuItems.alwaysShow),
            ReviewerMenuSettingsRecyclerItem.DisplayType(MenuDisplayType.MENU_ONLY),
            *toRecyclerItems(menuItems.menuOnly),
            ReviewerMenuSettingsRecyclerItem.DisplayType(MenuDisplayType.DISABLED),
            *toRecyclerItems(menuItems.disabled)
        )

        val callback = ReviewerMenuSettingsTouchHelperCallback(recyclerViewItems)
        callback.setOnClearViewListener(this)
        val itemTouchHelper = ItemTouchHelper(callback)

        val adapter = ReviewerMenuSettingsAdapter(recyclerViewItems).apply {
            setOnDragListener { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        }
        view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    override fun onClearView(items: List<ReviewerMenuSettingsRecyclerItem>) {
        val menuOnlyItemsIndex = items.indexOfFirst {
            it is ReviewerMenuSettingsRecyclerItem.DisplayType && it.menuDisplayType == MenuDisplayType.MENU_ONLY
        }
        val disabledItemsIndex = items.indexOfFirst {
            it is ReviewerMenuSettingsRecyclerItem.DisplayType && it.menuDisplayType == MenuDisplayType.DISABLED
        }

        val alwaysShowItems = items.subList(1, menuOnlyItemsIndex)
            .mapNotNull { (it as? ReviewerMenuSettingsRecyclerItem.Action)?.viewerAction }
        val onMenuItems = items.subList(menuOnlyItemsIndex, disabledItemsIndex)
            .mapNotNull { (it as? ReviewerMenuSettingsRecyclerItem.Action)?.viewerAction }
        val disabledItems = items.subList(disabledItemsIndex, items.lastIndex)
            .mapNotNull { (it as? ReviewerMenuSettingsRecyclerItem.Action)?.viewerAction }

        val preferences = sharedPrefs()
        MenuDisplayType.ALWAYS.setPreferenceValue(preferences, alwaysShowItems)
        MenuDisplayType.MENU_ONLY.setPreferenceValue(preferences, onMenuItems)
        MenuDisplayType.DISABLED.setPreferenceValue(preferences, disabledItems)
    }

    private fun addActionsToMenu(
        menu: Menu,
        actions: List<ViewerAction>,
        menuActionType: Int
    ) {
        val menuActions = ViewerAction.entries.mapNotNull { it.parentMenu }
        for (action in actions) {
            val title = action.titleRes?.let { getString(it) } ?: ""
            val menuItem = if (action in menuActions) {
                menu.addSubMenu(Menu.NONE, action.menuId, Menu.NONE, title)
                menu.findItem(action.menuId)
            } else {
                menu.add(Menu.NONE, action.menuId, Menu.NONE, title)
            }
            with(menuItem) {
                action.drawableRes?.let { setIcon(it) }
                setShowAsAction(menuActionType)
            }
        }
    }

    private fun setupMenu(menu: Menu, menuItems: MenuDisplayType.Items) {
        addActionsToMenu(menu, menuItems.alwaysShow, MenuItem.SHOW_AS_ACTION_ALWAYS)
        addActionsToMenu(menu, menuItems.menuOnly, MenuItem.SHOW_AS_ACTION_NEVER)
    }
}
