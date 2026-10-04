package app.jonaki.ui

import app.jonaki.feature.settings.GuardrailOptionsUi
import app.jonaki.feature.settings.MemoryOptionsUi
import app.jonaki.feature.settings.ApprovalRuleUi
import app.jonaki.feature.settings.ApprovalRuleChoiceUi
import app.jonaki.settings.ApprovalRules
import app.jonaki.core.agent.ApprovalRule
import androidx.compose.runtime.produceState
import app.jonaki.core.modelcatalog.ThinkingSupport
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.ui.ThinkingChoice
import android.net.Uri
import android.os.SystemClock
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.agent.AgentTypes
import app.jonaki.core.agent.ApprovalMode
import app.jonaki.feature.settings.CustomSubagentRowUi
import app.jonaki.feature.settings.ReminderSettingsUi
import app.jonaki.phone.ReminderPolicy
import app.jonaki.feature.settings.SubagentModelRowUi
import app.jonaki.settings.SubagentModelChoice
import app.jonaki.core.ui.ApprovalModeChoice
import app.jonaki.core.model.Role
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.MessageEntity
import app.jonaki.core.storage.ModelUsageRow
import app.jonaki.core.storage.ThreadSummary
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode
import app.jonaki.core.ui.resolvesToDark
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.ViewedImages
import app.jonaki.feature.chat.ApprovalChoice
import app.jonaki.feature.chat.AttachmentUi
import app.jonaki.files.AttachmentDrafts
import app.jonaki.files.StagedFile
import app.jonaki.files.CameraPhotos
import app.jonaki.files.RefusedFile
import app.jonaki.feature.chat.ChatScreen
import app.jonaki.feature.chat.ChatStatusUi
import app.jonaki.feature.chat.ContextUi
import app.jonaki.feature.chat.ChatUiState
import app.jonaki.feature.chat.ModelChoiceUi
import app.jonaki.feature.chat.ModelUsageUi
import app.jonaki.feature.chat.QueuedMessageUi
import app.jonaki.feature.chat.UsageUi
import app.jonaki.core.agent.ZoneInMessages
import app.jonaki.feature.settings.ZoneChoice
import app.jonaki.feature.settings.AccountLineUi
import app.jonaki.feature.settings.AddModelsScreen
import app.jonaki.feature.settings.AddModelsUiState
import app.jonaki.feature.settings.AddableModelUi
import app.jonaki.feature.settings.AddableServiceUi
import app.jonaki.feature.settings.ChatServiceCardUi
import app.jonaki.feature.settings.KeySlot
import app.jonaki.feature.settings.LocalModelsSummaryUi
import app.jonaki.feature.settings.McpServerUi
import app.jonaki.feature.settings.RoutingUi
import app.jonaki.feature.settings.SearchServiceRow
import app.jonaki.feature.settings.ServiceModelUi
import app.jonaki.feature.settings.SettingsActions
import app.jonaki.feature.settings.SettingsHomeScreen
import app.jonaki.feature.settings.SettingsPage
import app.jonaki.feature.settings.SettingsPageScreen
import app.jonaki.feature.settings.SettingsUiState
import app.jonaki.feature.settings.StatusIconsScreen
import app.jonaki.feature.settings.moveInOrder
import app.jonaki.feature.threads.ProjectModelOption
import app.jonaki.feature.threads.RenameThreadDialog
import app.jonaki.feature.threads.ShareTargetRow
import app.jonaki.feature.threads.ShareTargetScreen
import app.jonaki.feature.threads.ThreadListScreen
import app.jonaki.feature.threads.ThreadListUiState
import app.jonaki.feature.threads.ThreadRow
import app.jonaki.feature.threads.ThreadRunState
import app.jonaki.providers.openaicompatible.OpenRouterRouting
import app.jonaki.run.ChatProviders
import app.jonaki.settings.ChatModels
import app.jonaki.settings.ChatService
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SettingsSnapshot
import app.jonaki.settings.ThemeChoice
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolPicker
import app.jonaki.tools.sharefile.DestinationResult
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Routes are plain strings so they survive process death through rememberSaveable. */
private const val ROUTE_THREADS = "threads"
private const val ROUTE_SETTINGS = "settings"

/** "settings:<page key>", one Settings sub-page (D-128). */
private const val ROUTE_SETTINGS_PAGE_PREFIX = "settings:"
private const val ROUTE_STATUS_ICONS = "status-icons"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_PYTHON = "python"
private const val ROUTE_CHAT_PREFIX = "chat:"
private const val ROUTE_ADD_MODELS_PREFIX = "add-models:"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_MEMORY_THREAD_PREFIX = "memory:"
private const val ROUTE_MEMORY_PROJECT_PREFIX = "memory-project:"
private const val ROUTE_PROJECT_FILES_PREFIX = "project-files:"
private const val ROUTE_SKILLS = "skills"
private const val ROUTE_SKILLS_THREAD_PREFIX = "skills:"
private const val ROUTE_CUSTOM_INSTRUCTIONS = "custom-instructions"

/** "persona:<id>", the id empty for a new persona (D-109). */
private const val ROUTE_PERSONA_PREFIX = "persona:"

/** "subagent:<name>", the name empty for a new custom subagent (D-138). */
private const val ROUTE_SUBAGENT_PREFIX = "subagent:"

/** "artifact:<thread id>@<path relative to the thread folder>" (D-047). */
private const val ROUTE_ARTIFACT_PREFIX = "artifact:"

/** "skill:<name>@<thread id>", the thread id empty when the list was opened from Settings. */
private const val ROUTE_SKILL_EDIT_PREFIX = "skill:"

/** Separates a thread id from the message to show first: "chat:<thread>@<message>". */
private const val FOCUS_SEPARATOR = '@'

/** A thread is only created on its first message, so backing out leaves no empty thread. */
private const val NEW_THREAD = "new"

/** Like [NEW_THREAD], for an incognito chat (D-111). */
private const val NEW_INCOGNITO_THREAD = "new-incognito"

