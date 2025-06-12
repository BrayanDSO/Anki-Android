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
package com.ichi2.anki

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

/**
 * [value] should be kept in sync with the [com.ichi2.anki.api.Ease] enum.
 *
 * @param value The so called value of the button. For the sake of consistency with upstream and our API
 * the buttons are numbered from 1 to 4.
 */
enum class Ease(
    val value: Int,
    @DrawableRes val feedbackIcon: Int,
    @ColorRes val feedbackColor: Int,
) {
    AGAIN(1, R.drawable.close_icon, R.color.again_button_text),
    HARD(2, R.drawable.ic_check_small, R.color.hard_button_text),
    GOOD(3, R.drawable.ic_done, R.color.good_button_text),
    EASY(4, R.drawable.ic_done_all, R.color.easy_button_text),
    ;

    companion object {
        fun fromValue(value: Int) = entries.first { value == it.value }
    }
}
