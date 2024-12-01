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

    fun toToolbarItem(): ToolbarItem.DisplayType = ToolbarItem.DisplayType(this)

    private fun getPreferenceKey() = "ReviewerMenuDisplayType_$name"

    private fun getActions(sharedPreferences: SharedPreferences): List<ReviewerAction> {
        val prefValue = sharedPreferences.getString(getPreferenceKey(), null)
        if (prefValue != null) {
            val actionsNames = prefValue.split(SEPARATOR)
            return actionsNames.mapNotNull { name ->
                ReviewerAction.entries.firstOrNull { it.name == name }
            }
        } else {
            return ReviewerAction.entries.filter { it.defaultDisplayType == this }
        }
    }

    fun getToolbarActions(sharedPreferences: SharedPreferences) =
        getActions(sharedPreferences).map { ToolbarItem.Action(it) }

    fun setPreferenceValue(sharedPreferences: SharedPreferences, actions: List<ToolbarItem.Action>) {
        val prefValue = actions.joinToString(SEPARATOR) { it.action.name }
        sharedPreferences.edit {
            putString(getPreferenceKey(), prefValue)
        }
    }

    companion object {
        private const val SEPARATOR = ","
    }
}