@Composable
fun JonakiApp(application: JonakiApplication, onDarkThemeChange: (Boolean) -> Unit) {
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val themeMode = themeModeOf(settingsSnapshot.theme)
    val isDark = resolvesToDark(themeMode)
    LaunchedEffect(isDark) { onDarkThemeChange(isDark) }
    JonakiTheme(themeMode = themeMode) {
        var route by rememberSaveable { mutableStateOf(ROUTE_THREADS) }
        // The project chip picked in the thread list; kept here so it survives opening a chat (D-110).
        var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
        // The subagent page and the chat's place stay while a file viewer opened from them is in front (D-126).
        var openSubagentId by rememberSaveable { mutableStateOf<String?>(null) }
        val chatListStates = remember { mutableMapOf<String, LazyListState>() }
        // The first Settings page keeps its place and its search while a sub-page is open (D-128).
        var settingsVisit by rememberSaveable { mutableIntStateOf(0) }
        val settingsScroll = rememberSaveable(settingsVisit, saver = ScrollState.Saver) { ScrollState(0) }
        var settingsQuery by rememberSaveable(settingsVisit) { mutableStateOf("") }
        val leaveSettings = {
            settingsVisit += 1
            route = ROUTE_THREADS
        }
        RefusedFilesMessage(application)
        val pendingShare by application.incomingShares.pending.collectAsState()
        val share = pendingShare
        if (share != null) {
            // A share from another app asks for its thread first, over whatever screen was open.
            BackHandler { application.incomingShares.discard() }
            ShareTargetRoute(
                application = application,
                fileNames = share.files.map { file -> file.name },
                onPicked = { threadKey ->
                    application.incomingShares.deliverTo(threadKey)
                    route = ROUTE_CHAT_PREFIX + threadKey
                },
            )
            return@JonakiTheme
        }
        if (ToolPicker.shouldShow(settingsSnapshot.toolPickerSeenVersion)) {
            ToolPickerRoute(application)
            return@JonakiTheme
        }
        when {
            route == ROUTE_SETTINGS || route.startsWith(ROUTE_SETTINGS_PAGE_PREFIX) -> {
                val settingsPage = SettingsPage.byKey(route.removePrefix(ROUTE_SETTINGS_PAGE_PREFIX))
                // A sub-page goes back to the first page; the first page leaves Settings.
                val goBack: () -> Unit = {
                    if (settingsPage == null) {
                        leaveSettings()
                    } else {
                        route = ROUTE_SETTINGS
                    }
                }
                BackHandler(onBack = goBack)
                if (settingsPage == SettingsPage.LOCAL_MODELS) {
                    LocalModelsRoute(application, onBack = goBack)
                    return@JonakiTheme
                }
                SettingsRoute(
                    application = application,
                    page = settingsPage,
                    homeScroll = settingsScroll,
                    searchQuery = settingsQuery,
                    onSearchQueryChange = { query -> settingsQuery = query },
                    onOpenPage = { page -> route = settingsPageRoute(page) },
                    onBack = goBack,
                    onOpenStatusIcons = { route = ROUTE_STATUS_ICONS },
                    onAddModels = { serviceKey -> route = ROUTE_ADD_MODELS_PREFIX + serviceKey },
                    onOpenMemory = { route = ROUTE_MEMORY },
                    onOpenSkills = { route = ROUTE_SKILLS },
                    onOpenTools = { route = ROUTE_TOOLS },
                    onOpenPython = { route = ROUTE_PYTHON },
                    onOpenCustomInstructions = { route = ROUTE_CUSTOM_INSTRUCTIONS },
                    onOpenPersona = { personaId -> route = ROUTE_PERSONA_PREFIX + personaId.orEmpty() },
                    onOpenCustomSubagent = { name -> route = ROUTE_SUBAGENT_PREFIX + name.orEmpty() },
                )
            }
            route.startsWith(ROUTE_SUBAGENT_PREFIX) -> {
                val backRoute = settingsPageRoute(SettingsPage.SUBAGENTS)
                BackHandler { route = backRoute }
                CustomSubagentEditorRoute(
                    application = application,
                    subagentName = route.removePrefix(ROUTE_SUBAGENT_PREFIX).ifEmpty { null },
                    onBack = { route = backRoute },
                )
            }
            route == ROUTE_CUSTOM_INSTRUCTIONS -> {
                val backRoute = settingsPageRoute(SettingsPage.ANSWERS)
                BackHandler { route = backRoute }
                CustomInstructionsRoute(application, onBack = { route = backRoute })
            }
            route.startsWith(ROUTE_PERSONA_PREFIX) -> {
                val backRoute = settingsPageRoute(SettingsPage.ANSWERS)
                BackHandler { route = backRoute }
                PersonaEditorRoute(
                    application = application,
                    personaId = route.removePrefix(ROUTE_PERSONA_PREFIX).ifEmpty { null },
                    onBack = { route = backRoute },
                )
            }
            route == ROUTE_TOOLS -> {
                val backRoute = settingsPageRoute(SettingsPage.TOOLS)
                BackHandler { route = backRoute }
                ToolsRoute(application, onBack = { route = backRoute })
            }
            route == ROUTE_PYTHON -> {
                val backRoute = settingsPageRoute(SettingsPage.TOOLS)
                BackHandler { route = backRoute }
                PythonRoute(application, onBack = { route = backRoute })
            }
            route == ROUTE_SKILLS || route.startsWith(ROUTE_SKILLS_THREAD_PREFIX) -> {
                val skillsThreadId = if (route == ROUTE_SKILLS) null else route.removePrefix(ROUTE_SKILLS_THREAD_PREFIX)
                val backRoute = if (skillsThreadId == null) settingsPageRoute(SettingsPage.MEMORY_SKILLS) else ROUTE_CHAT_PREFIX + skillsThreadId
                BackHandler { route = backRoute }
                SkillsRoute(
                    application = application,
                    threadId = skillsThreadId,
                    onBack = { route = backRoute },
                    onOpenSkill = { name -> route = ROUTE_SKILL_EDIT_PREFIX + name + FOCUS_SEPARATOR + skillsThreadId.orEmpty() },
                )
            }
            route.startsWith(ROUTE_SKILL_EDIT_PREFIX) -> {
                val target = route.removePrefix(ROUTE_SKILL_EDIT_PREFIX)
                val skillName = target.substringBefore(FOCUS_SEPARATOR)
                val listThreadId = target.substringAfter(FOCUS_SEPARATOR, "")
                val backRoute = if (listThreadId.isEmpty()) ROUTE_SKILLS else ROUTE_SKILLS_THREAD_PREFIX + listThreadId
                BackHandler { route = backRoute }
                SkillEditorRoute(application = application, name = skillName, onBack = { route = backRoute })
            }
            route == ROUTE_MEMORY || route.startsWith(ROUTE_MEMORY_THREAD_PREFIX) -> {
                val memoryThreadId = if (route == ROUTE_MEMORY) null else route.removePrefix(ROUTE_MEMORY_THREAD_PREFIX)
                val backRoute = if (memoryThreadId == null) settingsPageRoute(SettingsPage.MEMORY_SKILLS) else ROUTE_CHAT_PREFIX + memoryThreadId
                BackHandler { route = backRoute }
                MemoryRoute(
                    application = application,
                    threadId = memoryThreadId,
                    onBack = { route = backRoute },
                    onOpenMessage = { threadId, messageId -> route = ROUTE_CHAT_PREFIX + threadId + FOCUS_SEPARATOR + messageId },
                )
            }
            route.startsWith(ROUTE_MEMORY_PROJECT_PREFIX) -> {
                BackHandler { route = ROUTE_THREADS }
                MemoryRoute(
                    application = application,
                    threadId = null,
                    projectId = route.removePrefix(ROUTE_MEMORY_PROJECT_PREFIX),
                    onBack = { route = ROUTE_THREADS },
                    onOpenMessage = { threadId, messageId -> route = ROUTE_CHAT_PREFIX + threadId + FOCUS_SEPARATOR + messageId },
                )
            }
            route.startsWith(ROUTE_PROJECT_FILES_PREFIX) -> {
                BackHandler { route = ROUTE_THREADS }
                ProjectFilesRoute(
                    application = application,
                    projectId = route.removePrefix(ROUTE_PROJECT_FILES_PREFIX),
                    onBack = { route = ROUTE_THREADS },
                )
            }
            route.startsWith(ROUTE_ARTIFACT_PREFIX) -> {
                val target = route.removePrefix(ROUTE_ARTIFACT_PREFIX)
                val artifactThreadId = target.substringBefore(FOCUS_SEPARATOR)
                val backRoute = ROUTE_CHAT_PREFIX + artifactThreadId
                BackHandler { route = backRoute }
                ArtifactRoute(
                    application = application,
                    threadId = artifactThreadId,
                    path = target.substringAfter(FOCUS_SEPARATOR),
                    onBack = { route = backRoute },
                )
            }
            route == ROUTE_STATUS_ICONS -> {
                val backRoute = settingsPageRoute(SettingsPage.THEME)
                BackHandler { route = backRoute }
                StatusIconsScreen(
                    showStatusStrip = settingsSnapshot.showStatusStrip,
                    onShowStatusStripChange = { show -> application.settings.update { it.copy(showStatusStrip = show) } },
                    onBack = { route = backRoute },
                )
            }
            route.startsWith(ROUTE_ADD_MODELS_PREFIX) -> {
                val backRoute = settingsPageRoute(SettingsPage.MODELS)
                BackHandler { route = backRoute }
                AddModelsRoute(
                    application = application,
                    serviceKey = route.removePrefix(ROUTE_ADD_MODELS_PREFIX),
                    onFinished = { route = backRoute },
                )
            }
            route.startsWith(ROUTE_CHAT_PREFIX) -> {
                val chatTarget = route.removePrefix(ROUTE_CHAT_PREFIX)
                val chatThreadId = chatTarget.substringBefore(FOCUS_SEPARATOR)
                val leaveChat = {
                    openSubagentId = null
                    chatListStates.remove(chatThreadId)
                    route = ROUTE_THREADS
                }
                BackHandler(onBack = leaveChat)
                val isNewChat = chatThreadId == NEW_THREAD || chatThreadId == NEW_INCOGNITO_THREAD
                ProvideChatImages(application, chatThreadId, isNewChat) {
                    ChatRoute(
                        application = application,
                        threadId = chatThreadId,
                        focusMessageId = chatTarget.substringAfter(FOCUS_SEPARATOR, "").ifEmpty { null },
                        onBack = leaveChat,
                        openSubagentId = openSubagentId,
                        onOpenSubagent = { subagentId -> openSubagentId = subagentId },
                        listState = chatListStates.getOrPut(chatThreadId) { LazyListState() },
                        onThreadCreated = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                        onEditModels = { route = settingsPageRoute(SettingsPage.MODELS) },
                        onOpenMemory = { route = ROUTE_MEMORY_THREAD_PREFIX + chatThreadId },
                        onOpenSkills = { route = ROUTE_SKILLS_THREAD_PREFIX + chatThreadId },
                        onOpenArtifact = { path -> route = ROUTE_ARTIFACT_PREFIX + chatThreadId + FOCUS_SEPARATOR + path },
                        newThreadProjectId = selectedProjectId,
                    )
                }
            }
            else -> ThreadsRoute(
                application = application,
                onOpenThread = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                onNewThread = { route = ROUTE_CHAT_PREFIX + NEW_THREAD },
                onOpenSettings = { route = ROUTE_SETTINGS },
                onNewIncognitoThread = { route = ROUTE_CHAT_PREFIX + NEW_INCOGNITO_THREAD },
                selectedProjectId = selectedProjectId,
                onProjectSelect = { projectId -> selectedProjectId = projectId },
                onOpenProjectMemory = { projectId -> route = ROUTE_MEMORY_PROJECT_PREFIX + projectId },
                onOpenProjectFiles = { projectId -> route = ROUTE_PROJECT_FILES_PREFIX + projectId },
            )
        }
    }
}

