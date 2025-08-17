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

enum class CardEndpoint(
    val value: String,
) {
    IS_MARKED("isMarked"),
    GET_FLAG("getFlag"),
    GET_REPS("getReps"),
    GET_INTERVAL("getInterval"),
    GET_FACTOR("getFactor"),
    GET_MOD("getMod"),
    GET_NID("getNid"),
    GET_TYPE("getType"),
    GET_DID("getDid"),
    GET_LEFT("getLeft"),
    GET_ODID("getOdid"),
    GET_ODUE("getOdue"),
    GET_QUEUE("getQueue"),
    GET_LAPSES("getLapses"),
    GET_DUE("getDue"),
    BURY("bury"),
    SUSPEND("suspend"),
    RESET_PROGRESS("resetProgress"),
    TOGGLE_FLAG("toggleFlag"),
    ;

    companion object {
        const val BASE = "card"

        fun from(value: String): CardEndpoint? = entries.firstOrNull { it.value == value }
    }
}
