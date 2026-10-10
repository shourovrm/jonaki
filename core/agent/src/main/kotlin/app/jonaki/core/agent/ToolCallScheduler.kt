package app.jonaki.core.agent

import app.jonaki.core.model.ToolCall
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Runs the tool calls of one model turn (D-080). Consecutive calls that
 * only read run side by side, at most [maxAtOnce] at a time. Any other call
 * runs alone in its place: the calls before it finish first and the calls
 * after it wait, so approval cards still come one at a time. Inside one
 * group, web_search calls start [searchSpacing] apart, so a burst of
 * searches does not hit the search service's rate limit.
 */
internal class ToolCallScheduler(
    private val timer: WaitTimer,
    private val maxAtOnce: Int = MAX_AT_ONCE,
    private val searchSpacing: Duration = SEARCH_SPACING,
) {
    /**
     * Returns the results in call order. [inCallOrder] gets each result in
     * call order too, as soon as every earlier call has finished, so that
     * saved results and their positions follow the model's order whatever
     * order the calls finish in.
     */
    suspend fun <R> runAll(
        toolCalls: List<ToolCall>,
        runsAlongsideOthers: (ToolCall) -> Boolean,
        run: suspend (ToolCall) -> R,
        inCallOrder: suspend (ToolCall, R) -> Unit,
    ): List<R> {
        val results = mutableListOf<R>()
        for (group in groups(toolCalls, runsAlongsideOthers)) {
            if (group.size == 1) {
                val toolCall = group.single()
                val result = run(toolCall)
                inCallOrder(toolCall, result)
                results += result
            } else {
                results += runSideBySide(group, run, inCallOrder)
            }
        }
        return results
    }

    private suspend fun <R> runSideBySide(
        group: List<ToolCall>,
        run: suspend (ToolCall) -> R,
        inCallOrder: suspend (ToolCall, R) -> Unit,
    ): List<R> = coroutineScope {
        val slots = Semaphore(maxAtOnce)
        val searchStarts = SearchStarts(timer, searchSpacing)
        val ordered = OrderedRelease(group, inCallOrder)
        val running = group.mapIndexed { index, toolCall ->
            async {
                val result = slots.withPermit {
                    if (toolCall.toolName == WEB_SEARCH_TOOL) {
                        searchStarts.awaitTurn()
                    }
                    run(toolCall)
                }
                ordered.finished(index, result)
                result
            }
        }
        running.awaitAll()
    }

    /** Lets each web search of a group start at least the spacing after the one before it. */
    private class SearchStarts(private val timer: WaitTimer, private val spacing: Duration) {
        // Held through the wait, so the next search counts its spacing from this one's start.
        private val lock = Mutex()
        private var anyStarted = false

        suspend fun awaitTurn() {
            lock.withLock {
                if (anyStarted) {
                    timer.wait(spacing)
                }
                anyStarted = true
            }
        }
    }

    /** Hands results on in call order: a finished call waits for every earlier one. */
    private class OrderedRelease<R>(
        private val toolCalls: List<ToolCall>,
        private val deliver: suspend (ToolCall, R) -> Unit,
    ) {
        private val lock = Mutex()
        private val waiting = mutableMapOf<Int, R>()
        private var nextIndex = 0

        suspend fun finished(index: Int, result: R) {
            lock.withLock {
                waiting[index] = result
                while (waiting.containsKey(nextIndex)) {
                    val next = waiting.getValue(nextIndex)
                    waiting.remove(nextIndex)
                    deliver(toolCalls[nextIndex], next)
                    nextIndex += 1
                }
            }
        }
    }

    companion object {
        /** Four calls at once keep a phone's network and memory calm while still saving most of the wait. */
        const val MAX_AT_ONCE = 4

        /** The user's ruling (2026-10-03), against search services' rate limits. */
        val SEARCH_SPACING: Duration = 500.milliseconds

        private const val WEB_SEARCH_TOOL = "web_search"

        /**
         * Declared read-only, yet they can show approval cards: delegate
         * through its subagents, request_tool directly. They run alone, so
         * cards still come one at a time.
         */
        private val RUN_ALONE: Set<String> = setOf("delegate", RequestTool.NAME)

        /**
         * Consecutive calls that may run alongside others form one group;
         * every other call is a group of its own, in the model's order.
         */
        fun groups(toolCalls: List<ToolCall>, runsAlongsideOthers: (ToolCall) -> Boolean): List<List<ToolCall>> {
            val groups = mutableListOf<MutableList<ToolCall>>()
            var lastGroupRunsSideBySide = false
            for (toolCall in toolCalls) {
                val sideBySide = runsAlongsideOthers(toolCall)
                if (sideBySide && lastGroupRunsSideBySide) {
                    groups.last() += toolCall
                } else {
                    groups += mutableListOf(toolCall)
                }
                lastGroupRunsSideBySide = sideBySide
            }
            return groups
        }

        /**
         * True when [toolCall] only reads and cannot ask the user anything.
         * An unknown tool or arguments that are not a JSON object count as
         * not read-only, to be safe. [mayAskAboutAddress] says that the call
         * contacts an address and the thread read outside content, so the
         * call may show a card and runs alone.
         */
        fun readsOnly(tool: Tool?, toolCall: ToolCall, mayAskAboutAddress: Boolean = false): Boolean {
            if (tool == null || tool.name in RUN_ALONE || mayAskAboutAddress) {
                return false
            }
            if (parseToolArguments(toolCall.argumentsJson) == null) {
                return false
            }
            return tool.sideEffect == SideEffect.READ_ONLY
        }
    }
}
