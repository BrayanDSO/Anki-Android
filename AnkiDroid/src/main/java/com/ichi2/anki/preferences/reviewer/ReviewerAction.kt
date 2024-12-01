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

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.ichi2.anki.R

enum class ReviewerAction(
    @IdRes val id: Int,
    @StringRes val title: Int,
    @DrawableRes val drawable: Int,
    val defaultDisplayType: MenuDisplayType
) {
    // Always
    FLAG(R.id.flag_menu, R.string.menu_flag_card, R.drawable.ic_flag_transparent, MenuDisplayType.ALWAYS),
    UNDO(R.id.action_undo, R.string.undo, R.drawable.ic_undo_white, MenuDisplayType.ALWAYS),

    // Menu only
    MARK(R.id.action_mark, R.string.menu_mark_note, R.drawable.ic_star, MenuDisplayType.MENU_ONLY),
    REDO(R.id.action_redo, R.string.redo, R.drawable.ic_redo, MenuDisplayType.MENU_ONLY),
    DELETE(R.id.action_delete, R.string.menu_delete_note, R.drawable.ic_delete, MenuDisplayType.MENU_ONLY),
    EDIT_NOTE(R.id.action_edit_note, R.string.cardeditor_title_edit_card, R.drawable.ic_mode_edit_white, MenuDisplayType.MENU_ONLY),
    DECK_OPTIONS(R.id.action_deck_options, R.string.menu__deck_options, R.drawable.ic_tune_white, MenuDisplayType.MENU_ONLY),

    // Disabled
    CARD_INFO(R.id.action_card_info, R.string.card_info_title, R.drawable.ic_dialog_info, MenuDisplayType.DISABLED),
    ADD_NOTE(R.id.action_add_note, R.string.menu_add_note, R.drawable.ic_add, MenuDisplayType.DISABLED),
    USER_ACTION_1(R.id.user_action_1, R.string.user_action_1, R.drawable.user_action_1, MenuDisplayType.DISABLED),
    USER_ACTION_2(R.id.user_action_2, R.string.user_action_2, R.drawable.user_action_2, MenuDisplayType.DISABLED),
    USER_ACTION_3(R.id.user_action_3, R.string.user_action_3, R.drawable.user_action_3, MenuDisplayType.DISABLED),
    USER_ACTION_4(R.id.user_action_4, R.string.user_action_4, R.drawable.user_action_4, MenuDisplayType.DISABLED),
    USER_ACTION_5(R.id.user_action_5, R.string.user_action_5, R.drawable.user_action_5, MenuDisplayType.DISABLED),
    USER_ACTION_6(R.id.user_action_6, R.string.user_action_6, R.drawable.user_action_6, MenuDisplayType.DISABLED),
    USER_ACTION_7(R.id.user_action_7, R.string.user_action_7, R.drawable.user_action_7, MenuDisplayType.DISABLED),
    USER_ACTION_8(R.id.user_action_8, R.string.user_action_8, R.drawable.user_action_8, MenuDisplayType.DISABLED),
    USER_ACTION_9(R.id.user_action_9, R.string.user_action_9, R.drawable.user_action_9, MenuDisplayType.DISABLED);

    companion object {
        fun fromId(@IdRes id: Int): ReviewerAction {
            return entries.first { it.id == id }
        }
    }
}
