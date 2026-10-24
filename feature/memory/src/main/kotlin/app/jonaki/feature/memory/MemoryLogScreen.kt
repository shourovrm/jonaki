package app.jonaki.feature.memory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One call that read a thread for facts. */
data class MemoryLogRowUi(
    val threadTitle: String,
    val atMillis: Long,
    /** The display name of the model that was asked; empty when none was chosen. */
    val modelName: String,
    /** What came of the call, as written by the extractor; empty while it runs. */
    val result: String,
    /** Shown in the warn colour. */
    val isFailure: Boolean,
)

/** "Today 02:05" for the current day, "Sat 13:59" for any other. */
object MemoryLogTime {
    fun format(atMillis: Long, nowMillis: Long, todayLabel: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val at = Instant.ofEpochMilli(atMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val clock = at.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        if (at.toLocalDate() == today) {
            return "$todayLabel $clock"
        }
        return at.format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))
    }
}

/** Every time Jonaki read a thread for facts: when, with which model, and what came of it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryLogScreen(
    rows: List<MemoryLogRowUi>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_log_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.memory_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    stringResource(R.string.memory_log_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(rows) { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                LogRow(row, nowMillis)
            }
            item {
                Text(
                    stringResource(R.string.memory_log_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun LogRow(row: MemoryLogRowUi, nowMillis: Long) {
    val todayLabel = stringResource(R.string.memory_log_today)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.threadTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val when_ = MemoryLogTime.format(row.atMillis, nowMillis, todayLabel)
            val detail = if (row.modelName.isEmpty()) when_ else "$when_ · ${row.modelName}"
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            row.result,
            style = MaterialTheme.typography.bodySmall,
            color = if (row.isFailure) JonakiTheme.colors.deny else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
