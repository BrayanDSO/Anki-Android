// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.themes

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.themedContext
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

internal enum class ThemeColorSource(
    @StringRes val title: Int,
    @StringRes val unavailableSummary: Int = 0,
) {
    ANKIDROID(R.string.dev_theme_ankidroid_color),
    DYNAMIC(R.string.dev_theme_dynamic_color, R.string.dev_theme_dynamic_unavailable),
    CUSTOM(R.string.dev_theme_custom_color, R.string.dev_theme_custom_unavailable),
    ;

    val isAvailable: Boolean
        get() =
            when (this) {
                ANKIDROID -> true
                DYNAMIC -> DynamicColors.isDynamicColorAvailable()
                CUSTOM -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            }
}

internal data class ThemeColorChoice(
    val source: ThemeColorSource = ThemeColorSource.ANKIDROID,
    @ColorInt val customColor: Int? = null,
) {
    val effectiveSource: ThemeColorSource
        get() =
            if (source.isAvailable && (source != ThemeColorSource.CUSTOM || customColor != null)) {
                source
            } else {
                ThemeColorSource.ANKIDROID
            }
}

internal enum class ThemeColorRole(
    @StringRes val title: Int,
    @StringRes private val lightKey: Int,
    @StringRes private val darkKey: Int,
    val attribute: Int,
) {
    MAIN(
        R.string.dev_theme_main_color,
        R.string.dev_theme_light_main_key,
        R.string.dev_theme_dark_main_key,
        AppCompatR.attr.colorPrimary,
    ),
    BACKGROUND(
        R.string.dev_theme_background_color,
        R.string.dev_theme_light_background_key,
        R.string.dev_theme_dark_background_key,
        MaterialR.attr.colorSurface,
    ),
    TEXT(
        R.string.dev_theme_text_color,
        R.string.dev_theme_light_text_key,
        R.string.dev_theme_dark_text_key,
        MaterialR.attr.colorOnSurface,
    ),
    ;

    @StringRes
    fun preferenceKey(dark: Boolean): Int = if (dark) darkKey else lightKey
}

/** The settings that affect an activity in its current light or dark mode. */
internal data class ActiveThemePreview(
    val scheme: AppTheme,
    val dark: Boolean,
    val eink: Boolean,
    val palette: Map<ThemeColorRole, ThemeColorChoice>,
)

internal fun Context.activeThemePreview(prefs: PrefsRepository = PrefsRepository(this)): ActiveThemePreview? {
    if (!prefs.isDeveloperOptionsEnabled || !prefs.devThemePreviewEnabled) return null
    val scheme = prefs.devThemeColorScheme
    val dark = scheme == AppTheme.NIGHT || (scheme == AppTheme.FOLLOW_SYSTEM && systemIsInNightMode(applicationContext))
    val eink = prefs.devThemeEink
    val palette =
        if (eink) {
            emptyMap()
        } else {
            ThemeColorRole.entries
                .mapNotNull { role ->
                    val choice = prefs.themeColor(dark, role)
                    when (choice.effectiveSource) {
                        ThemeColorSource.ANKIDROID -> null
                        ThemeColorSource.DYNAMIC -> role to ThemeColorChoice(ThemeColorSource.DYNAMIC)
                        ThemeColorSource.CUSTOM -> role to choice
                    }
                }.toMap()
        }
    return ActiveThemePreview(scheme, dark, eink, palette)
}

/** Resolves both swatches and overrides against the same unmodified light or dark theme. */
internal class ThemePreviewColors(
    context: Context,
    dark: Boolean,
) {
    private val base = (if (dark) NightTheme.DARK else DayTheme.LIGHT).themedContext(context.applicationContext)
    private val dynamic by lazy { DynamicColors.wrapContextIfAvailable(base) }

    @ColorInt
    fun swatch(
        role: ThemeColorRole,
        choice: ThemeColorChoice,
    ): Int = color(role, choice, role.attribute)

    @ColorInt
    private fun color(
        role: ThemeColorRole,
        choice: ThemeColorChoice,
        attribute: Int,
    ): Int =
        when (choice.effectiveSource) {
            ThemeColorSource.ANKIDROID -> MaterialColors.getColor(base, attribute, Color.TRANSPARENT)
            ThemeColorSource.DYNAMIC ->
                MaterialColors.getColor(
                    dynamic,
                    attribute,
                    MaterialColors.getColor(base, role.attribute, Color.TRANSPARENT),
                )
            ThemeColorSource.CUSTOM -> requireNotNull(choice.customColor)
        }

    fun applyTo(
        activity: Activity,
        palette: Map<ThemeColorRole, ThemeColorChoice>,
    ) {
        if (palette.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val colors = mutableMapOf<Int, Int>()
        val styles = palette.map { (role, choice) -> addRoleColors(colors, role, choice) }
        if (activity.installThemeColorOverrides(colors)) {
            styles.forEach { activity.theme.applyStyle(it, true) }
        }
    }

    private fun addRoleColors(
        colors: MutableMap<Int, Int>,
        role: ThemeColorRole,
        choice: ThemeColorChoice,
    ): Int =
        when (role) {
            ThemeColorRole.MAIN -> {
                val primary = swatch(role, choice)
                colors[R.color.theme_preview_primary] = primary
                colors[R.color.theme_preview_on_primary] =
                    if (choice.effectiveSource == ThemeColorSource.CUSTOM) {
                        if (ColorUtils.calculateLuminance(primary) > 0.179) Color.BLACK else Color.WHITE
                    } else {
                        color(role, choice, MaterialR.attr.colorOnPrimary)
                    }
                R.style.ThemeOverlay_AnkiDroid_PreviewMain
            }
            ThemeColorRole.BACKGROUND -> {
                for ((resource, attribute) in surfaceAttributes) {
                    colors[resource] = color(role, choice, attribute)
                }
                R.style.ThemeOverlay_AnkiDroid_PreviewBackground
            }
            ThemeColorRole.TEXT -> {
                colors[R.color.theme_preview_on_surface] = swatch(role, choice)
                colors[R.color.theme_preview_on_surface_variant] = color(role, choice, MaterialR.attr.colorOnSurfaceVariant)
                colors[R.color.theme_preview_on_background] = color(role, choice, MaterialR.attr.colorOnBackground)
                R.style.ThemeOverlay_AnkiDroid_PreviewText
            }
        }

    private companion object {
        val surfaceAttributes =
            mapOf(
                R.color.theme_preview_surface to MaterialR.attr.colorSurface,
                R.color.theme_preview_surface_dim to MaterialR.attr.colorSurfaceDim,
                R.color.theme_preview_surface_bright to MaterialR.attr.colorSurfaceBright,
                R.color.theme_preview_surface_variant to MaterialR.attr.colorSurfaceVariant,
                R.color.theme_preview_surface_lowest to MaterialR.attr.colorSurfaceContainerLowest,
                R.color.theme_preview_surface_low to MaterialR.attr.colorSurfaceContainerLow,
                R.color.theme_preview_surface_container to MaterialR.attr.colorSurfaceContainer,
                R.color.theme_preview_surface_high to MaterialR.attr.colorSurfaceContainerHigh,
                R.color.theme_preview_surface_highest to MaterialR.attr.colorSurfaceContainerHighest,
                R.color.theme_preview_background to android.R.attr.colorBackground,
            )
    }
}
