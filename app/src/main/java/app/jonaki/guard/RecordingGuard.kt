package app.jonaki.guard

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.GuardCallContext
import app.jonaki.core.guardapi.GuardUsage
import app.jonaki.core.guardapi.NoGuard
import app.jonaki.core.guardapi.ResultVerdict
import app.jonaki.core.providerapi.Usage
import app.jonaki.guards.jev.JevGuard
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Saves what a guard did. For every question it saves the guard's one-line
 * note on the step of the call that was being run, and the cost of the call
 * as a hidden BACKGROUND row, so that thread, month and usage totals include
 * the guard (D-036). It never changes an answer.
 */
class GuardRecorder(
    private val threadId: String,
    /** Adds a line to the step's guard note; the second line goes under the first. */
    private val appendNote: suspend (toolCallId: String, note: String) -> Unit,
    /** The hidden row that carries a call's usage; `BackgroundModel.saveUsage`. */
    private val saveUsage: suspend (threadId: String, modelKey: String, usage: Usage, costUsd: Double?) -> Unit,
) {
    /** One row per guard question: a long page asked in several parts is saved as one row with the summed cost. */
    suspend fun record(note: String?, costUsd: Double?, usage: GuardUsage?) {
        // The answer is already paid for, so a Stop that arrives now must not lose its cost or note.
        withContext(NonCancellable) {
            val toolCallId = coroutineContext[GuardCallContext]?.toolCallId
            if (note != null && toolCallId != null) {
                appendNote(toolCallId, note)
            }
            if (costUsd != null || usage != null) {
                val tokens = Usage(inputTokens = usage?.inputTokens ?: 0, outputTokens = usage?.outputTokens ?: 0)
                saveUsage(threadId, JEV_MODEL_KEY, tokens, costUsd)
            }
        }
    }

    companion object {
        /** In the usage sheet's "service:model" form, so that the guard shows as its own line. */
        const val JEV_MODEL_KEY = "openrouter:${JevGuard.MODEL}"
    }
}

/** Passes every question to [guard] and gives each answer to [recorder]. */
class RecordingGuard private constructor(
    private val guard: Guard,
    private val recorder: GuardRecorder,
) : Guard {
    override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict {
        val verdict = guard.judgeAction(userRequest, toolName, arguments)
        recorder.record(verdict.note, verdict.costUsd, verdict.usage)
        return verdict
    }

    override suspend fun screenResult(source: String, text: String): ResultVerdict {
        val verdict = guard.screenResult(source, text)
        recorder.record(verdict.note, verdict.costUsd, verdict.usage)
        return verdict
    }

    companion object {
        /** [NoGuard] asks nothing, so there is nothing to record and it stays as it is. */
        fun around(guard: Guard, recorder: GuardRecorder): Guard =
            if (guard === NoGuard) guard else RecordingGuard(guard, recorder)
    }
}