private fun settingsPageRoute(page: SettingsPage): String = ROUTE_SETTINGS_PAGE_PREFIX + page.key

@Composable
private fun ShareTargetRoute(application: JonakiApplication, fileNames: List<String>, onPicked: (threadKey: String) -> Unit) {
    val summaries by remember { application.database.threadDao().observeSummaries() }.collectAsState(initial = emptyList())
    val rows = summaries
        .sortedByDescending { summary -> summary.thread.updatedAtMillis }
        .map { summary -> ShareTargetRow(summary.thread.id, summary.thread.title) }
    ShareTargetScreen(
        fileNames = fileNames,
        threads = rows,
        onNewThread = { onPicked(NEW_THREAD) },
        onPickThread = onPicked,
        onCancel = { application.incomingShares.discard() },
    )
}

/** Files that were too large or unreadable are named once in a short message. */
@Composable
private fun RefusedFilesMessage(application: JonakiApplication) {
    val refused by application.incomingShares.refusedFiles.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(refused) {
        if (refused.isEmpty()) {
            return@LaunchedEffect
        }
        val lines = refused.map { file ->
            when (file) {
                is RefusedFile.TooLarge ->
                    context.getString(R.string.files_refused_too_large, file.name, IncomingFiles.describeSize(file.limitBytes))
                is RefusedFile.Unreadable -> context.getString(R.string.files_refused_unreadable, file.name)
            }
        }
        Toast.makeText(context, lines.joinToString("\n"), Toast.LENGTH_LONG).show()
        application.incomingShares.clearRefused()
    }
}

@Composable
private fun ThreadsRoute(
    application: JonakiApplication,
    onOpenThread: (String) -> Unit,
    onNewThread: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewIncognitoThread: () -> Unit,
    selectedProjectId: String?,
    onProjectSelect: (String?) -> Unit,
    onOpenProjectMemory: (projectId: String) -> Unit,
    onOpenProjectFiles: (projectId: String) -> Unit,
) {
    val database = application.database
    val summaries by remember { database.threadDao().observeSummaries() }.collectAsState(initial = emptyList())
    val projects by remember { database.projectDao().observeAll() }.collectAsState(initial = emptyList())
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val monthCost by remember { database.messageDao().observeCostSince(startOfThisMonthMillis()) }.collectAsState(initial = null)
    val running by application.runner.runningThreadIds.collectAsState()
    val approvals by application.runner.pendingApprovals.collectAsState()
    val stepCounts by application.runner.runStepCounts.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var threadToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // The list is one of the two moments incognito threads a day old are deleted (D-111).
        application.runner.deleteExpiredIncognitoThreads()
    }
    LaunchedEffect(Unit) {
        // Keeps "now" and the HH:mm labels current while the list is open.
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val rows = summaries.map { summary ->
        val runState = when {
            summary.thread.id in approvals -> ThreadRunState.WaitingForApproval
            summary.thread.id in running -> ThreadRunState.Running(stepNumber = stepCounts[summary.thread.id] ?: 0)
            summary.lastRole == HistoryMapper.ERROR_ROLE -> ThreadRunState.Failed
            else -> ThreadRunState.Idle
        }
        ThreadRow(
            id = summary.thread.id,
            title = summary.thread.title,
            lastLine = lastLineOf(summary),
            updatedAtMillis = summary.thread.updatedAtMillis,
            runState = runState,
            costUsd = summary.totalCostUsd,
            projectId = summary.thread.projectId,
            projectName = projects.firstOrNull { project -> project.id == summary.thread.projectId }?.name,
            incognito = summary.thread.incognito,
        )
    }
    ThreadListScreen(
        state = ThreadListUiState(
            threads = rows,
            searchQuery = searchQuery,
            monthCostUsd = monthCost,
            projects = projects.map { project -> Projects.uiOf(project, application.catalog) },
            selectedProjectId = selectedProjectId,
            projectModelOptions = modelChoices(settingsSnapshot.chatModels, application.catalog, application, rememberLocalModelKeys(application))
                .map { choice -> ProjectModelOption(choice.key, choice.name) },
        ),
        nowMillis = nowMillis,
        onSearchQueryChange = { query -> searchQuery = query },
        onThreadClick = onOpenThread,
        onNewThread = onNewThread,
        onOpenSettings = onOpenSettings,
        onRename = { threadId -> threadToRename = threadId },
        onDelete = { threadId ->
            scope.launch {
                application.runner.deleteThread(threadId)
                // Saved chips would otherwise wait forever for a thread that is gone.
                withContext(Dispatchers.IO) { application.attachmentDrafts.discardAll(threadId) }
            }
        },
        onNewIncognitoThread = onNewIncognitoThread,
        onProjectSelect = onProjectSelect,
        onSaveProject = { projectId, draft ->
            scope.launch {
                val savedId = Projects.save(database, projectId, draft)
                // A new project opens at once, so its first thread can be started there.
                onProjectSelect(savedId)
            }
        },
        onDeleteProject = { projectId, keepFacts ->
            onProjectSelect(null)
            scope.launch { Projects.delete(database, application, projectId, keepFacts) }
        },
        onOpenProjectMemory = onOpenProjectMemory,
        onOpenProjectFiles = onOpenProjectFiles,
        onMoveThread = { threadId, projectId -> scope.launch { database.threadDao().setProject(threadId, projectId) } },
    )
    val renaming = threadToRename?.let { id -> summaries.firstOrNull { it.thread.id == id } }
    if (renaming != null) {
        RenameThreadDialog(
            currentTitle = renaming.thread.title,
            onSave = { title ->
                threadToRename = null
                scope.launch { database.threadDao().rename(renaming.thread.id, title) }
            },
            onDismiss = { threadToRename = null },
        )
    }
}

private fun lastLineOf(summary: ThreadSummary): String =
    PreviewText.of(summary.lastText.orEmpty(), isUserMessage = summary.lastRole == Role.USER.name)

