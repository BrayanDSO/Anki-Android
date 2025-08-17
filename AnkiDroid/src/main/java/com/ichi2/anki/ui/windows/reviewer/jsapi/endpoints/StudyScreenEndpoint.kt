/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints

enum class StudyScreenEndpoint(
    val value: String,
) {
    GET_CURRENT_CARD_ID("getCurrentCardId"),
    SHOW_SNACKBAR("showSnackbar"),
    GET_NEW_COUNT("getNewCount"),
    GET_LRN_COUNT("getLrnCount"),
    GET_REV_COUNT("getRevCount"),
    SHOW_ANSWER("showAnswer"),
    ANSWER("answer"),
    IS_SHOWING_ANSWER("isShowingAnswer"),
    GET_NEXT_TIME("rating"),
    CARD_INFO("cardInfo"),
    EDIT_NOTE("editNote"),
    SEARCH("search"),
    ;

    companion object {
        const val BASE = "studyScreen"

        fun from(value: String): StudyScreenEndpoint? = entries.firstOrNull { it.value == value }
    }
}
