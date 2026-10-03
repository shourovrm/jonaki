package app.jonaki.ui

import app.jonaki.core.model.Role
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MessageEntity
import app.jonaki.feature.memory.FactScopeUi
import app.jonaki.feature.memory.MemoryFactUi
import app.jonaki.memory.RoomMemoryStore
import app.jonaki.tools.memory.FactScope
import app.jonaki.feature.memory.MemoryUiState

/** Builds the memory screen's state from saved facts and their source messages. */
object MemoryScreenState {
    /**
     * [threadId] is null when the screen was opened from Settings or from a
     * project: from Settings it shows the global facts and every thread's
     * facts waiting for review; from a project ([projectName] set) the
     * project's facts and the global ones (D-135).
     */
    fun build(
        threadId: String?,
        threadTitle: String?,
        threadFacts: List<MemoryEntity>,
        globalFacts: List<MemoryEntity>,
        pendingFacts: List<MemoryEntity>,
        sourceMessages: Map<String, MessageEntity>,
        threadTitles: Map<String, String>,
        reviewMode: Boolean,
        projectName: String? = null,
        projectFacts: List<MemoryEntity> = emptyList(),
    ): MemoryUiState {
        val ownWaiting = (threadFacts + projectFacts).filter { fact -> fact.pendingReview }
        val isSettingsView = threadId == null && projectName == null
        val waiting = if (isSettingsView) pendingFacts else ownWaiting
        return MemoryUiState(
            threadTitle = if (threadId == null) null else threadTitle.orEmpty(),
            threadFacts = threadFacts.filterNot { fact -> fact.pendingReview }.map { fact -> uiOf(fact, sourceMessages, null) },
            globalFacts = globalFacts.filterNot { fact -> fact.pendingReview }.map { fact -> uiOf(fact, sourceMessages, null) },
            waitingForReview = waiting.map { fact ->
                // From Settings the thread's name says where a fact was found; inside the thread it is obvious.
                val title = if (isSettingsView) fact.threadId?.let(threadTitles::get) else null
                uiOf(fact, sourceMessages, title)
            },
            reviewMode = reviewMode,
            projectName = projectName,
            projectFacts = projectFacts.filterNot { fact -> fact.pendingReview }.map { fact -> uiOf(fact, sourceMessages, null) },
        )
    }

    private fun uiOf(fact: MemoryEntity, sourceMessages: Map<String, MessageEntity>, threadTitle: String?): MemoryFactUi {
        val source = fact.sourceMessageId?.let(sourceMessages::get)
        val preview = source?.let { message -> PreviewText.of(message.text, isUserMessage = message.role == Role.USER.name) }
        return MemoryFactUi(
            id = fact.id,
            text = fact.text,
            pinned = fact.pinned,
            scope = when (RoomMemoryStore.scopeOf(fact)) {
                FactScope.THREAD -> FactScopeUi.THREAD
                FactScope.PROJECT -> FactScopeUi.PROJECT
                FactScope.GLOBAL -> FactScopeUi.GLOBAL
            },
            sourcePreview = preview?.takeIf { it.isNotEmpty() },
            threadTitle = threadTitle,
        )
    }
}
