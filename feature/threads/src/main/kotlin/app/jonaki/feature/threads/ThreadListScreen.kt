package app.jonaki.feature.threads

import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import app.jonaki.core.ui.MonospaceFamily
import app.jonaki.core.ui.UsageFormat
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
    /** Long-press menu: the app shows [RenameThreadDialog]. */
    onRename: (threadId: String) -> Unit = {},
    /** Called after the user confirms deletion. */
    onDelete: (threadId: String) -> Unit = {},
) {
    var threadToDelete by remember { mutableStateOf<ThreadRow?>(null) }
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
            val newThreadLabel = stringResource(R.string.threads_new)
            ExtendedFloatingActionButton(
                onClick = onNewThread,
                // The button's text slot is not exposed to accessibility in this Compose version.
                modifier = Modifier.semantics { contentDescription = newThreadLabel },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text(newThreadLabel) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.threads.isNotEmpty()) {
                SearchField(state.searchQuery, onSearchQueryChange)
            }
            val monthCost = state.monthCostUsd
            if (monthCost != null && state.threads.isNotEmpty()) {
                // Under the search field rather than beside the button: at large font
                // sizes the two would not fit side by side on a phone.
                Text(
                    stringResource(R.string.threads_month_cost, UsageFormat.cost(monthCost)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            when {
                state.threads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_empty_title), body = null)
                visibleThreads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_no_matches), body = null)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(visibleThreads, key = { thread -> thread.id }) { thread ->
                        ThreadRowView(
                            thread = thread,
                            timeLabel = ThreadTimeLabel.of(thread.updatedAtMillis, nowMillis, zone),
                            onThreadClick = onThreadClick,
                            onRename = onRename,
                            onDeleteRequest = { threadToDelete = thread },
                        )
                    }
                }
            }
        }
    }
    val deleting = threadToDelete
    if (deleting != null) {
        DeleteThreadDialog(
            title = deleting.title,
            onConfirm = {
                threadToDelete = null
                onDelete(deleting.id)
            },
            onDismiss = { threadToDelete = null },
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRowView(
    thread: ThreadRow,
    timeLabel: ThreadTimeLabel,
    onThreadClick: (String) -> Unit,
    onRename: (String) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
    val renameLabel = stringResource(R.string.threads_rename)
    val deleteLabel = stringResource(R.string.threads_delete)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(rowBackground)
                .combinedClickable(
                    onClick = { onThreadClick(thread.id) },
                    onLongClick = { menuOpen = true },
                )
                .semantics {
                    // TalkBack users reach Rename and Delete without a long-press.
                    customActions = listOf(
                        CustomAccessibilityAction(renameLabel) {
                            onRename(thread.id)
                            true
                        },
                        CustomAccessibilityAction(deleteLabel) {
                            onDeleteRequest()
                            true
                        },
                    )
                }
                .heightIn(min = 72.dp)
                .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            GlowDot(dotColor, dotStyle)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // The title wraps so the whole name shows; only the preview line is cut short.
                Text(
                    thread.title,
                    style = MaterialTheme.typography.titleMedium,
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
                val cost = thread.costUsd
                if (thread.runState == ThreadRunState.Idle && cost != null) {
                    Text(
                        UsageFormat.cost(cost),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    RunStateText(thread.runState)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(renameLabel) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRename(thread.id)
                },
            )
            DropdownMenuItem(
                text = { Text(deleteLabel, color = JonakiTheme.colors.deny) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = JonakiTheme.colors.deny) },
                onClick = {
                    menuOpen = false
                    onDeleteRequest()
                },
            )
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
