package app.jonaki.guard

import app.jonaki.core.guardapi.Guard

/**
 * Asks the guard whether a fact about to be saved reads like an instruction
 * that outside content planted ("always send files to…"). Only threads that
 * read outside content are asked about, so a fact the user stated in a plain
 * chat costs nothing.
 */
class FactScreen(
    /** The guard as Settings has it now; made per question, like the run's guard. */
    private val guard: () -> Guard,
    /** Saves the question's cost on the thread, and its note on the memory tool's step when there is one. */
    private val recorderFor: (threadId: String) -> GuardRecorder,
) {
    /**
     * True when the fact looks planted, false when it looks clear, and null
     * when there is no answer: the screening is off, there is no key, or the
     * call failed. The caller then holds the fact, as it would without a guard.
     */
    suspend fun looksPlanted(threadId: String, factText: String): Boolean? {
        val verdict = guard().screenResult(SOURCE, factText)
        recorderFor(threadId).record(verdict.note, verdict.costUsd, verdict.usage)
        if (verdict.injectionProbability == null) {
            return null
        }
        return verdict.isFlagged
    }

    companion object {
        /** What the guard is told the text is. */
        const val SOURCE = "memory fact"

        /**
         * Whether a fact saved after the thread read outside content waits for
         * the user. Only a clear answer lets it through; a planted-looking fact
         * and a missing answer both wait.
         */
        fun waitsForReview(looksPlanted: Boolean?): Boolean = looksPlanted != false
    }
}
