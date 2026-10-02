package app.jonaki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.jonaki.JonakiApplication
import app.jonaki.core.agent.ApprovalDecision
import app.jonaki.core.model.Role
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.storage.HistoryMapper
import app.jonaki.core.storage.ThreadSummary
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode
import app.jonaki.core.ui.resolvesToDark
import app.jonaki.feature.chat.ApprovalChoice
import app.jonaki.feature.chat.ChatScreen
import app.jonaki.feature.chat.ChatUiState
import app.jonaki.feature.settings.KeySlot
import app.jonaki.feature.settings.ProviderChoice
import app.jonaki.feature.settings.SearchServiceRow
import app.jonaki.feature.settings.SettingsActions
import app.jonaki.feature.settings.SettingsScreen
import app.jonaki.feature.settings.SettingsUiState
import app.jonaki.feature.threads.ThreadListScreen
import app.jonaki.feature.threads.ThreadListUiState
import app.jonaki.feature.threads.ThreadRow
import app.jonaki.feature.threads.ThreadRunState
import app.jonaki.feature.settings.moveInOrder
import app.jonaki.run.ChatProviders
import app.jonaki.settings.ChatService
import app.jonaki.settings.SearchService
import app.jonaki.settings.SecretName
import app.jonaki.settings.ThemeChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Routes are plain strings so they survive process death through rememberSaveable. */
private const val ROUTE_THREADS = "threads"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHAT_PREFIX = "chat:"

/** A thread is only created on its first message, so backing out leaves no empty thread. */
private const val NEW_THREAD = "new"

@Composable
fun JonakiApp(application: JonakiApplication, onDarkThemeChange: (Boolean) -> Unit) {
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val themeMode = themeModeOf(settingsSnapshot.theme)
    val isDark = resolvesToDark(themeMode)
    LaunchedEffect(isDark) { onDarkThemeChange(isDark) }
    JonakiTheme(themeMode = themeMode) {
        var route by rememberSaveable { mutableStateOf(ROUTE_THREADS) }
        when {
            route == ROUTE_SETTINGS -> {
                BackHandler { route = ROUTE_THREADS }
                SettingsRoute(application, onBack = { route = ROUTE_THREADS })
            }
            route.startsWith(ROUTE_CHAT_PREFIX) -> {
                BackHandler { route = ROUTE_THREADS }
                ChatRoute(
                    application = application,
                    threadId = route.removePrefix(ROUTE_CHAT_PREFIX),
                    onBack = { route = ROUTE_THREADS },
                    onThreadCreated = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                )
            }
            else -> ThreadsRoute(
                application = application,
                onOpenThread = { threadId -> route = ROUTE_CHAT_PREFIX + threadId },
                onNewThread = { route = ROUTE_CHAT_PREFIX + NEW_THREAD },
                onOpenSettings = { route = ROUTE_SETTINGS },
            )
        }
    }
}

@Composable
private fun ThreadsRoute(
    application: JonakiApplication,
    onOpenThread: (String) -> Unit,
    onNewThread: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val summaries by remember { application.database.threadDao().observeSummaries() }.collectAsState(initial = emptyList())
    val running by application.runner.runningThreadIds.collectAsState()
    val approvals by application.runner.pendingApprovals.collectAsState()
    val stepCounts by application.runner.runStepCounts.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
        )
    }
    ThreadListScreen(
        state = ThreadListUiState(threads = rows, searchQuery = searchQuery),
        nowMillis = nowMillis,
        onSearchQueryChange = { query -> searchQuery = query },
        onThreadClick = onOpenThread,
        onNewThread = onNewThread,
        onOpenSettings = onOpenSettings,
    )
}

private fun lastLineOf(summary: ThreadSummary): String =
    PreviewText.of(summary.lastText.orEmpty(), isUserMessage = summary.lastRole == Role.USER.name)

