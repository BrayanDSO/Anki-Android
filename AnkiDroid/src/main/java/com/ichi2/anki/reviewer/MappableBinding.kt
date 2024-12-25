/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ScreenAction
import com.ichi2.anki.reviewer.Binding.AxisButtonBinding
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.Companion.unicode
import com.ichi2.anki.reviewer.Binding.GestureInput
import com.ichi2.anki.reviewer.Binding.KeyBinding
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.UnicodeCharacter
import com.ichi2.utils.hash
import timber.log.Timber
import java.util.Objects

fun interface BindingProcessor<B : MappableBinding, A : ScreenAction<B>> {
    fun executeAction(
        action: A,
        forBinding: B,
    ): Boolean
}

/**
 * Binding + additional contextual information
 * Also defines equality over bindings.
 * https://stackoverflow.com/questions/5453226/java-need-a-hash-map-where-one-supplies-a-function-to-do-the-hashing
 */
@Suppress("EqualsOrHashCode")
sealed class MappableBinding(
    val binding: Binding,
) {
    val isKey: Boolean get() = binding is KeyBinding

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        val otherBinding = (other as MappableBinding).binding

        return when {
            binding is KeyCode && otherBinding is KeyCode -> binding.keycode == otherBinding.keycode && modifierEquals(otherBinding)
            binding is UnicodeCharacter && otherBinding is UnicodeCharacter -> {
                binding.unicodeCharacter == otherBinding.unicodeCharacter &&
                    modifierEquals(otherBinding)
            }
            binding is GestureInput && otherBinding is GestureInput -> binding.gesture == otherBinding.gesture
            binding is AxisButtonBinding && otherBinding is AxisButtonBinding -> {
                binding.axis == otherBinding.axis && binding.threshold == otherBinding.threshold
            }
            else -> false
        }
    }

    protected fun getBindingHash(): Any {
        // don't include the modifierKeys
        return when (binding) {
            is KeyCode -> binding.keycode
            is UnicodeCharacter -> binding.unicodeCharacter
            is GestureInput -> binding.gesture
            is AxisButtonBinding -> hash(binding.axis.motionEventValue, binding.threshold.toInt())
            else -> 0
        }
    }

    abstract override fun hashCode(): Int

    private fun modifierEquals(otherBinding: KeyBinding): Boolean {
        // equals allowing subclasses
        val keys = otherBinding.modifierKeys
        val thisKeys = (this.binding as KeyBinding).modifierKeys
        if (thisKeys === keys) return true
        return thisKeys.semiStructuralEquals(keys)

        // allow subclasses to work - a subclass which overrides shiftMatches will return true on one of the tests
    }

    abstract fun toDisplayString(context: Context): String

    abstract fun toPreferenceString(): String?

    companion object {
        const val PREF_SEPARATOR = '|'
        private const val VERSION_PREFIX = "1/"

        @CheckResult
        fun List<MappableBinding>.toPreferenceString(): String =
            this
                .mapNotNull { it.toPreferenceString() }
                .joinToString(prefix = VERSION_PREFIX, separator = PREF_SEPARATOR.toString())

        @CheckResult
        fun getPreferenceBindingStrings(string: String): List<String> {
            if (string.isEmpty()) return emptyList()
            if (!string.startsWith(VERSION_PREFIX)) {
                Timber.w("cannot handle version of string %s", string)
                return emptyList()
            }
            return string.substring(VERSION_PREFIX.length).split(PREF_SEPARATOR).filter { it.isNotEmpty() }
        }
    }
}

