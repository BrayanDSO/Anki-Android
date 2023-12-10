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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.navigation.NavigationView
import com.ichi2.anki.pages.StatisticsActivity
import com.ichi2.anki.preferences.Preferences
import kotlin.reflect.KClass

open class NavigationBarActivity : AnkiActivity(), NavigationView.OnNavigationItemSelectedListener {

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        val view =
            findViewById<NavigationView>(R.id.navigation_bar)
                ?.setNavigationItemSelectedListener(this)
        return super.onCreateView(name, context, attrs)
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
            R.id.nav_more -> MoreNavigationDialog().show(supportFragmentManager, null)
            else -> return false
        }
        return true
    }
}

class MoreNavigationDialog : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.more_nav_drawer, container, false)

        view.findViewById<LinearLayout>(R.id.drawer_settings_container).setOnClickListener {
            requireActivity().run {
                startActivity(Intent(this, Preferences::class.java))
            }
        }

        dialog?.let { dialog ->
            dialog.setOnShowListener {
                val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        return view
    }
}
