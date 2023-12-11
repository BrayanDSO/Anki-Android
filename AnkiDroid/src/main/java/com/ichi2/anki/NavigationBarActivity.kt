/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.navigation.NavigationBarView
import com.ichi2.anki.dialogs.HelpDialog
import com.ichi2.anki.pages.StatisticsActivity
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import kotlin.reflect.KClass

open class NavigationBarActivity :
    AnkiActivity(),
    NavigationBarView.OnItemSelectedListener,
    NavigationBarView.OnItemReselectedListener,
    BaseSnackbarBuilderProvider {

    val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val intent = intent
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        finish()
        startActivity(intent)
    }

    override val baseSnackbarBuilder: SnackbarBuilder =
        { anchorView = findViewById(R.id.navigation_bar) }

    override fun onStart() {
        super.onStart()
        findViewById<NavigationBarView>(R.id.navigation_bar)?.let {
            it.selectedItemId = when (this) {
                is DeckPicker -> R.id.nav_decks
                is CardBrowser -> R.id.nav_browser
                is StatisticsActivity -> R.id.nav_stats
                else -> TODO("Unhandled class")
            }
            it.setOnItemReselectedListener(this)
            it.setOnItemSelectedListener(this)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        fun openActivity(clazz: KClass<out Activity>) {
            val intent = Intent(this, clazz.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            // deliberately avoid using animation to look like there is just one activity
            startActivityWithoutAnimation(intent)
        }
        when (item.itemId) {
            R.id.nav_decks -> openActivity(DeckPicker::class)
            R.id.nav_browser -> openActivity(CardBrowser::class)
            R.id.nav_stats -> openActivity(StatisticsActivity::class)
            R.id.nav_more -> {
                MoreNavigationDialog().show(supportFragmentManager, null)
                return false
            }
            else -> return false
        }
        return true
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        // do nothing
    }
}

class MoreNavigationDialog : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.more_nav_drawer, container, false)

        // Settings
        view.findViewById<LinearLayout>(R.id.drawer_settings_container).setOnClickListener {
            (requireActivity() as NavigationBarActivity).settingsLauncher.launch(Intent(requireContext(), Preferences::class.java))
            dialog?.dismiss()
        }
        // Help
        view.findViewById<LinearLayout>(R.id.help_container).setOnClickListener {
            HelpDialog.createInstance().show(parentFragmentManager, null)
            dialog?.dismiss()
        }
        // Support AnkiDroid
        view.findViewById<LinearLayout>(R.id.support_container).setOnClickListener {
            HelpDialog.createInstanceForSupportAnkiDroid(requireContext())
                .show(parentFragmentManager, null)
            dialog?.dismiss()
        }

        // Start the dialog expanded
        dialog?.let { dialog ->
            dialog.setOnShowListener {
                val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        return view
    }
}
