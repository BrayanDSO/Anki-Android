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

    /**
     * @return the configured actions for this menu display type.
     */
    private fun getConfiguredActions(preferences: SharedPreferences): List<ViewerAction> {
        val prefValue = preferences.getString(preferenceKey, null)
            ?: return emptyList()

        val actionsNames = prefValue.split(SEPARATOR)
        return actionsNames.mapNotNull { name ->
            ViewerAction.entries.firstOrNull { it.name == name }
        }
    }

    fun setPreferenceValue(preferences: SharedPreferences, actions: List<ViewerAction>) {
        val prefValue = actions.joinToString(SEPARATOR) { it.name }
        preferences.edit { putString(preferenceKey, prefValue) }
    }

    data class Items(
        val alwaysShow: List<ViewerAction>,
        val menuOnly: List<ViewerAction>,
        val disabled: List<ViewerAction>
    )

    companion object {
        private const val SEPARATOR = ","

        /**
         * @return actions that weren't configured yet. May happen if the user hasn't configured
         * any of the menu actions, or if a new action was implemented but not configured yet.
         */
        private fun getAllNotConfiguredActions(prefs: SharedPreferences): List<ViewerAction> {
            val mappedActions = MenuDisplayType.entries.flatMap { it.getConfiguredActions(prefs) }
            return ViewerAction.entries.filter {
                it.defaultDisplayType != null && it !in mappedActions
            }
        }

        fun getMenuItems(prefs: SharedPreferences): Items {
            val notConfiguredActions = getAllNotConfiguredActions(prefs)
            val alwaysShowActions = ALWAYS.getConfiguredActions(prefs) +
                notConfiguredActions.filter { it.defaultDisplayType == ALWAYS }
            val menuOnlyActions = MENU_ONLY.getConfiguredActions(prefs) +
                notConfiguredActions.filter { it.defaultDisplayType == MENU_ONLY }
            val disabledActions = DISABLED.getConfiguredActions(prefs) +
                notConfiguredActions.filter { it.defaultDisplayType == DISABLED }

            return Items(alwaysShowActions, menuOnlyActions, disabledActions)
        }
    }
}
