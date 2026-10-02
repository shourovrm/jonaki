package app.jonaki.run

import app.jonaki.core.agent.ConversationSummary
import app.jonaki.core.model.Role
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.core.storage.CompactionEntity
import app.jonaki.core.storage.CompactionPlan
import app.jonaki.core.storage.JonakiDatabase
import java.util.UUID

/**
 * Summarises the older part of a thread once its context is 70 % full (M4
 * step 6, D-033). It runs after a run ends, so it never happens during a
 * tool run and the user never waits for it; the original messages stay.
 */
class ThreadCompactor(
    private val database: JonakiDatabase,
    private val backgroundModel: BackgroundModel,
    private val catalog: ModelCatalog,
    private val clock: () -> Long,
) {
    suspend fun compactIfDue(threadId: String, threadModelKey: String?) {
        val rows = database.messageDao().listThread(threadId)
        // Background rows carry the size of their own requests, not of the chat's context.
        val lastInputTokens = rows.lastOrNull { row -> row.role == Role.ASSISTANT.name && row.inputTokens != null }?.inputTokens
        val contextWindowTokens = threadModelKey?.let { modelKey -> catalog.find(modelKey)?.contextWindowTokens }
        if (!CompactionPlan.isNeeded(lastInputTokens, contextWindowTokens)) {
            return
        }
        val compactionDao = database.compactionDao()
        val previous = compactionDao.latestForThread(threadId)
        val cut = CompactionPlan.cutPosition(rows, previous?.upToPosition) ?: return
        val transcript = CompactionPlan.transcript(rows, previous?.upToPosition, cut)
        val answer = backgroundModel.complete(
            threadId = threadId,
            threadModelKey = threadModelKey,
            systemPrompt = ConversationSummary.SYSTEM_PROMPT,
            userText = ConversationSummary.requestText(previous?.summaryText, transcript),
            maxOutputTokens = ConversationSummary.MAX_SUMMARY_TOKENS,
        )
        // A failed summary leaves the thread whole; the next run's end tries again.
        if (answer !is BackgroundAnswer.Success || answer.text.isBlank()) {
            return
        }
        compactionDao.insert(
            CompactionEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                upToPosition = cut,
                summaryText = answer.text.trim(),
                createdAtMillis = clock(),
                model = answer.modelKey,
                // The call's cost is already in its hidden usage row (D-036).
                costUsd = null,
            ),
        )
    }
}
