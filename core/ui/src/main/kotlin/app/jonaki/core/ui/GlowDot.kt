package app.jonaki.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DotStyle {
    /** Filled, no light: finished or idle. */
    QUIET,

    /** Filled in the live colour with a breathing glow: work in progress. */
    GLOWING,

    /** A ring: waiting for the user. */
    RING,
}

/**
 * The firefly mark (D-024). The canvas is larger than the dot so the glow has
 * room; [dotSize] is the visible dot.
 */
@Composable
fun GlowDot(color: Color, style: DotStyle, modifier: Modifier = Modifier, dotSize: Dp = 10.dp) {
    val glowColor = JonakiTheme.colors.glow
    val glowStrength by if (style == DotStyle.GLOWING) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "glowStrength",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier.size(dotSize * 2.4f)) {
        val radius = dotSize.toPx() / 2
        val centre = Offset(size.width / 2, size.height / 2)
        when (style) {
            DotStyle.GLOWING -> {
                val glowRadius = size.minDimension / 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = glowColor.alpha * glowStrength), Color.Transparent),
                        center = centre,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = centre,
                )
                drawCircle(color, radius, centre)
            }
            DotStyle.QUIET -> drawCircle(color, radius, centre)
            DotStyle.RING -> drawCircle(color, radius - 1.dp.toPx(), centre, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
