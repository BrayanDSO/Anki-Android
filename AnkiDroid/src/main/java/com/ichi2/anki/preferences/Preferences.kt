/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.themes.setTransparentStatusBar
import com.ichi2.utils.getInstanceFromClassName
import timber.log.Timber
import kotlin.reflect.jvm.jvmName

class Preferences :
    AnkiActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SearchPreferenceResultListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)
        setTransparentStatusBar()

        enableToolbar().setDisplayHomeAsUpEnabled(true)

        // Load initial fragment if activity is being first created
        if (savedInstanceState == null) {
            loadInitialFragment()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            // Expand bar in new fragments if scrolled to top
            val fragment = supportFragmentManager.findFragmentById(R.id.settings_container)
                as? PreferenceFragmentCompat ?: return@addOnBackStackChangedListener
            fragment.listView.post {
                val viewHolder = fragment.listView?.findViewHolderForAdapterPosition(0)
                val isAtTop = viewHolder != null && viewHolder.itemView.top >= 0
                findViewById<AppBarLayout>(R.id.appbar).setExpanded(isAtTop, false)
            }

            val title = if (fragment is TitleProvider) fragment.title else ""
            findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)?.title = title
            supportActionBar?.title = title
        }
    }

    /**
     * Starts the first fragment for the [Preferences] activity,
     * which by default is [HeaderFragment].
     * The initial fragment may be overridden by putting the java class name
     * of the fragment on an intent extra with the key [INITIAL_FRAGMENT_EXTRA]
     */
    private fun loadInitialFragment() {
        val fragmentClassName = intent?.getStringExtra(INITIAL_FRAGMENT_EXTRA)
        val initialFragment = if (fragmentClassName == null) {
            if (resources.isWindowCompact()) HeaderFragment() else GeneralSettingsFragment()
        } else {
            try {
                getInstanceFromClassName<Fragment>(fragmentClassName)
            } catch (e: Exception) {
                throw RuntimeException("Failed to load $fragmentClassName", e)
            }
        }
        supportFragmentManager.commit {
            // In tablets, show the headers fragment at the lateral navigation container
            if (!resources.isWindowCompact()) {
                replace(R.id.lateral_nav_container, HeaderFragment())
            }
            replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // avoid reopening the same fragment if already active
        val currentFragment = supportFragmentManager.findFragmentById(R.id.settings_container)
            ?: return true
        if (pref.fragment == currentFragment::class.jvmName) return true

        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment ?: return true
        )
        fragment.arguments = pref.extras
        supportFragmentManager.commit {
            replace(R.id.settings_container, fragment, fragment::class.jvmName)
            addToBackStack(null)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (resources.isWindowCompact()) {
                onBackPressedDispatcher.onBackPressed()
            } else {
                finish()
            }
        }
        return true
    }

    // ----------------------------------------------------------------------------
    // Class methods
    // ----------------------------------------------------------------------------

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val fragment = getFragmentFromXmlRes(result.resourceFile) ?: return

        supportFragmentManager.popBackStack() // clear the search fragment from the backstack
        supportFragmentManager.commit {
            replace(R.id.settings_container, fragment, fragment.javaClass.name)
            addToBackStack(fragment.javaClass.name)
        }

        Timber.i("Highlighting key '%s' on %s", result.key, fragment)
        result.highlight(fragment as PreferenceFragmentCompat)
    }
}

interface TitleProvider {
    val title: CharSequence
}

/* Only enable AnkiDroid notifications unrelated to due reminders */
const val PENDING_NOTIFICATIONS_ONLY = 1000000

const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

/**
 * @return the [SettingsFragment] which uses the given [screen] resource.
 * i.e. [SettingsFragment.preferenceResource] value is the same of [screen]
 */
fun getFragmentFromXmlRes(@XmlRes screen: Int): SettingsFragment? {
    return when (screen) {
        R.xml.preferences_general -> GeneralSettingsFragment()
        R.xml.preferences_reviewing -> ReviewingSettingsFragment()
        R.xml.preferences_sync -> SyncSettingsFragment()
        R.xml.preferences_backup_limits -> BackupLimitsSettingsFragment()
        R.xml.preferences_custom_sync_server -> CustomSyncServerSettingsFragment()
        R.xml.preferences_notifications -> NotificationsSettingsFragment()
        R.xml.preferences_appearance -> AppearanceSettingsFragment()
        R.xml.preferences_controls -> ControlsSettingsFragment()
        R.xml.preferences_advanced -> AdvancedSettingsFragment()
        R.xml.preferences_accessibility -> AccessibilitySettingsFragment()
        R.xml.preferences_dev_options -> DevOptionsFragment()
        R.xml.preferences_custom_buttons -> CustomButtonsSettingsFragment()
        else -> null
    }
}
