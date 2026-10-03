package app.jonaki.core.storage

import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import kotlin.math.ceil

/**
 * Which messages a thread's summary covers and how the summary enters the
 * history (M4 step 6, D-033). Compaction runs only between messages, and the
 * original rows stay in the database (D-005).
 */
object CompactionPlan {
    const val SUMMARY_HEADING = "[Summary of the earlier conversation]"
    const val SUMMARY_END = "[End of summary. The conversation continues below.]"

    /** Share of the context window the last request may fill before the thread is compacted. */
    private const val COMPACT_FROM_SHARE = 0.7

    /** Assumed when the catalog does not know the model's window; most current models have at least this. */
    private const val ASSUMED_WINDOW_TOKENS = 128_000

    /** Turns kept word for word, so the model sees the latest exchange exactly. */
    private const val KEPT_USER_TURNS = 2

    private const val TOOL_RESULT_CHARACTERS = 1_500
    private const val TOOL_ARGUMENT_CHARACTERS = 300

    fun isNeeded(lastInputTokens: Int?, contextWindowTokens: Int?): Boolean {
        if (lastInputTokens == null) {
            return false
        }
        return lastInputTokens >= thresholdTokens(contextWindowTokens)
    }

    /** Input tokens of a run's last request from which the thread is summarised after the run. */
    fun thresholdTokens(contextWindowTokens: Int?): Int {
        val window = contextWindowTokens ?: ASSUMED_WINDOW_TOKENS
        return ceil(window * COMPACT_FROM_SHARE).toInt()
    }

    /** The summary as it is sent, at the start of the first kept message. */
    fun summaryBlock(summaryText: String): String = "$SUMMARY_HEADING\n${summaryText.trim()}\n\n$SUMMARY_END"

    /** Messages the summary covers, as the chat shows them: user and assistant rows up to [upToPosition]. */
    fun coveredMessageCount(rows: List<MessageEntity>, upToPosition: Long): Int = rows.count { row ->
        val isShown = row.role == Role.USER.name || row.role == Role.ASSISTANT.name
        isShown && row.position <= upToPosition
    }

    /**
     * The position of the last message to summarise, or null when there is
     * nothing new to summarise. The cut falls just before a user message, so a
     * tool call is never separated from its result.
     */
    fun cutPosition(rows: List<MessageEntity>, afterPosition: Long?, keepUserTurns: Int = KEPT_USER_TURNS): Long? {
        val userPositions = rows.filter { row -> row.role == Role.USER.name }.map { row -> row.position }
        if (userPositions.size <= keepUserTurns) {
            return null
        }
        val firstKeptPosition = userPositions[userPositions.size - keepUserTurns]
        val cut = rows.filter { row -> row.position < firstKeptPosition }.maxOfOrNull { row -> row.position } ?: return null
        if (afterPosition != null && cut <= afterPosition) {
            return null
        }
        return cut
    }

    /**
     * The history the model gets: rows after [upToPosition], with the summary
     * placed at the start of the first kept message. The summary is stored,
     * so these bytes repeat exactly on every request until the next
     * compaction and the prompt cache keeps working.
     */
    fun historyAfter(rows: List<MessageEntity>, summaryText: String?, upToPosition: Long?): List<Message> {
        if (summaryText == null || upToPosition == null) {
            return HistoryMapper.toHistory(rows)
        }
        val history = HistoryMapper.toHistory(rows.filter { row -> row.position > upToPosition }).toMutableList()
        val summaryBlock = summaryBlock(summaryText)
        val first = history.firstOrNull()
        if (first == null || first.role != Role.USER) {
            history.add(0, Message(Role.USER, summaryBlock))
        } else {
            history[0] = first.copy(text = "$summaryBlock\n\n${first.text}")
        }
        return history
    }

    /** The messages after [afterPosition] up to [upToPosition] as plain text for the summarising model. */
    fun transcript(rows: List<MessageEntity>, afterPosition: Long?, upToPosition: Long): String {
        val covered = rows.filter { row ->
            val isAfterPrevious = afterPosition == null || row.position > afterPosition
            isAfterPrevious && row.position <= upToPosition
        }
        return covered.mapNotNull(::transcriptLine).joinToString("\n\n")
    }

    private fun transcriptLine(row: MessageEntity): String? = when (row.role) {
        Role.USER.name -> "User: ${row.text}"
        Role.ASSISTANT.name -> assistantLine(row)
        Role.TOOL.name -> "Tool result: ${shortened(row.text, TOOL_RESULT_CHARACTERS, "[result shortened]")}"
        // Error rows were never sent to the model.
        else -> null
    }

    private fun assistantLine(row: MessageEntity): String? {
        val parts = mutableListOf<String>()
        if (row.text.isNotBlank()) {
            parts += "Assistant: ${row.text}"
        }
        for (call in HistoryMapper.toolCallsFromJson(row.toolCallsJson)) {
            val arguments = shortened(call.argumentsJson, TOOL_ARGUMENT_CHARACTERS, "…")
            parts += "Assistant called ${call.toolName} $arguments"
        }
        return parts.joinToString("\n").ifEmpty { null }
    }

    private fun shortened(text: String, limit: Int, marker: String): String {
        if (text.length <= limit) {
            return text
        }
        return text.take(limit) + " " + marker
    }
}
