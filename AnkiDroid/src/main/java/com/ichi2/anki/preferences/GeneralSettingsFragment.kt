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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.ichi2.anki.*
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.utils.LanguageUtil
import kotlinx.coroutines.runBlocking
import java.util.*

class GeneralSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_general
    override val analyticsScreenNameConstant: String
        get() = "prefs.general"

    override fun initSubscreen() {
        initializeLanguagePreference()

        // Deck for new cards
        // Represents in the collections pref "addToCur": i.e.
        // if true, then add note to current decks, otherwise let the note type's configuration decide
        // Note that "addToCur" is a boolean while USE_CURRENT is "0" or "1"
        requirePreference<ListPreference>(R.string.deck_for_new_cards_key).apply {
            setValueIndex(if (col.get_config("addToCur", true)!!) 0 else 1)
            setOnPreferenceChangeListener { newValue ->
                col.set_config("addToCur", "0" == newValue)
            }
        }
        // Paste PNG
        // Represents in the collection's pref "pastePNG" , i.e.
        // whether to convert clipboard uri to png format or not.
        requirePreference<SwitchPreference>(R.string.paste_png_key).apply {
            isChecked = col.get_config("pastePNG", false)!!
            setOnPreferenceChangeListener { newValue ->
                col.set_config("pastePNG", newValue)
            }
        }
        // Error reporting mode
        requirePreference<ListPreference>(R.string.error_reporting_mode_key).setOnPreferenceChangeListener { newValue ->
            CrashReportService.onPreferenceChanged(requireContext(), newValue as String)
        }
        // Anki card context menu
        requirePreference<SwitchPreference>(R.string.anki_card_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.context_menu_anki_card_label))
            summary = getString(R.string.card_browser_enable_external_context_menu_summary, getString(R.string.context_menu_anki_card_label))
            setOnPreferenceChangeListener { newValue ->
                AnkiCardContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue as Boolean)
            }
        }
        // Card browser context menu
        requirePreference<SwitchPreference>(R.string.card_browser_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.card_browser_context_menu))
            summary = getString(R.string.card_browser_enable_external_context_menu_summary, getString(R.string.card_browser_context_menu))
            setOnPreferenceChangeListener { newValue ->
                CardBrowserContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue as Boolean)
            }
        }
    }

    private fun initializeLanguagePreference() {
        val languagePref = requirePreference<ListPreference>(R.string.pref_language_key)

        /* Starting at API 33, app language can be configured at the app's system settings.
        * So, the app language ListPreference is converted
        * to a Preference that leads to the new configuration place */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val currentLocale = LanguageUtil.getCurrentLocale()
            val newLanguagePref = Preference(requireContext()).apply {
                key = languagePref.key
                title = languagePref.title
                summary = currentLocale.displayName.ifBlank {
                    getString(R.string.language_system)
                }
                setOnPreferenceClickListener {
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    }
                    // TODO try using a `launchActivityForResult` to try
                    //  dismissing the backend (if it is not automatically dismissed)
                    startActivity(intent)
                    true
                }
            }
            preferenceScreen.removePreference(languagePref)
            preferenceScreen.addPreference(newLanguagePref)
            newLanguagePref.order = languagePref.order
        } else {
            val items: MutableMap<String, String> = TreeMap(java.lang.String.CASE_INSENSITIVE_ORDER)
            for (localeCode in LanguageUtil.APP_LANGUAGES) {
                val locale = LanguageUtil.getLocale(localeCode)
                items[locale.getDisplayName(locale)] = localeCode
            }

            languagePref.apply {
                entries = arrayOf(resources.getString(R.string.language_system), *items.keys.toTypedArray())
                entryValues = arrayOf(LanguageUtil.SYSTEM_DEFAULT, *items.values.toTypedArray())
                value = LanguageUtil.getCurrentLocaleCode()
                setOnPreferenceChangeListener { selectedLanguage ->
                    LanguageUtil.setDefaultBackendLanguages(selectedLanguage as String)
                    runBlocking { CollectionManager.discardBackend() }

                    val localeCode = if (selectedLanguage != LanguageUtil.SYSTEM_DEFAULT) {
                        selectedLanguage
                    } else {
                        null
                    }
                    val localeList = LocaleListCompat.forLanguageTags(localeCode)
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            }
        }
    }
}
