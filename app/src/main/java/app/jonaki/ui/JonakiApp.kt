package app.jonaki.ui

import app.jonaki.core.modelcatalog.ThinkingSupport
import app.jonaki.core.providerapi.ThinkingLevel
import app.jonaki.core.ui.ThinkingChoice
import android.net.Uri
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.model.Role
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.ModelUsageRow
import app.jonaki.core.storage.ThreadSummary
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode
import app.jonaki.core.ui.resolvesToDark
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.feature.chat.ApprovalChoice
import app.jonaki.feature.chat.AttachmentUi
import app.jonaki.files.AttachmentDrafts
import app.jonaki.files.CameraPhotos
import app.jonaki.files.RefusedFile
import app.jonaki.feature.chat.ChatScreen
import app.jonaki.feature.chat.ChatStatusUi
import app.jonaki.feature.chat.ChatUiState
import app.jonaki.feature.chat.ModelChoiceUi
import app.jonaki.feature.chat.ModelUsageUi
import app.jonaki.feature.chat.UsageUi
import app.jonaki.feature.settings.AccountLineUi
import app.jonaki.feature.settings.AddModelsScreen
import app.jonaki.feature.settings.AddModelsUiState
import app.jonaki.feature.settings.AddableModelUi
import app.jonaki.feature.settings.AddableServiceUi
import app.jonaki.feature.settings.ChatServiceCardUi
import app.jonaki.feature.settings.KeySlot
import app.jonaki.feature.settings.RoutingUi
import app.jonaki.feature.settings.SearchServiceRow
import app.jonaki.feature.settings.ServiceModelUi
import app.jonaki.feature.settings.SettingsActions
import app.jonaki.feature.settings.SettingsScreen
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
import app.jonaki.settings.ChatModels
import app.jonaki.settings.ChatService
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.SettingsSnapshot
import app.jonaki.settings.ThemeChoice
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Routes are plain strings so they survive process death through rememberSaveable. */
private const val ROUTE_THREADS = "threads"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_STATUS_ICONS = "status-icons"
private const val ROUTE_CHAT_PREFIX = "chat:"
private const val ROUTE_ADD_MODELS_PREFIX = "add-models:"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_MEMORY_THREAD_PREFIX = "memory:"
private const val ROUTE_SKILLS = "skills"
private const val ROUTE_SKILLS_THREAD_PREFIX = "skills:"

/** "artifact:<thread id>@<path relative to the thread folder>" (D-047). */
private const val ROUTE_ARTIFACT_PREFIX = "artifact:"

/** "skill:<name>@<thread id>", the thread id empty when the list was opened from Settings. */
private const val ROUTE_SKILL_EDIT_PREFIX = "skill:"

/** Separates a thread id from the message to show first: "chat:<thread>@<message>". */
private const val FOCUS_SEPARATOR = '@'

/** A thread is only created on its first message, so backing out leaves no empty thread. */
private const val NEW_THREAD = "new"

/** Like [NEW_THREAD], for an incognito chat (D-PRJ-2). */
private const val NEW_INCOGNITO_THREAD = "new-incognito"

