// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.themes

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot
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
    @ColorRes val swatchResource: Int,
    @StyleRes val overlay: Int,
) {
    MAIN(
        R.string.dev_theme_main_color,
        R.string.dev_theme_light_main_key,
        R.string.dev_theme_dark_main_key,
        R.color.theme_preview_primary,
        R.style.ThemeOverlay_AnkiDroid_PreviewMain,
    ),
    BACKGROUND(
        R.string.dev_theme_background_color,
        R.string.dev_theme_light_background_key,
        R.string.dev_theme_dark_background_key,
        R.color.theme_preview_surface,
        R.style.ThemeOverlay_AnkiDroid_PreviewBackground,
    ),
    FOREGROUND(
        R.string.dev_theme_foreground_color,
        R.string.dev_theme_light_text_key,
        R.string.dev_theme_dark_text_key,
        R.color.theme_preview_on_surface,
        R.style.ThemeOverlay_AnkiDroid_PreviewForeground,
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
    private val dark: Boolean,
) {
    private val base = (if (dark) NightTheme.DARK else DayTheme.LIGHT).themedContext(context.applicationContext)
    private val dynamic by lazy { DynamicColors.wrapContextIfAvailable(base) }

    @ColorInt
    fun swatch(
        role: ThemeColorRole,
        choice: ThemeColorChoice,
    ): Int =
        when (choice.effectiveSource) {
            ThemeColorSource.ANKIDROID -> defaultSwatch(role)
            ThemeColorSource.DYNAMIC ->
                MaterialColors.getColor(
                    dynamic,
                    attributes.getValue(role).getValue(role.swatchResource),
                    defaultSwatch(role),
                )
            ThemeColorSource.CUSTOM -> customThemeColors(role, requireNotNull(choice.customColor), dark).getValue(role.swatchResource)
        }

    @ColorInt
    private fun defaultSwatch(role: ThemeColorRole): Int =
        MaterialColors.getColor(base, attributes.getValue(role).getValue(role.swatchResource), Color.TRANSPARENT)

    fun applyTo(
        activity: Activity,
        palette: Map<ThemeColorRole, ThemeColorChoice>,
    ) {
        if (palette.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val colors = mutableMapOf<Int, Int>()
        val styles = palette.mapNotNull { (role, choice) -> addRoleColors(colors, role, choice) }
        if (activity.installThemeColorOverrides(colors)) {
            styles.forEach { activity.theme.applyStyle(it, true) }
        }
    }

    private fun addRoleColors(
        colors: MutableMap<Int, Int>,
        role: ThemeColorRole,
        choice: ThemeColorChoice,
    ): Int? {
        when (choice.effectiveSource) {
            ThemeColorSource.ANKIDROID -> return null
            ThemeColorSource.DYNAMIC -> {
                val fallback = defaultSwatch(role)
                for ((resource, attribute) in attributes.getValue(role)) {
                    colors[resource] = MaterialColors.getColor(dynamic, attribute, fallback)
                }
            }
            ThemeColorSource.CUSTOM -> {
                colors.putAll(customThemeColors(role, requireNotNull(choice.customColor), dark))
                if (role == ThemeColorRole.MAIN) return R.style.ThemeOverlay_AnkiDroid_PreviewCustomMain
            }
        }
        return role.overlay
    }

    private companion object {
        val attributes =
            mapOf(
                ThemeColorRole.MAIN to
                    mapOf(
                        R.color.theme_preview_primary to AppCompatR.attr.colorPrimary,
                        R.color.theme_preview_on_primary to MaterialR.attr.colorOnPrimary,
                    ),
                ThemeColorRole.BACKGROUND to
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
                    ),
                ThemeColorRole.FOREGROUND to
                    mapOf(
                        R.color.theme_preview_on_surface to MaterialR.attr.colorOnSurface,
                        R.color.theme_preview_on_surface_variant to MaterialR.attr.colorOnSurfaceVariant,
                        R.color.theme_preview_on_background to MaterialR.attr.colorOnBackground,
                    ),
            )
    }
}

// Keep the Material Components color utilities behind this resolver.
@SuppressLint("RestrictedApi")
private fun customThemeColors(
    role: ThemeColorRole,
    @ColorInt seed: Int,
    dark: Boolean,
): Map<Int, Int> {
    val seedColor = Hct.fromInt(seed)
    val scheme =
        if (Color.red(seed) == Color.green(seed) && Color.green(seed) == Color.blue(seed)) {
            SchemeMonochrome(seedColor, dark, 0.0)
        } else {
            SchemeTonalSpot(seedColor, dark, 0.0)
        }
    val colors = MaterialDynamicColors()
    return when (role) {
        ThemeColorRole.MAIN ->
            mapOf(
                R.color.theme_preview_primary to colors.primary().getArgb(scheme),
                R.color.theme_preview_on_primary to colors.onPrimary().getArgb(scheme),
                R.color.theme_preview_primary_container to colors.primaryContainer().getArgb(scheme),
                R.color.theme_preview_on_primary_container to colors.onPrimaryContainer().getArgb(scheme),
            )
        ThemeColorRole.BACKGROUND -> {
            val surface = colors.surface().getArgb(scheme)
            mapOf(
                R.color.theme_preview_surface to surface,
                R.color.theme_preview_surface_dim to colors.surfaceDim().getArgb(scheme),
                R.color.theme_preview_surface_bright to colors.surfaceBright().getArgb(scheme),
                R.color.theme_preview_surface_variant to colors.surfaceVariant().getArgb(scheme),
                R.color.theme_preview_surface_lowest to colors.surfaceContainerLowest().getArgb(scheme),
                R.color.theme_preview_surface_low to colors.surfaceContainerLow().getArgb(scheme),
                R.color.theme_preview_surface_container to colors.surfaceContainer().getArgb(scheme),
                R.color.theme_preview_surface_high to colors.surfaceContainerHigh().getArgb(scheme),
                R.color.theme_preview_surface_highest to colors.surfaceContainerHighest().getArgb(scheme),
                R.color.theme_preview_background to surface,
            )
        }
        ThemeColorRole.FOREGROUND ->
            mapOf(
                R.color.theme_preview_on_surface to colors.onSurface().getArgb(scheme),
                R.color.theme_preview_on_surface_variant to colors.onSurfaceVariant().getArgb(scheme),
                R.color.theme_preview_on_background to colors.onBackground().getArgb(scheme),
            )
    }
}
