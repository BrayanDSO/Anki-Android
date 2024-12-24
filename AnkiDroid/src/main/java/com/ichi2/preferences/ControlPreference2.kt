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

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.preference.DialogPreference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils
import com.ichi2.anki.dialogs.GestureSelectionDialogUtils.onGestureChanged
import com.ichi2.anki.dialogs.KeySelectionDialogUtils
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.ui.AxisPicker
import com.ichi2.ui.KeyPicker
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * A preference which allows mapping of inputs to actions (example: keys -> commands)
 *
 * This is implemented as a List, the elements allow the user to either add, or
 * remove previously mapped keys
 */
abstract class ControlPreference2<T : MappableBinding> :
    DialogPreference,
    DialogFragmentProvider {
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

    var value: String = ""
        set(value) {
            val changed = !TextUtils.equals(field, value)
            if (changed) {
                field = value
                persistString(value)
                notifyChanged()
            }
        }

    val action: ViewerCommand = ViewerCommand.USER_ACTION_1

    override fun getSummary(): CharSequence {
        val prefValue = sharedPreferences?.getString(key, null) ?: return ""
        val bindings = MappableBinding.fromPreferenceString(prefValue)
        return bindings.joinToString(", ") { it.toDisplayString(context) }
    }

    override fun makeDialogFragment(): DialogFragment = ControlPreferenceDialogFragment<T>()

    fun addMappableBinding(mappableBinding: MappableBinding) {
        val bindings = action.getBindings(context.sharedPrefs()).toMutableList()
        bindings.add(mappableBinding as ReviewerBinding)
        value = bindings.toPreferenceString()
    }

    fun showGesturePickerDialog() {
        AlertDialog.Builder(context).show {
            setTitle(title)
            val gesturePicker = GestureSelectionDialogUtils.getGesturePicker(context)
            positiveButton(R.string.dialog_ok) {
                val gesture = gesturePicker.getGesture() ?: return@positiveButton
                val mappableBinding = ReviewerBinding.fromGesture(gesture)
                addMappableBinding(mappableBinding)
                it.dismiss()
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            customView(view = gesturePicker)
            gesturePicker.onGestureChanged { _ ->
            }
        }
    }

    fun showKeyPickerDialog() {
        AlertDialog.Builder(context).show {
            val keyPicker: KeyPicker = KeyPicker.inflate(context)
            customView(view = keyPicker.rootLayout)
            setTitle(title)

            // When the user presses a key
            keyPicker.setBindingChangedListener { _ ->
            }
            positiveButton(R.string.dialog_ok) {
                val binding = keyPicker.getBinding() ?: return@positiveButton
                CardSideSelectionDialog.displayInstance(context) { side ->
                    val currentCommand = getViewerCommandAssociatedTo(binding)
                    if (currentCommand != null && currentCommand != action) {
                        it.dismiss()
                    } else {
                        val reviewerBinding = ReviewerBinding(binding, side)
                        addMappableBinding(reviewerBinding)
                        it.dismiss()
                    }
                }
            }
            negativeButton(R.string.dialog_cancel) { it.dismiss() }
            keyPicker.setKeycodeValidation(KeySelectionDialogUtils.disallowModifierKeyCodes())
        }
    }

    fun showAddAxisDialog() {
        val axisPicker: AxisPicker = AxisPicker.inflate(context)

        val dialog =
            AlertDialog
                .Builder(context)
                .customView(view = axisPicker.rootLayout)
                .setTitle(title)
                .negativeButton(R.string.dialog_cancel) { it.dismiss() }
                .create()

        axisPicker.setBindingChangedListener { _ ->
        }

        dialog.show()
    }

    private fun getViewerCommandAssociatedTo(binding: Binding): ViewerCommand? {
        val mappings = ViewerCommand.entries.associateWith { it.getBindings(sharedPreferences!!) }
        return mappings.entries
            .firstOrNull { x ->
                x.value.any { reviewerBinding -> reviewerBinding.binding == binding }
            }?.key
    }
}

class ControlPreferenceDialogFragment<T : MappableBinding> : DialogFragment() {
    private lateinit var preference: ControlPreference2<T>
    private lateinit var preferences: SharedPreferences
    private var title: CharSequence? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key = requireArguments().getString(SettingsFragment.PREF_DIALOG_KEY)!!
        preference = (targetFragment as PreferenceFragmentCompat).requirePreference(key)
        preferences = preference.sharedPreferences!!
        title = preference.title
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.control_preference, null)

        setupAddBindingDialogs(view)
        setupRemovalEntries(view)

        return AlertDialog.Builder(requireContext()).create {
            setTitle(title)
            customView(view)
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun setupAddBindingDialogs(view: View) {
        view.findViewById<View>(R.id.add_gesture).apply {
            setOnClickListener {
                preference.showGesturePickerDialog()
                dismiss()
            }
            isVisible = sharedPrefs().getBoolean(GestureProcessor.PREF_KEY, false)
        }

        view.findViewById<View>(R.id.add_key).setOnClickListener {
            preference.showKeyPickerDialog()
            dismiss()
        }

        view.findViewById<View>(R.id.add_axis).setOnClickListener {
            preference.showAddAxisDialog()
            dismiss()
        }
    }

    private fun setupRemovalEntries(view: View) {
        val listView = view.findViewById<ListView>(R.id.list_view)
        val bindings = MappableBinding.fromPreferenceString(preferences.getString(preference.key, ""))
        if (bindings.isEmpty()) {
            listView.isVisible = false
            return
        }
        val titles =
            bindings.map {
                getString(R.string.binding_remove_binding, it.toDisplayString(requireContext()))
            }
        listView.apply {
            adapter = ArrayAdapter(requireContext(), R.layout.control_preference_list_item, titles)
            setOnItemClickListener { _, _, index, _ ->
                bindings.removeAt(index)
                preference.value = bindings.toPreferenceString()
                dismiss()
            }
        }
    }
}

class ReviewerPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
        defStyleRes: Int = androidx.preference.R.style.Preference_DialogPreference,
    ) : ControlPreference2<ReviewerBinding>(context, attrs, defStyleAttr, defStyleRes)
