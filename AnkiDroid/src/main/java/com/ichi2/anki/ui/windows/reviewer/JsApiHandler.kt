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
import com.ichi2.libanki.CardId
import com.ichi2.libanki.DeckId
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
    private fun parseContract(byteArray: ByteArray): Contract? {
        try {
            val data = JSONObject(byteArray.decodeToString())
            val version = data.optString("version", "")
            if (version != CURRENT_VERSION) return null
            val developer = data.optString("developer", "")
            return Contract(version, developer)
        } catch (j: JSONException) {
            Timber.w(j)
        }
        return null
    }

    suspend fun handleRequest(
        path: String,
        bytes: ByteArray,
    ): ByteArray {
        val (firstSegment, subSegment) = path.split('/', limit = 2)
        return when (firstSegment) {
            "card" -> byteArrayOf()
            "deck" -> handleDeckMethods(1, subSegment)
            else -> byteArrayOf()
        }
    }

    private suspend fun handleCardMethods(
        cardId: CardId,
        subSegment: String,
    ) {
    }

    private suspend fun handleDeckMethods(
        deckId: DeckId,
        subSegment: String,
    ): ByteArray {
        val deck = CollectionManager.withCol { decks.get(deckId) }
        return when (subSegment) {
            "getId" -> bytes(deck!!.id)
            "getName" -> bytes(deck!!.name)
            else -> byteArrayOf()
        }
    }

    private fun bytes(value: Long): ByteArray = ApiResult.Long(true, value).toString().toByteArray()

    private fun bytes(value: String): ByteArray = ApiResult.String(true, value).toString().toByteArray()
}

data class Contract(
    val version: String,
    val developer: String,
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
