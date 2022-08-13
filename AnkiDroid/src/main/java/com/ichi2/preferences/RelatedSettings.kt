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
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.Preferences
import com.ichi2.anki.R

class RelatedSettings @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.relatedSettingsStyle,
    defStyleRes: Int = R.style.relatedSettings
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var entriesNames: Array<CharSequence>
    private lateinit var entriesFragments: Array<CharSequence>
    lateinit var activity: Preferences

    init {
        // key is intentionally hardcoded, since there shouldn't be more than one RelatedSettings
        // pref per PreferenceScreen, and this makes retrieving the preference easier
        key = context.getString(R.string.related_settings_key)

        context.withStyledAttributes(attrs, R.styleable.RelatedSettings) {
            entriesNames = getTextArray(R.styleable.RelatedSettings_entriesNames)
                ?: throw Exception("RelatedSettings must have an 'entriesNames' attribute configured")

            entriesFragments = getTextArray(R.styleable.RelatedSettings_entriesFragments)
                ?: throw Exception("RelatedSettings must have an 'entriesFragments' attribute configured")
        }
        assert(entriesNames.size == entriesFragments.size)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val layout = holder.findViewById(R.id.related_settings_items_container) as LinearLayout

        for (i in entriesNames.indices) {
            val view = RelatedSettingsItem(context).apply {
                text = entriesNames[i]
                fragmentClassName = entriesFragments[i].toString()
                parent = this@RelatedSettings
            }
            layout.addView(view)
        }
        super.onBindViewHolder(holder)
    }

    fun openFragmentFromName(fragmentClassName: String) {
        val fragment = Preferences.getClassInstanceFromName<Fragment>(fragmentClassName)

        activity.supportFragmentManager.commit {
            replace(R.id.settings_container, fragment)
            addToBackStack(null)
        }
    }
}

class RelatedSettingsItem(context: Context) :
    TextView(context, null, R.attr.relatedSettingsItemStyle, R.style.relatedSettingsItem) {

    lateinit var parent: RelatedSettings
    lateinit var fragmentClassName: String

    init {
        setOnClickListener {
            parent.openFragmentFromName(fragmentClassName)
        }
    }
}
