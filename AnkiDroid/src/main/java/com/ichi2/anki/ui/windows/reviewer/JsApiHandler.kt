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
package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.JvmBoolean
import com.ichi2.anki.JvmFloat
import com.ichi2.anki.JvmInt
import com.ichi2.anki.JvmLong
import com.ichi2.anki.JvmString
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import org.json.JSONObject

const val CURRENT_VERSION = "0.0.4"
const val VALUE_KEY = "value"
const val SUCCESS_KEY = "success"

class JsApiHandler {
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

        val developer = requestBody.getStringOrNull("developer")
        if (developer == null) {
            throw InvalidContractException.ContactError(developer)
        }

        return JsApiContract(version, developer)
    }

    suspend fun handleRequest(
        path: String,
        bytes: ByteArray,
    ): ByteArray? {
        val request = parseRequest(bytes)
        val data = request.data!!
        val (mainSegment, endpoint) = path.split('/', limit = 2)

        return when (mainSegment) {
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
            CardEndpoint.GET_NID -> toBytes(card.nid)
            CardEndpoint.GET_FLAG -> toBytes(card.flag.code)
            CardEndpoint.GET_REPS -> toBytes(card.reps)
            CardEndpoint.GET_INTERVAL -> toBytes(card.ivl)
            CardEndpoint.GET_FACTOR -> toBytes(card.factor)
            CardEndpoint.GET_MOD -> toBytes(card.mod)
            CardEndpoint.GET_TYPE -> toBytes(card.type.code)
            CardEndpoint.GET_DID -> toBytes(card.did)
            CardEndpoint.GET_LEFT -> toBytes(card.left)
            CardEndpoint.GET_ODID -> toBytes(card.oDid)
            CardEndpoint.GET_ODUE -> toBytes(card.oDue)
            CardEndpoint.GET_QUEUE -> toBytes(card.queue.code)
            CardEndpoint.GET_LAPSES -> toBytes(card.lapses)
            CardEndpoint.GET_DUE -> toBytes(card.due)
            CardEndpoint.BURY -> {
                val count =
                    undoableOp {
                        sched.buryCards(cids = listOf(cardId))
                    }.count
                toBytes(count)
            }
            CardEndpoint.IS_MARKED -> {
                val isMarked = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
                toBytes(isMarked)
            }
            CardEndpoint.SUSPEND -> {
                val count =
                    undoableOp {
                        sched.suspendCards(ids = listOf(cardId))
                    }.count
                toBytes(count)
            }
            CardEndpoint.RESET_PROGRESS -> {
                undoableOp {
                    sched.forgetCards(listOf(card.id), restorePosition = false, resetCounts = false)
                }
                toBytes(true)
            }
            CardEndpoint.TOGGLE_FLAG -> {
                val requestFlag = data.getInt("flag")
                val currentFlag = card.flag
                val newFlag =
                    if (requestFlag == currentFlag.code) {
                        Flag.NONE
                    } else {
                        Flag.fromCode(requestFlag)
                    }
                undoableOp {
                    setUserFlagForCards(listOf(card.id), newFlag)
                }
                toBytes(true)
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
                val count =
                    undoableOp {
                        sched.buryNotes(listOf(note.id))
                    }.count
                toBytes(count)
            }
            NoteEndpoint.SUSPEND -> {
                val count =
                    undoableOp {
                        sched.suspendNotes(listOf(note.id))
                    }.count
                toBytes(count)
            }
            NoteEndpoint.GET_TAGS -> {
                val tags = withCol { note.stringTags(this) }
                toBytes(tags)
            }
            NoteEndpoint.SET_TAGS -> {
                val tags = data.getString("data")
                undoableOp {
                    note.setTagsFromStr(this, tags)
                    updateNote(note)
                }
                toBytes(true)
            }
            NoteEndpoint.TOGGLE_MARK -> {
                NoteService.toggleMark(note)
                toBytes(true)
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
            DeckEndpoint.GET_NAME -> toBytes(deck.name)
        }
    }

    private fun toBytes(value: Long): ByteArray = ApiResult.Long(true, value).toString().toByteArray()

    private fun toBytes(value: Int): ByteArray = ApiResult.Integer(true, value).toString().toByteArray()

    private fun toBytes(value: String): ByteArray = ApiResult.String(true, value).toString().toByteArray()

    private fun toBytes(value: Boolean): ByteArray = ApiResult.Boolean(true, value).toString().toByteArray()
}

data class JsApiContract(
    val version: String,
    val developer: String,
)

data class JsApiRequest(
    val contract: JsApiContract,
    val data: JSONObject?,
)

sealed class ApiResult protected constructor(
    private val success: JvmBoolean,
) {
    class Boolean(
        success: JvmBoolean,
        val value: JvmBoolean,
    ) : ApiResult(success) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Integer(
        success: JvmBoolean,
        val value: JvmInt,
    ) : ApiResult(success) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Float(
        success: JvmBoolean,
        val value: JvmFloat,
    ) : ApiResult(success) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Long(
        success: JvmBoolean,
        val value: JvmLong,
    ) : ApiResult(success) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class String(
        success: JvmBoolean,
        val value: JvmString,
    ) : ApiResult(success) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    abstract fun putValue(o: JSONObject)

    override fun toString() =
        JSONObject()
            .apply {
                put(SUCCESS_KEY, success)
                putValue(this)
            }.toString()
}

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

enum class NoteEndpoint(
    val value: String,
) {
    BURY("bury"),
    SUSPEND("suspend"),
    GET_TAGS("getTags"),
    SET_TAGS("setTags"),
    TOGGLE_MARK("toggleMark"),
    ;

    companion object {
        const val BASE = "note"

        fun from(value: String): NoteEndpoint? = entries.firstOrNull { it.value == value }
    }
}

enum class DeckEndpoint(
    val value: String,
) {
    GET_NAME("getName"),
    ;

    companion object {
        const val BASE = "deck"

        fun from(value: String): DeckEndpoint? = entries.firstOrNull { it.value == value }
    }
}

sealed class InvalidContractException : IllegalArgumentException() {
    class VersionError(
        val version: String?,
    ) : InvalidContractException()

    class ContactError(
        val developer: String?,
    ) : InvalidContractException()
}
