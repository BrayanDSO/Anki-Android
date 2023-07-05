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
package com.ichi2.preferences

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.preferences.PrefUtils.prefs
import kotlin.reflect.KProperty

interface EnumSetting {
    val value: String
}

interface GenericDelegate<T> {
    fun get(): T
    fun set(value: T)
    operator fun getValue(prefs: Prefs, property: KProperty<*>): T {
        return get()
    }
    operator fun setValue(prefs: Prefs, property: KProperty<*>, value: T) {
        set(value)
    }
}

inline fun <reified E> enumSetting(keyResId: Int): GenericDelegate<E> where E : Enum<E>, E : EnumSetting {
    return object : GenericDelegate<E> {
        override fun get(): E = enumValues<E>().first { it.value == Prefs.getString(keyResId)!! }
        override fun set(value: E) {
            Prefs.putString(keyResId, value.value)
        }
    }
}

object Prefs {
    // fazer constantes dos default values
    // TODO como testar se fica pareado isso daqui com os defaultValues?
    val automaticSync by BooleanSetting(R.string.automatic_sync_choice_key, false)
    val meteredSync by enumSetting<FetchMediaOptions>(R.string.sync_fetch_media_key)
    val imageZoom by IntSetting(R.string.image_zoom_preference, 100)
    val cardZoom by IntSetting(R.string.card_zoom_preference, 100)

    class BooleanSetting(@StringRes val keyResId: Int, val defaultValue: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            getBoolean(keyResId, defaultValue)

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            putBoolean(keyResId, value)
        }
    }

    class IntSetting(@StringRes val keyResId: Int, val defaultValue: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int =
            getInt(keyResId, defaultValue)

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            putInt(keyResId, value)
        }
    }

    // TODO teste para garantir que os valores são iguais às prefs
    // TODO teste para garantir que a ordem tá igual a array original. Ou não, tipo
    //    meu objetivo aqui é poder fazer retrieval dos valores no analytics. E lá eu só tenho o value disponível que é um int
    //    o problema é que se a pref mudar
    enum class FetchMediaOptions(override val value: String) : EnumSetting {
        ALWAYS("always"),
        ONLY_UNMETERED("only_unmetered"),
        NEVER("never");
    }

    fun getString(@StringRes resId: Int, defaultValue: String? = null) =
        prefs.getString(PrefUtils.getKey(resId), defaultValue)

    fun getBoolean(@StringRes resId: Int, defaultValue: Boolean) =
        prefs.getBoolean(PrefUtils.getKey(resId), defaultValue)

    fun getInt(@StringRes resId: Int, defaultValue: Int) =
        prefs.getInt(PrefUtils.getKey(resId), defaultValue)

    fun getFloat(@StringRes resId: Int, defaultValue: Float) =
        prefs.getFloat(PrefUtils.getKey(resId), defaultValue)

    fun getLong(@StringRes resId: Int, defaultValue: Long) =
        prefs.getLong(PrefUtils.getKey(resId), defaultValue)

    fun putBoolean(@StringRes keyResId: Int, value: Boolean) {
        prefs.edit { putBoolean(PrefUtils.getKey(keyResId), value) }
    }

    fun putString(@StringRes keyResId: Int, value: String) {
        prefs.edit { putString(PrefUtils.getKey(keyResId), value) }
    }

    fun putInt(@StringRes keyResId: Int, value: Int) {
        prefs.edit { putInt(PrefUtils.getKey(keyResId), value) }
    }
}

object PrefUtils {
    private val appContext
        get() = AnkiDroidApp.instance

    val prefs: SharedPreferences
        get() = with(appContext) {
            PreferenceManager.getDefaultSharedPreferences(this)
        }

    fun getKey(@StringRes keyResId: Int) = appContext.getString(keyResId)

    fun SharedPreferences.getString(@StringRes keyResId: Int, defaultValue: String? = null) =
        getString(getKey(keyResId), defaultValue)

    fun SharedPreferences.getBoolean(@StringRes keyResId: Int, defaultValue: Boolean) =
        getBoolean(getKey(keyResId), defaultValue)

    fun SharedPreferences.getInt(@StringRes keyResId: Int, defaultValue: Int) =
        getInt(getKey(keyResId), defaultValue)

    fun SharedPreferences.getFloat(@StringRes keyResId: Int, defaultValue: Float) =
        getFloat(getKey(keyResId), defaultValue)

    fun SharedPreferences.getLong(@StringRes keyResId: Int, defaultValue: Long) =
        getLong(getKey(keyResId), defaultValue)

    fun SharedPreferences.getStringSet(@StringRes keyResId: Int, defaultValues: Set<String>? = null) =
        getStringSet(getKey(keyResId), defaultValues)

    fun SharedPreferences.contains(@StringRes keyResId: Int) =
        contains(getKey(keyResId))

    fun SharedPreferences.putBoolean(@StringRes keyResId: Int, value: Boolean) {
        edit { putBoolean(getKey(keyResId), value) }
    }
}
