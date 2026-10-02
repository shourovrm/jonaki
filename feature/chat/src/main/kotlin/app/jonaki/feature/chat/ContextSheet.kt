package app.jonaki.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat
import java.util.Locale

/** How the context window is used: a bar, then one row per part and the free space (D-081). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextSheet(context: ContextUi?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(stringResource(R.string.chat_context_sheet_title))
            if (context != null) {
                ContextSummary(context)
            }
        }
    }
}

@Composable
private fun ContextSummary(context: ContextUi) {
    val percent = UsageFormat.percentUsed(context.usedTokens, context.windowTokens)
    Text(
        "$percent%",
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonospaceFamily),
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(
            R.string.chat_context_total,
            UsageFormat.exactTokens(context.usedTokens),
            UsageFormat.exactTokens(context.windowTokens),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    ContextBar(context)
    Spacer(Modifier.height(8.dp))
    for (part in context.parts) {
        ContextRow(
            color = colorOf(part.kind),
            label = labelOf(part.kind),
            detail = detailOf(part),
            tokens = part.tokens,
            windowTokens = context.windowTokens,
        )
    }
    ContextRow(
        color = JonakiTheme.colors.track,
        label = stringResource(R.string.chat_context_free),
        detail = null,
        tokens = context.freeTokens,
        windowTokens = context.windowTokens,
    )
    val notes = listOf(
        stringResource(if (context.totalIsReported) R.string.chat_context_estimated else R.string.chat_context_no_request),
        stringResource(
            R.string.chat_context_compact_at,
            UsageFormat.percentUsed(context.compactAtTokens, context.windowTokens),
            UsageFormat.exactTokens(context.compactAtTokens),
        ),
    )
    for (note in notes) {
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** One segment per part, in the rows' order, on a track that stands for the free space. */
@Composable
private fun ContextBar(context: ContextUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(JonakiTheme.colors.track)
            // The rows below say the same in words.
            .clearAndSetSemantics {},
    ) {
        for (part in context.parts) {
            Box(Modifier.weight(part.tokens.toFloat()).fillMaxHeight().background(colorOf(part.kind)))
        }
        if (context.freeTokens > 0) {
            Spacer(Modifier.weight(context.freeTokens.toFloat()))
        }
    }
}

@Composable
private fun ContextRow(color: Color, label: String, detail: String?, tokens: Int, windowTokens: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                UsageFormat.exactTokens(tokens),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
                maxLines = 1,
            )
            Text(
                shareOfWindow(tokens, windowTokens),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** One decimal, because most parts are a few percent of a large window. */
private fun shareOfWindow(tokens: Int, windowTokens: Int): String {
    if (windowTokens <= 0) {
        return "0%"
    }
    return String.format(Locale.ENGLISH, "%.1f%%", tokens * 100.0 / windowTokens)
}

@Composable
private fun colorOf(kind: ContextPartUiKind): Color = when (kind) {
    ContextPartUiKind.SYSTEM_PROMPT -> MaterialTheme.colorScheme.outline
    ContextPartUiKind.TOOLS -> MaterialTheme.colorScheme.primary
    ContextPartUiKind.SKILLS -> MaterialTheme.colorScheme.tertiary
    ContextPartUiKind.MEMORY -> MaterialTheme.colorScheme.secondary
    ContextPartUiKind.SUMMARY -> MaterialTheme.colorScheme.inversePrimary
    ContextPartUiKind.MESSAGES -> JonakiTheme.colors.live
    ContextPartUiKind.TOOL_RESULTS -> JonakiTheme.colors.done
    ContextPartUiKind.IMAGES -> MaterialTheme.colorScheme.error
}

@Composable
private fun labelOf(kind: ContextPartUiKind): String = stringResource(
    when (kind) {
        ContextPartUiKind.SYSTEM_PROMPT -> R.string.chat_context_system_prompt
        ContextPartUiKind.TOOLS -> R.string.chat_context_tools
        ContextPartUiKind.SKILLS -> R.string.chat_context_skills
        ContextPartUiKind.MEMORY -> R.string.chat_context_memory
        ContextPartUiKind.SUMMARY -> R.string.chat_context_summary
        ContextPartUiKind.MESSAGES -> R.string.chat_context_messages
        ContextPartUiKind.TOOL_RESULTS -> R.string.chat_context_tool_results
        ContextPartUiKind.IMAGES -> R.string.chat_context_images
    },
)

@Composable
private fun detailOf(part: ContextPartUi): String? {
    val count = part.count ?: return null
    val plural = when (part.kind) {
        ContextPartUiKind.TOOLS -> R.plurals.chat_context_tool_count
        ContextPartUiKind.SKILLS -> R.plurals.chat_context_skill_count
        ContextPartUiKind.MEMORY -> R.plurals.chat_context_fact_count
        ContextPartUiKind.SUMMARY -> R.plurals.chat_context_summary_covers
        ContextPartUiKind.IMAGES -> R.plurals.chat_context_image_count
        else -> return null
    }
    return pluralStringResource(plural, count, count)
}
