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

import android.speech.tts.TextToSpeech
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.JavaScriptTTS
import com.ichi2.anki.common.utils.ext.getDoubleOrNull
import com.ichi2.anki.common.utils.ext.getIntOrNull
import com.ichi2.anki.common.utils.ext.getLongOrNull
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.ui.windows.reviewer.jsapi.Endpoint
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
    private val tts by lazy { JavaScriptTTS() }

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
        endpoint: Endpoint,
        bytes: ByteArray,
    ): ByteArray {
        val request = parseRequest(bytes)
        return when (endpoint) {
            is Endpoint.Android -> handleAndroidEndpoints(endpoint)
            is Endpoint.Card -> handleCardMethods(endpoint, request.data)
            is Endpoint.Deck -> handleDeckMethods(endpoint, request.data)
            is Endpoint.Note -> handleNoteMethods(endpoint, request.data)
            is Endpoint.Tts -> handleTtsEndpoints(endpoint, request.data)
            else -> fail("Unhandled endpoint")
        }
    }

    private suspend fun handleCardMethods(
        endpoint: Endpoint.Card,
        data: JSONObject?,
    ): ByteArray {
        val cardId = data?.getLongOrNull("id")
        val card =
            if (cardId != null) {
                withCol { Card(this, cardId) }
            } else {
                getTopCard() ?: return fail("There is no card at top of the queue")
            }
        return when (endpoint) {
            Endpoint.Card.GET_ID -> success(card.id)
            Endpoint.Card.GET_NID -> success(card.nid)
            Endpoint.Card.GET_FLAG -> success(card.flag.code)
            Endpoint.Card.GET_REPS -> success(card.reps)
            Endpoint.Card.GET_INTERVAL -> success(card.ivl)
            Endpoint.Card.GET_FACTOR -> success(card.factor)
            Endpoint.Card.GET_MOD -> success(card.mod)
            Endpoint.Card.GET_TYPE -> success(card.type.code)
            Endpoint.Card.GET_DID -> success(card.did)
            Endpoint.Card.GET_LEFT -> success(card.left)
            Endpoint.Card.GET_O_DID -> success(card.oDid)
            Endpoint.Card.GET_O_DUE -> success(card.oDue)
            Endpoint.Card.GET_QUEUE -> success(card.queue.code)
            Endpoint.Card.GET_LAPSES -> success(card.lapses)
            Endpoint.Card.GET_DUE -> success(card.due)
            Endpoint.Card.BURY -> {
                val count = undoableOp { sched.buryCards(cids = listOf(card.id)) }.count
                success(count)
            }
            Endpoint.Card.IS_MARKED -> {
                val isMarked = withCol { card.note(this).hasTag(this, MARKED_TAG) }
                success(isMarked)
            }
            Endpoint.Card.SUSPEND -> {
                val count = undoableOp { sched.suspendCards(ids = listOf(card.id)) }.count
                success(count)
            }
            Endpoint.Card.RESET_PROGRESS -> {
                undoableOp {
                    sched.forgetCards(listOf(card.id), restorePosition = false, resetCounts = false)
                }
                success()
            }
            Endpoint.Card.TOGGLE_FLAG -> {
                val requestBody = data?.getJSONObject("data") ?: return fail("Missing data")
                val requestFlag = requestBody.getInt("flag")
                if (requestFlag < 0 || requestFlag > 7) return fail("Invalid flag code")

                val newFlag = if (requestFlag == card.userFlag()) Flag.NONE else Flag.fromCode(requestFlag)
                undoableOp { setUserFlagForCards(listOf(card.id), newFlag) }
                success()
            }
        }
    }

    private suspend fun handleNoteMethods(
        endpoint: Endpoint.Note,
        data: JSONObject?,
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
            Endpoint.Note.GET_ID -> success(note.id)
            Endpoint.Note.BURY -> {
                val count = undoableOp { sched.buryNotes(listOf(note.id)) }.count
                success(count)
            }
            Endpoint.Note.SUSPEND -> {
                val count = undoableOp { sched.suspendNotes(listOf(note.id)) }.count
                success(count)
            }
            Endpoint.Note.GET_TAGS -> {
                val tags = withCol { note.stringTags(this) }
                success(tags)
            }
            Endpoint.Note.SET_TAGS -> {
                val noteRequestData = data?.optJSONObject("data") ?: return fail("Missing request data")
                val tags = noteRequestData.optString("tags") ?: return fail("Missing tags")
                undoableOp {
                    note.setTagsFromStr(this, tags)
                    updateNote(note)
                }
                success()
            }
            Endpoint.Note.TOGGLE_MARK -> {
                NoteService.toggleMark(note)
                success()
            }
        }
    }

    private suspend fun handleDeckMethods(
        endpoint: Endpoint.Deck,
        data: JSONObject?,
    ): ByteArray {
        val deckId = data?.getLongOrNull("id") ?: getTopCard()?.did ?: return fail("There is no card at top of the queue")
        val deck = withCol { decks.get(deckId) } ?: return fail("Found no deck with the id '$deckId'")
        return when (endpoint) {
            Endpoint.Deck.GET_ID -> success(deck.id)
            Endpoint.Deck.GET_NAME -> success(deck.name)
            Endpoint.Deck.IS_FILTERED -> success(deck.isFiltered)
        }
    }

    private fun handleAndroidEndpoints(endpoint: Endpoint.Android): ByteArray =
        when (endpoint) {
            Endpoint.Android.IS_SYSTEM_IN_DARK_MODE -> success(Themes.systemIsInNightMode(AnkiDroidApp.instance))
            Endpoint.Android.IS_NETWORK_METERED -> success(NetworkUtils.isActiveNetworkMetered())
        }

    private fun handleTtsEndpoints(
        endpoint: Endpoint.Tts,
        data: JSONObject?,
    ): ByteArray {
        return when (endpoint) {
            Endpoint.Tts.SPEAK -> {
                val text = data?.optString("text") ?: return fail("Missing text")
                val queueMode = data.getIntOrNull("queueMode") ?: return fail("Missing queueMode")
                if (queueMode != TextToSpeech.QUEUE_FLUSH && queueMode != TextToSpeech.QUEUE_ADD) return fail("Invalid queueMode")
                val result = tts.speak(text, queueMode)
                success(result)
            }
            Endpoint.Tts.SET_LANGUAGE -> {
                val locale = data?.optString("locale") ?: return fail("Missing locale")
                success(tts.setLanguage(locale))
            }
            Endpoint.Tts.SET_PITCH -> {
                val pitch = data?.getDoubleOrNull("pitch") ?: return fail("Missing pitch")
                success(tts.setPitch(pitch.toFloat()))
            }
            Endpoint.Tts.SET_SPEECH_RATE -> {
                val speechRate = data?.getDoubleOrNull("speechRate") ?: return fail("Missing speechRate")
                success(tts.setSpeechRate(speechRate.toFloat()))
            }
            Endpoint.Tts.IS_SPEAKING -> {
                success(tts.isSpeaking)
            }
            Endpoint.Tts.STOP -> {
                success(tts.stop())
            }
        }
    }

    private suspend fun getTopCard() = withCol { sched }.currentQueueState()?.topCard

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

    fun fail(error: String): ByteArray {
        Timber.i("JsApi fail: %s", error)
        return JSONObject()
            .apply {
                put(SUCCESS_KEY, false)
                put(ERROR_KEY, error)
            }.toString()
            .toByteArray()
    }
}
