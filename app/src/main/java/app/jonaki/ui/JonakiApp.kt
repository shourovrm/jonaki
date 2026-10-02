package app.jonaki.ui

import androidx.activity.compose.BackHandler
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
import app.jonaki.feature.chat.ApprovalChoice
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
import app.jonaki.feature.threads.RenameThreadDialog
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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Routes are plain strings so they survive process death through rememberSaveable. */
private const val ROUTE_THREADS = "threads"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_STATUS_ICONS = "status-icons"
private const val ROUTE_CHAT_PREFIX = "chat:"
private const val ROUTE_ADD_MODELS_PREFIX = "add-models:"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_MEMORY_THREAD_PREFIX = "memory:"

/** Separates a thread id from the message to show first: "chat:<thread>@<message>". */
private const val FOCUS_SEPARATOR = '@'

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
                SettingsRoute(
                    application = application,
                    onBack = { route = ROUTE_THREADS },
                    onOpenStatusIcons = { route = ROUTE_STATUS_ICONS },
                    onAddModels = { serviceKey -> route = ROUTE_ADD_MODELS_PREFIX + serviceKey },
                    onOpenMemory = { route = ROUTE_MEMORY },
                )
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
    val database = application.database
    val summaries by remember { database.threadDao().observeSummaries() }.collectAsState(initial = emptyList())
    val monthCost by remember { database.messageDao().observeCostSince(startOfThisMonthMillis()) }.collectAsState(initial = null)
    val running by application.runner.runningThreadIds.collectAsState()
    val approvals by application.runner.pendingApprovals.collectAsState()
    val stepCounts by application.runner.runStepCounts.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var threadToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
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
        )
    }
    ThreadListScreen(
        state = ThreadListUiState(threads = rows, searchQuery = searchQuery, monthCostUsd = monthCost),
        nowMillis = nowMillis,
        onSearchQueryChange = { query -> searchQuery = query },
        onThreadClick = onOpenThread,
        onNewThread = onNewThread,
        onOpenSettings = onOpenSettings,
        onRename = { threadId -> threadToRename = threadId },
        onDelete = { threadId -> scope.launch { application.runner.deleteThread(threadId) } },
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
) {
    val database = application.database
    val runner = application.runner
    val catalog = application.catalog
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
    var draft by rememberSaveable(threadId) { mutableStateOf("") }
    // A new thread does not exist yet, so a model picked before the first message is kept here.
    var modelForNewThread by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
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
    val modelKey = (if (isNew) modelForNewThread else null) ?: runner.modelKeyFor(thread)
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
                val pickedModel = modelForNewThread
                if (isNew && pickedModel != null) {
                    runner.setThreadModel(targetThreadId, pickedModel)
                }
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
        focusMessageId = focusMessageId,
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
        )
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
        monthOnly = { month -> application.getString(R.string.balance_month_only, month) },
    )
    val serviceCosts by remember { application.database.messageDao().observeServiceCostSince(startOfThisMonthMillis()) }
        .collectAsState(initial = emptyList())
    val appCountedMonth = serviceCosts.associate { row -> row.service to row.costUsd }
    fun accountFor(service: ChatService): AccountLineUi? {
        val balance = service.secret?.let { secret -> balances[secret] }
        val line = BalanceText.cardLine(balance, appCountedMonth[service.key], balanceWords) ?: return null
        return AccountLineUi(line.text, line.isLow, line.monthCountedByApp)
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
    )
    SettingsScreen(state = state, actions = actions)
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
