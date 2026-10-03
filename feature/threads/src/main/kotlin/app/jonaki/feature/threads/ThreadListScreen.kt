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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalConfiguration
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
    /** The lock in the top bar: a new incognito chat (D-111). */
    onNewIncognitoThread: () -> Unit = {},
    /** A project chip was tapped; null is All (D-110). */
    onProjectSelect: (projectId: String?) -> Unit = {},
    /** The project dialog was saved; [projectId] is null for a new project. */
    onSaveProject: (projectId: String?, draft: ProjectDraft) -> Unit = { _, _ -> },
    /** Called after the user confirms; the project's threads stay. */
    onDeleteProject: (projectId: String) -> Unit = {},
    /** Move to project; null takes the thread out of its project. */
    onMoveThread: (threadId: String, projectId: String?) -> Unit = { _, _ -> },
) {
    var threadToDelete by remember { mutableStateOf<ThreadRow?>(null) }
    var threadToMove by remember { mutableStateOf<ThreadRow?>(null) }
    var projectDialogOpen by rememberSaveable { mutableStateOf(false) }
    // Null with the dialog open means a new project.
    var projectToEdit by remember { mutableStateOf<ProjectUi?>(null) }
    var projectToDelete by remember { mutableStateOf<ProjectUi?>(null) }
    // The Incognito chip is a view of this screen only; the app keeps the selected project.
    var incognitoChipSelected by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val selectedProject = state.projects.firstOrNull { project -> project.id == state.selectedProjectId }
    val hasIncognitoThreads = state.threads.any { thread -> thread.incognito }
    // The chip goes when the last incognito thread does, and the list falls back to All.
    val showingIncognito = incognitoChipSelected && hasIncognitoThreads && selectedProject == null
    val filteredThreads = remember(state.threads, selectedProject, showingIncognito) {
        if (showingIncognito) incognitoThreads(state.threads) else threadsInProject(state.threads, selectedProject?.id)
    }
    val visibleThreads = remember(filteredThreads, state.searchQuery) { filterThreads(filteredThreads, state.searchQuery) }
    val zone = remember { ZoneId.systemDefault() }
    val entries = remember(visibleThreads, nowMillis, zone) { withGroupLabels(visibleThreads, nowMillis, zone) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.threads_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onNewIncognitoThread) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.threads_new_incognito))
                    }
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
                // Under the Incognito chip a new thread is incognito, as one under a project joins it (D-110).
                onClick = if (showingIncognito) onNewIncognitoThread else onNewThread,
                // The button's text slot is not exposed to accessibility in this Compose version.
                modifier = Modifier.semantics { contentDescription = newThreadLabel },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(newThreadLabel, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(18.dp),
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
            if (state.threads.isNotEmpty() || state.projects.isNotEmpty()) {
                ProjectChips(
                    projects = state.projects,
                    selectedProjectId = selectedProject?.id,
                    onSelect = { projectId ->
                        incognitoChipSelected = false
                        onProjectSelect(projectId)
                    },
                    incognitoChip = if (hasIncognitoThreads) {
                        IncognitoChip(
                            selected = showingIncognito,
                            onSelect = {
                                incognitoChipSelected = true
                                onProjectSelect(null)
                            },
                        )
                    } else {
                        null
                    },
                    onNewProject = {
                        projectToEdit = null
                        projectDialogOpen = true
                    },
                )
            }
            if (selectedProject != null) {
                ProjectHeader(
                    project = selectedProject,
                    onEdit = {
                        projectToEdit = selectedProject
                        projectDialogOpen = true
                    },
                    onDelete = { projectToDelete = selectedProject },
                )
            }
            when {
                state.threads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_empty_title), body = null)
                filteredThreads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_project_empty), body = null)
                visibleThreads.isEmpty() -> EmptyState(title = stringResource(R.string.threads_no_matches), body = null)
                else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)) {
                    items(entries, key = { entry -> entry.key }) { entry ->
                        when (entry) {
                            is ThreadListEntry.Label -> GroupLabel(entry.group)
                            is ThreadListEntry.Thread -> {
                                val thread = entry.row
                                ThreadRowView(
                                    thread = thread,
                                    timeLabel = ThreadTimeLabel.of(thread.updatedAtMillis, nowMillis, zone, locale),
                                    onThreadClick = onThreadClick,
                                    onRename = onRename,
                                    onDeleteRequest = { threadToDelete = thread },
                                    // Inside one project its name would repeat on every row.
                                    showProjectName = selectedProject == null,
                                    onMoveRequest = if (state.projects.isEmpty()) null else ({ threadToMove = thread }),
                                )
                            }
                        }
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
    val moving = threadToMove
    if (moving != null) {
        MoveToProjectDialog(
            projects = state.projects,
            currentProjectId = moving.projectId,
            onMove = { projectId ->
                threadToMove = null
                onMoveThread(moving.id, projectId)
            },
            onDismiss = { threadToMove = null },
        )
    }
    if (projectDialogOpen) {
        val editing = projectToEdit
        ProjectDialog(
            project = editing,
            modelOptions = state.projectModelOptions,
            onSave = { draft ->
                projectDialogOpen = false
                projectToEdit = null
                onSaveProject(editing?.id, draft)
            },
            onDismiss = {
                projectDialogOpen = false
                projectToEdit = null
            },
        )
    }
    val deletingProject = projectToDelete
    if (deletingProject != null) {
        DeleteProjectDialog(
            name = deletingProject.name,
            onConfirm = {
                projectToDelete = null
                onDeleteProject(deletingProject.id)
            },
            onDismiss = { projectToDelete = null },
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
        shape = RoundedCornerShape(22.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun GroupLabel(group: ThreadGroup) {
    val label = when (group) {
        ThreadGroup.TODAY -> R.string.threads_group_today
        ThreadGroup.YESTERDAY -> R.string.threads_group_yesterday
        ThreadGroup.LAST_SEVEN_DAYS -> R.string.threads_group_week
        ThreadGroup.OLDER -> R.string.threads_group_older
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() }.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
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
    showProjectName: Boolean,
    /** Null hides Move to project, when there is no project yet. */
    onMoveRequest: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.threads_rename)
    val deleteLabel = stringResource(R.string.threads_delete)
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    Box {
        // No card and no tint: rows stand apart by their spacing, and only a working thread glows (D-123).
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                .padding(horizontal = 20.dp, vertical = 11.dp),
        ) {
            Row {
                ThreadMark(thread, titleStyle.fontSize)
                // The title wraps so the whole name shows; only the preview line is cut short.
                Text(
                    thread.title,
                    style = titleStyle,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    timeText(timeLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                val projectName = thread.projectName
                val previewLine = if (showProjectName && projectName != null) {
                    stringResource(R.string.threads_project_preview, projectName, thread.lastLine)
                } else {
                    thread.lastLine
                }
                Text(
                    previewLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val cost = thread.costUsd
                if (thread.runState == ThreadRunState.Idle && cost != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        UsageFormat.cost(cost),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonospaceFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else if (thread.runState != ThreadRunState.Idle) {
                    Spacer(Modifier.width(12.dp))
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
            if (onMoveRequest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.threads_move)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMoveRequest()
                    },
                )
            }
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

/**
 * Before the title: the glowing dot while the thread works, a lock on an
 * incognito thread (D-111), nothing otherwise. It sits on the middle of the
 * title's first line, whatever the font scale.
 */
@Composable
private fun RowScope.ThreadMark(thread: ThreadRow, titleFontSize: TextUnit) {
    val density = LocalDensity.current
    // The middle of a lowercase letter is about 0.3 em above the baseline.
    val markCentreAboveBaseline = with(density) { (titleFontSize * 0.3f).roundToPx() }
    val onTitleLine = Modifier.alignBy { mark -> mark.measuredHeight / 2 + markCentreAboveBaseline }
    if (thread.runState is ThreadRunState.Running) {
        // GlowDot's canvas is wider than the dot, so the dot itself starts near the text edge.
        GlowDot(JonakiTheme.colors.live, DotStyle.GLOWING, onTitleLine.offset(x = (-4).dp), dotSize = 8.dp)
    } else if (thread.incognito) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = stringResource(R.string.threads_incognito),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = onTitleLine.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun RunStateText(runState: ThreadRunState) {
    val colors = JonakiTheme.colors
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    when (runState) {
        // In ink, not in the live colour: the dot already glows, and the day theme's live green is too light for text.
        is ThreadRunState.Running -> Text(
            stringResource(R.string.threads_state_running, runState.stepNumber),
            style = style,
            color = colors.inkSoft,
            maxLines = 1,
        )
        ThreadRunState.WaitingForApproval -> Text(stringResource(R.string.threads_state_waiting), style = style, color = colors.deny, maxLines = 1)
        ThreadRunState.Failed -> Text(stringResource(R.string.threads_state_failed), style = style, color = colors.deny, maxLines = 1)
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
