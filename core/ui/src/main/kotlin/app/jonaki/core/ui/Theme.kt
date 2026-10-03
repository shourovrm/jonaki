package app.jonaki.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Roles Material 3 has no slot for (D-024, D-123). Only work in progress
 * gives off light: [live] and [glow] are reserved for running threads and
 * the running step.
 */
@Immutable
data class JonakiColors(
    /** The firefly dot of a running thread or step. */
    val live: Color,
    /** The halo around [live]. */
    val glow: Color,
    val done: Color,
    val deny: Color,
    /** The step track's rail, and the fill of a finished station. */
    val track: Color,
    val stop: Color,
    val onStop: Color,
    /** Secondary text that must stay readable on every surface: labels, step names, pill text. */
    val inkSoft: Color,
    val userBubble: Color,
    /** The run block's panel; transparent at night, where its border alone frames it. */
    val runPanel: Color,
    val selectedChip: Color,
    val onSelectedChip: Color,
)

// The Leaf palette of revamp direction A "Lantern": docs/mockups/revamp-a-themes.html.
// Token names from the mockup are noted where they differ from Material's.
private val darkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC9E86B), // accent
    onPrimary = Color(0xFF1F2A00), // on-accent
    primaryContainer = Color(0xFF34420F),
    onPrimaryContainer = Color(0xFFE3F3A8),
    secondary = Color(0xFFC3C9B9), // ink-2
    onSecondary = Color(0xFF10130E),
    secondaryContainer = Color(0xFF2A3122),
    onSecondaryContainer = Color(0xFFDDE4CF),
    tertiary = Color(0xFFA0CFC0),
    onTertiary = Color(0xFF05372D),
    background = Color(0xFF10130E), // bg
    onBackground = Color(0xFFE6E9E0), // ink
    surface = Color(0xFF10130E),
    onSurface = Color(0xFFE6E9E0),
    surfaceVariant = Color(0xFF262B21),
    onSurfaceVariant = Color(0xFF8B927F), // muted
    surfaceTint = Color(0xFFC9E86B),
    inverseSurface = Color(0xFFE6E9E0),
    inverseOnSurface = Color(0xFF10130E),
    inversePrimary = Color(0xFF3F5600),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6C7462),
    outlineVariant = Color(0xFF262B21), // line
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF33392B),
    surfaceDim = Color(0xFF10130E),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF151912),
    surfaceContainer = Color(0xFF1A1E16), // field
    surfaceContainerHigh = Color(0xFF20261A), // user bubble
    surfaceContainerHighest = Color(0xFF2A3024),
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F5600), // accent
    onPrimary = Color(0xFFF3F6EF), // on-accent
    primaryContainer = Color(0xFFD6EA92),
    onPrimaryContainer = Color(0xFF172000),
    secondary = Color(0xFF3A4233), // ink-2
    onSecondary = Color(0xFFF3F6EF),
    secondaryContainer = Color(0xFFDCE4CE),
    onSecondaryContainer = Color(0xFF1C2410),
    tertiary = Color(0xFF39665A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6EF), // bg
    onBackground = Color(0xFF171B14), // ink
    surface = Color(0xFFF3F6EF),
    onSurface = Color(0xFF171B14),
    surfaceVariant = Color(0xFFDDE3D5),
    onSurfaceVariant = Color(0xFF5F6857), // muted
    surfaceTint = Color(0xFF3F5600),
    inverseSurface = Color(0xFF2C3127),
    inverseOnSurface = Color(0xFFF3F6EF),
    inversePrimary = Color(0xFFC9E86B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7C8572),
    outlineVariant = Color(0xFFDDE3D5), // line
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF3F6EF),
    surfaceDim = Color(0xFFD5DBCC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2E8),
    surfaceContainer = Color(0xFFE7ECE1), // field
    surfaceContainerHigh = Color(0xFFE2E9D6), // user bubble
    surfaceContainerHighest = Color(0xFFDBE2D0),
)

