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

import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.HideSystemBars

/**
 * Developer options to test some of the new reviewer settings and features
 *
 * Not a `SettingsFragment` to avoid boilerplate and sending analytics reports,
 * since this is just a temporary screen while the new reviewer is being developed.
 */
class ReviewerOptionsFragment :
    SettingsFragment(),
    PreferenceXmlSource {
    override val preferenceResource: Int = R.xml.preferences_reviewer
    override val analyticsScreenNameConstant: String = "prefs.studyScreen"

    override fun initSubscreen() {
        val ignoreDisplayCutout =
            requirePreference<SwitchPreferenceCompat>(R.string.ignore_display_cutout_key).apply {
                isEnabled = Prefs.hideSystemBars != HideSystemBars.NONE
            }

        requirePreference<ListPreference>(R.string.hide_system_bars_key).setOnPreferenceChangeListener { value ->
            ignoreDisplayCutout.isEnabled = value != HideSystemBars.NONE.entryValue
        }
    }
}
