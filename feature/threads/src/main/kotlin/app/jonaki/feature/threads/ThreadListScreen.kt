package app.jonaki.feature.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.DotStyle
import app.jonaki.core.ui.GlowDot
import app.jonaki.core.ui.JonakiTheme
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    state: ThreadListUiState,
    nowMillis: Long,
    onSearchQueryChange: (String) -> Unit,
    onThreadClick: (threadId: String) -> Unit,
    onNewThread: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val visibleThreads = remember(state.threads, state.searchQuery) { filterThreads(state.threads, state.searchQuery) }
    val zone = remember { ZoneId.systemDefault() }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.threads_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.threads_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewThread,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.threads_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.threads.isNotEmpty()) {
                SearchField(state.searchQuery, onSearchQueryChange)
            }
            when {
                state.threads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_empty_title), body = null)
                visibleThreads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_no_matches), body = null)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(visibleThreads, key = { thread -> thread.id }) { thread ->
                        ThreadRowView(thread, ThreadTimeLabel.of(thread.updatedAtMillis, nowMillis, zone), onThreadClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.threads_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.threads_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ThreadRowView(thread: ThreadRow, timeLabel: ThreadTimeLabel, onThreadClick: (String) -> Unit) {
    val colors = JonakiTheme.colors
    val dotColor: Color
    val dotStyle: DotStyle
    when (thread.runState) {
        is ThreadRunState.Running -> {
            dotColor = colors.live
            dotStyle = DotStyle.GLOWING
        }
        ThreadRunState.WaitingForApproval, ThreadRunState.Failed -> {
            dotColor = colors.deny
            dotStyle = DotStyle.QUIET
        }
        ThreadRunState.Idle -> {
            dotColor = colors.track
            dotStyle = DotStyle.QUIET
        }
    }
    val rowBackground = if (thread.runState is ThreadRunState.Running) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(rowBackground)
            .clickable { onThreadClick(thread.id) }
            .heightIn(min = 72.dp)
            .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        GlowDot(dotColor, dotStyle)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                thread.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                thread.lastLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                timeText(timeLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RunStateText(thread.runState)
        }
    }
}

@Composable
private fun timeText(label: ThreadTimeLabel): String = when (label) {
    ThreadTimeLabel.Now -> stringResource(R.string.threads_now)
    is ThreadTimeLabel.Text -> label.text
}

@Composable
private fun RunStateText(runState: ThreadRunState) {
    val colors = JonakiTheme.colors
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    when (runState) {
        is ThreadRunState.Running -> Text(
            stringResource(R.string.threads_state_running, runState.stepNumber),
            style = style,
            color = colors.live,
        )
        ThreadRunState.WaitingForApproval -> Text(stringResource(R.string.threads_state_waiting), style = style, color = colors.deny)
        ThreadRunState.Failed -> Text(stringResource(R.string.threads_state_failed), style = style, color = colors.deny)
        ThreadRunState.Idle -> Unit
    }
}

@Composable
private fun EmptyState(title: String, body: String?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, dotSize = 14.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
