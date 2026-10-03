package app.jonaki.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.MarkdownText

/**
 * A thin line where the summarised part of the thread ends (D-033,
 * D-114). The model sees the summary instead of the messages above it;
 * a tap shows that summary under the line.
 */
@Composable
internal fun SummaryDividerRow(divider: ChatItem.SummaryDivider) {
    var open by rememberSaveable(divider.id) { mutableStateOf(false) }
    val quietColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 4.dp),
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                stringResource(R.string.chat_summary_divider),
                style = MaterialTheme.typography.labelMedium,
                color = quietColor,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = quietColor,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        if (open) {
            MarkdownText(divider.summaryMarkdown, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}
