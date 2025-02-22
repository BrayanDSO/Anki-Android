/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.Companion.possibleKeyBindings

class BindingMap<B : MappableBinding, A : MappableAction<B>>(
    sharedPrefs: SharedPreferences,
    actions: List<A>,
    private var processor: BindingProcessor<B, A>? = null,
) {
    private val keyMap = HashMap<Binding.KeyBinding, Pair<B, A>>()
    private val gestureMap = HashMap<Gesture, Pair<B, A>>()

    init {
        for (action in actions) {
            val mappableBindings = action.getBindings(sharedPrefs)
            for (mappableBinding in mappableBindings) {
                when (val binding = mappableBinding.binding) {
                    is Binding.KeyBinding -> {
                        keyMap[binding] = mappableBinding to action
                    }
                    is Binding.GestureInput -> {
                        gestureMap[binding.gesture] = mappableBinding to action
                    }
                    else -> continue
                }
            }
        }
    }

    fun setProcessor(processor: BindingProcessor<B, A>) {
        this.processor = processor
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        val bindings = possibleKeyBindings(event)
        for (binding in bindings) {
            val (mappableBinding, action) = keyMap[binding] ?: continue
            if (processor?.processAction(action, mappableBinding) == true) return true
        }
        return false
    }

    fun onTap(code: String) {
        val gesture =
            when (code) {
                "double" -> Gesture.DOUBLE_TAP
                "topLeft" -> Gesture.TAP_TOP_LEFT
                "topCenter" -> Gesture.TAP_TOP
                "topRight" -> Gesture.TAP_TOP_RIGHT
                "midLeft" -> Gesture.TAP_LEFT
                "midCenter" -> Gesture.TAP_CENTER
                "midRight" -> Gesture.TAP_RIGHT
                "bottomLeft" -> Gesture.TAP_BOTTOM_LEFT
                "bottomCenter" -> Gesture.TAP_BOTTOM
                "bottomRight" -> Gesture.TAP_BOTTOM_RIGHT
                else -> return
            }
        val (mappableBinding, action) = gestureMap[gesture] ?: return
        processor?.processAction(action, mappableBinding)
    }
}
