// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.compose.ui.components.GroupedList
import com.ichi2.compose.ui.dialogs.AnkiDroidDialog
import com.ichi2.compose.ui.dialogs.ankiDroidDialogContentPadding
import com.ichi2.compose.ui.dialogs.ankiDroidDialogElevation
import com.ichi2.themes.ThemeColorChoice
import com.ichi2.themes.ThemeColorRole
import com.ichi2.themes.ThemeColorSource
import com.ichi2.themes.ThemePreviewColors
import androidx.appcompat.R as AppCompatR

private const val DISABLED_CONTROL_ALPHA = 0.38f

internal data class ThemeSettingsState(
    val enabled: Boolean,
    val scheme: AppTheme,
    val eink: Boolean,
    val lightColors: Map<ThemeColorRole, ThemeColorChoice>,
    val darkColors: Map<ThemeColorRole, ThemeColorChoice>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSettingsScreen(
    state: ThemeSettingsState,
    systemIsDark: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    onSchemeChange: (AppTheme) -> Unit,
    onEinkChange: (Boolean) -> Unit,
    onColorSourceChange: (Boolean, ThemeColorRole, ThemeColorSource, Int) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var editingDark by rememberSaveable { mutableStateOf(systemIsDark) }
    var choosingScheme by remember { mutableStateOf(false) }
    var choosingColor by remember { mutableStateOf<ThemeColorRole?>(null) }
    val dark =
        when (state.scheme) {
            AppTheme.FOLLOW_SYSTEM -> editingDark
            AppTheme.DAY -> false
            AppTheme.NIGHT -> true
        }
    val colorsEnabled = state.enabled && !state.eink
    val choices = if (dark) state.darkColors else state.lightColors
    val colorTabs: (@Composable () -> Unit)? =
        if (state.scheme == AppTheme.FOLLOW_SYSTEM) {
            { ThemeColorTabs(dark, colorsEnabled, onSelect = { editingDark = it }) }
        } else {
            null
        }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(context, configuration, dark) { ThemePreviewColors(context, dark) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_theme_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_arrow_back_24),
                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupedList {
                item(
                    modifier = Modifier.toggleable(state.enabled, role = Role.Switch, onValueChange = onPreviewChange),
                    trailingContent = { Switch(checked = state.enabled, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.dev_theme_preview_enabled))
                }
                item(
                    modifier = Modifier.clickable(enabled = state.enabled) { choosingScheme = true },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_palette),
                            contentDescription = null,
                            modifier = Modifier.alpha(if (state.enabled) 1f else DISABLED_CONTROL_ALPHA),
                        )
                    },
                ) {
                    SettingLabel(
                        title = stringResource(R.string.dev_theme_color_scheme),
                        summary = stringResource(state.scheme.label),
                        enabled = state.enabled,
                    )
                }
            }
            GroupedList(header = colorTabs) {
                ThemeColorRole.entries.forEach { role ->
                    val choice = choices.getValue(role)
                    item(
                        modifier = Modifier.clickable(enabled = colorsEnabled) { choosingColor = role },
                        leadingContent = { ColorSwatch(colors.swatch(role, choice), colorsEnabled) },
                    ) {
                        SettingLabel(
                            title = stringResource(role.title),
                            summary = stringResource(choice.effectiveSource.title),
                            enabled = colorsEnabled,
                        )
                    }
                }
            }
            GroupedList {
                item(
                    modifier =
                        Modifier.toggleable(
                            value = state.eink,
                            enabled = state.enabled,
                            role = Role.Switch,
                            onValueChange = onEinkChange,
                        ),
                    trailingContent = { Switch(checked = state.eink, enabled = state.enabled, onCheckedChange = null) },
                ) {
                    SettingLabel(
                        title = stringResource(CommonString.day_theme_eink),
                        summary = stringResource(R.string.dev_theme_eink_summary),
                        enabled = state.enabled,
                    )
                }
            }
        }
    }

    if (choosingScheme) {
        ThemeChoiceDialog(
            title = stringResource(R.string.dev_theme_color_scheme),
            options = AppTheme.entries.map { ThemeOption(it, stringResource(it.label)) },
            selected = state.scheme,
            onChoose = { scheme ->
                choosingScheme = false
                if (scheme != AppTheme.FOLLOW_SYSTEM) editingDark = scheme == AppTheme.NIGHT
                onSchemeChange(scheme)
            },
            onDismiss = { choosingScheme = false },
        )
    }
    choosingColor?.let { role ->
        val choice = choices.getValue(role)
        ThemeChoiceDialog(
            title = stringResource(role.title),
            options =
                ThemeColorSource.entries.map { source ->
                    ThemeOption(
                        value = source,
                        label = stringResource(source.title),
                        unavailableSummary = if (source.isAvailable) null else stringResource(source.unavailableSummary),
                    )
                },
            selected = choice.effectiveSource,
            onChoose = { source ->
                choosingColor = null
                onColorSourceChange(dark, role, source, colors.swatch(role, choice))
            },
            onDismiss = { choosingColor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeColorTabs(
    dark: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    SecondaryTabRow(
        selectedTabIndex = if (dark) 1 else 0,
        modifier = Modifier.alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA),
        containerColor = Color.Transparent,
    ) {
        for (mode in listOf(AppTheme.DAY, AppTheme.NIGHT)) {
            val darkTab = mode == AppTheme.NIGHT
            LeadingIconTab(
                selected = dark == darkTab,
                onClick = { onSelect(darkTab) },
                modifier = Modifier.heightIn(min = 56.dp),
                enabled = enabled,
                text = { Text(stringResource(mode.label)) },
                icon = {
                    Icon(
                        painterResource(if (darkTab) R.drawable.ic_dark_mode_24 else R.drawable.ic_light_mode_24),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

private val AppTheme.label: Int
    @StringRes get() =
        when (this) {
            AppTheme.FOLLOW_SYSTEM -> CommonString.theme_follow_system
            AppTheme.DAY -> CommonString.day_theme_light
            AppTheme.NIGHT -> CommonString.night_theme_dark
        }

@Composable
private fun SettingLabel(
    title: String,
    summary: String?,
    enabled: Boolean,
) {
    Column(modifier = Modifier.alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColorSwatch(
    @ColorInt color: Int,
    enabled: Boolean,
) {
    AndroidView(
        factory = { context ->
            View(context).apply {
                background = AppCompatResources.getDrawable(context, R.drawable.brush_preview_background)?.mutate()
            }
        },
        modifier = Modifier.size(24.dp).alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA),
        update = { view ->
            val fill = (view.background as LayerDrawable).findDrawableByLayerId(R.id.brush_preview_fill) as GradientDrawable
            fill.setColor(color)
        },
    )
}

private data class ThemeOption<T>(
    val value: T,
    val label: String,
    val unavailableSummary: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ThemeChoiceDialog(
    title: String,
    options: List<ThemeOption<T>>,
    selected: T,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AnkiDroidDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            shadowElevation = ankiDroidDialogElevation(),
        ) {
            Column(Modifier.padding(ankiDroidDialogContentPadding())) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    for (option in options) {
                        val checked = option.value == selected
                        val enabled = option.unavailableSummary == null
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = checked, enabled = enabled, role = Role.RadioButton) { onChoose(option.value) }
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = checked, enabled = enabled, onClick = null)
                            SettingLabel(option.label, option.unavailableSummary, enabled)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(CommonString.dialog_cancel))
                }
            }
        }
    }
}
