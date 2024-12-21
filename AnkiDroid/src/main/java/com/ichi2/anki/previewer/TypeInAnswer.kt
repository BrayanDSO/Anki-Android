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
package com.ichi2.anki.previewer

import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.Card
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject

// Adapted from https://github.com/ankitects/anki/blob/8af63f81eb235b8d21df4e8eeaa6e02f46b3fbf6/qt/aqt/reviewer.py
//  and https://github.com/ankitects/anki/blob/df70564079f53e587dc44f015c503fdf6a70924f/qt/aqt/clayout.py#L579
class TypeInAnswer private constructor(
    private val text: String,
    private val combining: Boolean,
    val field: JSONObject?,
    val expectedAnswer: String?,
) {
    fun exists() = field != null

    suspend fun answerFilter(typedAnswer: String = ""): String {
        if (field == null || expectedAnswer == null) return removeTypeAnswerTags(text)

        val typeFont = field.getString("font")
        val typeSize = field.getString("size")
        val answerComparison = withCol { compareAnswer(expectedAnswer, provided = typedAnswer, combining = combining) }

        @Language("HTML")
        val repl =
            """<div style="font-family: '$typeFont'; font-size: ${typeSize}px">$answerComparison</div>"""
        val output = typeAnsRe.replaceFirst(text, repl)

        val warning = "<center><b>${TR.cardTemplatesTypeBoxesWarning()}</b></center>"
        return typeAnsRe.replace(output, warning)
    }

    companion object {
        /** removes `[[type:]]` tags */
        fun removeTypeAnswerTags(text: String): String = typeAnsRe.replace(text, "")

        private fun getTypeAnswerTag(text: String): String? {
            val match = typeAnsRe.find(text) ?: return null
            return match.groups[1]?.value
        }

        private suspend fun getExpectedTypeInAnswer(
            card: Card,
            field: JSONObject,
        ): String? {
            val fieldName = field.getString("name")
            val expected = withCol { card.note(this@withCol).getItem(fieldName) }
            return if (fieldName.startsWith("cloze:")) {
                val clozeIdx = card.ord + 1
                withCol {
                    extractClozeForTyping(expected, clozeIdx).takeIf { it.isNotBlank() }
                }
            } else {
                expected
            }
        }

        suspend fun getInstance(
            card: Card,
            text: String,
        ): TypeInAnswer {
            val tag =
                getTypeAnswerTag(text)
                    ?: return TypeInAnswer(
                        text = text,
                        combining = true,
                        field = null,
                        expectedAnswer = null,
                    )

            val combining = !tag.startsWith("nc:")
            val typeAnsFieldName =
                tag.let { field ->
                    val prefix = field.substringBefore(":")
                    when (prefix) {
                        "cloze",
                        "nc",
                        -> field.substringAfter(":")
                        else -> field
                    }
                }

            val fields = withCol { card.noteType(this).flds }
            var typeAnswerField: JSONObject? = null
            for (i in 0 until fields.length()) {
                val field = fields.get(i) as JSONObject
                if (field.getString("name") == typeAnsFieldName) {
                    typeAnswerField = field
                    break
                }
            }

            val expectedAnswer = typeAnswerField?.let { getExpectedTypeInAnswer(card, it) }

            return TypeInAnswer(
                text = text,
                combining = combining,
                field = typeAnswerField,
                expectedAnswer = expectedAnswer,
            )
        }
    }
}

@VisibleForTesting
val typeAnsRe = Regex("\\[\\[type:(.+?)]]")
