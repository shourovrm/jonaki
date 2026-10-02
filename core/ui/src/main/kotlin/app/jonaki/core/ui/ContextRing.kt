package app.jonaki.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small gauge whose filled arc is the share of the context window in use
 * (D-027). It turns to the warning colour once a thread nears the point where
 * it gets summarised. Used by the chat's status strip and the Settings icon guide.
 */
@Composable
fun ContextRing(percent: Int, modifier: Modifier = Modifier, ringSize: Dp = 14.dp) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = if (percent >= WARNING_PERCENT) JonakiTheme.colors.deny else MaterialTheme.colorScheme.primary
    Canvas(modifier.size(ringSize)) {
        val strokeWidth = ringSize.toPx() * STROKE_SHARE
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth))
        drawArc(
            fillColor,
            startAngle = -90f,
            sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokeWidth),
        )
    }
}

private const val WARNING_PERCENT = 80

/** Stroke width as a share of the ring's size, so a bigger ring keeps the same look. */
private const val STROKE_SHARE = 0.18f