private fun startOfThisMonthMillis(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

@Composable
private fun ChatRoute(
    application: JonakiApplication,
    threadId: String,
    focusMessageId: String?,
    onBack: () -> Unit,
    onThreadCreated: (String) -> Unit,
    onEditModels: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenArtifact: (path: String) -> Unit,
    /** The project a new regular thread joins: the one selected in the thread list (D-110). */
    newThreadProjectId: String?,
    openSubagentId: String?,
    onOpenSubagent: (String?) -> Unit,
    listState: LazyListState,
) {
    val database = application.database
    val runner = application.runner
    val catalog = application.catalog
    val isNewIncognito = threadId == NEW_INCOGNITO_THREAD
    val isNew = threadId == NEW_THREAD || isNewIncognito
    // An incognito chat stays out of projects, so the project's instructions never reach it.
    val projectIdForNewThread = if (isNew && !isNewIncognito) newThreadProjectId else null
    val projectForNewThread by remember(projectIdForNewThread) {
        if (projectIdForNewThread == null) flowOf(null) else database.projectDao().observeAll()
    }.collectAsState(initial = null)
    val newThreadProjectModel = projectForNewThread?.firstOrNull { project -> project.id == projectIdForNewThread }?.modelKey
    val thread by remember(threadId) {
        if (isNew) flowOf(null) else database.threadDao().observe(threadId)
    }.collectAsState(initial = null)
    val messages by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.messageDao().observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val steps by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.stepDao().observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val threadCost by remember(threadId) {
        if (isNew) flowOf(null) else database.messageDao().observeThreadCost(threadId)
    }.collectAsState(initial = null)
    val lastInputTokens by remember(threadId) {
        if (isNew) flowOf(null) else database.messageDao().observeLastInputTokens(threadId)
    }.collectAsState(initial = null)
    val subagents by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.subagentDao().observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val compaction by remember(threadId) {
        if (isNew) flowOf(null) else database.compactionDao().observeLatestForThread(threadId)
    }.collectAsState(initial = null)
    val modelUsage by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.messageDao().observeModelUsage(threadId)
    }.collectAsState(initial = emptyList())
    val running by runner.runningThreadIds.collectAsState()
    val approvals by runner.pendingApprovals.collectAsState()
    val queuedByThread by runner.queuedMessages.collectAsState()
    val handedBackTexts by runner.handedBackText.collectAsState()
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val attachmentsByThread by application.attachmentDrafts.byThread.collectAsState()
    val sharedTexts by application.incomingShares.textFor.collectAsState()
    var draft by rememberSaveable(threadId) { mutableStateOf("") }
    // The sent prompt being edited (D-056); null when the field holds a new message.
    var editingMessageId by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        application.incomingShares.attach(threadId, uris)
    }
    // Saveable, because Android may stop Jonaki while the camera app is open (D-053).
    var pendingPhotoPath by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val photoPath = pendingPhotoPath ?: return@rememberLauncherForActivityResult
        pendingPhotoPath = null
        val photo = File(photoPath)
        if (saved && photo.length() > 0) {
            application.incomingShares.attachPhoto(threadId, photo, photo.name)
        } else {
            photo.delete()
        }
    }
    val pickPhotos = rememberPhotosChoice(threadId, application)
    LaunchedEffect(threadId, sharedTexts[threadId]) {
        val sharedText = application.incomingShares.takeText(threadId) ?: return@LaunchedEffect
        draft = if (draft.isBlank()) sharedText else draft.trimEnd() + "\n\n" + sharedText
    }
    // A stopped or failed run hands its queued messages back instead of sending them.
    LaunchedEffect(threadId, handedBackTexts[threadId]) {
        val handedBack = runner.takeHandedBackText(threadId) ?: return@LaunchedEffect
        draft = if (draft.isBlank()) handedBack else draft.trimEnd() + "\n\n" + handedBack
    }
    // A new thread does not exist yet, so a model picked before the first message is kept here.
    var modelForNewThread by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    // A new thread has no row to hold its thinking level until the first message creates it.
    var thinkingForNewThread by rememberSaveable(threadId) { mutableStateOf(ThinkingChoice.DEFAULT) }
    // The globe pill can be tapped before the first message too; null follows Settings.
    var webSearchForNewThread by rememberSaveable(threadId) { mutableStateOf<Boolean?>(null) }
    var renaming by rememberSaveable(threadId) { mutableStateOf(false) }
    // Style, persona and instructions picked before the first message, like the thinking level above.
    var styleForNewThread by rememberSaveable(threadId, stateSaver = ThreadStyleDraftSaver) { mutableStateOf(ThreadStyleDraft()) }
    var styleSheetOpen by rememberSaveable(threadId) { mutableStateOf(false) }
    // Worked out each time the ring pill is tapped, so the sheet matches the thread at that moment (D-081).
    var contextUi by remember(threadId) { mutableStateOf<ContextUi?>(null) }
    // The run_code step whose code sheet is open (D-090); saved so the sheet survives rotation.
    var openCodeStepId by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    val codeRun by codeRunOf(application, threadId, steps.firstOrNull { step -> step.toolCallId == openCodeStepId })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Answers whose first draw was already sent to the request log; the draw pass may report one several times.
    val answersReportedDrawn = remember(threadId) { mutableSetOf<String>() }

    DisposableEffect(threadId) {
        // Leaving a thread is one of the two moments memory extraction runs (D-009).
        onDispose {
            if (!isNew) {
                runner.threadLeft(threadId)
            }
        }
    }

    // Composed after the app's own Back for the chat, so Back closes the subagent page first.
    BackHandler(enabled = subagents.any { subagent -> subagent.id == openSubagentId }) { onOpenSubagent(null) }
    val isRunning = threadId in running
    val pending = approvals[threadId].orEmpty()
    val tryAgainText = stringResource(R.string.python_try_again_message)
    val pythonCards = rememberPythonCardHooks(application, threadId) {
        if (!isNew) {
            runner.send(threadId, tryAgainText)
        }
    }
    val webSearchEnabled = if (isNew) {
        webSearchForNewThread ?: !settingsSnapshot.webSearchOffInNewThreads
    } else {
        thread?.webSearchEnabled ?: !settingsSnapshot.webSearchOffInNewThreads
    }
    val modelKey = (if (isNew) modelForNewThread ?: newThreadProjectModel else null) ?: runner.modelKeyFor(thread)
    val modelInfo = modelKey?.let(catalog::find)
    val modelName = modelInfo?.displayName ?: modelKey?.let(ModelKey::modelOf).orEmpty()
    val status = if (settingsSnapshot.showStatusStrip && modelKey != null) {
        ChatStatusUi(
            modelName = modelName,
            contextWindowTokens = modelInfo?.contextWindowTokens,
            contextUsedTokens = lastInputTokens ?: 0,
            costUsd = threadCost ?: 0.0,
        )
    } else {
        null
    }
    val state = ChatUiState(
        title = thread?.title.orEmpty(),
        webSearchEnabled = webSearchEnabled,
        items = ChatItems.build(
            rows = messages,
            steps = steps,
            isRunning = isRunning,
            pendingApprovals = pending,
            fallbackNote = stringResource(R.string.routing_fallback_note),
            subagents = subagents,
            modelNameOf = { key -> catalog.find(key)?.displayName },
            pythonCard = pythonCards.cardFor,
            compaction = compaction,
            subagentLimits = settingsSnapshot.subagentLimits,
            subagentTypeNames = AgentTypes.ALL.map { type -> type.name } + settingsSnapshot.customSubagents.map { subagent -> subagent.name },
            stepWords = stepDetailWords(),
        ),
        isRunning = isRunning,
        queuedMessages = queuedByThread[threadId].orEmpty().map { queued -> QueuedMessageUi(queued.id, queued.text) },
        draft = draft,
        status = status,
        modelChoices = modelChoices(settingsSnapshot.chatModels, catalog, application, rememberLocalModelKeys(application)),
        selectedModelKey = modelKey,
        usage = usageOf(modelUsage, catalog, threadCost, messages),
        attachments = attachmentsByThread[threadId].orEmpty().map { file -> AttachmentUi(file.id, file.name, previewPath = previewPathOf(file)) },
        editingMessageId = editingMessageId,
        threadThinking = if (isNew) {
            thinkingForNewThread
        } else {
            thinkingChoiceOf(ThinkingLevel.entries.firstOrNull { level -> level.name == thread?.thinkingLevel })
        },
        incognito = if (isNew) isNewIncognito else thread?.incognito == true,
        threadApprovalMode = ApprovalMode.entries.firstOrNull { mode -> mode.name == thread?.approvalMode }?.let(::approvalChoiceOf),
        defaultApprovalMode = approvalChoiceOf(settingsSnapshot.defaultApprovalMode),
        allowAllInThread = !isNew && thread?.allowAllInThread == true,
        context = contextUi,
        codeRun = codeRun.takeIf { openCodeStepId != null },
    )
    val contextWindowTokens = modelInfo?.contextWindowTokens
    ChatScreen(
        state = state,
        onBack = onBack,
        onDraftChange = { text -> draft = text },
        onSend = {
            val text = draft
            draft = ""
            val editedMessageId = editingMessageId
            editingMessageId = null
            scope.launch {
                if (editedMessageId != null && !isNew) {
                    val inboxPaths = withContext(Dispatchers.IO) {
                        application.attachmentDrafts.moveIntoInbox(threadId, runner.threadFolder(threadId))
                    }
                    runner.editAndResend(threadId, editedMessageId, AttachmentDrafts.messageWith(text, inboxPaths))
                    return@launch
                }
                val targetThreadId = if (isNew) {
                    runner.createThread(projectId = projectIdForNewThread, incognito = isNewIncognito)
                } else {
                    threadId
                }
                val pickedModel = modelForNewThread
                if (isNew && pickedModel != null) {
                    runner.setThreadModel(targetThreadId, pickedModel)
                }
                val pickedWebSearch = webSearchForNewThread
                if (isNew && pickedWebSearch != null) {
                    runner.setWebSearchEnabled(targetThreadId, pickedWebSearch)
                }
                if (isNew && thinkingForNewThread != ThinkingChoice.DEFAULT) {
                    runner.setThreadThinking(targetThreadId, thinkingLevelOf(thinkingForNewThread))
                }
                if (isNew && styleForNewThread != ThreadStyleDraft()) {
                    saveThreadStyle(database, targetThreadId, styleForNewThread)
                }
                // threadId is still "new" for a new thread, which is the key its attachments wait under.
                val inboxPaths = withContext(Dispatchers.IO) {
                    application.attachmentDrafts.moveIntoInbox(threadId, runner.threadFolder(targetThreadId))
                }
                runner.send(targetThreadId, AttachmentDrafts.messageWith(text, inboxPaths))
                if (isNew) {
                    onThreadCreated(targetThreadId)
                }
            }
        },
        onStop = { runner.stop(threadId) },
        onCancelQueued = { queuedId -> runner.cancelQueued(threadId, queuedId) },
        onEditQueued = { queuedId ->
            val queuedText = runner.takeQueuedForEdit(threadId, queuedId)
            if (queuedText != null) {
                draft = if (draft.isBlank()) queuedText else queuedText + "\n\n" + draft.trimStart()
            }
        },
        onOpenContext = if (isNew || contextWindowTokens == null) {
            null
        } else {
            {
                contextUi = null
                scope.launch {
                    val breakdown = runner.contextBreakdown(threadId) ?: return@launch
                    contextUi = ContextSheetState.of(
                        breakdown,
                        lastInputTokens,
                        contextWindowTokens,
                        memoryFacts = runner.promptFactLines(threadId),
                    )
                }
            }
        },
        onEditMessage = { messageId, text ->
            editingMessageId = messageId
            draft = text
        },
        onThinkingChange = { choice ->
            if (isNew) {
                thinkingForNewThread = choice
            } else {
                scope.launch { runner.setThreadThinking(threadId, thinkingLevelOf(choice)) }
            }
        },
        onApprovalModeChange = { choice ->
            if (!isNew) {
                scope.launch { runner.setThreadApprovalMode(threadId, choice?.let(::approvalModeOf)) }
            }
        },
        onCancelEdit = {
            editingMessageId = null
            draft = ""
        },
        onWithdrawAllowAll = {
            if (!isNew) {
                scope.launch { runner.withdrawAllowAll(threadId) }
            }
        },
        onApprovalChoice = { approvalId, choice -> runner.answerApproval(threadId, approvalId, decisionOf(choice)) },
        onRetry = { runner.retry(threadId) },
        onWebSearchChange = { enabled ->
            if (isNew) {
                webSearchForNewThread = enabled
            } else {
                scope.launch { runner.setWebSearchEnabled(threadId, enabled) }
            }
        },
        onModelSelect = { key ->
            if (isNew) {
                modelForNewThread = key
            } else {
                scope.launch { runner.setThreadModel(threadId, key) }
            }
        },
        onEditModels = onEditModels,
        onRename = { renaming = true },
        onOpenMemory = onOpenMemory,
        onOpenSkills = onOpenSkills,
        onOpenArtifact = onOpenArtifact,
        focusMessageId = focusMessageId,
        onAttach = { filePicker.launch(arrayOf("*/*")) },
        onPickPhotos = pickPhotos,
        onTakePhoto = {
            val photo = CameraPhotos.newFile(application)
            pendingPhotoPath = photo.path
            try {
                camera.launch(CameraPhotos.uriFor(application, photo))
            } catch (noCameraApp: ActivityNotFoundException) {
                pendingPhotoPath = null
                Toast.makeText(application, R.string.files_no_camera, Toast.LENGTH_LONG).show()
            }
        },
        onOpenStep = { stepId -> openCodeStepId = stepId },
        onCloseCodeRun = { openCodeStepId = null },
        onOpenFile = { path -> openSavedFile(context, runner.threadFolder(threadId), path, onOpenArtifact) },
        onRemoveAttachment = { attachmentId ->
            scope.launch(Dispatchers.IO) { application.attachmentDrafts.remove(threadId, attachmentId) }
        },
        onPythonCard = pythonCards.onAction,
        onKeepThread = { scope.launch { runner.keepIncognitoThread(threadId) } },
        onOpenStyle = { styleSheetOpen = true },
        openSubagentId = openSubagentId,
        onOpenSubagent = onOpenSubagent,
        onStopSubagent = { subagentId -> runner.stopSubagent(threadId, subagentId) },
        onAnswerDrawn = { messageId ->
            // Read in the draw pass itself; the save may run a moment later.
            val drawnElapsedMillis = SystemClock.elapsedRealtime()
            if (answersReportedDrawn.add(messageId)) {
                scope.launch { database.messageDao().markFirstShown(messageId, drawnElapsedMillis) }
            }
        },
        listState = listState,
    )
    if (styleSheetOpen) {
        ThreadStyleSheetRoute(
            application = application,
            draft = if (isNew) styleForNewThread else styleDraftOf(thread),
            onChange = { draft ->
                if (isNew) {
                    styleForNewThread = draft
                } else {
                    scope.launch { saveThreadStyle(database, threadId, draft) }
                }
            },
            onDismiss = { styleSheetOpen = false },
        )
    }
    val currentThread = thread
    if (renaming && currentThread != null) {
        RenameThreadDialog(
            currentTitle = currentThread.title,
            onSave = { title ->
                renaming = false
                scope.launch { database.threadDao().rename(currentThread.id, title) }
            },
            onDismiss = { renaming = false },
        )
    }
}

