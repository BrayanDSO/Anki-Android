/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.cardviewer.ScreenAction

class ScreenKeyMap<M : MappableBinding, A : ScreenAction<M>>(
    sharedPrefs: SharedPreferences,
    actions: List<A>,
    private val processor: BindingProcessor<M, A>,
) {
    private val bindingMap = HashMap<Binding, Pair<M, A>>()

    init {
        for (action in actions) {
            val mappableBindings = action.getBindings(sharedPrefs)
            for (mappableBinding in mappableBindings) {
                bindingMap[mappableBinding.binding] = mappableBinding to action
            }
        }
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        var ret = false
        val bindings = Binding.possibleKeyBindings(event)
        for (binding in bindings) {
            val (mappableBinding, action) = bindingMap[binding] ?: continue
            ret = ret or processor.executeAction(action, mappableBinding)
        }

        return ret
    }
}
