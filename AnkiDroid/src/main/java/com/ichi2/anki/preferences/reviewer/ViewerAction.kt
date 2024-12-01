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
import com.ichi2.anki.Flag
import com.ichi2.anki.R

/**
 * @param defaultDisplayType the default display type of the action in the toolbar.
 * Use `null` if the action is restricted to gestures/controls and shouldn't be in the menu, or
 * if the item
 */
enum class ViewerAction(
    @IdRes override val id: Int,
    @StringRes override val titleRes: Int?,
    @DrawableRes override val drawableRes: Int?,
    override val defaultDisplayType: MenuDisplayType? = null,
    val parentMenu: ViewerActionMenu? = null
) : ViewerMenuItem {
    // Always
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
    USER_ACTION_9(R.id.user_action_9, R.string.user_action_9, R.drawable.user_action_9, MenuDisplayType.DISABLED),

    // Child items
    BURY_NOTE(R.id.action_bury_note, R.string.menu_bury_note, drawableRes = null, parentMenu = ViewerActionMenu.BURY),
    BURY_CARD(R.id.action_bury_card, R.string.menu_bury_card, drawableRes = null, parentMenu = ViewerActionMenu.BURY),
    SUSPEND_NOTE(R.id.action_suspend_note, R.string.menu_suspend_note, drawableRes = null, parentMenu = ViewerActionMenu.SUSPEND),
    SUSPEND_CARD(R.id.action_suspend_card, R.string.menu_suspend_card, drawableRes = null, parentMenu = ViewerActionMenu.SUSPEND),
    UNSET_FLAG(R.id.flag_none, titleRes = null, Flag.NONE.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_RED(R.id.flag_red, titleRes = null, Flag.RED.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_BLUE(R.id.flag_blue, titleRes = null, Flag.BLUE.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_PINK(R.id.flag_pink, titleRes = null, Flag.PINK.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_TURQUOISE(R.id.flag_turquoise, titleRes = null, Flag.TURQUOISE.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_GREEN(R.id.flag_green, titleRes = null, Flag.GREEN.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_ORANGE(R.id.flag_orange, titleRes = null, Flag.ORANGE.drawableRes, parentMenu = ViewerActionMenu.FLAG),
    FLAG_PURPLE(R.id.flag_purple, titleRes = null, Flag.PURPLE.drawableRes, parentMenu = ViewerActionMenu.FLAG)
    ;

    companion object {
        fun fromId(@IdRes id: Int): ViewerAction {
            return entries.first { it.id == id }
        }
    }
}

interface ViewerMenuItem {
    @get:IdRes val id: Int

    @get:StringRes val titleRes: Int?

    @get:DrawableRes val drawableRes: Int?
    val defaultDisplayType: MenuDisplayType?
    val name: String
}

enum class ViewerActionMenu(
    @IdRes override val id: Int,
    @StringRes override val titleRes: Int?,
    @DrawableRes override val drawableRes: Int?,
    override val defaultDisplayType: MenuDisplayType?
) : ViewerMenuItem {
    SUSPEND(R.id.action_suspend, R.string.menu_suspend, R.drawable.ic_suspend, MenuDisplayType.MENU_ONLY),
    BURY(R.id.action_bury, R.string.menu_bury, R.drawable.ic_flip_to_back_white, MenuDisplayType.MENU_ONLY),
    FLAG(R.id.action_flag, R.string.menu_flag, R.drawable.ic_flag_transparent, MenuDisplayType.MENU_ONLY)
}
