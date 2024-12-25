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
    private val bindingMap = HashMap<M, A>()

    init {
        for (action in actions) {
            val bindings = action.getBindings(sharedPrefs)
            for (binding in bindings) {
                bindingMap[binding] = action
            }
        }
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        var ret = false
        val possibleKeyBindings = Binding.possibleKeyBindings(event)
        for ((mappableBinding, action) in bindingMap) {
            if (possibleKeyBindings.contains(mappableBinding.binding)) {
                ret = ret or processor.executeAction(action)
            }
        }
//        val mds = bindingMap.filterKeys { m -> possibleKeyBindings.any { it == m.binding } }
//        for ((mappableBinding, action) in mds) {
//            ret = ret or processor.executeAction(action)
//        }
        return ret
    }
}
