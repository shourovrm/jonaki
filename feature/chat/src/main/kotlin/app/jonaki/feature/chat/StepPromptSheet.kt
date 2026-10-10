package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons

/**
 * What a picture or video step asked for, shown when its line is tapped:
 * the whole prompt, which the line cuts short, and the settings that change
 * the result or the price. The line stays short; this view shows everything.
 */
@Immutable
data class StepPromptUi(
    /** The prompt as the model wrote it, with its line breaks. */
    val prompt: String,
    /** "service:model", or the model the call used by default; null when none is known. */
    val model: String? = null,
    /** True when the call asks for high quality; the row is left out otherwise. */
    val isHighQuality: Boolean = false,
    val aspectRatio: String? = null,
    /** Paths relative to the thread folder, as the call wrote them; empty when no reference pictures were given. */
    val referencePaths: List<String> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StepPromptSheet(detail: StepPromptUi, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StepPromptContent(detail, Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun StepPromptContent(detail: StepPromptUi, modifier: Modifier = Modifier) {
    Column(modifier) {
        StepPromptHeader(detail.prompt)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            SelectionContainer {
                Text(detail.prompt, style = MaterialTheme.typography.bodyLarge)
            }
            detail.model?.let { model -> Fact(stringResource(R.string.chat_prompt_model), model) }
            if (detail.isHighQuality) {
                Fact(stringResource(R.string.chat_prompt_quality), stringResource(R.string.chat_prompt_quality_high))
            }
            detail.aspectRatio?.let { ratio -> Fact(stringResource(R.string.chat_prompt_aspect_ratio), ratio) }
            if (detail.referencePaths.isNotEmpty()) {
                // Each path on a line of its own, so that a long name wraps by itself.
                Fact(stringResource(R.string.chat_prompt_references), detail.referencePaths.joinToString("\n"))
            }
        }
    }
}

/** "Prompt" and the copy button, which copies the prompt alone. */
@Composable
private fun StepPromptHeader(prompt: String) {
    val clipboard = LocalClipboardManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
    ) {
        Text(
            stringResource(R.string.chat_prompt_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { clipboard.setText(AnnotatedString(prompt)) }) {
            Icon(
                JonakiIcons.ContentCopy,
                contentDescription = stringResource(R.string.chat_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A label above its value, each fact on lines of its own (D-029). */
@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
