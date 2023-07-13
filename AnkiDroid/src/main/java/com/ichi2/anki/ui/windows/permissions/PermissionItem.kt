/*
 *  Copyright (c) 2020 Hemanth Savarla
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
package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.Permissions

/**
 * Layout object that can be used to get a permission from the user.
 *
 * XML attributes:
 * * app:permissionTitle ([R.styleable.PermissionItem_permissionTitle]):
 *     Title of the permission
 * * app:permissionSummary ([R.styleable.PermissionItem_permissionSummary]):
 *     Brief description of the permission. It can be used to explain to the user
 *     why the permission should be granted
 * * app:permissionButtonText ([R.styleable.PermissionItem_permissionButtonText]):
 *     Text inside the permission button. Normally it should be something like `Grant access`
 * * app:permissionNumber ([R.styleable.PermissionItem_permissionNumber]):
 *     Number to be displayed at the layout's side. Normally, it should be the
 *     PermissionItem position among others in a screen
 * * app:permissionIcon ([R.styleable.PermissionItem_permissionIcon]):
 *     Icon to be shown at the side of the button
 */
class PermissionItem(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {

    private lateinit var button: AppCompatButton
    private lateinit var checkMark: AppCompatImageView
    private lateinit var number: FixedTextView
    lateinit var permissions: List<String>
    val isGranted
        get() = Permissions.hasAllPermissions(context, permissions)

    init {
        LayoutInflater.from(context).inflate(R.layout.permission_item, this, true)

        context.withStyledAttributes(attrs, R.styleable.PermissionItem) {
            number = findViewById<FixedTextView>(R.id.number).apply {
                text = getText(R.styleable.PermissionItem_permissionNumber)
            }
            findViewById<FixedTextView>(R.id.title).text = getText(R.styleable.PermissionItem_permissionTitle)
            findViewById<FixedTextView>(R.id.summary).text = getText(R.styleable.PermissionItem_permissionSummary)
            button = findViewById<AppCompatButton>(R.id.button).apply {
                text = getText(R.styleable.PermissionItem_permissionButtonText)
                val icon = getDrawable(R.styleable.PermissionItem_permissionIcon)
                icon?.setTint(Themes.getColorFromAttr(context, android.R.attr.textColor))
                setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            }
            checkMark = findViewById(R.id.checkImage)
        }
    }

    fun setButtonClickListener(listener: (AppCompatButton, PermissionItem) -> Unit) {
        button.setOnClickListener { button ->
            listener.invoke(button as AppCompatButton, this)
        }
    }

    /**
     * Sets the visibility of a checkmark icon at the side of the permission button.
     * Useful for showing that the permission has been granted
     * */
    fun setCheckMarkVisibility(isVisible: Boolean) {
        checkMark.isVisible = isVisible
    }
}