/** The staged file's path when it is an image, so its chip shows a thumbnail. */
private fun previewPathOf(file: StagedFile): String? = file.file.path.takeIf { ViewedImages.isImagePath(file.name) }

/** The step track's labels in the app's language (M11). */
@Composable
private fun stepDetailWords(): StepDetail.Words {
    val resources = LocalContext.current.resources
    return StepDetail.Words(
        readCalendar = stringResource(R.string.step_read_calendar),
        addToCalendar = stringResource(R.string.step_add_to_calendar),
        reminder = stringResource(R.string.step_reminder),
        notify = stringResource(R.string.step_notify),
        readClipboard = stringResource(R.string.step_read_clipboard),
        copyToClipboard = stringResource(R.string.step_copy_to_clipboard),
        openApp = stringResource(R.string.step_open_app),
        schedule = stringResource(R.string.step_schedule),
        cancelTask = stringResource(R.string.step_cancel_task),
        listTasks = stringResource(R.string.step_list_tasks),
        toDownloads = stringResource(R.string.step_to_downloads),
        saveAs = stringResource(R.string.step_save_as),
        share = stringResource(R.string.step_share),
        toLinkedFolder = stringResource(R.string.step_to_linked_folder),
        listLinkedFolder = stringResource(R.string.step_list_linked_folder),
        fromLinkedFolder = stringResource(R.string.step_from_linked_folder),
        lineCount = { lines -> resources.getQuantityString(app.jonaki.feature.chat.R.plurals.chat_code_lines, lines, lines) },
    )
}

/** The downloaded models' keys, read again whenever a download finishes or a model is deleted (D-133). */
@Composable
private fun rememberLocalModelKeys(application: JonakiApplication): List<String> {
    val localModelChanges by application.localModels.changes.collectAsState()
    return remember(localModelChanges) { application.localModelRuntime.modelKeys() }
}

