package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.ContextRing
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat
import app.jonaki.core.ui.approvalModeLabel

/**
 * The status strip above the message field (D-027): context window with the
 * share of it in use, this thread's cost, then at the right the media button
 * (D-174), the web search switch (D-123) and the approval mode. The model is in
 * the top bar (D-176).
 * Each pill is labelled for screen readers; Settings has a page that
 * explains the icons.
 */
@Composable
internal fun StatusStrip(
    status: ChatStatusUi,
    onCostClick: (() -> Unit)?,
    webSearchEnabled: Boolean,
    onWebSearchChange: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the context sheet (D-081); null leaves the ring pill without a tap. */
    onContextClick: (() -> Unit)? = null,
    /** The thread's approval mode as it applies now: its own, else the default from Settings. */
    approvalMode: ApprovalModeChoice = ApprovalModeChoice.ASK,
    /** "Allow all in this thread" is on. */
    allowAllInThread: Boolean = false,
    onApprovalClick: () -> Unit = {},
    /** The media button and its menu (D-174); null when the thread can make no picture, vector image or video. */
    mediaMode: MediaModeUi? = null,
    onMediaModeChange: (selected: MediaKind?) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The context sheet shows the window's size too, so the pill can drop it on a narrow phone.
        val showsWindowSize = maxWidth >= WidthForWindowSize
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
        ) {
            val window = status.contextWindowTokens
            if (window != null) {
                // One pill for the window and its use: the strip has no room for the approval chip otherwise at 360 dp.
                val windowText = UsageFormat.tokenCount(window)
                val percent = UsageFormat.percentUsed(status.contextUsedTokens, window)
                Pill(description = stringResource(R.string.chat_status_context, percent, windowText), onClick = onContextClick) {
                    ContextRing(percent)
                    PillNumber("$percent%")
                    if (showsWindowSize) {
                        PillNumber("/$windowText")
                    }
                }
            }
            val costText = UsageFormat.cost(status.costUsd)
            Pill(description = stringResource(R.string.chat_status_cost, costText), onClick = onCostClick) {
                Icon(JonakiIcons.Payments, contentDescription = null, modifier = Modifier.size(PillIconSize))
                PillNumber(costText)
            }
            // The numbers sit at the left and the switches at the right.
            Spacer(Modifier.weight(1f))
            if (mediaMode != null) {
                MediaPill(mediaMode, onMediaModeChange)
            }
            WebSearchPill(webSearchEnabled, onWebSearchChange)
            ApprovalPill(approvalMode, allowAllInThread, onApprovalClick)
        }
    }
}

/**
 * One button for the three kinds of media. While the mode is off, a tap opens
 * a menu that names each kind's model and price; while a kind is on, the
 * button shows that kind in the accent colour and a tap switches it off.
 */
@Composable
private fun MediaPill(mediaMode: MediaModeUi, onChange: (selected: MediaKind?) -> Unit) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val selected = mediaMode.selected
    val isOn = selected != null
    val description = if (selected == null) {
        stringResource(R.string.chat_media_button)
    } else {
        stringResource(R.string.chat_media_button_on, stringResource(selected.labelRes))
    }
    Box {
        Surface(
            onClick = {
                if (selected != null) {
                    onChange(MediaMode.afterTap(selected, selected, mediaMode.canChange))
                } else {
                    isMenuOpen = true
                }
            },
            // A mode that is on can always be switched off.
            enabled = mediaMode.canChange || isOn,
            shape = PillShape,
            color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (isOn) MaterialTheme.colorScheme.onPrimary else JonakiTheme.colors.inkSoft,
            modifier = Modifier.height(PillHeight).clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 9.dp)) {
                Icon(
                    (selected ?: MediaKind.PICTURE).icon,
                    contentDescription = null,
                    modifier = Modifier.size(PillIconSize).then(if (mediaMode.canChange || isOn) Modifier else Modifier.alpha(0.38f)),
                )
            }
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            for (row in mediaMode.kindRows) {
                DropdownMenuItem(
                    text = { MediaKindMenuText(row) },
                    leadingIcon = { Icon(row.kind.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = row.priceText?.let { price -> { PillNumber(price) } },
                    onClick = {
                        isMenuOpen = false
                        onChange(MediaMode.afterTap(null, row.kind, mediaMode.canChange))
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaKindMenuText(row: MediaKindRowUi) {
    // The menu's width is bounded, so a long model name ends in "…" and the price beside it stays whole (D-029).
    Column(Modifier.widthIn(max = 168.dp)) {
        Text(stringResource(row.kind.labelRes), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        row.modelName?.let { modelName ->
            Text(
                modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PillIconSize = 16.dp

/** Narrower than this, the context pill drops the window's size: on a 360 dp phone the six-control row would push the approvals button off the edge. */
private val WidthForWindowSize = 400.dp

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

/**
 * The thread's approval mode as a shield: an empty one for Ask, a checked one for Auto and, in the
 * warning colour, a crossed one for Bypass, so a thread where nothing asks is never mistaken for one
 * that does. While "Allow all in this thread" is on, a double check sits beside the shield.
 */
@Composable
private fun ApprovalPill(mode: ApprovalModeChoice, allowAllInThread: Boolean, onClick: () -> Unit) {
    val deny = JonakiTheme.colors.deny
    val isBypass = mode == ApprovalModeChoice.BYPASS
    val container = if (isBypass) deny.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isBypass) deny else JonakiTheme.colors.inkSoft
    val icon = when (mode) {
        ApprovalModeChoice.ASK -> JonakiIcons.ShieldOutline
        ApprovalModeChoice.AUTO -> JonakiIcons.ShieldCheck
        ApprovalModeChoice.BYPASS -> JonakiIcons.ShieldCross
    }
    val modeLabel = approvalModeLabel(mode)
    val description = if (allowAllInThread) {
        stringResource(R.string.chat_status_approval_all, modeLabel)
    } else {
        stringResource(R.string.chat_status_approval, modeLabel)
    }
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = container,
        contentColor = content,
        modifier = Modifier.height(PillHeight).clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(PillIconSize))
            if (allowAllInThread) {
                Icon(JonakiIcons.DoneAll, contentDescription = null, modifier = Modifier.size(12.dp))
            }
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
