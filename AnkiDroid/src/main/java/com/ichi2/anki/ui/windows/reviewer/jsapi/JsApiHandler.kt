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
package com.ichi2.anki.ui.windows.reviewer.jsapi

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints.AndroidEndpoint
import com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints.CardEndpoint
import com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints.DeckEndpoint
import com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints.NoteEndpoint
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import com.ichi2.themes.Themes
import com.ichi2.utils.NetworkUtils
import org.json.JSONObject

object JsApiHandler {
    private const val CURRENT_VERSION = "0.0.4"
    private const val VALUE_KEY = "value"
    private const val SUCCESS_KEY = "success"

    private fun parseRequest(byteArray: ByteArray): JsApiRequest {
        val requestBody = JSONObject(byteArray.decodeToString())
        val contract = parseContract(requestBody)
        val data = requestBody.optJSONObject("data")
        return JsApiRequest(contract, data)
    }

    private fun parseContract(requestBody: JSONObject): JsApiContract {
        val version = requestBody.getStringOrNull("version")
        if (version != CURRENT_VERSION) {
            throw InvalidContractException.VersionError(version)
        }

        val developer =
            requestBody.getStringOrNull("developer")
                ?: throw InvalidContractException.ContactError(null)

        return JsApiContract(version, developer)
    }

    suspend fun handleRequest(
        path: String,
        bytes: ByteArray,
    ): ByteArray? {
        val request = parseRequest(bytes)
        val data = request.data ?: return null
        val (base, endpoint) = path.split('/', limit = 2)

        return when (base) {
            CardEndpoint.BASE -> {
                val cardEndpoint = CardEndpoint.from(endpoint) ?: return null
                handleCardMethods(data, cardEndpoint)
            }
            NoteEndpoint.BASE -> {
                val noteEndpoint = NoteEndpoint.from(endpoint) ?: return null
                handleNoteMethods(data, noteEndpoint)
            }
            DeckEndpoint.BASE -> {
                val deckEndpoint = DeckEndpoint.from(endpoint) ?: return null
                handleDeckMethods(data, deckEndpoint)
            }
            AndroidEndpoint.BASE -> {
                val androidEndpoint = AndroidEndpoint.from(endpoint) ?: return null
                handleAndroidEndpoints(androidEndpoint)
            }
            else -> null
        }
    }

    private suspend fun handleCardMethods(
        data: JSONObject,
        endpoint: CardEndpoint,
    ): ByteArray? {
        val cardId = data.getLong("id")
        val card = withCol { Card(this, cardId) }
        return when (endpoint) {
            CardEndpoint.GET_NID -> result(card.nid)
            CardEndpoint.GET_FLAG -> result(card.flag.code)
            CardEndpoint.GET_REPS -> result(card.reps)
            CardEndpoint.GET_INTERVAL -> result(card.ivl)
            CardEndpoint.GET_FACTOR -> result(card.factor)
            CardEndpoint.GET_MOD -> result(card.mod)
            CardEndpoint.GET_TYPE -> result(card.type.code)
            CardEndpoint.GET_DID -> result(card.did)
            CardEndpoint.GET_LEFT -> result(card.left)
            CardEndpoint.GET_ODID -> result(card.oDid)
            CardEndpoint.GET_ODUE -> result(card.oDue)
            CardEndpoint.GET_QUEUE -> result(card.queue.code)
            CardEndpoint.GET_LAPSES -> result(card.lapses)
            CardEndpoint.GET_DUE -> result(card.due)
            CardEndpoint.BURY -> {
                val count = undoableOp { sched.buryCards(cids = listOf(cardId)) }.count
                result(count)
            }
            CardEndpoint.IS_MARKED -> {
                val isMarked = withCol { card.note(this).hasTag(this, MARKED_TAG) }
                result(isMarked)
            }
            CardEndpoint.SUSPEND -> {
                val count = undoableOp { sched.suspendCards(ids = listOf(cardId)) }.count
                result(count)
            }
            CardEndpoint.RESET_PROGRESS -> {
                undoableOp {
                    sched.forgetCards(listOf(card.id), restorePosition = false, resetCounts = false)
                }
                success()
            }
            CardEndpoint.TOGGLE_FLAG -> {
                val requestFlag = data.getInt("flag")
                val currentFlag = card.flag
                val newFlag = if (requestFlag == currentFlag.code) Flag.NONE else Flag.fromCode(requestFlag)
                undoableOp { setUserFlagForCards(listOf(card.id), newFlag) }
                success()
            }
        }
    }

    private suspend fun handleNoteMethods(
        data: JSONObject,
        endpoint: NoteEndpoint,
    ): ByteArray? {
        val noteId = data.getLong("id")
        val note = withCol { Note(this, noteId) }
        return when (endpoint) {
            NoteEndpoint.BURY -> {
                val count = undoableOp { sched.buryNotes(listOf(note.id)) }.count
                result(count)
            }
            NoteEndpoint.SUSPEND -> {
                val count = undoableOp { sched.suspendNotes(listOf(note.id)) }.count
                result(count)
            }
            NoteEndpoint.GET_TAGS -> {
                val tags = withCol { note.stringTags(this) }
                result(tags)
            }
            NoteEndpoint.SET_TAGS -> {
                val tags = data.getString("data")
                undoableOp {
                    note.setTagsFromStr(this, tags)
                    updateNote(note)
                }
                success()
            }
            NoteEndpoint.TOGGLE_MARK -> {
                NoteService.toggleMark(note)
                success()
            }
        }
    }

    private suspend fun handleDeckMethods(
        data: JSONObject,
        endpoint: DeckEndpoint,
    ): ByteArray? {
        val deckId = data.getLong("id")
        val deck = withCol { decks.get(deckId) } ?: return null
        return when (endpoint) {
            DeckEndpoint.GET_NAME -> result(deck.name)
        }
    }

    private fun handleAndroidEndpoints(endpoint: AndroidEndpoint): ByteArray? =
        when (endpoint) {
            AndroidEndpoint.IS_SYSTEM_IN_DARK_MODE -> result(Themes.systemIsInNightMode(AnkiDroidApp.instance))
            AndroidEndpoint.IS_NETWORK_METERED -> result(NetworkUtils.isActiveNetworkMetered())
        }

    // region Helpers
    private fun result(
        value: Boolean,
        success: Boolean = true,
    ): ByteArray = buildApiResponse(success, value)

    private fun result(
        value: Int,
        success: Boolean = true,
    ): ByteArray = buildApiResponse(success, value)

    fun result(
        value: Long,
        success: Boolean = true,
    ): ByteArray = buildApiResponse(success, value)

    private fun result(
        value: String,
        success: Boolean = true,
    ): ByteArray = buildApiResponse(success, value)

    private fun success(): ByteArray = buildApiResponse(true, null)

    private fun buildApiResponse(
        success: Boolean,
        value: Any?,
    ): ByteArray =
        JSONObject()
            .apply {
                put(SUCCESS_KEY, success)
                value?.let { put(VALUE_KEY, it) }
            }.toString()
            .toByteArray()
    // endregion
}
