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

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.JvmBoolean
import com.ichi2.anki.JvmFloat
import com.ichi2.anki.JvmInt
import com.ichi2.anki.JvmLong
import com.ichi2.anki.JvmString
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.utils.ext.flag
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

const val CURRENT_VERSION = "0.0.4"
const val VALUE_KEY = "value"
const val SUCCESS_KEY = "success"

class JsApiHandler {
    /**
     * The method parse json data and return api contract object
     * @param byteArray
     * @return apiContract or null
     */
    private fun parseRequest(byteArray: ByteArray): JsApiRequest? {
        try {
            val requestBody = JSONObject(byteArray.decodeToString())

            val version = requestBody.optString("version", "")
            if (version != CURRENT_VERSION) return null

            val developer = requestBody.optString("developer", "")
            val contract = JsApiContract(version, developer)
            val data = requestBody.optJSONObject("data")

            return JsApiRequest(contract, data)
        } catch (j: JSONException) {
            Timber.w(j)
        }
        return null
    }

    suspend fun handleRequest(
        path: String,
        bytes: ByteArray,
    ): ByteArray? {
        val request = parseRequest(bytes)
        if (request == null) {
            Timber.w("INVALID CONTRACT< BRO")
            return null
        }

        val (mainSegment, endpoint) = path.split('/', limit = 2)
        return when (mainSegment) {
            "card" -> {
                val cardId = request.data!!.getLong("id")
                val endpoint = CardEndpoint.from(endpoint) ?: return null
                handleCardMethods(cardId, endpoint)
            }
            "deck" -> {
                val deckId = request.data!!.getLong("id")
                handleDeckMethods(deckId, endpoint)
            }
            else -> null
        }
    }

    private suspend fun handleCardMethods(
        cardId: CardId,
        endpoint: CardEndpoint,
    ): ByteArray? {
        val card = CollectionManager.withCol { Card(this, cardId) }
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
            CardEndpoint.BURY,
            CardEndpoint.IS_MARKED,
            CardEndpoint.SUSPEND,
            CardEndpoint.RESET_PROGRESS,
            CardEndpoint.TOGGLE_FLAG,
            -> null
        }
    }

    private suspend fun handleNoteMethods(
        noteId: NoteId,
        endpoint: String,
    ): ByteArray? =
        when (endpoint) {
            "bury",
            "suspend",
            "getTags",
            "setTags",
            "toggleMark",
            -> null
            else -> null
        }

    private suspend fun handleDeckMethods(
        deckId: DeckId,
        endpoint: String,
    ): ByteArray? {
        val deck = CollectionManager.withCol { decks.get(deckId) } ?: return null
        return when (endpoint) {
            "getName" -> toBytes(deck.name)
            else -> null
        }
    }

    private fun toBytes(value: Long): ByteArray = ApiResult.Long(true, value).toString().toByteArray()

    private fun toBytes(value: Int): ByteArray = ApiResult.Integer(true, value).toString().toByteArray()

    private fun toBytes(value: String): ByteArray = ApiResult.String(true, value).toString().toByteArray()
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
    private val status: JvmBoolean,
) {
    class Boolean(
        status: JvmBoolean,
        val value: JvmBoolean,
    ) : ApiResult(status) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Integer(
        status: JvmBoolean,
        val value: JvmInt,
    ) : ApiResult(status) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Float(
        status: JvmBoolean,
        val value: JvmFloat,
    ) : ApiResult(status) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class Long(
        status: JvmBoolean,
        val value: JvmLong,
    ) : ApiResult(status) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    class String(
        status: JvmBoolean,
        val value: JvmString,
    ) : ApiResult(status) {
        override fun putValue(o: JSONObject) {
            o.put(VALUE_KEY, value)
        }
    }

    abstract fun putValue(o: JSONObject)

    override fun toString() =
        JSONObject()
            .apply {
                put(SUCCESS_KEY, status)
                putValue(this)
            }.toString()

    @Suppress("RemoveRedundantQualifierName") // we don't want `String(true, value)`
    companion object {
        fun success(value: JvmString) = ApiResult.String(true, value)

        fun failure(value: JvmString) = ApiResult.String(false, value)
    }
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
        fun from(value: String): CardEndpoint? = entries.firstOrNull { it.value == value }
    }
}
