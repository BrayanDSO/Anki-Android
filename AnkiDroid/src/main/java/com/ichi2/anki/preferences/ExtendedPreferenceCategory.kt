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
package com.ichi2.anki.preferences

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.text.parseAsHtml
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R
import com.ichi2.preferences.usingStyledAttributes

/**
 * Extended version of [androidx.preference.PreferenceCategory] with the extra attributes:
 *
 * * app:isSingleLineSummary (boolean): whether the summary should be shown in one line.
 *       (default: false)
 * * app:summaryReplacements (string or array): the summary placeholder replacements. It can be
 *       either a string resource for a single replacement, or an array if more are necessary
 * * app:parseSummaryAsHtml (boolean): whether to parse the summary as HTML. (default: false)
 */
class ExtendedPreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceCategoryStyle,
    defStyleRes: Int = androidx.preference.R.style.Preference_Category
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    private val isSingleLineSummary: Boolean
    private val summaryReplacements: Array<String>?
    private val parseSummaryAsHtml: Boolean

    init {
        context.usingStyledAttributes(attrs, R.styleable.ExtendedPreferenceCategory) {
            isSingleLineSummary = getBoolean(R.styleable.ExtendedPreferenceCategory_isSingleLineSummary, false)
            parseSummaryAsHtml = getBoolean(R.styleable.ExtendedPreferenceCategory_parseSummaryAsHtml, false)

            val summaryReplacementsRes = getResourceId(R.styleable.ExtendedPreferenceCategory_summaryReplacements, 0)
            summaryReplacements = if (summaryReplacementsRes != 0) {
                getArrayFromStringOrArrayResource(context, summaryReplacementsRes)
            } else {
                null
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summaryView = holder.findViewById(android.R.id.summary) as TextView
        summaryView.isSingleLine = isSingleLineSummary

        var summaryText = summaryView.text.toString()
        summaryReplacements?.let { summaryText = summaryText.format(*it) }

        summaryView.text = if (parseSummaryAsHtml) {
            summaryView.movementMethod = LinkMovementMethod.getInstance()
            summaryText.parseAsHtml()
        } else {
            summaryText
        }
    }

    private fun getArrayFromStringOrArrayResource(context: Context, resId: Int): Array<String> {
        val res = context.resources
        return when (res.getResourceTypeName(resId)) {
            "string" -> arrayOf(res.getString(resId))
            "array" -> res.getStringArray(resId)
            else -> throw IllegalArgumentException("Provided resId is not a valid @StringRes or @ArrayRes")
        }
    }
}
