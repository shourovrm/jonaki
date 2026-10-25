package app.jonaki.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

private const val MARK_WIDTH = 168f
private const val MARK_HEIGHT = 84f
private val fireflyCentre = Offset(132f, 22f)
private const val HALO_RADIUS = 20f
private const val DOT_RADIUS = 5.5f
private const val TRAIL_START_X = 8f

/**
 * What the middle of a thread shows before its first message: a firefly at
 * the end of a dotted flight curve, and "Hi!". The mark is drawn in the
 * running-thread colours, so the light theme gets its darker green.
 */
@Composable
internal fun EmptyChatGreeting(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(bottom = 48.dp),
    ) {
        FireflyTrail()
        Text(
            stringResource(R.string.chat_empty_greeting),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun FireflyTrail() {
    val fireflyColour = JonakiTheme.colors.live
    Canvas(Modifier.size(width = MARK_WIDTH.dp, height = MARK_HEIGHT.dp)) {
        // The mark is drawn in a 168 x 84 box, one unit per dp.
        val unit = size.width / MARK_WIDTH
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)

        val curve = Path().apply {
            moveTo(8f * unit, 70f * unit)
            cubicTo(28f * unit, 76f * unit, 40f * unit, 48f * unit, 62f * unit, 52f * unit)
            cubicTo(84f * unit, 56f * unit, 82f * unit, 24f * unit, 108f * unit, 30f * unit)
            cubicTo(118f * unit, 32f * unit, 126f * unit, 26f * unit, 132f * unit, 22f * unit)
        }
        drawPath(
            path = curve,
            brush = Brush.horizontalGradient(
                colors = listOf(fireflyColour.copy(alpha = 0f), fireflyColour.copy(alpha = 0.85f)),
                startX = TRAIL_START_X * unit,
                endX = fireflyCentre.x * unit,
            ),
            style = Stroke(
                width = 2f * unit,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * unit, 6f * unit)),
            ),
        )
        val centre = point(fireflyCentre.x, fireflyCentre.y)
        drawCircle(
            brush = Brush.radialGradient(
                0f to fireflyColour.copy(alpha = 0.55f),
                0.55f to fireflyColour.copy(alpha = 0.18f),
                1f to fireflyColour.copy(alpha = 0f),
                center = centre,
                radius = HALO_RADIUS * unit,
            ),
            radius = HALO_RADIUS * unit,
            center = centre,
        )
        drawCircle(color = fireflyColour, radius = DOT_RADIUS * unit, center = centre)
    }
}
