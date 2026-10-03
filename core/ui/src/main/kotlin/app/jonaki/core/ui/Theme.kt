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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Roles Material 3 has no slot for (D-024). Only work in progress gives off
 * light: [live] and [glow] are reserved for running threads and steps.
 */
@Immutable
data class JonakiColors(
    val live: Color,
    val glow: Color,
    val done: Color,
    val deny: Color,
    val track: Color,
    val stop: Color,
    val onStop: Color,
)

// Palettes copied from the Firefly direction in docs/mockups/index.html.
private val darkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC9E86B),
    onPrimary = Color(0xFF263400),
    primaryContainer = Color(0xFF3A4A12),
    onPrimaryContainer = Color(0xFFE6F7B0),
    secondary = Color(0xFFBFCBA4),
    onSecondary = Color(0xFF2A3318),
    secondaryContainer = Color(0xFF333B27),
    onSecondaryContainer = Color(0xFFDDE6C8),
    tertiary = Color(0xFFA0CFC0),
    onTertiary = Color(0xFF05372D),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE4E8DC),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE4E8DC),
    surfaceVariant = Color(0xFF2C3227),
    onSurfaceVariant = Color(0xFFA9B19D),
    surfaceTint = Color(0xFFC9E86B),
    inverseSurface = Color(0xFFE4E8DC),
    inverseOnSurface = Color(0xFF2E3229),
    inversePrimary = Color(0xFF4B6300),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7A8370),
    outlineVariant = Color(0xFF33392D),
    surfaceBright = Color(0xFF373C32),
    surfaceDim = Color(0xFF11140F),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF161A13),
    surfaceContainer = Color(0xFF1A1E17),
    surfaceContainerHigh = Color(0xFF22271E),
    surfaceContainerHighest = Color(0xFF2C3227),
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4B6300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4EE7C),
    onPrimaryContainer = Color(0xFF161F00),
    secondary = Color(0xFF59624A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE6C9),
    onSecondaryContainer = Color(0xFF1C2410),
    tertiary = Color(0xFF39665A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8F0),
    onBackground = Color(0xFF1A1F12),
    surface = Color(0xFFF6F8F0),
    onSurface = Color(0xFF1A1F12),
    surfaceVariant = Color(0xFFDDE3D0),
    onSurfaceVariant = Color(0xFF50593F),
    surfaceTint = Color(0xFF4B6300),
    inverseSurface = Color(0xFF2E3229),
    inverseOnSurface = Color(0xFFF1F4E8),
    inversePrimary = Color(0xFFC9E86B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7A8468),
    outlineVariant = Color(0xFFCDD4BD),
    surfaceBright = Color(0xFFF6F8F0),
    surfaceDim = Color(0xFFD7DBCF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4EA),
    surfaceContainer = Color(0xFFEDF1E4),
    surfaceContainerHigh = Color(0xFFE6EBDB),
    surfaceContainerHighest = Color(0xFFDDE3D0),
)

private val darkExtras = JonakiColors(
    live = Color(0xFFD8F36A),
    glow = Color(0x8CD8F36A),
    done = Color(0xFF8D9A7C),
    deny = Color(0xFFF2B8A9),
    track = Color(0xFF33392D),
    stop = Color(0xFF3A4A12),
    onStop = Color(0xFFE6F7B0),
)

private val lightExtras = JonakiColors(
    live = Color(0xFF6A8A00),
    glow = Color(0x5996BE14),
    done = Color(0xFF7A8468),
    deny = Color(0xFFA1361C),
    track = Color(0xFFD6DCC6),
    stop = Color(0xFF1A1F12),
    onStop = Color(0xFFE6F7B0),
)

private val jonakiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Monospace for durations, paths and code; everything else uses the system face. */
val MonospaceFamily: FontFamily = FontFamily.Monospace

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
            typography = Typography(),
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
