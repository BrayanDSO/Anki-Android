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

enum class ToolbarAction(
    @IdRes val id: Int,
    @StringRes val title: Int,
    @DrawableRes val drawable: Int
) {
    ADD_NOTE(R.id.reviewer_add_note, R.string.menu_add_note, R.drawable.ic_add_note),
    UNDO(R.id.reviewer_undo, R.string.undo, R.drawable.ic_undo_white),
    REDO(R.id.reviewer_redo, R.string.redo, R.drawable.ic_redo);
}
