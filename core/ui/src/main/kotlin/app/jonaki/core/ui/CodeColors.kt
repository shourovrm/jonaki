package app.jonaki.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Syntax colours for the code viewer (D-090), one set per theme. They stay
 * off the firefly yellow-green, which D-024 keeps for live work. [warning]
 * is the amber of text a program printed to stderr.
 */
@Immutable
data class CodeColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val warning: Color,
)

internal val darkCodeColors = CodeColors(
    keyword = Color(0xFFCFA8EC),
    string = Color(0xFFA0CFC0),
    number = Color(0xFFF2B98A),
    comment = Color(0xFF8A937F),
    function = Color(0xFF9CC5EE),
    warning = Color(0xFFF0C25A),
)

internal val lightCodeColors = CodeColors(
    keyword = Color(0xFF7A3E9D),
    string = Color(0xFF2E6A5A),
    number = Color(0xFF9A4A14),
    comment = Color(0xFF5F6852),
    function = Color(0xFF285C8E),
    warning = Color(0xFF8A5A00),
)

internal val LocalCodeColors = staticCompositionLocalOf { darkCodeColors }
