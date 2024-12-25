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
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.showThemedToast

class ReviewerControlPreference : ControlPreference2<ReviewerBinding> {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    override val areGesturesEnabled: Boolean
        get() = sharedPreferences?.getBoolean(GestureProcessor.PREF_KEY, false) ?: false

    override fun getMappableBindings(): List<ReviewerBinding> = ReviewerBinding.fromPreferenceString(getValue())

    override fun onKeySelected(binding: Binding) {
        CardSideSelectionDialog.displayInstance(context) { side ->
            addBinding(binding, side)
        }
    }

    override fun onGestureSelected(gesture: Gesture) {
        addBinding(Binding.GestureInput(gesture), CardSide.BOTH)
    }

    override fun onAxisSelected(binding: Binding) {
        CardSideSelectionDialog.displayInstance(context) { side ->
            addBinding(binding, side)
        }
    }

    override fun warnIfUsed(
        binding: Binding,
        warningDisplay: WarningDisplay?,
    ): Boolean {
        val prefs = context.sharedPrefs()
        val mappableBinding = ReviewerBinding(binding, CardSide.BOTH)
        val actionsMap =
            ViewerCommand.entries
                .associateWith { a -> a.getBindings(prefs) }
                .filterValues { it.isNotEmpty() }
        val commandWithBinding =
            actionsMap.entries
                .firstOrNull {
                    it.value.any { b -> b == mappableBinding }
                }?.key ?: return false

        if (commandWithBinding.preferenceKey == key) return false

        val actionTitle = context.getString(commandWithBinding.nameRes)
        val warning = context.getString(R.string.bindings_already_bound, actionTitle)
        if (warningDisplay != null) {
            warningDisplay.setWarning(warning)
        } else {
            showThemedToast(context, warning, true)
        }
        return true
    }

    private fun addBinding(
        binding: Binding,
        side: CardSide,
    ) {
        val reviewerBinding = ReviewerBinding(binding, side)
        val bindings = ReviewerBinding.fromPreferenceString(getValue()).toMutableList()
        bindings.add(reviewerBinding)
        val newPrefValue = bindings.toPreferenceString()
        setValue(newPrefValue)
    }
}