class ReviewerBinding(
    binding: Binding,
    val side: CardSide,
) : MappableBinding(binding) {
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        if (other !is ReviewerBinding) return false

        return side === CardSide.BOTH ||
            other.side === CardSide.BOTH ||
            side === other.side
    }

    override fun hashCode(): Int = Objects.hash(getBindingHash(), PREFIX, side)

    override fun toPreferenceString(): String? {
        if (!binding.isValid) return null
        val s = StringBuilder().append(PREFIX).append(binding.toString())
        when (side) {
            CardSide.QUESTION -> s.append(QUESTION_SUFFIX)
            CardSide.ANSWER -> s.append(ANSWER_SUFFIX)
            CardSide.BOTH -> s.append(QUESTION_AND_ANSWER_SUFFIX)
        }
        return s.toString()
    }

    override fun toDisplayString(context: Context): String {
        val formatString =
            when (side) {
                CardSide.QUESTION -> context.getString(R.string.display_binding_card_side_question)
                CardSide.ANSWER -> context.getString(R.string.display_binding_card_side_answer)
                CardSide.BOTH -> context.getString(R.string.display_binding_card_side_both) // intentionally no prefix
            }
        return String.format(formatString, binding.toDisplayString(context))
    }

    companion object {
        private const val PREFIX = "r"
        private const val QUESTION_SUFFIX = '0'
        private const val ANSWER_SUFFIX = '1'
        private const val QUESTION_AND_ANSWER_SUFFIX = '2'

        fun fromPreferenceString(prefString: String?): List<ReviewerBinding> {
            if (prefString.isNullOrEmpty()) return emptyList()

            fun fromString(string: String): ReviewerBinding? {
                if (string.isEmpty()) return null
                val bindingString =
                    StringBuilder(string)
                        .substring(0, string.length - 1)
                        .removePrefix(PREFIX)
                val binding = Binding.fromString(bindingString)
                val side =
                    when (string.last()) {
                        QUESTION_SUFFIX -> CardSide.QUESTION
                        ANSWER_SUFFIX -> CardSide.ANSWER
                        else -> CardSide.BOTH
                    }
                return ReviewerBinding(binding, side)
            }

            val strings = getPreferenceBindingStrings(prefString)
            return strings.mapNotNull { fromString(it) }
        }

        @CheckResult
        fun fromGesture(gesture: Gesture): ReviewerBinding = ReviewerBinding(GestureInput(gesture), CardSide.BOTH)
    }
}

class PreviewerBinding(
    binding: Binding,
) : MappableBinding(binding) {
    override fun toDisplayString(context: Context): String = binding.toDisplayString(context)

    override fun toPreferenceString(): String = binding.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false
        return true
    }

    override fun hashCode(): Int = Objects.hash(binding, 'p')

    companion object {
        fun fromPreferenceValue(prefValue: String?): List<PreviewerBinding> {
            if (prefValue.isNullOrEmpty()) return emptyList()
            return getPreferenceBindingStrings(prefValue).map {
                val binding = Binding.fromString(it)
                PreviewerBinding(binding)
            }
        }
    }
}

enum class PreviewerAction(
    override val titleRes: Int,
) : ScreenAction<PreviewerBinding> {
    BACK(R.string.previewer_back),
    NEXT(R.string.previewer_next),
    MARK(R.string.menu_mark_note),
    EDIT(R.string.cardeditor_title_edit_card),
    TOGGLE_BACKSIDE_ONLY(R.string.toggle_backside_only),
    REPLAY_AUDIO(R.string.replay_audio),
    TOGGLE_FLAG_RED(R.string.gesture_flag_red),
    TOGGLE_FLAG_ORANGE(R.string.gesture_flag_orange),
    TOGGLE_FLAG_GREEN(R.string.gesture_flag_green),
    TOGGLE_FLAG_BLUE(R.string.gesture_flag_blue),
    TOGGLE_FLAG_PINK(R.string.gesture_flag_pink),
    TOGGLE_FLAG_TURQUOISE(R.string.gesture_flag_turquoise),
    TOGGLE_FLAG_PURPLE(R.string.gesture_flag_purple),
    UNSET_FLAG(R.string.gesture_flag_purple),
    ;

    override val preferenceKey = "previewer_$name"

    override fun getBindings(prefs: SharedPreferences): List<PreviewerBinding> {
        val prefValue = prefs.getString(preferenceKey, null) ?: return defaultBindings
        return PreviewerBinding.fromPreferenceValue(prefValue)
    }

    private val defaultBindings: List<PreviewerBinding> get() {
        val binding =
            when (this) {
                BACK -> keyCode(KeyEvent.KEYCODE_DPAD_LEFT)
                NEXT -> keyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                MARK -> unicode('*')
                REPLAY_AUDIO -> keyCode(KeyEvent.KEYCODE_R)
                EDIT -> keyCode(KeyEvent.KEYCODE_E)
                TOGGLE_BACKSIDE_ONLY -> keyCode(KeyEvent.KEYCODE_B)
                TOGGLE_FLAG_RED -> keyCode(KeyEvent.KEYCODE_1, ctrl())
                TOGGLE_FLAG_ORANGE -> keyCode(KeyEvent.KEYCODE_2, ctrl())
                TOGGLE_FLAG_GREEN -> keyCode(KeyEvent.KEYCODE_3, ctrl())
                TOGGLE_FLAG_BLUE -> keyCode(KeyEvent.KEYCODE_4, ctrl())
                TOGGLE_FLAG_PINK -> keyCode(KeyEvent.KEYCODE_5, ctrl())
                TOGGLE_FLAG_TURQUOISE -> keyCode(KeyEvent.KEYCODE_6, ctrl())
                TOGGLE_FLAG_PURPLE -> keyCode(KeyEvent.KEYCODE_7, ctrl())
                UNSET_FLAG -> return emptyList()
            }
        return listOf(PreviewerBinding(binding))
    }
}
