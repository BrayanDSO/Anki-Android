// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.themes.ThemeColorRole
import com.ichi2.themes.ThemeColorSource
import com.ichi2.themes.activeThemePreview

class ThemeSettingsFragment : Fragment() {
    private var colorPicker: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val activity = requireActivity()
        val prefs = PrefsRepository(activity)
        return ComposeView(activity).apply {
            id = R.id.dev_theme_settings_compose
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var state by remember { mutableStateOf(prefs.themeSettingsState()) }

                fun save(change: PrefsRepository.() -> Unit) {
                    val previousTheme = activity.activeThemePreview(prefs)
                    prefs.change()
                    state = prefs.themeSettingsState()
                    if (activity.activeThemePreview(prefs) != previousTheme) {
                        ActivityCompat.recreate(activity)
                    }
                }

                AnkiDroidTheme {
                    ThemeSettingsScreen(
                        state = state,
                        systemIsDark = systemIsInNightMode(activity.applicationContext),
                        onPreviewChange = { save { devThemePreviewEnabled = it } },
                        onSchemeChange = { save { devThemeColorScheme = it } },
                        onEinkChange = { save { devThemeEink = it } },
                        onColorSourceChange = { dark, role, source, swatch ->
                            val choice = prefs.themeColor(dark, role)
                            if (source == ThemeColorSource.CUSTOM) {
                                colorPicker =
                                    activity.showColorPickerDialog(
                                        initialColor = choice.customColor ?: swatch,
                                        allowTransparency = false,
                                    ) { picked ->
                                        save {
                                            setThemeColor(
                                                dark,
                                                role,
                                                choice.copy(source = source, customColor = ColorUtils.setAlphaComponent(picked, 255)),
                                            )
                                        }
                                    }
                            } else {
                                save { setThemeColor(dark, role, choice.copy(source = source)) }
                            }
                        },
                        onNavigateUp = { activity.onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        colorPicker?.dismiss()
        colorPicker = null
        super.onDestroyView()
    }
}

private fun PrefsRepository.themeSettingsState() =
    ThemeSettingsState(
        enabled = devThemePreviewEnabled,
        scheme = devThemeColorScheme,
        eink = devThemeEink,
        lightColors = ThemeColorRole.entries.associateWith { themeColor(dark = false, it) },
        darkColors = ThemeColorRole.entries.associateWith { themeColor(dark = true, it) },
    )
