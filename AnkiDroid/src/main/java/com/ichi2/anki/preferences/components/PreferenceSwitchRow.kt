// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun PreferenceSwitchRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer)
                .toggleable(isChecked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(24.dp)) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.onSurface.copy(alpha = contentAlpha),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = isChecked, onCheckedChange = null, enabled = enabled)
    }
}
