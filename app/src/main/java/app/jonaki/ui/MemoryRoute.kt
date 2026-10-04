package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.jonaki.JonakiApplication
import app.jonaki.core.storage.MessageEntity
import app.jonaki.feature.memory.FactScopeUi
import app.jonaki.feature.memory.MemoryActions
import app.jonaki.feature.memory.MemoryScreen
import app.jonaki.memory.MemoryEdits
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The memory screen for one thread, for one project when [projectId] is
 * given (D-135), or for the global facts when both are null (opened from
 * Settings). A thread in a project also shows the project's facts.
 */
@Composable
internal fun MemoryRoute(
    application: JonakiApplication,
    threadId: String?,
    projectId: String? = null,
    onBack: () -> Unit,
    /** Opens a thread's chat at a message: the source of a fact. */
    onOpenMessage: (threadId: String, messageId: String) -> Unit,
) {
    val database = application.database
    val memoryDao = database.memoryDao()
    val edits = remember { MemoryEdits(database, System::currentTimeMillis) }
    val scope = rememberCoroutineScope()
    val settingsSnapshot by application.settings.snapshot.collectAsState()
    val thread by remember(threadId) {
        if (threadId == null) flowOf(null) else database.threadDao().observe(threadId)
    }.collectAsState(initial = null)
    val threadFacts by remember(threadId) {
        if (threadId == null) flowOf(emptyList()) else memoryDao.observeThread(threadId)
    }.collectAsState(initial = emptyList())
    val globalFacts by remember { memoryDao.observeGlobal() }.collectAsState(initial = emptyList())
    // The project shown: the one opened, or the thread's own.
    val shownProjectId = projectId ?: thread?.projectId
    val project by remember(shownProjectId) {
        if (shownProjectId == null) flowOf(null) else database.projectDao().observe(shownProjectId)
    }.collectAsState(initial = null)
    val projectFacts by remember(shownProjectId) {
        if (shownProjectId == null) flowOf(emptyList()) else memoryDao.observeProject(shownProjectId)
    }.collectAsState(initial = emptyList())
    val isSettingsView = threadId == null && projectId == null
    val pendingFacts by remember(isSettingsView) {
        // Inside a thread or project, its waiting facts are already among its own facts.
        if (isSettingsView) memoryDao.observePendingReview() else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val threads by remember { database.threadDao().observeAll() }.collectAsState(initial = emptyList())
    val supersededThreadFacts by remember(threadId) {
        if (threadId == null) flowOf(emptyList()) else memoryDao.observeSupersededThread(threadId)
    }.collectAsState(initial = emptyList())
    val supersededProjectFacts by remember(shownProjectId) {
        if (shownProjectId == null) flowOf(emptyList()) else memoryDao.observeSupersededProject(shownProjectId)
    }.collectAsState(initial = emptyList())
    val supersededGlobalFacts by remember { memoryDao.observeSupersededGlobal() }.collectAsState(initial = emptyList())

    val supersededFacts = supersededThreadFacts + (if (project == null) emptyList() else supersededProjectFacts) + supersededGlobalFacts
    val allFacts = threadFacts + projectFacts + globalFacts + pendingFacts + supersededFacts
    val sourceIds = allFacts.mapNotNull { fact -> fact.sourceMessageId }.toSet()
    var sourceMessages by remember { mutableStateOf<Map<String, MessageEntity>>(emptyMap()) }
    LaunchedEffect(sourceIds) {
        sourceMessages = database.messageDao().findAll(sourceIds.toList()).associateBy { message -> message.id }
    }

    val state = MemoryScreenState.build(
        threadId = threadId,
        threadTitle = thread?.title,
        threadFacts = threadFacts,
        globalFacts = globalFacts,
        pendingFacts = pendingFacts,
        sourceMessages = sourceMessages,
        threadTitles = threads.associate { entity -> entity.id to entity.title },
        reviewMode = settingsSnapshot.reviewExtractedMemories,
        projectName = project?.name,
        projectFacts = if (project == null) emptyList() else projectFacts,
        supersededFacts = supersededFacts,
    )
    val actions = MemoryActions(
        onBack = onBack,
        onAdd = { text, factScope ->
            scope.launch {
                when (factScope) {
                    FactScopeUi.THREAD -> edits.add(text, threadId = threadId)
                    FactScopeUi.PROJECT -> edits.add(text, threadId = null, projectId = shownProjectId)
                    FactScopeUi.GLOBAL -> edits.add(text, threadId = null)
                }
            }
        },
        onEdit = { factId, text -> scope.launch { edits.edit(factId, text) } },
        onDelete = { factId -> scope.launch { edits.delete(factId) } },
        onPinChange = { factId, pinned -> scope.launch { edits.setPinned(factId, pinned) } },
        onPromote = { factId -> scope.launch { edits.promote(factId) } },
        onMoveToProject = { factId ->
            val targetProjectId = shownProjectId
            if (targetProjectId != null) {
                scope.launch { edits.moveToProject(factId, targetProjectId) }
            }
        },
        onOpenSource = { factId ->
            val sourceId = allFacts.firstOrNull { fact -> fact.id == factId }?.sourceMessageId
            val source = sourceId?.let(sourceMessages::get)
            if (source != null) {
                onOpenMessage(source.threadId, source.id)
            }
        },
        onKeep = { factId -> scope.launch { edits.keep(factId) } },
        onRestore = { factId -> scope.launch { edits.restore(factId) } },
        onReviewModeChange = { enabled ->
            application.settings.update { current -> current.copy(reviewExtractedMemories = enabled) }
        },
    )
    MemoryScreen(state = state, actions = actions)
}
