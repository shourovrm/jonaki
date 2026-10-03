package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.ThinkingChoice
import app.jonaki.core.ui.ThinkingChoiceRow
import app.jonaki.core.ui.UsageFormat

/** Picks the model for this thread from the user's scoped models (D-027). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSheet(
    choices: List<ModelChoiceUi>,
    selectedKey: String?,
    thinking: ThinkingChoice,
    onThinkingChange: (ThinkingChoice) -> Unit,
    onSelect: (modelKey: String) -> Unit,
    onEditModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(stringResource(R.string.chat_model_sheet_title))
            for (choice in choices) {
                val isSelected = choice.key == selectedKey
                ModelRow(choice, isSelected = isSelected, onClick = { onSelect(choice.key) })
                if (isSelected && choice.supportsThinking) {
                    Text(
                        stringResource(app.jonaki.core.ui.R.string.ui_thinking),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                    )
                    ThinkingChoiceRow(
                        selected = thinking,
                        onSelect = onThinkingChange,
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 8.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.chat_model_sheet_prices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditModels) {
                    Text(stringResource(R.string.chat_model_sheet_edit))
                }
            }
        }
    }
}

@Composable
private fun ModelRow(choice: ModelChoiceUi, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 6.dp)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(14.dp))
        // Name, service and prices each get their own line, so a long model id
        // ellipsizes alone and never pushes the prices off the screen.
        Column(Modifier.weight(1f)) {
            Text(
                choice.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                choice.serviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ModelPrices(choice)
        }
    }
}

/** Input, output and cached-input prices per million tokens as labelled values (D-027). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelPrices(choice: ModelChoiceUi) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Each value stays whole; at large font sizes the row wraps between values.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.chat_price_in, UsageFormat.price(choice.inputPricePerMillion)), style = style, color = color, maxLines = 1)
        Text(stringResource(R.string.chat_price_out, UsageFormat.price(choice.outputPricePerMillion)), style = style, color = color, maxLines = 1)
        Text(stringResource(R.string.chat_price_cache, UsageFormat.price(choice.cachedInputPricePerMillion)), style = style, color = color, maxLines = 1)
    }
}

/** Where this thread's money went: totals, token kinds, and a line per model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageSheet(usage: UsageUi, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetTitle(stringResource(R.string.chat_usage_sheet_title))
            Text(
                UsageFormat.cost(usage.totalCostUsd),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonospaceFamily),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                TokenTile(stringResource(R.string.chat_usage_input), usage.inputTokens, Modifier.weight(1f))
                TokenTile(stringResource(R.string.chat_usage_cached), usage.cachedTokens, Modifier.weight(1f))
                TokenTile(stringResource(R.string.chat_usage_output), usage.outputTokens, Modifier.weight(1f))
            }
            for (model in usage.perModel) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ModelUsageRow(model)
            }
            Text(
                stringResource(R.string.chat_usage_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (usage.requests.isNotEmpty()) {
                RequestLog(usage.requests)
            }
        }
    }
}

/** The latest requests' times, newest first (D-132); a table, so each figure sits under its label. */
@Composable
private fun RequestLog(requests: List<RequestTimeUi>) {
    Text(
        stringResource(R.string.chat_requests_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
    RequestLogRow(
        sentAt = stringResource(R.string.chat_requests_sent),
        providerWait = stringResource(R.string.chat_requests_provider_wait),
        shownAfter = stringResource(R.string.chat_requests_shown_after),
        isHeader = true,
    )
    for (request in requests) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RequestLogRow(
            sentAt = request.sentAt,
            providerWait = millisText(request.providerWaitMillis),
            shownAfter = millisText(request.shownAfterMillis),
            isHeader = false,
        )
    }
}

@Composable
private fun RequestLogRow(sentAt: String, providerWait: String, shownAfter: String, isHeader: Boolean) {
    val style = if (isHeader) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily)
    }
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // Labels may wrap at large font sizes; the figures are short and keep one line.
        Text(sentAt, style = style, color = color, modifier = Modifier.weight(1f))
        Text(providerWait, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(shownAfter, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A dash when the request had no such moment, for example a turn that only called tools. */
@Composable
private fun millisText(millis: Long?): String {
    if (millis == null) {
        return "–"
    }
    return stringResource(R.string.chat_requests_millis, millis.toInt())
}

@Composable
private fun TokenTile(label: String, tokens: Int, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                UsageFormat.tileTokens(tokens),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonospaceFamily),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ModelUsageRow(model: ModelUsageUi) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            model.modelName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            pluralStringResource(R.plurals.chat_usage_turns, model.turns, model.turns),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
        Text(
            UsageFormat.cost(model.costUsd),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFamily),
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}
