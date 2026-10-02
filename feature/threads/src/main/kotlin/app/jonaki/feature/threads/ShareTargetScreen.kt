package app.jonaki.feature.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One thread the shared files can go to. */
data class ShareTargetRow(
    val id: String,
    val title: String,
)

/**
 * Where shared files and text go: a new thread or one of the existing
 * threads, newest first. The subtitle names what was shared: the file's
 * name, "3 files", or "Text".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    fileNames: List<String>,
    threads: List<ShareTargetRow>,
    onNewThread: () -> Unit,
    onPickThread: (threadId: String) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.threads_cancel))
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.share_target_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            summaryOf(fileNames),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "new") {
                TargetRow(stringResource(R.string.threads_new), isNew = true, onClick = onNewThread)
                HorizontalDivider()
            }
            items(threads, key = { thread -> thread.id }) { thread ->
                TargetRow(thread.title.ifBlank { stringResource(R.string.share_target_untitled) }, isNew = false) {
                    onPickThread(thread.id)
                }
            }
        }
    }
}

@Composable
private fun summaryOf(fileNames: List<String>): String = when (fileNames.size) {
    0 -> stringResource(R.string.share_target_text)
    1 -> fileNames.first()
    else -> pluralStringResource(R.plurals.share_target_files, fileNames.size, fileNames.size)
}

@Composable
private fun TargetRow(text: String, isNew: Boolean, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        // Two lines tell similar thread names apart; longer names end in "…" (D-029).
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
