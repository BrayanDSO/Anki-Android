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
import com.ichi2.anki.common.utils.ext.getLongOrNull
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
import timber.log.Timber

object JsApi {
    private const val CURRENT_VERSION = "0.0.4"
    private const val SUCCESS_KEY = "success"
    private const val VALUE_KEY = "value"
    private const val ERROR_KEY = "error"

    fun parseRequest(byteArray: ByteArray): JsApiRequest {
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
        base: String,
        endpoint: String,
        bytes: ByteArray,
    ): ByteArray {
        val request = parseRequest(bytes)

        return when (base) {
            CardEndpoint.BASE -> {
                val cardEndpoint = CardEndpoint.from(endpoint) ?: return fail("Invalid Card endpoint")
                handleCardMethods(request.data, cardEndpoint)
            }
            NoteEndpoint.BASE -> {
                val noteEndpoint = NoteEndpoint.from(endpoint) ?: return fail("Invalid Note endpoint")
                handleNoteMethods(request.data, noteEndpoint)
            }
            DeckEndpoint.BASE -> {
                val deckEndpoint = DeckEndpoint.from(endpoint) ?: return fail("Invalid Deck endpoint")
                handleDeckMethods(request.data, deckEndpoint)
            }
            AndroidEndpoint.BASE -> {
                val androidEndpoint = AndroidEndpoint.from(endpoint) ?: return fail("Invalid Android endpoint")
                handleAndroidEndpoints(androidEndpoint)
            }
            else -> {
                Timber.w("Unhandled base: %s", base)
                fail("Unhandled base")
            }
        }
    }

    private suspend fun handleCardMethods(
        data: JSONObject?,
        endpoint: CardEndpoint,
    ): ByteArray {
        val cardId = data?.getLongOrNull("id")
        val card =
            if (cardId != null) {
                withCol { Card(this, cardId) }
            } else {
                getTopCard() ?: return fail("There is no card at top of the queue")
            }
        return when (endpoint) {
            CardEndpoint.GET_ID -> success(card.id)
            CardEndpoint.GET_NID -> success(card.nid)
            CardEndpoint.GET_FLAG -> success(card.flag.code)
            CardEndpoint.GET_REPS -> success(card.reps)
            CardEndpoint.GET_INTERVAL -> success(card.ivl)
            CardEndpoint.GET_FACTOR -> success(card.factor)
            CardEndpoint.GET_MOD -> success(card.mod)
            CardEndpoint.GET_TYPE -> success(card.type.code)
            CardEndpoint.GET_DID -> success(card.did)
            CardEndpoint.GET_LEFT -> success(card.left)
            CardEndpoint.GET_ODID -> success(card.oDid)
            CardEndpoint.GET_ODUE -> success(card.oDue)
            CardEndpoint.GET_QUEUE -> success(card.queue.code)
            CardEndpoint.GET_LAPSES -> success(card.lapses)
            CardEndpoint.GET_DUE -> success(card.due)
            CardEndpoint.BURY -> {
                val count = undoableOp { sched.buryCards(cids = listOf(card.id)) }.count
                success(count)
            }
            CardEndpoint.IS_MARKED -> {
                val isMarked = withCol { card.note(this).hasTag(this, MARKED_TAG) }
                success(isMarked)
            }
            CardEndpoint.SUSPEND -> {
                val count = undoableOp { sched.suspendCards(ids = listOf(card.id)) }.count
                success(count)
            }
            CardEndpoint.RESET_PROGRESS -> {
                undoableOp {
                    sched.forgetCards(listOf(card.id), restorePosition = false, resetCounts = false)
                }
                success()
            }
            CardEndpoint.TOGGLE_FLAG -> {
                val requestBody = data?.getJSONObject("data") ?: return fail("Missing data")
                val requestFlag = requestBody.getInt("flag")
                val currentFlag = card.flag
                val newFlag = if (requestFlag == currentFlag.code) Flag.NONE else Flag.fromCode(requestFlag)
                undoableOp { setUserFlagForCards(listOf(card.id), newFlag) }
                success()
            }
        }
    }

    private suspend fun handleNoteMethods(
        data: JSONObject?,
        endpoint: NoteEndpoint,
    ): ByteArray {
        val noteId = data?.getLongOrNull("id")
        val note =
            if (noteId != null) {
                withCol { Note(this, noteId) }
            } else {
                val topCard = getTopCard() ?: return fail("There is no card at top of the queue")
                withCol { topCard.note(this) }
            }
        return when (endpoint) {
            NoteEndpoint.GET_ID -> success(note.id)
            NoteEndpoint.BURY -> {
                val count = undoableOp { sched.buryNotes(listOf(note.id)) }.count
                success(count)
            }
            NoteEndpoint.SUSPEND -> {
                val count = undoableOp { sched.suspendNotes(listOf(note.id)) }.count
                success(count)
            }
            NoteEndpoint.GET_TAGS -> {
                val tags = withCol { note.stringTags(this) }
                success(tags)
            }
            NoteEndpoint.SET_TAGS -> {
                val tags = data?.getString("data") ?: return fail("Missing tags")
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
        data: JSONObject?,
        endpoint: DeckEndpoint,
    ): ByteArray {
        val deckId = data?.getLongOrNull("id") ?: getTopCard()?.did ?: return fail("There is no card at top of the queue")
        val deck = withCol { decks.get(deckId) } ?: return fail("Found no deck with the id '$deckId'")
        return when (endpoint) {
            DeckEndpoint.GET_ID -> success(deck.id)
            DeckEndpoint.GET_NAME -> success(deck.name)
            DeckEndpoint.IS_FILTERED -> success(deck.isFiltered)
        }
    }

    private fun handleAndroidEndpoints(endpoint: AndroidEndpoint): ByteArray =
        when (endpoint) {
            AndroidEndpoint.IS_SYSTEM_IN_DARK_MODE -> success(Themes.systemIsInNightMode(AnkiDroidApp.instance))
            AndroidEndpoint.IS_NETWORK_METERED -> success(NetworkUtils.isActiveNetworkMetered())
        }

    private suspend fun getTopCard() = withCol { sched }.currentQueueState()?.topCard

    // region Helpers
    fun success() = successResult(null)

    fun success(string: String) = successResult(string)

    fun success(boolean: Boolean) = successResult(boolean)

    fun success(number: Int) = successResult(number)

    fun success(number: Long) = successResult(number)

    private fun successResult(value: Any?): ByteArray =
        JSONObject()
            .apply {
                put(SUCCESS_KEY, true)
                put(VALUE_KEY, value)
            }.toString()
            .toByteArray()

    fun fail(error: String): ByteArray =
        JSONObject()
            .apply {
                put(SUCCESS_KEY, false)
                put(ERROR_KEY, error)
            }.toString()
            .toByteArray()
    // endregion
}
