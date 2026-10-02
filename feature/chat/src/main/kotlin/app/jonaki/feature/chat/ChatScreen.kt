package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onApprovalChoice: (approvalId: String, choice: ApprovalChoice) -> Unit,
    onRetry: (errorId: String) -> Unit,
    onWebSearchChange: (enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChatTopBar(state, onBack, onWebSearchChange) },
        bottomBar = {
            Composer(
                draft = state.draft,
                isRunning = state.isRunning,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.items.isNotEmpty()) {
                MessageList(state.items, onApprovalChoice, onRetry)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(state: ChatUiState, onBack: () -> Unit, onWebSearchChange: (Boolean) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
            }
        },
        title = {
            Column {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                val subtitle = if (state.webSearchEnabled) R.string.chat_subtitle_search_on else R.string.chat_subtitle_search_off
                Text(
                    stringResource(subtitle, state.modelLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_menu_web_search)) },
                        trailingIcon = {
                            Switch(checked = state.webSearchEnabled, onCheckedChange = null)
                        },
                        onClick = { onWebSearchChange(!state.webSearchEnabled) },
                    )
                }
            }
        },
    )
}

@Composable
private fun MessageList(
    items: List<ChatItem>,
    onApprovalChoice: (String, ApprovalChoice) -> Unit,
    onRetry: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    // Follow the stream only while the user is at the bottom; scrolling up to
    // read earlier steps must not be undone by the next chunk.
    // This is read after the new items are laid out, so "at the bottom" allows
    // for the few rows one update adds (a run block replacing the caret plus an
    // approval card).
    val nearBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - ROWS_ONE_UPDATE_CAN_ADD
        }
    }
    var openedAtBottom by remember { mutableStateOf(false) }
    val lastItem = items.lastOrNull()
    val contentSignature = items.size to contentLength(lastItem)
    LaunchedEffect(contentSignature) {
        if (items.isEmpty()) {
            return@LaunchedEffect
        }
        if (!openedAtBottom || nearBottom) {
            listState.scrollToItem(items.lastIndex, scrollOffset = Int.MAX_VALUE)
            openedAtBottom = true
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { item -> item.id }) { item ->
            when (item) {
                is ChatItem.UserMessage -> UserBubble(item.text)
                is ChatItem.AssistantMessage -> MarkdownText(item.markdown, showCaret = item.isStreaming)
                is ChatItem.Run -> RunBlock(item)
                is ChatItem.Approval -> ApprovalCard(item, onApprovalChoice)
                is ChatItem.Error -> ErrorRow(item, onRetry)
            }
        }
    }
}

private const val ROWS_ONE_UPDATE_CAN_ADD = 3

private fun contentLength(item: ChatItem?): Int = when (item) {
    is ChatItem.AssistantMessage -> item.markdown.length
    is ChatItem.Run -> item.steps.size
    else -> 0
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalCard(approval: ChatItem.Approval, onChoice: (String, ApprovalChoice) -> Unit) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.chat_approval_title, approval.toolName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                approval.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onChoice(approval.id, ApprovalChoice.ALLOW_ONCE) },
                    colors = ButtonDefaults.buttonColors(containerColor = onContainer, contentColor = container),
                ) {
                    Text(stringResource(R.string.chat_approval_once))
                }
                FilledTonalButton(
                    onClick = { onChoice(approval.id, ApprovalChoice.ALLOW_FOR_THREAD) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = onContainer.copy(alpha = 0.12f),
                        contentColor = onContainer,
                    ),
                ) {
                    Text(stringResource(R.string.chat_approval_thread))
                }
                TextButton(
                    onClick = { onChoice(approval.id, ApprovalChoice.DENY) },
                    colors = ButtonDefaults.textButtonColors(contentColor = JonakiTheme.colors.deny),
                ) {
                    Text(stringResource(R.string.chat_approval_deny))
                }
            }
        }
    }
}

@Composable
private fun ErrorRow(error: ChatItem.Error, onRetry: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = JonakiTheme.colors.deny,
            modifier = Modifier.weight(1f),
        )
        if (error.canRetry) {
            TextButton(onClick = { onRetry(error.id) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.chat_retry))
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    isRunning: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                maxLines = 6,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (isRunning) {
                StopButton(onStop)
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(JonakiIcons.ArrowUpward, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
    }
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    val colors = JonakiTheme.colors
    Button(
        onClick = onStop,
        colors = ButtonDefaults.buttonColors(containerColor = colors.stop, contentColor = colors.onStop),
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
        Icon(JonakiIcons.Stop, contentDescription = null, tint = colors.live, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chat_stop), fontWeight = FontWeight.SemiBold)
    }
}
