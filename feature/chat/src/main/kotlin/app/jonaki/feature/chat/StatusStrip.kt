package app.jonaki.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/**
 * The status strip above the message field (D-027): model, context window,
 * share of it in use, and this thread's cost. Each pill is labelled for
 * screen readers; Settings has a page that explains the icons.
 */
@Composable
internal fun StatusStrip(
    status: ChatStatusUi,
    isRunning: Boolean,
    onModelClick: () -> Unit,
    onCostClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        ModelPill(status.modelName, isRunning, onModelClick)
        val window = status.contextWindowTokens
        if (window != null) {
            val windowText = UsageFormat.tokenCount(window)
            Pill(description = stringResource(R.string.chat_status_context_window, windowText)) {
                Icon(JonakiIcons.Memory, contentDescription = null, modifier = Modifier.size(PillIconSize))
                PillNumber(windowText)
            }
            val percent = UsageFormat.percentUsed(status.contextUsedTokens, window)
            Pill(description = stringResource(R.string.chat_status_context_used, percent)) {
                ContextRing(percent)
                PillNumber("$percent%")
            }
        }
        val costText = UsageFormat.cost(status.costUsd)
        Pill(description = stringResource(R.string.chat_status_cost, costText), onClick = onCostClick) {
            Icon(JonakiIcons.Payments, contentDescription = null, modifier = Modifier.size(PillIconSize))
            PillNumber(costText)
        }
    }
}

private val PillIconSize = 16.dp

@Composable
private fun RowScope.ModelPill(modelName: String, isRunning: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.chat_status_model, modelName)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        // The model name gives way first, so the numbers always fit on a 360 dp phone.
        modifier = Modifier
            .weight(1f, fill = false)
            .height(28.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 2.dp)) {
            // The dot glows while this thread's agent works, like the thread list's dot.
            GlowDot(
                color = JonakiTheme.colors.live,
                style = if (isRunning) DotStyle.GLOWING else DotStyle.QUIET,
                dotSize = 8.dp,
            )
            Text(
                modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Pill(description: String, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val pillModifier = Modifier.height(28.dp).clearAndSetSemantics {
        contentDescription = description
        if (onClick != null) {
            role = Role.Button
        }
    }
    val body: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp),
            content = content,
        )
    }
    val color = MaterialTheme.colorScheme.surfaceContainer
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(14.dp)
    if (onClick == null) {
        Surface(shape = shape, color = color, contentColor = contentColor, modifier = pillModifier, content = body)
    } else {
        Surface(onClick = onClick, shape = shape, color = color, contentColor = contentColor, modifier = pillModifier, content = body)
    }
}

@Composable
private fun PillNumber(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
        maxLines = 1,
    )
}

/** A small gauge: the filled arc is the share of the context window in use. */
@Composable
internal fun ContextRing(percent: Int, modifier: Modifier = Modifier, ringSize: androidx.compose.ui.unit.Dp = 14.dp) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = ringColor(percent)
    Canvas(modifier.size(ringSize)) {
        val strokeWidth = 2.5.dp.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth))
        drawArc(
            fillColor,
            startAngle = -90f,
            sweepAngle = 360f * percent / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(strokeWidth),
        )
    }
}

/** The ring turns to the warning colour once the thread nears the point where it gets summarised. */
@Composable
private fun ringColor(percent: Int): Color =
    if (percent >= RING_WARNING_PERCENT) JonakiTheme.colors.deny else MaterialTheme.colorScheme.primary

private const val RING_WARNING_PERCENT = 80
