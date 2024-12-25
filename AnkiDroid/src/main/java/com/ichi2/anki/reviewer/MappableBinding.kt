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
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ScreenAction
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.Binding.AxisButtonBinding
import com.ichi2.anki.reviewer.Binding.GestureInput
import com.ichi2.anki.reviewer.Binding.KeyBinding
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.Binding.UnicodeCharacter
import com.ichi2.utils.hash
import timber.log.Timber
import java.util.Objects

interface BindingProcessor<B : MappableBinding, A : ScreenAction<B>> {
    fun executeAction(action: A)
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
        fun fromString(s: String): MappableBinding? {
            if (s.isEmpty()) {
                return null
            }
            return try {
                // the prefix of the serialized
                when (s[0]) {
                    'r' -> ReviewerBinding.fromString(s.substring(1))
                    else -> null
                }
            } catch (e: Exception) {
                Timber.w(e, "failed to deserialize binding")
                null
            }
        }

        @CheckResult
        fun getPreferenceBindingStrings(string: String): List<String> {
            if (string.isEmpty()) return emptyList()
            if (!string.startsWith(VERSION_PREFIX)) {
                Timber.w("cannot handle version of string %s", string)
                return emptyList()
            }
            return string.substring(VERSION_PREFIX.length).split(PREF_SEPARATOR).filter { it.isNotEmpty() }
        }

        @CheckResult
        fun fromPreferenceString(string: String?): MutableList<MappableBinding> {
            if (string.isNullOrEmpty()) return ArrayList()
            try {
                val version = string.takeWhile { x -> x != '/' }
                val remainder = string.substring(version.length + 1) // skip the /
                if (version != "1") {
                    Timber.w("cannot handle version '$version'")
                    return ArrayList()
                }
                return remainder.split(PREF_SEPARATOR).mapNotNull { fromString(it) }.toMutableList()
            } catch (e: Exception) {
                Timber.w(e, "Failed to deserialize preference")
                return ArrayList()
            }
        }

        @CheckResult
        fun fromPreference(
            prefs: SharedPreferences,
            command: ViewerCommand,
        ): MutableList<MappableBinding> {
            val value = prefs.getString(command.preferenceKey, null) ?: return command.defaultValue.toMutableList()
            return fromPreferenceString(value)
        }

        @CheckResult
        fun allMappings(prefs: SharedPreferences): MutableList<Pair<ViewerCommand, MutableList<MappableBinding>>> =
            ViewerCommand.entries
                .map {
                    Pair(it, fromPreference(prefs, it))
                }.toMutableList()
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

    override fun hashCode(): Int = Objects.hash(getBindingHash(), PREFIX)

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

        fun fromString(string: String): ReviewerBinding? {
            if (string.isEmpty()) return null
            val bindingString = string.substring(0, string.length - 1)
            val binding = Binding.fromString(bindingString)
            val side =
                when (string.last()) {
                    QUESTION_SUFFIX -> CardSide.QUESTION
                    ANSWER_SUFFIX -> CardSide.ANSWER
                    else -> CardSide.BOTH
                }
            return ReviewerBinding(binding, side)
        }

        fun fromPreferenceString(prefString: String?): List<ReviewerBinding> {
            if (prefString.isNullOrEmpty()) return emptyList()
            val strings = getPreferenceBindingStrings(prefString) // TODO
            return strings.mapNotNull {
                if (it.isEmpty()) return@mapNotNull null
                fromString(it.substring(1))
            }
        }

        @CheckResult
        fun fromGesture(gesture: Gesture): ReviewerBinding = ReviewerBinding(GestureInput(gesture), CardSide.BOTH)
    }
}
