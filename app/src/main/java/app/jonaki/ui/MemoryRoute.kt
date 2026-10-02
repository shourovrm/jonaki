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
import app.jonaki.feature.memory.MemoryActions
import app.jonaki.feature.memory.MemoryScreen
import app.jonaki.memory.MemoryEdits
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The memory screen for one thread, or for the global facts when [threadId]
 * is null (opened from Settings).
 */
@Composable
internal fun MemoryRoute(
    application: JonakiApplication,
    threadId: String?,
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
    val pendingFacts by remember(threadId) {
        // Inside a thread, its waiting facts are already among threadFacts.
        if (threadId == null) memoryDao.observePendingReview() else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val threads by remember { database.threadDao().observeAll() }.collectAsState(initial = emptyList())

    val allFacts = threadFacts + globalFacts + pendingFacts
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
    )
    val actions = MemoryActions(
        onBack = onBack,
        onAdd = { text, isGlobal -> scope.launch { edits.add(text, if (isGlobal) null else threadId) } },
        onEdit = { factId, text -> scope.launch { edits.edit(factId, text) } },
        onDelete = { factId -> scope.launch { edits.delete(factId) } },
        onPinChange = { factId, pinned -> scope.launch { edits.setPinned(factId, pinned) } },
        onPromote = { factId -> scope.launch { edits.promote(factId) } },
        onOpenSource = { factId ->
            val sourceId = allFacts.firstOrNull { fact -> fact.id == factId }?.sourceMessageId
            val source = sourceId?.let(sourceMessages::get)
            if (source != null) {
                onOpenMessage(source.threadId, source.id)
            }
        },
        onKeep = { factId -> scope.launch { edits.keep(factId) } },
        onReviewModeChange = { enabled ->
            application.settings.update { current -> current.copy(reviewExtractedMemories = enabled) }
        },
    )
    MemoryScreen(state = state, actions = actions)
}