/** The user's added models, then the model files on the phone (D-133). */
private fun modelChoices(
chatModels: ChatModels, catalog: ModelCatalog, application: JonakiApplication, localModelKeys: List<String>): List<ModelChoiceUi> =
    (chatModels.allModelKeys + localModelKeys).map { key ->
        val info = catalog.find(key)
        ModelChoiceUi(
            key = key,
            name = info?.displayName ?: ModelKey.modelOf(key),
            serviceName = ChatService.byKey(ModelKey.serviceOf(key))?.let { service -> serviceNameOf(service, application) }.orEmpty(),
            inputPricePerMillion = info?.inputUsdPerMillion,
            outputPricePerMillion = info?.outputUsdPerMillion,
            cachedInputPricePerMillion = info?.cachedInputUsdPerMillion,
            supportsThinking = ThinkingSupport.isSupported(key, info),
        )
    }

/** DEFAULT is "no entry": the thread follows the model, the model its own default (D-057). */
private fun thinkingLevelOf(choice: ThinkingChoice): ThinkingLevel? = when (choice) {
    ThinkingChoice.DEFAULT -> null
    ThinkingChoice.OFF -> ThinkingLevel.OFF
    ThinkingChoice.LOW -> ThinkingLevel.LOW
    ThinkingChoice.MEDIUM -> ThinkingLevel.MEDIUM
    ThinkingChoice.HIGH -> ThinkingLevel.HIGH
}

private fun thinkingChoiceOf(level: ThinkingLevel?): ThinkingChoice = when (level) {
    null -> ThinkingChoice.DEFAULT
    ThinkingLevel.OFF -> ThinkingChoice.OFF
    ThinkingLevel.LOW -> ThinkingChoice.LOW
    ThinkingLevel.MEDIUM -> ThinkingChoice.MEDIUM
    ThinkingLevel.HIGH -> ThinkingChoice.HIGH
}

private fun usageOf(rows: List<ModelUsageRow>, catalog: ModelCatalog, totalCost: Double?, messages: List<MessageEntity>): UsageUi? {
    if (rows.isEmpty()) {
        return null
    }
    return UsageUi(
        totalCostUsd = totalCost ?: 0.0,
        inputTokens = rows.sumOf { it.inputTokens ?: 0L }.toInt(),
        cachedTokens = rows.sumOf { it.cachedInputTokens ?: 0L }.toInt(),
        outputTokens = rows.sumOf { it.outputTokens ?: 0L }.toInt(),
        perModel = rows.map { row ->
            ModelUsageUi(
                modelName = catalog.find(row.model)?.displayName ?: ModelKey.modelOf(row.model),
                turns = row.turns,
                costUsd = row.costUsd ?: 0.0,
            )
        },
        requests = RequestLogRows.build(messages),
    )
}

