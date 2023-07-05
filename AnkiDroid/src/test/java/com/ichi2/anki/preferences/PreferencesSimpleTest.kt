/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.preferences.HeaderPreference
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.full.declaredMembers

object Carolina {
    @Suppress("UNUSED_PARAMETER")
    var a
        get() = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).getBoolean("useRustBackend", false)
        set(vaiNoChao) {
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit { putBoolean("useRustBackend", vaiNoChao) }
        }
    var b
        get() = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).getBoolean("useRustBackend", false)
        set(vaiNoChao) {
            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance).edit { putBoolean("useRustBackend", vaiNoChao) }
        }
}
// minnha ideia aqui é mapear

fun testeSupremo() {
//    val listao = mapOf(
//
//    )
    @Suppress("UNUSED_VARIABLE")
    val fields = Carolina::class.declaredMembers
    // usa um sharedPreferences listener pra garantir que só aquela preference vai ser alterada
    // e que os valores vão ser validos

    // então eu meio que tenho que pegar todas as preferences, separar de acordo com o tipo e verificar
    // se tá gettando e setando certo
    4 + 4
}

/**
 * Test for [Preferences] without [RobolectricTest]. For performance
 */
class PreferencesSimpleTest {
    @ParameterizedTest
    @MethodSource("buildCategorySummary_LTR_Test_args")
    fun buildCategorySummary_LTR_Test(entries: Array<String>, expected_summary: String) {
        testeSupremo()
        assertThat(HeaderPreference.buildHeaderSummary(*entries), equalTo(expected_summary))
    }

    companion object {
        @JvmStatic // required for @MethodSource
        fun buildCategorySummary_LTR_Test_args(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(arrayOf(""), ""),
                Arguments.of(arrayOf("foo"), "foo"),
                Arguments.of(arrayOf("foo", "bar"), "foo • bar"),
                Arguments.of(arrayOf("foo", "bar", "hi", "there"), "foo • bar • hi • there")
            )
        }
    }
}
