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

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.ichi2.anki.R

enum class MenuDisplayType(@StringRes val title: Int) {
    ALWAYS(R.string.custom_buttons_setting_always_show),
    MENU_ONLY(R.string.custom_buttons_setting_menu_only),
    DISABLED(R.string.disabled);

    private val preferenceKey get() = "ReviewerMenuDisplayType_$name"

    private fun getPreferenceActions(preferences: SharedPreferences): List<ViewerMenuItem> {
        val prefValue = preferences.getString(preferenceKey, null)
            ?: return emptyList()

        val actionsNames = prefValue.split(SEPARATOR)
        return actionsNames.mapNotNull { name ->
            ViewerAction.entries.firstOrNull { it.name == name }
                ?: ViewerActionMenu.entries.firstOrNull { it.name == name }
        }
    }

    fun getToolbarActions(preferences: SharedPreferences): List<ToolbarItem.ViewerItem> {
        val prefActions = getPreferenceActions(preferences)
        val unmappedActions = getUnmappedActions(preferences).filter {
            it.defaultDisplayType == this
        }
        return (prefActions + unmappedActions).map { ToolbarItem.ViewerItem(it) }
    }

    fun setPreferenceValue(preferences: SharedPreferences, actions: List<ToolbarItem.ViewerItem>) {
        val prefValue = actions.joinToString(SEPARATOR) { it.viewerItem.name }
        preferences.edit {
            putString(preferenceKey, prefValue)
        }
    }

    companion object {
        private const val SEPARATOR = ","

        private fun getUnmappedActions(preferences: SharedPreferences): List<ViewerMenuItem> {
            val mappedActions = MenuDisplayType.entries.flatMap {
                it.getPreferenceActions(preferences)
            }
            return ViewerAction.entries.filter {
                it.defaultDisplayType != null && it !in mappedActions
            } + ViewerActionMenu.entries.filter {
                it !in mappedActions
            }
        }
    }
}
