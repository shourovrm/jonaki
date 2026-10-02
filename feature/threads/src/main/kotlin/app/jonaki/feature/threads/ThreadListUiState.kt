package app.jonaki.feature.threads

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Immutable
data class ThreadListUiState(
    val threads: List<ThreadRow>,
    val searchQuery: String = "",
    /** Spent across all threads this calendar month (D-027); null hides the line. */
    val monthCostUsd: Double? = null,
)

@Immutable
data class ThreadRow(
    val id: String,
    val title: String,
    /** The latest line of the thread: last message, or what the agent is doing. */
    val lastLine: String,
    val updatedAtMillis: Long,
    val runState: ThreadRunState,
    /** The thread's total cost; shown under the time while the thread is idle. */
    val costUsd: Double? = null,
)

sealed interface ThreadRunState {
    data object Idle : ThreadRunState

    data class Running(val stepNumber: Int) : ThreadRunState

    data object WaitingForApproval : ThreadRunState

    data object Failed : ThreadRunState
}

/** The time column of the thread list: "now", a clock time, a weekday or a date. */
sealed interface ThreadTimeLabel {
    data object Now : ThreadTimeLabel

    data class Text(val text: String) : ThreadTimeLabel

    companion object {
        private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
        private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        fun of(updatedAtMillis: Long, nowMillis: Long, zone: ZoneId): ThreadTimeLabel {
            if (nowMillis - updatedAtMillis in 0 until 60_000) {
                return Now
            }
            val updated = Instant.ofEpochMilli(updatedAtMillis).atZone(zone)
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val daysAgo = ChronoUnit.DAYS.between(updated.toLocalDate(), now.toLocalDate())
            val formatter = when {
                daysAgo == 0L -> clock
                daysAgo in 1..6 -> weekday
                updated.year == now.year -> dayMonth
                else -> dayMonthYear
            }
            return Text(formatter.format(updated))
        }
    }
}

fun filterThreads(threads: List<ThreadRow>, query: String): List<ThreadRow> {
    val needle = query.trim()
    if (needle.isEmpty()) {
        return threads
    }
    return threads.filter { thread ->
        thread.title.contains(needle, ignoreCase = true) || thread.lastLine.contains(needle, ignoreCase = true)
    }
}