@Composable
fun JonakiApp(application: JonakiApplication, onDarkThemeChange: (Boolean) -> Unit) {
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val themeMode = themeModeOf(settingsSnapshot.theme)
    val isDark = resolvesToDark(themeMode)
    LaunchedEffect(isDark) { onDarkThemeChange(isDark) }
    JonakiTheme(themeMode = themeMode) {
        var route by rememberSaveable { mutableStateOf(ROUTE_THREADS) }
        // The project chip picked in the thread list; kept here so it survives opening a chat (D-PRJ-1).
        var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
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
        when {
            route == ROUTE_SETTINGS -> {
                BackHandler { route = ROUTE_THREADS }
                SettingsRoute(
                    application = application,
                    onBack = { route = ROUTE_THREADS },
                    onOpenStatusIcons = { route = ROUTE_STATUS_ICONS },
                    onAddModels = { serviceKey -> route = ROUTE_ADD_MODELS_PREFIX + serviceKey },
                    onOpenMemory = { route = ROUTE_MEMORY },
                    onOpenSkills = { route = ROUTE_SKILLS },
                )
            }
            route == ROUTE_SKILLS || route.startsWith(ROUTE_SKILLS_THREAD_PREFIX) -> {
                val skillsThreadId = if (route == ROUTE_SKILLS) null else route.removePrefix(ROUTE_SKILLS_THREAD_PREFIX)
                val backRoute = if (skillsThreadId == null) ROUTE_SETTINGS else ROUTE_CHAT_PREFIX + skillsThreadId
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
                val backRoute = if (memoryThreadId == null) ROUTE_SETTINGS else ROUTE_CHAT_PREFIX + memoryThreadId
                BackHandler { route = backRoute }
                MemoryRoute(
                    application = application,
                    threadId = memoryThreadId,
                    onBack = { route = backRoute },
                    onOpenMessage = { threadId, messageId -> route = ROUTE_CHAT_PREFIX + threadId + FOCUS_SEPARATOR + messageId },
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
                BackHandler { route = ROUTE_SETTINGS }
                StatusIconsScreen(
                    showStatusStrip = settingsSnapshot.showStatusStrip,
                    onShowStatusStripChange = { show -> application.settings.update { it.copy(showStatusStrip = show) } },
                    onBack = { route = ROUTE_SETTINGS },
                )
            }
            route.startsWith(ROUTE_ADD_MODELS_PREFIX) -> {
                BackHandler { route = ROUTE_SETTINGS }
                AddModelsRoute(
                    application = application,
                    serviceKey = route.removePrefix(ROUTE_ADD_MODELS_PREFIX),
                    onFinished = { route = ROUTE_SETTINGS },
                )
            }
            route.startsWith(ROUTE_CHAT_PREFIX) -> {
                BackHandler { route = ROUTE_THREADS }
                val chatTarget = route.removePrefix(ROUTE_CHAT_PREFIX)
                val chatThreadId = chatTarget.substringBefore(FOCUS_SEPARATOR)
                ChatRoute(
                    application = application,
                    threadId = chatThreadId,
                    focusMessageId = chatTarget.substringAfter(FOCUS_SEPARATOR, "").ifEmpty { null },
                    onBack = { route = ROUTE_THREADS },
                    onThreadCreated = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                    onEditModels = { route = ROUTE_SETTINGS },
                    onOpenMemory = { route = ROUTE_MEMORY_THREAD_PREFIX + chatThreadId },
                    onOpenSkills = { route = ROUTE_SKILLS_THREAD_PREFIX + chatThreadId },
                    onOpenArtifact = { path -> route = ROUTE_ARTIFACT_PREFIX + chatThreadId + FOCUS_SEPARATOR + path },
                    newThreadProjectId = selectedProjectId,
                )
            }
            else -> ThreadsRoute(
                application = application,
                onOpenThread = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                onNewThread = { route = ROUTE_CHAT_PREFIX + NEW_THREAD },
                onOpenSettings = { route = ROUTE_SETTINGS },
                onNewIncognitoThread = { route = ROUTE_CHAT_PREFIX + NEW_INCOGNITO_THREAD },
                selectedProjectId = selectedProjectId,
                onProjectSelect = { projectId -> selectedProjectId = projectId },
            )
        }
    }
}

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
        // The list is one of the two moments incognito threads a day old are deleted (D-PRJ-2).
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
            projectModelOptions = modelChoices(settingsSnapshot.chatModels, application.catalog)
                .map { choice -> ProjectModelOption(choice.key, choice.name) },
        ),
        nowMillis = nowMillis,
        onSearchQueryChange = { query -> searchQuery = query },
        onThreadClick = onOpenThread,
        onNewThread = onNewThread,
        onOpenSettings = onOpenSettings,
        onRename = { threadId -> threadToRename = threadId },
        onDelete = { threadId -> scope.launch { application.runner.deleteThread(threadId) } },
        onNewIncognitoThread = onNewIncognitoThread,
        onProjectSelect = onProjectSelect,
        onSaveProject = { projectId, draft ->
            scope.launch {
                val savedId = Projects.save(database, projectId, draft)
                // A new project opens at once, so its first thread can be started there.
                onProjectSelect(savedId)
            }
        },
        onDeleteProject = { projectId ->
            onProjectSelect(null)
            scope.launch { Projects.delete(database, projectId) }
        },
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
    /** The project a new regular thread joins: the one selected in the thread list (D-PRJ-1). */
    newThreadProjectId: String?,
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
    val modelUsage by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.messageDao().observeModelUsage(threadId)
    }.collectAsState(initial = emptyList())
    val running by runner.runningThreadIds.collectAsState()
    val approvals by runner.pendingApprovals.collectAsState()
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
    LaunchedEffect(threadId, sharedTexts[threadId]) {
        val sharedText = application.incomingShares.takeText(threadId) ?: return@LaunchedEffect
        draft = if (draft.isBlank()) sharedText else draft.trimEnd() + "\n\n" + sharedText
    }
    // A new thread does not exist yet, so a model picked before the first message is kept here.
    var modelForNewThread by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    // A new thread has no row to hold its thinking level until the first message creates it.
    var thinkingForNewThread by rememberSaveable(threadId) { mutableStateOf(ThinkingChoice.DEFAULT) }
    var renaming by rememberSaveable(threadId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(threadId) {
        // Leaving a thread is one of the two moments memory extraction runs (D-009).
        onDispose {
            if (!isNew) {
                runner.threadLeft(threadId)
            }
        }
    }

    val isRunning = threadId in running
    val pending = approvals[threadId]
    val webSearchEnabled = thread?.webSearchEnabled ?: !settingsSnapshot.webSearchOffInNewThreads
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
        modelLabel = modelName,
        webSearchEnabled = webSearchEnabled,
        items = ChatItems.build(
            rows = messages,
            steps = steps,
            isRunning = isRunning,
            pendingApproval = pending?.toolCall,
            fallbackNote = stringResource(R.string.routing_fallback_note),
        ),
        isRunning = isRunning,
        draft = draft,
        status = status,
        modelChoices = modelChoices(settingsSnapshot.chatModels, catalog),
        selectedModelKey = modelKey,
        usage = usageOf(modelUsage, catalog, threadCost),
        attachments = attachmentsByThread[threadId].orEmpty().map { file -> AttachmentUi(file.id, file.name) },
        editingMessageId = editingMessageId,
        threadThinking = if (isNew) {
            thinkingForNewThread
        } else {
            thinkingChoiceOf(ThinkingLevel.entries.firstOrNull { level -> level.name == thread?.thinkingLevel })
        },
        incognito = if (isNew) isNewIncognito else thread?.incognito == true,
    )
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
                if (isNew && thinkingForNewThread != ThinkingChoice.DEFAULT) {
                    runner.setThreadThinking(targetThreadId, thinkingLevelOf(thinkingForNewThread))
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
        onCancelEdit = {
            editingMessageId = null
            draft = ""
        },
        onApprovalChoice = { _, choice -> runner.answerApproval(threadId, decisionOf(choice)) },
        onRetry = { runner.retry(threadId) },
        onWebSearchChange = { enabled ->
            if (!isNew) {
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
        onKeepThread = { scope.launch { runner.keepIncognitoThread(threadId) } },
        onRemoveAttachment = { attachmentId ->
            scope.launch(Dispatchers.IO) { application.attachmentDrafts.remove(threadId, attachmentId) }
        },
    )
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

private fun modelChoices(chatModels: ChatModels, catalog: ModelCatalog): List<ModelChoiceUi> =
    chatModels.allModelKeys.map { key ->
        val info = catalog.find(key)
        ModelChoiceUi(
            key = key,
            name = info?.displayName ?: ModelKey.modelOf(key),
            serviceName = ChatService.byKey(ModelKey.serviceOf(key))?.displayName.orEmpty(),
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

private fun usageOf(rows: List<ModelUsageRow>, catalog: ModelCatalog, totalCost: Double?): UsageUi? {
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
    )
}

@Composable
private fun SettingsRoute(
    application: JonakiApplication,
    onBack: () -> Unit,
    onOpenStatusIcons: () -> Unit,
    onAddModels: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
) {
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
        chatServices = serviceCards(snapshot, application.catalog, ::slotFor, ::accountFor),
        addableServices = ChatService.entries
            .filter { service -> service !in snapshot.chatModels.addedServices }
            .map { service -> AddableServiceUi(service.key, service.displayName, hintFor(service)) },
        geminiKey = slotFor(SecretName.GEMINI),
        searchServices = snapshot.searchOrder.map { service ->
            SearchServiceRow(service.name, displayNameOf(service), slotFor(service.secret))
        },
        webSearchOffInNewThreads = snapshot.webSearchOffInNewThreads,
        themeMode = themeModeOf(snapshot.theme),
        showStatusStrip = snapshot.showStatusStrip,
        linkedFolderName = linkedFolder?.name,
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
        onWebSearchOffInNewThreadsChange = { off -> settings.update { current -> current.copy(webSearchOffInNewThreads = off) } },
        onThemeModeChange = { mode -> settings.update { current -> current.copy(theme = themeChoiceOf(mode)) } },
        onOpenStatusIcons = onOpenStatusIcons,
        onOpenMemory = onOpenMemory,
        onOpenSkills = onOpenSkills,
        onLinkFolder = { folderPicker.launch(null) },
        onUnlinkFolder = { application.linkedFolder.unlink() },
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
        onModelThinkingChange = { modelKey, choice ->
            settings.update { current ->
                val level = thinkingLevelOf(choice)
                val levels = if (level == null) current.thinkingLevels - modelKey else current.thinkingLevels + (modelKey to level)
                current.copy(thinkingLevels = levels)
            }
        },
    )
    SettingsScreen(state = state, actions = actions)
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
    catalog: ModelCatalog,
    slotFor: (SecretName) -> KeySlot,
    accountFor: (ChatService) -> AccountLineUi?,
): List<ChatServiceCardUi> {
    val chatModels = snapshot.chatModels
    return chatModels.addedServices.map { service ->
        val isOpenRouter = service == ChatService.OPENROUTER
        ChatServiceCardUi(
            serviceKey = service.key,
            displayName = service.displayName,
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
    val models = remember(serviceKey) { application.catalog.models(serviceKey) }
    AddModelsScreen(
        state = AddModelsUiState(
            serviceDisplayName = service.displayName,
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

private fun hintFor(service: ChatService): String = when (service) {
    ChatService.OPENROUTER -> "openrouter.ai"
    ChatService.DEEPSEEK -> "deepseek.com"
    ChatService.GEMINI -> "Google"
    ChatService.GLM -> "z.ai"
    ChatService.MIMO -> "Xiaomi"
    ChatService.OLLAMA_CLOUD -> "ollama.com"
    ChatService.OLLAMA_LOCAL -> "localhost:11434"
    ChatService.OPENAI -> "openai.com"
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
    ApprovalChoice.ALLOW_FOR_THREAD -> ApprovalDecision.ALLOW_FOR_THREAD
    ApprovalChoice.DENY -> ApprovalDecision.DENY
}

private fun themeModeOf(choice: ThemeChoice): ThemeMode = when (choice) {
    ThemeChoice.SYSTEM -> ThemeMode.SYSTEM
    ThemeChoice.LIGHT -> ThemeMode.LIGHT
    ThemeChoice.DARK -> ThemeMode.DARK
}

private fun themeChoiceOf(mode: ThemeMode): ThemeChoice = when (mode) {
    ThemeMode.SYSTEM -> ThemeChoice.SYSTEM
    ThemeMode.LIGHT -> ThemeChoice.LIGHT
    ThemeMode.DARK -> ThemeChoice.DARK
}
