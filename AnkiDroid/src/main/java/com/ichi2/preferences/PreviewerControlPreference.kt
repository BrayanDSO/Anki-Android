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
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.PreviewerAction
import com.ichi2.anki.reviewer.PreviewerBinding
import com.ichi2.anki.showThemedToast

class PreviewerControlPreference : ControlPreference<PreviewerBinding> {
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

    override val areGesturesEnabled: Boolean = false

    override fun getMappableBindings(): List<PreviewerBinding> = PreviewerBinding.fromPreferenceValue(getValue())

    override fun onKeySelected(binding: Binding) = addBinding(binding)

    override fun onGestureSelected(binding: Binding) = addBinding(binding)

    override fun onAxisSelected(binding: Binding) = addBinding(binding)

    override fun warnIfUsed(
        binding: Binding,
        warningDisplay: WarningDisplay?,
    ): Boolean {
        val prefs = context.sharedPrefs()
        val mappableBinding = PreviewerBinding(binding)
        val actionsMap =
            PreviewerAction.entries
                .associateWith { a -> a.getBindings(prefs) }
                .filterValues { it.isNotEmpty() }
        val commandWithBinding =
            actionsMap.entries
                .firstOrNull {
                    it.value.any { b -> b == mappableBinding }
                }?.key ?: return false

        if (commandWithBinding.preferenceKey == key) return false

        val actionTitle = context.getString(commandWithBinding.titleRes)
        val warning = context.getString(R.string.bindings_already_bound, actionTitle)
        if (warningDisplay != null) {
            warningDisplay.setWarning(warning)
        } else {
            showThemedToast(context, warning, true)
        }
        return true
    }

    private fun addBinding(binding: Binding) {
        val previewerBinding = PreviewerBinding(binding)
        val bindings = getMappableBindings().toMutableList()
        bindings.add(previewerBinding)
        setValue(bindings.toPreferenceString())
    }
}