private val darkExtras = JonakiColors(
    live = Color(0xFFD4F06A), // glow
    glow = Color(0x55C9E86B), // glow-halo
    done = Color(0xFF8B927F),
    deny = Color(0xFFF2B8A9),
    track = Color(0xFF33392B), // rail
    stop = Color(0xFF1A1E16),
    onStop = Color(0xFFE6E9E0),
    inkSoft = Color(0xFFC3C9B9),
    userBubble = Color(0xFF20261A),
    runPanel = Color.Transparent,
    selectedChip = Color(0xFFE6E9E0),
    onSelectedChip = Color(0xFF10130E),
)

private val lightExtras = JonakiColors(
    // The mockup's #9CC520 is 1.85:1 on the day background, below the 3:1 a status mark
    // needs; the dot is darkened to 3.17:1 and the mockup's colour stays as its halo.
    live = Color(0xFF729600),
    glow = Color(0x669CC520),
    done = Color(0xFF5F6857),
    deny = Color(0xFFA1361C),
    track = Color(0xFFD3DBC8), // rail
    stop = Color(0xFFE7ECE1),
    onStop = Color(0xFF171B14),
    inkSoft = Color(0xFF3A4233),
    userBubble = Color(0xFFE2E9D6),
    runPanel = Color(0xFFFFFFFF),
    selectedChip = Color(0xFF3F5600),
    onSelectedChip = Color(0xFFF3F6EF),
)

private val jonakiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Geist and Geist Mono (SIL Open Font License, core/ui/OFL-Geist.txt) as variable
// fonts: one file per family holds every weight, about half the size of four static
// files. Variation settings are applied on API 26+, which is minSdk. Bangla falls back
// to the system font.
@OptIn(ExperimentalTextApi::class)
private fun geistWeight(fontResource: Int, weight: Int): Font = Font(
    resId = fontResource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GeistFamily: FontFamily = FontFamily(
    geistWeight(R.font.geist, 400),
    geistWeight(R.font.geist, 500),
    geistWeight(R.font.geist, 600),
    geistWeight(R.font.geist, 700),
)

/** Geist Mono, for numbers (costs, times, token counts), paths and code. */
val MonospaceFamily: FontFamily = FontFamily(
    geistWeight(R.font.geist_mono, 400),
    geistWeight(R.font.geist_mono, 500),
    geistWeight(R.font.geist_mono, 600),
    geistWeight(R.font.geist_mono, 700),
)

/** Material's sizes in Geist. Geist is drawn for tight setting, so Material's added tracking goes. */
private fun geist(style: TextStyle, letterSpacingEm: Float = 0f): TextStyle =
    style.copy(fontFamily = GeistFamily, letterSpacing = letterSpacingEm.em)

private val jonakiTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = geist(base.displayLarge, -0.02f),
        displayMedium = geist(base.displayMedium, -0.02f),
        displaySmall = geist(base.displaySmall, -0.02f),
        headlineLarge = geist(base.headlineLarge, -0.02f),
        headlineMedium = geist(base.headlineMedium, -0.015f),
        headlineSmall = geist(base.headlineSmall, -0.01f),
        titleLarge = geist(base.titleLarge, -0.01f),
        titleMedium = geist(base.titleMedium),
        titleSmall = geist(base.titleSmall),
        bodyLarge = geist(base.bodyLarge).copy(lineHeight = 24.sp),
        bodyMedium = geist(base.bodyMedium).copy(lineHeight = 21.sp),
        bodySmall = geist(base.bodySmall),
        labelLarge = geist(base.labelLarge),
        labelMedium = geist(base.labelMedium),
        labelSmall = geist(base.labelSmall),
    )
}

private val LocalJonakiColors = staticCompositionLocalOf { darkExtras }

object JonakiTheme {
    val colors: JonakiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJonakiColors.current

    val codeColors: CodeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCodeColors.current
}

@Composable
fun JonakiTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = resolvesToDark(themeMode)
    val extras = if (dark) darkExtras else lightExtras
    val codeColors = if (dark) darkCodeColors else lightCodeColors
    CompositionLocalProvider(LocalJonakiColors provides extras, LocalCodeColors provides codeColors) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            shapes = jonakiShapes,
            typography = jonakiTypography,
            content = content,
        )
    }
}

/** True when the theme resolved to dark; screens use it for system bar icons. */
@Composable
fun resolvesToDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
