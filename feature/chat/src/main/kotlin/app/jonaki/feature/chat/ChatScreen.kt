package app.jonaki.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.ui.ApprovalModeOptions
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.approvalModeLabel
import app.jonaki.core.ui.ThinkingChoice
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.MarkdownText
import app.jonaki.core.ui.MonospaceFamily
import kotlinx.coroutines.delay

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
    /** A scoped model was picked in the model sheet; it applies from the next message. */
    onModelSelect: (modelKey: String) -> Unit = {},
    /** "Edit list" in the model sheet: open the models in Settings. */
    onEditModels: () -> Unit = {},
    /** Rename in the overflow menu: the app shows the rename dialog. */
    onRename: () -> Unit = {},
    /** Memory in the overflow menu: the app opens this thread's memory screen. */
    onOpenMemory: () -> Unit = {},
    /** Skills in the overflow menu: the app opens this thread's skill switches. */
    onOpenSkills: () -> Unit = {},
    /** An artifact card was tapped: the app opens the viewer for that path. */
    onOpenArtifact: (path: String) -> Unit = {},
    /** A message to show first instead of the end, when opened from a memory fact's source. */
    focusMessageId: String? = null,
    /** Files in the + sheet: the app opens the system file picker. */
    onAttach: () -> Unit = {},
    /** Camera in the + sheet: the app opens the system camera app. */
    onTakePhoto: () -> Unit = {},
    /** Photos in the + sheet: the app opens its gallery, or the system photo picker without access (D-086). */
    onPickPhotos: () -> Unit = {},
    /** The close mark on an attachment chip. */
    onRemoveAttachment: (attachmentId: String) -> Unit = {},
    /** Edit under a sent prompt: the app puts its text in the field and marks it as editing. */
    onEditMessage: (messageId: String, text: String) -> Unit = { _, _ -> },
    /** The close mark on the editing banner. */
    onCancelEdit: () -> Unit = {},
    /** A thinking level picked for this thread in the model sheet (D-057). */
    onThinkingChange: (ThinkingChoice) -> Unit = {},
    /** Style and persona in the overflow menu: the app shows [ThreadStyleSheet] (D-107 to D-109). */
    onOpenStyle: () -> Unit = {},
    /** Keep on the incognito banner: the thread becomes a regular one (D-111). */
    onKeepThread: () -> Unit = {},
    /** An approval mode picked for this thread in the menu; null follows Settings (D-058). */
    onApprovalModeChange: (ApprovalModeChoice?) -> Unit = {},
    /** The ring pill: the app works out [ChatUiState.context] for the sheet; null leaves the pill without a tap. */
    onOpenContext: (() -> Unit)? = null,
    /** A run_code step was tapped: the app fills [ChatUiState.codeRun] for the code sheet (D-090). */
    onOpenStep: (stepId: String) -> Unit = {},
    onCloseCodeRun: () -> Unit = {},
    /** A file the program saved was tapped in the code sheet. */
    onOpenFile: (path: String) -> Unit = {},
    /** A button on the Python install card (plan M8 step 4). */
    onPythonCard: (card: ChatItem.PythonInstall, action: PythonCardAction) -> Unit = { _, _ -> },
    /**
     * The subagent whose page is open, or null (D-126). The app holds it, so
     * the page is still open after a file viewer it opened is closed.
     */
    openSubagentId: String? = null,
    onOpenSubagent: (subagentId: String?) -> Unit = {},
    /** Stop on a subagent's page; null hides it. */
    onStopSubagent: ((subagentId: String) -> Unit)? = null,
    /**
     * Called from the draw pass while an answer awaits its first draw, possibly
     * more than once; the app keeps the first call's time for the request log (D-132).
     */
    onAnswerDrawn: (messageId: String) -> Unit = {},
    /** The app keeps the list's position while a file viewer opened from the chat is in front. */
    listState: LazyListState = rememberLazyListState(),
) {
    // Which sheet is open is screen-local: it needs no data the app doesn't already pass in.
    var openSheet by rememberSaveable { mutableStateOf(ChatSheet.NONE) }
    var openWorkId by rememberSaveable { mutableStateOf<String?>(null) }
    Box(modifier) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Column {
                    ChatTopBar(
                        state = state,
                        onBack = onBack,
                        onWebSearchChange = onWebSearchChange,
                        onRename = onRename,
                        onOpenMemory = onOpenMemory,
                        onOpenSkills = onOpenSkills,
                        onOpenApprovals = { openSheet = ChatSheet.APPROVALS },
                        onOpenStyle = onOpenStyle,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (state.incognito) {
                        // A thread that has no message yet does not exist, so there is nothing to keep.
                        IncognitoBanner(onKeep = if (state.title.isNotBlank()) onKeepThread else null)
                    }
                }
            },
            bottomBar = {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    val status = state.status
                    if (status != null) {
                        StatusStrip(
                            status = status,
                            isRunning = state.isRunning,
                            onModelClick = { openSheet = ChatSheet.MODEL },
                            onCostClick = if (state.usage == null) null else ({ openSheet = ChatSheet.USAGE }),
                            webSearchEnabled = state.webSearchEnabled,
                            onWebSearchChange = onWebSearchChange,
                            onContextClick = onOpenContext?.let { openContext ->
                                {
                                    openSheet = ChatSheet.CONTEXT
                                    openContext()
                                }
                            },
                        )
                    }
                    if (state.attachments.isNotEmpty()) {
                        AttachmentChips(state.attachments, onRemoveAttachment)
                    }
                    if (state.editingMessageId != null) {
                        EditingBanner(onCancelEdit)
                    }
                    Composer(
                        draft = state.draft,
                        canSend = state.draft.isNotBlank() || state.attachments.isNotEmpty(),
                        isRunning = state.isRunning,
                        onDraftChange = onDraftChange,
                        onSend = onSend,
                        onStop = onStop,
                        onAttach = onAttach,
                        onTakePhoto = onTakePhoto,
                        onPickPhotos = onPickPhotos,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.items.isNotEmpty()) {
                    MessageList(
                        listState = listState,
                        items = state.items,
                        onApprovalChoice = onApprovalChoice,
                        onRetry = onRetry,
                        focusMessageId = focusMessageId,
                        onOpenArtifact = onOpenArtifact,
                        onOpenStep = onOpenStep,
                        // Editing while the agent works would change the history under the run.
                        onEditMessage = if (state.isRunning) null else onEditMessage,
                        onPythonCard = onPythonCard,
                        canTryAgain = !state.isRunning,
                        onOpenSubagent = { subagentId -> onOpenSubagent(subagentId) },
                        onOpenWork = { workId -> openWorkId = workId },
                        onAnswerDrawn = onAnswerDrawn,
                    )
                }
            }
        }
        val codeRun = state.codeRun
        if (codeRun != null) {
            CodeRunSheet(codeRun, onOpenFile = onOpenFile, onDismiss = onCloseCodeRun)
        }
        val usage = state.usage
        when {
            openSheet == ChatSheet.MODEL -> ModelSheet(
                choices = state.modelChoices,
                selectedKey = state.selectedModelKey,
                thinking = state.threadThinking,
                onThinkingChange = onThinkingChange,
                onSelect = { modelKey ->
                    openSheet = ChatSheet.NONE
                    onModelSelect(modelKey)
                },
                onEditModels = {
                    openSheet = ChatSheet.NONE
                    onEditModels()
                },
                onDismiss = { openSheet = ChatSheet.NONE },
            )
            openSheet == ChatSheet.USAGE && usage != null -> UsageSheet(usage, onDismiss = { openSheet = ChatSheet.NONE })
            openSheet == ChatSheet.CONTEXT -> ContextSheet(state.context, onDismiss = { openSheet = ChatSheet.NONE })
            openSheet == ChatSheet.APPROVALS -> ApprovalModeDialog(
                selected = state.threadApprovalMode,
                defaultChoice = state.defaultApprovalMode,
                onSelect = { choice ->
                    openSheet = ChatSheet.NONE
                    onApprovalModeChange(choice)
                },
                onDismiss = { openSheet = ChatSheet.NONE },
            )
        }
        val openWork = state.items.firstOrNull { item -> item.id == openWorkId } as? ChatItem.SubagentWork
        if (openWork != null) {
            SubagentWorkSheet(
                work = openWork,
                onOpenSubagent = { subagentId ->
                    openWorkId = null
                    onOpenSubagent(subagentId)
                },
                onOpenFile = onOpenFile,
                onDismiss = { openWorkId = null },
            )
        }
        // Drawn over the chat, which stays composed underneath, so Back finds it at the same place.
        val subagents = state.items.filterIsInstance<ChatItem.Run>().flatMap { run -> run.subagents }
        val openSubagent = subagents.firstOrNull { subagent -> subagent.id == openSubagentId }
        if (openSubagent != null) {
            SubagentPage(
                subagent = openSubagent,
                siblings = subagents.filter { subagent -> subagent.delegateStepId == openSubagent.delegateStepId },
                onSelect = { subagentId -> onOpenSubagent(subagentId) },
                onBack = { onOpenSubagent(null) },
                onOpenFile = onOpenFile,
                onOpenStep = onOpenStep,
                onStop = onStopSubagent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class ChatSheet {
    NONE,
    MODEL,
    USAGE,
    APPROVALS,
    CONTEXT,
}

/** Follow Settings, or this thread's own mode (D-058). */
@Composable
private fun ApprovalModeDialog(
    selected: ApprovalModeChoice?,
    defaultChoice: ApprovalModeChoice,
    onSelect: (ApprovalModeChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_approvals_title)) },
        text = { ApprovalModeOptions(selected = selected, onSelect = onSelect, defaultChoice = defaultChoice) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_approvals_close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: ChatUiState,
    onBack: () -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenStyle: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
            }
        },
        // The title only: the model and web search are pills in the status strip (D-123).
        title = {
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    // A thread that has no message yet has no name to change.
                    if (state.title.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_rename)) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        // An incognito thread uses no memory (D-111).
                        if (!state.incognito) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_menu_memory)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenMemory()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_skills)) },
                            onClick = {
                                menuOpen = false
                                onOpenSkills()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_approvals)) },
                            trailingIcon = {
                                Text(
                                    approvalModeLabel(state.threadApprovalMode ?: state.defaultApprovalMode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onOpenApprovals()
                            },
                        )
                    }
                    // Shown before the first message too, so a new thread can start with a persona.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_menu_style)) },
                        onClick = {
                            menuOpen = false
                            onOpenStyle()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun MessageList(
    listState: LazyListState,
    items: List<ChatItem>,
    onApprovalChoice: (String, ApprovalChoice) -> Unit,
    onRetry: (String) -> Unit,
    focusMessageId: String?,
    onOpenArtifact: (path: String) -> Unit,
    onOpenStep: (stepId: String) -> Unit,
    onEditMessage: ((messageId: String, text: String) -> Unit)?,
    onPythonCard: (ChatItem.PythonInstall, PythonCardAction) -> Unit,
    canTryAgain: Boolean,
    onOpenSubagent: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onAnswerDrawn: (String) -> Unit,
) {
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
    // A list the app kept while a file viewer was in front is already placed; only a new one jumps to the end.
    var openedAtBottom by remember { mutableStateOf(listState.firstVisibleItemIndex > 0) }
    // The working line is always last and never grows, so the newest few items are measured.
    val contentSignature = items.size to items.takeLast(ITEMS_THAT_GROW).sumOf(::contentLength)
    LaunchedEffect(contentSignature) {
        if (items.isEmpty()) {
            return@LaunchedEffect
        }
        val focusIndex = items.indexOfFirst { item -> item.id == focusMessageId }
        if (!openedAtBottom && focusIndex >= 0) {
            // Opened from a memory fact: show its source message, then follow the stream as usual.
            listState.scrollToItem(focusIndex)
            openedAtBottom = true
            return@LaunchedEffect
        }
        if (!openedAtBottom || nearBottom) {
            listState.scrollToItem(items.lastIndex, scrollOffset = Int.MAX_VALUE)
            openedAtBottom = true
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { item -> item.id }) { item ->
            when (item) {
                is ChatItem.UserMessage -> Column {
                    UserBubble(item.text)
                    MessageActions(item.text, alignEnd = true, onEdit = onEditMessage?.let { edit -> { edit(item.id, item.text) } })
                }
                is ChatItem.AssistantMessage -> Column(Modifier.reportDraw(item.awaitsFirstDraw) { onAnswerDrawn(item.id) }) {
                    MarkdownText(item.markdown, showCaret = item.isStreaming)
                    if (!item.isStreaming) {
                        MessageActions(item.markdown, alignEnd = false, onEdit = null)
                    }
                }
                is ChatItem.Reasoning -> ReasoningBlock(item)
                is ChatItem.Working -> WorkingRow(item)
                is ChatItem.Run -> RunBlock(item, onOpenStep, onOpenSubagent = onOpenSubagent)
                is ChatItem.SubagentWork -> SubagentWorkRow(item, onOpenWork)
                is ChatItem.Approval -> ApprovalCard(item, onApprovalChoice)
                is ChatItem.Error -> ErrorRow(item, onRetry)
                is ChatItem.Note -> NoteRow(item)
                is ChatItem.Artifact -> ArtifactRow(item, onOpenArtifact)
                is ChatItem.PythonInstall -> PythonInstallCard(item, canTryAgain, onPythonCard)
                is ChatItem.SummaryDivider -> SummaryDividerRow(item)
            }
        }
    }
}

private const val ROWS_ONE_UPDATE_CAN_ADD = 4

/**
 * Calls [onDrawn] each time the content is drawn while [enabled]. The draw pass
 * is the latest point the app sees before the frame goes to the screen, so the
 * request log's "Shown after" is measured to it (D-132).
 */
private fun Modifier.reportDraw(enabled: Boolean, onDrawn: () -> Unit): Modifier {
    if (!enabled) {
        return this
    }
    return drawWithContent {
        drawContent()
        onDrawn()
    }
}

private const val ITEMS_THAT_GROW = 3

private fun contentLength(item: ChatItem?): Int = when (item) {
    is ChatItem.AssistantMessage -> item.markdown.length
    is ChatItem.Reasoning -> item.text.length
    // Rows of subagents grow too: count their steps and the lines they wrote.
    is ChatItem.Run -> item.steps.size + item.subagents.sumOf { subagent -> subagent.steps.size + subagent.latestText.orEmpty().length }
    else -> 0
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = JonakiTheme.colors.userBubble,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            // The mockup's 86 % of the column; agent answers take the full width, like a page.
            modifier = Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End),
        ) {
            // A long press selects part of the prompt; the Copy button below takes all of it.
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
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
                val agentLabel = approval.agentLabel
                val title = if (agentLabel == null) {
                    stringResource(R.string.chat_approval_title, approval.toolName)
                } else {
                    stringResource(R.string.chat_approval_title_subagent, agentLabel.replaceFirstChar { it.uppercase() }, approval.toolName)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val waitEndsAt = approval.waitEndsAtMillis
                if (waitEndsAt != null) {
                    Spacer(Modifier.width(8.dp))
                    WaitCountdown(waitEndsAt)
                }
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
                // A subagent's allowance lasts for its task; the thread's own agent's for the thread (D-062).
                val isSubagent = approval.agentLabel != null
                FilledTonalButton(
                    onClick = {
                        onChoice(approval.id, if (isSubagent) ApprovalChoice.ALLOW_FOR_TASK else ApprovalChoice.ALLOW_FOR_THREAD)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = onContainer.copy(alpha = 0.12f),
                        contentColor = onContainer,
                    ),
                ) {
                    Text(stringResource(if (isSubagent) R.string.chat_approval_task else R.string.chat_approval_thread))
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

/** One tappable line per shown artifact: the file name, opened in the viewer. */
@Composable
private fun ArtifactRow(artifact: ChatItem.Artifact, onOpenArtifact: (String) -> Unit) {
    Surface(
        onClick = { onOpenArtifact(artifact.path) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(JonakiIcons.Document, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                artifact.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.chat_artifact_open),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NoteRow(note: ChatItem.Note) {
    Text(
        note.text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
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
    canSend: Boolean,
    isRunning: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local to the composer, like the sheet it opens: nothing outside needs to know it is open.
    var addSheetOpen by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        ) {
            OutlinedIconButton(
                onClick = { addSheetOpen = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.chat_attach),
                    tint = JonakiTheme.colors.inkSoft,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                maxLines = 6,
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    enabled = canSend,
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
    if (addSheetOpen) {
        // Each choice closes the sheet first, so the picker or gallery it opens is not stacked under it.
        AddSheet(
            onCamera = {
                addSheetOpen = false
                onTakePhoto()
            },
            onPhotos = {
                addSheetOpen = false
                onPickPhotos()
            },
            onFiles = {
                addSheetOpen = false
                onAttach()
            },
            onDismiss = { addSheetOpen = false },
        )
    }
}

/** The + button's choices, like ChatGPT's: Camera, Photos and Files side by side (D-085). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            AddChoice(JonakiIcons.PhotoCamera, stringResource(R.string.chat_add_camera), onCamera, Modifier.weight(1f))
            AddChoice(JonakiIcons.PhotoLibrary, stringResource(R.string.chat_add_photos), onPhotos, Modifier.weight(1f))
            AddChoice(JonakiIcons.AttachFile, stringResource(R.string.chat_add_files), onFiles, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddChoice(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One chip per file; a long name keeps one line and ends in "…" (D-029). Tapping a chip removes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentChips(attachments: List<AttachmentUi>, onRemove: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        for (attachment in attachments) {
            InputChip(
                selected = false,
                onClick = { onRemove(attachment.id) },
                label = {
                    Text(
                        attachment.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_attachment_remove, attachment.name),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
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
        Icon(JonakiIcons.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chat_stop), fontWeight = FontWeight.SemiBold)
    }
}

/** The time left before a subagent's card is withdrawn unanswered (D-062), ticking each second. */
@Composable
private fun WaitCountdown(waitEndsAtMillis: Long) {
    val now by produceState(System.currentTimeMillis(), waitEndsAtMillis) {
        while (value < waitEndsAtMillis) {
            delay(COUNTDOWN_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    Text(
        formatCountdown(waitEndsAtMillis - now),
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonospaceFamily),
    )
}

private const val COUNTDOWN_TICK_MILLIS = 1_000L
