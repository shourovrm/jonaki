package app.jonaki.feature.chat

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ContextRing
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat

/**
 * The status strip above the message field (D-027): model, context window,
 * share of it in use, this thread's cost and its web search switch (D-123).
 * Each pill is labelled for screen readers; Settings has a page that
 * explains the icons.
 */
@Composable
internal fun StatusStrip(
    status: ChatStatusUi,
    isRunning: Boolean,
    onModelClick: () -> Unit,
    onCostClick: (() -> Unit)?,
    webSearchEnabled: Boolean,
    onWebSearchChange: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the context sheet (D-081); null leaves the ring pill without a tap. */
    onContextClick: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
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
            Pill(description = stringResource(R.string.chat_status_context_used, percent), onClick = onContextClick) {
                ContextRing(percent)
                PillNumber("$percent%")
            }
        }
        if (status.bypassApprovals) {
            BypassPill()
        }
        val costText = UsageFormat.cost(status.costUsd)
        Pill(description = stringResource(R.string.chat_status_cost, costText), onClick = onCostClick) {
            Icon(JonakiIcons.Payments, contentDescription = null, modifier = Modifier.size(PillIconSize))
            PillNumber(costText)
        }
        WebSearchPill(webSearchEnabled, onWebSearchChange)
    }
}

private val PillIconSize = 16.dp

private val PillHeight = 30.dp

private val PillShape = RoundedCornerShape(15.dp)

@Composable
private fun WebSearchPill(webSearchEnabled: Boolean, onWebSearchChange: (Boolean) -> Unit) {
    val pill = WebSearchPillState.of(webSearchEnabled)
    Pill(
        description = stringResource(pill.descriptionResource),
        onClick = { onWebSearchChange(pill.enabledAfterTap) },
    ) {
        if (pill.crossedOut) {
            CrossedOutGlobe()
        } else {
            Icon(JonakiIcons.Globe, contentDescription = null, modifier = Modifier.size(PillIconSize))
        }
    }
}

/** The globe in the muted colour with a slash; the slash's dark edge keeps it apart from the globe's lines. */
@Composable
private fun CrossedOutGlobe() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pillColour = MaterialTheme.colorScheme.surfaceContainer
    Icon(
        JonakiIcons.Globe,
        contentDescription = null,
        tint = muted,
        modifier = Modifier
            .size(PillIconSize)
            .drawWithContent {
                drawContent()
                val start = Offset(size.width * 0.08f, size.height * 0.08f)
                val end = Offset(size.width * 0.92f, size.height * 0.92f)
                drawLine(pillColour, start, end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(muted, start, end, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            },
    )
}

@Composable
private fun RowScope.ModelPill(modelName: String, isRunning: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.chat_status_model, modelName)
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = JonakiTheme.colors.inkSoft,
        // The model name gives way first, so the numbers always fit on a 360 dp phone.
        modifier = Modifier
            .weight(1f, fill = false)
            .height(PillHeight)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
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
    val pillModifier = Modifier.height(PillHeight).clearAndSetSemantics {
        contentDescription = description
        if (onClick != null) {
            role = Role.Button
        }
    }
    val body: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp),
            content = content,
        )
    }
    val color = MaterialTheme.colorScheme.surfaceContainer
    val contentColor = JonakiTheme.colors.inkSoft
    val shape = PillShape
    if (onClick == null) {
        Surface(shape = shape, color = color, contentColor = contentColor, modifier = pillModifier, content = body)
    } else {
        Surface(onClick = onClick, shape = shape, color = color, contentColor = contentColor, modifier = pillModifier, content = body)
    }
}

/** Drawn in the warning colour, so a thread where nothing asks is never mistaken for one that does. */
@Composable
private fun BypassPill() {
    val deny = JonakiTheme.colors.deny
    val description = stringResource(R.string.chat_status_bypass_description)
    Surface(
        shape = PillShape,
        color = deny.copy(alpha = 0.14f),
        contentColor = deny,
        modifier = Modifier.height(PillHeight).clearAndSetSemantics {
            contentDescription = description
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(PillIconSize))
            Text(stringResource(R.string.chat_status_bypass), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
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
