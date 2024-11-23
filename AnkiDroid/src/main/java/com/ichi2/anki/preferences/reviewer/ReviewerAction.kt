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
    ADD_NOTE(R.id.reviewer_add_note, R.string.menu_add_note, R.drawable.ic_add_note, MenuDisplayType.DISABLED),
    UNDO(R.id.reviewer_undo, R.string.undo, R.drawable.ic_undo_white, MenuDisplayType.ALWAYS),
    REDO(R.id.reviewer_redo, R.string.redo, R.drawable.ic_redo, MenuDisplayType.MENU_ONLY),
    MARK(R.id.reviewer_mark, R.string.menu_mark_note, R.drawable.ic_star, MenuDisplayType.ALWAYS),
    DELETE(R.id.menu_delete, R.string.menu_delete_note, R.drawable.ic_delete, MenuDisplayType.MENU_ONLY);

    fun toToolbarItem(): ToolbarItem.Action = ToolbarItem.Action(this)
}
