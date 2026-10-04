package app.jonaki.ui

import app.jonaki.core.agent.ContextBreakdown
import app.jonaki.core.agent.ContextPartKind
import app.jonaki.core.storage.CompactionPlan
import app.jonaki.feature.chat.ContextPartUi
import app.jonaki.feature.chat.ContextPartUiKind
import app.jonaki.feature.chat.ContextUi

/** Turns a thread's context breakdown into what the context sheet shows (D-081). */
object ContextSheetState {
    /**
     * [lastInputTokens] is what the ring shows, the service's count for the
     * latest request. The sheet opens only from the ring, which shows only
     * when the model's window is known.
     */
    fun of(
        breakdown: ContextBreakdown,
        lastInputTokens: Int?,
        contextWindowTokens: Int,
        memoryFacts: List<String> = emptyList(),
    ): ContextUi {
        val use = breakdown.measure(lastInputTokens, contextWindowTokens)
        return ContextUi(
            windowTokens = contextWindowTokens,
            usedTokens = use.totalTokens,
            totalIsReported = use.totalIsReported,
            parts = use.parts.map { part -> ContextPartUi(uiKindOf(part.kind), part.tokens, part.count) },
            freeTokens = use.freeTokens ?: 0,
            // The same threshold the compactor checks after each run.
            compactAtTokens = CompactionPlan.thresholdTokens(contextWindowTokens),
            memoryFacts = memoryFacts,
        )
    }

    private fun uiKindOf(kind: ContextPartKind): ContextPartUiKind = when (kind) {
        ContextPartKind.SYSTEM_PROMPT -> ContextPartUiKind.SYSTEM_PROMPT
        ContextPartKind.TOOLS -> ContextPartUiKind.TOOLS
        ContextPartKind.SKILLS -> ContextPartUiKind.SKILLS
        ContextPartKind.MEMORY -> ContextPartUiKind.MEMORY
        ContextPartKind.SUMMARY -> ContextPartUiKind.SUMMARY
        ContextPartKind.MESSAGES -> ContextPartUiKind.MESSAGES
        ContextPartKind.TOOL_RESULTS -> ContextPartUiKind.TOOL_RESULTS
        ContextPartKind.IMAGES -> ContextPartUiKind.IMAGES
    }
}