@Composable
private fun ChatRoute(
    application: JonakiApplication,
    threadId: String,
    onBack: () -> Unit,
    onThreadCreated: (String) -> Unit,
) {
    val database = application.database
    val runner = application.runner
    val isNew = threadId == NEW_THREAD
    val thread by remember(threadId) {
        if (isNew) flowOf(null) else database.threadDao().observe(threadId)
    }.collectAsState(initial = null)
    val messages by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.messageDao().observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val steps by remember(threadId) {
        if (isNew) flowOf(emptyList()) else database.stepDao().observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val running by runner.runningThreadIds.collectAsState()
    val approvals by runner.pendingApprovals.collectAsState()
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    var draft by rememberSaveable(threadId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val isRunning = threadId in running
    val pending = approvals[threadId]
    val webSearchEnabled = thread?.webSearchEnabled ?: !settingsSnapshot.webSearchOffInNewThreads
    val state = ChatUiState(
        title = thread?.title.orEmpty(),
        modelLabel = runner.modelKeyFor(thread)?.let(ModelKey::modelOf).orEmpty(),
        webSearchEnabled = webSearchEnabled,
        items = ChatItems.build(messages, steps, isRunning, pending?.toolCall),
        isRunning = isRunning,
        draft = draft,
    )
    ChatScreen(
        state = state,
        onBack = onBack,
        onDraftChange = { text -> draft = text },
        onSend = {
            val text = draft
            draft = ""
            scope.launch {
                val targetThreadId = if (isNew) runner.createThread() else threadId
                runner.send(targetThreadId, text)
                if (isNew) {
                    onThreadCreated(targetThreadId)
                }
            }
        },
        onStop = { runner.stop(threadId) },
        onApprovalChoice = { _, choice -> runner.answerApproval(threadId, decisionOf(choice)) },
        onRetry = { runner.retry(threadId) },
        onWebSearchChange = { enabled ->
            if (!isNew) {
                scope.launch { runner.setWebSearchEnabled(threadId, enabled) }
            }
        },
    )
}

@Composable
private fun SettingsRoute(application: JonakiApplication, onBack: () -> Unit) {
    val settings = application.settings
    val secrets = application.secrets
    val snapshot by settings.snapshot.collectAsState()
    val savedKeys by secrets.names.collectAsState()
    // Interim screen until the D-028 service cards replace it: the starred model's
    // service is the selected provider, and the field edits the starred model's id.
    val defaultModelKey = snapshot.chatModels.defaultModelKey
    val selectedService = defaultModelKey?.let { ChatService.byKey(ModelKey.serviceOf(it)) } ?: ChatService.OPENROUTER
    // The field keeps its own text, so clearing it does not refill it from settings.
    var modelText by remember(defaultModelKey) { mutableStateOf(defaultModelKey?.let(ModelKey::modelOf).orEmpty()) }
    val interimServices = listOf(ChatService.OPENROUTER, ChatService.DEEPSEEK)

    val state = SettingsUiState(
        providers = interimServices.map { service ->
            val secret = service.secret!!
            ProviderChoice(service.key, service.displayName, KeySlot(slotIdOf(secret), secret in savedKeys))
        },
        selectedProviderKey = selectedService.key,
        model = modelText,
        geminiKey = KeySlot(slotIdOf(SecretName.GEMINI), SecretName.GEMINI in savedKeys),
        searchServices = snapshot.searchOrder.map { service ->
            SearchServiceRow(service.name, displayNameOf(service), KeySlot(slotIdOf(service.secret), service.secret in savedKeys))
        },
        webSearchOffInNewThreads = snapshot.webSearchOffInNewThreads,
        themeMode = themeModeOf(snapshot.theme),
    )
    val actions = SettingsActions(
        onBack = onBack,
        onProviderSelect = { key ->
            val service = ChatService.byKey(key) ?: ChatService.OPENROUTER
            settings.updateChatModels { models ->
                val firstModel = models.modelsByService[service].orEmpty().firstOrNull()
                if (firstModel != null) {
                    models.setDefault(ModelKey.of(service.key, firstModel))
                } else {
                    models.addModel(service, ChatProviders.defaultModel(service))
                        .setDefault(ModelKey.of(service.key, ChatProviders.defaultModel(service)))
                }
            }
        },
        onModelChange = { model ->
            modelText = model
            val trimmed = model.trim()
            if (trimmed.isNotEmpty() && defaultModelKey != null) {
                settings.updateChatModels { models ->
                    models.removeModel(defaultModelKey)
                        .addModel(selectedService, trimmed)
                        .setDefault(ModelKey.of(selectedService.key, trimmed))
                }
            }
        },
        onKeySave = { slotId, value -> secrets.save(secretOf(slotId), value) },
        onKeyClear = { slotId -> secrets.remove(secretOf(slotId)) },
        onSearchServiceMove = { serviceKey, offset ->
            settings.update { current ->
                val names = moveInOrder(current.searchOrder.map { it.name }, serviceKey, offset)
                current.copy(searchOrder = names.map { name -> SearchService.valueOf(name) })
            }
        },
        onWebSearchOffInNewThreadsChange = { off -> settings.update { current -> current.copy(webSearchOffInNewThreads = off) } },
        onThemeModeChange = { mode -> settings.update { current -> current.copy(theme = themeChoiceOf(mode)) } },
    )
    SettingsScreen(state = state, actions = actions)
}

private fun slotIdOf(secret: SecretName): String = secret.name

private fun secretOf(slotId: String): SecretName = SecretName.valueOf(slotId)

private fun displayNameOf(service: SearchService): String = when (service) {
    SearchService.TAVILY -> "Tavily"
    SearchService.OLLAMA -> "Ollama"
    SearchService.EXA -> "Exa"
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
