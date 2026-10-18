/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.core.AppSettings

/**
 * A deliberately dark, low-chroma palette.
 *
 * This is a living-room app: it is looked at in a dark room, from three metres away, often
 * for hours. Bright surfaces and saturated accents that read well on a phone in daylight are
 * actively unpleasant on a 55" panel at night, so everything here is anchored near-black with
 * a single restrained accent used only for focus and selection.
 */
private val Accent = Color(0xFF7C93FF)
private val AccentDim = Color(0xFF4B5DB8)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0A0C14),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFE6EAFF),
    secondary = Color(0xFF9AA3C0),
    background = Color(0xFF07080C),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0D0F16),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF171A24),
    onSurfaceVariant = Color(0xFFA8AEC0),
    outline = Color(0xFF2A2F3D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
)

/**
 * Light mode for anyone who wants it. By default TV boxes stay dark (a white living-room
 * screen at night is nobody's friend) — that default lives at the call site, so a user who
 * explicitly picks Light in settings gets it on any device.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3B4FCC),
    onPrimary = Color.White,
    background = Color(0xFFFBFBFE),
    onBackground = Color(0xFF13141A),
    surface = Color.White,
    onSurface = Color(0xFF13141A),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF4A4F60),
    outline = Color(0xFFD3D7E2),
)

/*
 * The OwnTV style: OwnTV's teal-seeded Material 3 tonal palette over a near-black with a green
 * undertone (#040E0B), so focus and selection pop against deep surfaces. Values copied from
 * OwnTV's OwnTVPalette (GPLv3, like OpenTV) with the default teal accent.
 */
private val OwnTvDarkScheme = darkColorScheme(
    primary = Color(0xFF52DBC8),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004F46),
    onPrimaryContainer = Color(0xFF6FF8E4),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFA9CBE4),
    onTertiary = Color(0xFF0B3445),
    tertiaryContainer = Color(0xFF294B5D),
    onTertiaryContainer = Color(0xFFC5E7FF),
    background = Color(0xFF040E0B),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF252B29),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
)

private val OwnTvLightScheme = lightColorScheme(
    primary = Color(0xFF006B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF8E4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4B635C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DF),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF416278),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5E7FF),
    onTertiaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF5FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFE3EAE6),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E1),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
    error = Color(0xFFBA1A1A),
)

/**
 * Plus Jakarta Sans (SIL OFL, see docs/font-licenses), the OwnTV typeface. Static weights rather
 * than the variable font: variable weights need API 26 and the old Fire TV sticks run 25.
 */
private val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** Scaled up from the Material defaults: ten-foot viewing needs bigger text than a phone. */
private fun openTvTypography(family: FontFamily = FontFamily.Default): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = TextStyle(fontFamily = family, fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontFamily = family, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontFamily = family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = family, fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = family, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp),
        labelLarge = TextStyle(fontFamily = family, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        // Roles left at Material defaults still take the style's typeface.
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

private val ClassicTypography = openTvTypography()
private val OwnTvTypography = openTvTypography(PlusJakartaSans)

@Composable
fun OpenTvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    style: AppSettings.AppStyle = AppSettings.AppStyle.OWNTV,
    content: @Composable () -> Unit,
) {
    val ownTv = style == AppSettings.AppStyle.OWNTV
    MaterialTheme(
        colorScheme = when {
            ownTv && darkTheme -> OwnTvDarkScheme
            ownTv -> OwnTvLightScheme
            darkTheme -> DarkScheme
            else -> LightScheme
        },
        typography = if (ownTv) OwnTvTypography else ClassicTypography,
        content = content,
    )
}
