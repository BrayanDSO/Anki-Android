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

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import com.ichi2.anki.AnkiDroidApp

class NumberPreference : EditTextPreference {
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) { initializeSettings(attrs) }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initializeSettings(attrs) }
    constructor(context: Context) : super(context) { initializeSettings(null) }

    var max: Int = Int.MAX_VALUE
    var min: Int = 0

    /**
     * Return the integer rounded to the nearest bound if it is outside of the acceptable range.
     *
     * @param input Integer to validate.
     * @return The input value within acceptable range.
     */
    private fun getValidatedRange(input: Int): Int {
        return if (input < min) {
            min
        } else if (input > max) {
            max
        } else {
            input
        }
    }

    var value: String
        get() = getPersistedString(min.toString())
        set(value) {
            val validatedValue = getValidatedRange(value.toInt()).toString()
            text = validatedValue
            persistString(validatedValue)
        }

    private fun initializeSettings(attrs: AttributeSet?) {
        if (attrs != null) {
            max = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "max", Int.MAX_VALUE)
            min = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0)
        }
        setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
            val maxLength = max.toString().length
            it.filters = arrayOf(*it.filters, InputFilter.LengthFilter(maxLength))
        }
    }
}