@Composable
private fun SettingsRoute(
    application: JonakiApplication,
    /** Null is the first page. */
    page: SettingsPage?,
    homeScroll: ScrollState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onOpenStatusIcons: () -> Unit,
    onAddModels: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCustomInstructions: () -> Unit,
    onOpenPersona: (personaId: String?) -> Unit,
    onOpenTools: () -> Unit,
    onOpenPython: () -> Unit,
    onOpenCustomSubagent: (name: String?) -> Unit,
) {
    val personas by remember { application.database.personaDao().observeAll() }.collectAsState(initial = emptyList())
    val settings = application.settings
    val secrets = application.secrets
    val snapshot by settings.snapshot.collectAsState()
    val savedKeys by secrets.names.collectAsState()
    val previews by secrets.previews.collectAsState()
    val balances by application.balances.balances.collectAsState()
    LaunchedEffect(Unit) {
        // Balances change as the user spends elsewhere, so they are fetched each time Settings opens (D-031).
        application.balances.refreshAll()
    }
    val balanceWords = BalanceText.Words(
        moneyLeft = { amount -> application.getString(R.string.balance_money_left, amount) },
        creditsOfLimit = { used, limit -> application.getString(R.string.balance_credits_of_limit, used, limit) },
        creditsUsed = { used -> application.getString(R.string.balance_credits_used, used) },
        leftAndMonth = { left, month -> application.getString(R.string.balance_left_and_month, left, month) },
    )
    val linkedFolder by application.linkedFolder.current.collectAsState()
    val reminders by application.reminders.book.reminders.collectAsState()
    val scheduledTasks by application.scheduledTasks.book.tasks.collectAsState()
    val mcpServers by application.mcpServers.servers.collectAsState()
    // Builds every tool to read its declared actions, which reads saved keys; again when the servers change.
    val approvalRuleChoices by produceState(emptyList<ApprovalRuleChoiceUi>(), mcpServers) {
        value = withContext(Dispatchers.IO) { application.runner.approvalRuleChoices() }
    }
    val globalFacts by remember { application.database.memoryDao().observeGlobal() }.collectAsState(initial = emptyList())
    var skillCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // The library is plain files with no change feed; Settings reads the count each time it opens.
        skillCount = withContext(Dispatchers.IO) { application.skillLibrary.list().size }
    }
    val context = LocalContext.current
    val permissionScope = rememberCoroutineScope()
    // Bumped on every resume, so statuses are right after a visit to system settings (D-124).
    var permissionCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionCheck += 1 }
    val permissionRows = remember(permissionCheck, snapshot.refusedPermissions) {
        readPermissionRows(context, snapshot.refusedPermissions)
    }
    val appVersion = remember { installedVersionName(context) }
    val localModelChanges by application.localModels.changes.collectAsState()
    var localModelsSummary by remember { mutableStateOf(LocalModelsSummaryUi()) }
    LaunchedEffect(localModelChanges) {
        localModelsSummary = withContext(Dispatchers.IO) { localModelsSummaryOf(application.localModels, context) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            linkFolder(application, treeUri)
        }
    }
    fun accountFor(service: ChatService): AccountLineUi? {
        val balance = service.secret?.let { secret -> balances[secret] }
        val line = BalanceText.cardLine(balance, balanceWords) ?: return null
        return AccountLineUi(line.text, line.isLow)
    }
    fun slotFor(secret: SecretName) = KeySlot(
        id = secret.name,
        isSet = secret in savedKeys,
        maskedKey = previews[secret],
        balance = BalanceText.of(balances[secret], balanceWords),
    )

    val state = SettingsUiState(
        chatServices = serviceCards(snapshot, application, ::slotFor, ::accountFor),
        addableServices = ChatService.entries
            // Local models come from downloaded files, not from a service card (D-133).
            .filter { service -> service != ChatService.LOCAL }
            .filter { service -> service !in snapshot.chatModels.addedServices }
            .map { service -> AddableServiceUi(service.key, serviceNameOf(service, application), hintFor(service)) },
        geminiKey = slotFor(SecretName.GEMINI),
        searchServices = snapshot.searchOrder.map { service ->
            SearchServiceRow(service.name, displayNameOf(service), slotFor(service.secret))
        },
        webSearchOffInNewThreads = snapshot.webSearchOffInNewThreads,
        themeMode = themeModeOf(snapshot.theme),
        showStatusStrip = snapshot.showStatusStrip,
        linkedFolderName = linkedFolder?.name,
        scheduledItems = ScheduledItems.of(application, reminders, scheduledTasks),
        reminders = ReminderSettingsUi(
            intervalMinutes = snapshot.reminderPolicy.intervalMinutes,
            intervalOptions = ReminderPolicy.INTERVAL_OPTIONS,
            maxRepeats = snapshot.reminderPolicy.maxRepeats,
            maxRepeatsLimit = ReminderPolicy.MAX_REPEATS_LIMIT,
        ),
        mcpServers = mcpServers.map { server ->
            McpServerUi(server.id, server.name, server.url, server.headerName, application.mcpServers.hasHeaderValue(server.id))
        },
        answerStyle = answerStyleChoiceOf(snapshot.answerStyle),
        customInstructions = snapshot.customInstructions,
        personas = personaRowsOf(personas),
        approvalMode = approvalChoiceOf(snapshot.defaultApprovalMode),
        jevGuardOn = snapshot.jevGuardOn,
        guardrails = GuardrailOptionsUi(
            askBeforeSendingOut = snapshot.askBeforeSendingOutAfterOutsideContent,
            holdNewFacts = snapshot.holdFactsAfterOutsideContent,
        ),
        memoryOptions = MemoryOptionsUi(
            saveFactsFromChats = snapshot.saveFactsFromChats,
            reviewNewFacts = snapshot.reviewExtractedMemories,
            suggestFactsForAllThreads = snapshot.suggestFactsForAllThreads,
            suggestSkills = snapshot.suggestSkills,
        ),
        // The two enums have the same three names; the screen's module does not see the agent's.
        zoneInMessages = ZoneChoice.valueOf(snapshot.zoneInMessages.name),
        jevGuardAvailable = SecretName.OPENROUTER in savedKeys,
        approvalRules = snapshot.approvalRules.map { rule -> ApprovalRuleUi(rule.toolName, rule.action, rule.detail) },
        approvalRuleChoices = approvalRuleChoices,
        subagentModels = AgentTypes.ALL.map { type ->
            SubagentModelRowUi(
                agentType = type.name,
                selectedKey = snapshot.subagentModels[type.name]?.takeIf { key -> key in snapshot.chatModels.allModelKeys },
                defaultIsCheapest = type.name == SubagentModelChoice.SCOUT,
            )
        },
        subagentModelOptions = subagentModelOptionsOf(snapshot, application),
        subagentLimits = SubagentLimitRows.of(snapshot.subagentLimits),
        subagentBudgets = SubagentLimitRows.budgetsOf(
            snapshot.subagentLimits,
            AgentTypes.ALL.map { type -> type.name } + snapshot.customSubagents.map { subagent -> subagent.name },
        ),
        customSubagents = snapshot.customSubagents.map { subagent -> CustomSubagentRowUi(subagent.name, subagent.description) },
        permissions = permissionRows,
        appVersion = appVersion,
        toolGroupsOn = snapshot.enabledToolGroups.size,
        toolGroupCount = ToolGroup.entries.size,
        factCount = globalFacts.size,
        skillCount = skillCount,
        localModels = localModelsSummary,
    )
    val actions = SettingsActions(
        onBack = onBack,
        onKeySave = { slotId, value -> secrets.save(SecretName.valueOf(slotId), value) },
        onKeyClear = { slotId -> secrets.remove(SecretName.valueOf(slotId)) },
        onSearchServiceMove = { serviceKey, offset ->
            settings.update { current ->
                val names = moveInOrder(current.searchOrder.map { it.name }, serviceKey, offset)
                current.copy(searchOrder = names.map { name -> SearchService.valueOf(name) })
            }
        },
        onJevGuardChange = { on -> settings.update { current -> current.copy(jevGuardOn = on) } },
        onExportMemory = {
            permissionScope.launch {
                val result = application.memoryExport.saveToDownloads()
                val message = when (result) {
                    null -> context.getString(R.string.memory_export_empty)
                    is DestinationResult.Done -> context.getString(R.string.memory_export_done, result.location)
                    else -> context.getString(R.string.memory_export_failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        onGuardrailOptionsChange = { options ->
            settings.update { current ->
                current.copy(
                    askBeforeSendingOutAfterOutsideContent = options.askBeforeSendingOut,
                    holdFactsAfterOutsideContent = options.holdNewFacts,
                )
            }
        },
        onMemoryOptionsChange = { options ->
            settings.update { current ->
                current.copy(
                    saveFactsFromChats = options.saveFactsFromChats,
                    reviewExtractedMemories = options.reviewNewFacts,
                    suggestFactsForAllThreads = options.suggestFactsForAllThreads,
                    suggestSkills = options.suggestSkills,
                )
            }
        },
        onZoneInMessagesChange = { choice ->
            settings.update { current -> current.copy(zoneInMessages = ZoneInMessages.valueOf(choice.name)) }
        },
        onWebSearchOffInNewThreadsChange = { off -> settings.update { current -> current.copy(webSearchOffInNewThreads = off) } },
        onThemeModeChange = { mode -> settings.update { current -> current.copy(theme = themeChoiceOf(mode)) } },
        onOpenPage = onOpenPage,
        onOpenStatusIcons = onOpenStatusIcons,
        onOpenMemory = onOpenMemory,
        onOpenSkills = onOpenSkills,
        onOpenTools = onOpenTools,
        onOpenPython = onOpenPython,
        onLinkFolder = { folderPicker.launch(null) },
        onUnlinkFolder = { application.linkedFolder.unlink() },
        onCancelScheduled = { itemId -> cancelScheduled(application, itemId) },
        onAddService = { serviceKey ->
            val service = ChatService.byKey(serviceKey)
            if (service != null) {
                settings.updateChatModels { models -> models.addService(service) }
            }
        },
        onRemoveService = { serviceKey ->
            val service = ChatService.byKey(serviceKey)
            if (service != null) {
                settings.updateChatModels { models -> models.removeService(service) }
                removeKeyIfUnshared(service, application)
            }
        },
        onServiceRoutingChange = { _, routing ->
            settings.update { current -> current.copy(routing = current.routing.copy(openRouter = routingOf(routing))) }
        },
        onAddModels = onAddModels,
        onModelSetDefault = { modelKey -> settings.updateChatModels { models -> models.setDefault(modelKey) } },
        onModelRoutingChange = { modelKey, routing ->
            settings.update { current -> current.copy(routing = current.routing.withOverride(modelKey, routing?.let(::routingOf))) }
        },
        onModelRemove = { modelKey -> settings.updateChatModels { models -> models.removeModel(modelKey) } },
        onSubagentModelChange = { agentType, modelKey ->
            settings.update { current ->
                val choices = if (modelKey == null) current.subagentModels - agentType else current.subagentModels + (agentType to modelKey)
                current.copy(subagentModels = choices)
            }
        },
        onSubagentLimitChange = { limit, value ->
            settings.update { current -> current.copy(subagentLimits = SubagentLimitRows.changed(current.subagentLimits, limit, value)) }
        },
        onSubagentBudgetChange = { agentType, limit, value ->
            settings.update { current ->
                current.copy(subagentLimits = SubagentLimitRows.budgetChanged(current.subagentLimits, agentType, limit, value))
            }
        },
        onReminderIntervalChange = { minutes ->
            settings.update { current -> current.copy(reminderPolicy = current.reminderPolicy.copy(intervalMinutes = minutes).withinBounds()) }
        },
        onReminderMaxRepeatsChange = { repeats ->
            settings.update { current -> current.copy(reminderPolicy = current.reminderPolicy.copy(maxRepeats = repeats).withinBounds()) }
        },
        onOpenCustomSubagent = onOpenCustomSubagent,
        onApprovalModeChange = { choice -> settings.update { current -> current.copy(defaultApprovalMode = approvalModeOf(choice)) } },
        onApprovalRuleAdd = { rule ->
            settings.update { current ->
                current.copy(approvalRules = ApprovalRules.added(current.approvalRules, ApprovalRule(rule.toolName, rule.action, rule.detail)))
            }
        },
        onApprovalRuleRemove = { rule ->
            settings.update { current ->
                current.copy(approvalRules = current.approvalRules - ApprovalRule(rule.toolName, rule.action, rule.detail))
            }
        },
        onModelThinkingChange = { modelKey, choice ->
            settings.update { current ->
                val level = thinkingLevelOf(choice)
                val levels = if (level == null) current.thinkingLevels - modelKey else current.thinkingLevels + (modelKey to level)
                current.copy(thinkingLevels = levels)
            }
        },
        onMcpServerSave = { input ->
            application.mcpServers.save(input.id, input.name, input.url, input.headerName, input.headerValue)
        },
        onMcpServerRemove = { id -> application.mcpServers.remove(id) },
        onAnswerStyleChange = { choice ->
            val style = answerStyleOf(choice)
            if (style != null) {
                settings.update { current -> current.copy(answerStyle = style) }
            }
        },
        onOpenCustomInstructions = onOpenCustomInstructions,
        onOpenPersona = onOpenPersona,
        onPermissionTap = { row ->
            val item = permissionRows.first { candidate -> candidate.row == row }
            permissionScope.launch {
                onPermissionTapped(application, context, item)
                permissionCheck += 1
            }
        },
        onOpenGitHub = { openGitHub(context) },
    )
    if (page == null) {
        SettingsHomeScreen(
            state = state,
            actions = actions,
            scrollState = homeScroll,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
        )
    } else {
        SettingsPageScreen(page = page, state = state, actions = actions)
    }
}

/** Cancels the reminder or scheduled task that a Settings row names. */
private fun cancelScheduled(application: JonakiApplication, itemId: String) {
    val reminderId = ScheduledItems.reminderId(itemId)
    if (reminderId != null) {
        application.reminders.cancel(reminderId)
    }
    val taskId = ScheduledItems.taskId(itemId)
    if (taskId != null) {
        application.scheduledTasks.cancel(taskId)
    }
}

/** Some folder pickers give a folder whose access cannot be kept; then nothing is linked. */
private fun linkFolder(application: JonakiApplication, treeUri: Uri) {
    try {
        application.linkedFolder.link(treeUri)
    } catch (refused: SecurityException) {
        Toast.makeText(application, R.string.files_link_failed, Toast.LENGTH_LONG).show()
    }
}

private fun serviceCards(
    snapshot: SettingsSnapshot,
    application: JonakiApplication,
    slotFor: (SecretName) -> KeySlot,
    accountFor: (ChatService) -> AccountLineUi?,
): List<ChatServiceCardUi> {
    val catalog = application.catalog
    val chatModels = snapshot.chatModels
    return chatModels.addedServices.map { service ->
        val isOpenRouter = service == ChatService.OPENROUTER
        ChatServiceCardUi(
            serviceKey = service.key,
            displayName = serviceNameOf(service, application),
            apiKey = service.secret?.let(slotFor),
            routing = if (isOpenRouter) routingUiOf(snapshot.routing.openRouter) else null,
            account = accountFor(service),
            models = chatModels.modelsByService[service].orEmpty().map { modelId ->
                val key = ModelKey.of(service.key, modelId)
                val info = catalog.find(key)
                ServiceModelUi(
                    key = key,
                    name = info?.displayName ?: modelId,
                    contextWindowTokens = info?.contextWindowTokens,
                    inputPricePerMillion = info?.inputUsdPerMillion,
                    outputPricePerMillion = info?.outputUsdPerMillion,
                    cachedInputPricePerMillion = info?.cachedInputUsdPerMillion,
                    isDefault = key == chatModels.defaultModelKey,
                    routingOverride = if (isOpenRouter) snapshot.routing.overrides[key]?.let(::routingUiOf) else null,
                    thinking = if (ThinkingSupport.isSupported(key, info)) thinkingChoiceOf(snapshot.thinkingLevels[key]) else null,
                )
            },
        )
    }
}

/** Gemini's key also serves YouTube summaries and Ollama's also serves web search, so those stay. */
private fun removeKeyIfUnshared(service: ChatService, application: JonakiApplication) {
    val secret = service.secret ?: return
    val sharedWithOtherFeatures = secret == SecretName.GEMINI || secret == SecretName.OLLAMA
    if (!sharedWithOtherFeatures) {
        application.secrets.remove(secret)
    }
}

@Composable
private fun AddModelsRoute(application: JonakiApplication, serviceKey: String, onFinished: () -> Unit) {
    val service = ChatService.byKey(serviceKey)
    if (service == null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val snapshot by application.settings.snapshot.collectAsState()
    val alreadyAdded = snapshot.chatModels.modelsByService[service].orEmpty().toSet()
    var models by remember(serviceKey) { mutableStateOf(application.catalog.models(serviceKey)) }
    LaunchedEffect(serviceKey) {
        // Services with their own list add the ids they serve today; listing is free (D-105).
        refreshServiceModels(application, service)
        models = application.catalog.models(serviceKey)
    }
    AddModelsScreen(
        state = AddModelsUiState(
            serviceDisplayName = serviceNameOf(service, application),
            models = models.map { info ->
                AddableModelUi(
                    id = info.modelId,
                    name = info.displayName,
                    contextWindowTokens = info.contextWindowTokens,
                    inputPricePerMillion = info.inputUsdPerMillion,
                    outputPricePerMillion = info.outputUsdPerMillion,
                    cachedInputPricePerMillion = info.cachedInputUsdPerMillion,
                    isAdded = info.modelId in alreadyAdded,
                )
            },
        ),
        onClose = onFinished,
        onDone = { modelIds ->
            application.settings.updateChatModels { chatModels ->
                modelIds.fold(chatModels) { updated, modelId -> updated.addModel(service, modelId) }
            }
            onFinished()
        },
    )
}

/**
 * Asks the service's GET /models when it has one. A service that needs a
 * key is asked only once one is saved; Ollama Cloud lists its models to anyone.
 */
private suspend fun refreshServiceModels(application: JonakiApplication, service: ChatService) {
    val preset = ChatProviders.presetOrNull(service) ?: return
    if (!preset.listsModels) {
        return
    }
    val apiKey = service.secret?.let { secret -> withContext(Dispatchers.IO) { application.secrets.read(secret) } }
    val mayListWithoutKey = !preset.needsApiKey || service == ChatService.OLLAMA_CLOUD
    if (apiKey == null && !mayListWithoutKey) {
        return
    }
    application.catalog.refreshServiceModels(service.key, preset.baseUrl, apiKey)
}

/** "Ollama on this network" is a phrase rather than a brand, so it follows the app's language. */
private fun serviceNameOf(service: ChatService, application: JonakiApplication): String {
    if (service == ChatService.OLLAMA_LOCAL) {
        return application.getString(R.string.service_ollama_local)
    }
    return service.displayName
}

private fun hintFor(service: ChatService): String = when (service) {
    ChatService.OPENROUTER -> "openrouter.ai"
    ChatService.DEEPSEEK -> "deepseek.com"
    ChatService.GEMINI -> "Google"
    ChatService.GLM -> "z.ai"
    ChatService.MIMO -> "Xiaomi"
    ChatService.OLLAMA_CLOUD -> "ollama.com"
    ChatService.OLLAMA_LOCAL -> "localhost:11434"
    ChatService.OPENAI -> "openai.com"
    ChatService.MINIMAX -> "minimax.io"
    ChatService.QWEN -> "Alibaba Cloud"
    ChatService.LOCAL -> "llama.cpp"
}

private fun displayNameOf(service: SearchService): String = when (service) {
    SearchService.TAVILY -> "Tavily"
    SearchService.OLLAMA -> "Ollama"
    SearchService.EXA -> "Exa"
}

private fun routingOf(routing: RoutingUi): OpenRouterRouting = when (routing) {
    RoutingUi.PRIVATE_THEN_CHEAPEST -> OpenRouterRouting.PRIVATE_THEN_CHEAPEST
    RoutingUi.CHEAPEST -> OpenRouterRouting.CHEAPEST
    RoutingUi.AUTOMATIC -> OpenRouterRouting.AUTOMATIC
}

private fun routingUiOf(routing: OpenRouterRouting): RoutingUi = when (routing) {
    OpenRouterRouting.PRIVATE_THEN_CHEAPEST -> RoutingUi.PRIVATE_THEN_CHEAPEST
    OpenRouterRouting.CHEAPEST -> RoutingUi.CHEAPEST
    OpenRouterRouting.AUTOMATIC -> RoutingUi.AUTOMATIC
}

private fun decisionOf(choice: ApprovalChoice): ApprovalDecision = when (choice) {
    ApprovalChoice.ALLOW_ONCE -> ApprovalDecision.ALLOW_ONCE
    ApprovalChoice.ALLOW_ALL_IN_THREAD -> ApprovalDecision.ALLOW_ALL_IN_THREAD
    ApprovalChoice.DENY -> ApprovalDecision.DENY
}

private fun approvalChoiceOf(mode: ApprovalMode): ApprovalModeChoice = when (mode) {
    ApprovalMode.ASK -> ApprovalModeChoice.ASK
    ApprovalMode.AUTO -> ApprovalModeChoice.AUTO
    ApprovalMode.BYPASS -> ApprovalModeChoice.BYPASS
}

private fun approvalModeOf(choice: ApprovalModeChoice): ApprovalMode = when (choice) {
    ApprovalModeChoice.ASK -> ApprovalMode.ASK
    ApprovalModeChoice.AUTO -> ApprovalMode.AUTO
    ApprovalModeChoice.BYPASS -> ApprovalMode.BYPASS
}

internal fun themeModeOf(choice: ThemeChoice): ThemeMode = when (choice) {
    ThemeChoice.SYSTEM -> ThemeMode.SYSTEM
    ThemeChoice.LIGHT -> ThemeMode.LIGHT
    ThemeChoice.DARK -> ThemeMode.DARK
}

private fun themeChoiceOf(mode: ThemeMode): ThemeChoice = when (mode) {
    ThemeMode.SYSTEM -> ThemeChoice.SYSTEM
    ThemeMode.LIGHT -> ThemeChoice.LIGHT
    ThemeMode.DARK -> ThemeChoice.DARK
}
