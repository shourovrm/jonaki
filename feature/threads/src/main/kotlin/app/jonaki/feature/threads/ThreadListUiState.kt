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
    /** Projects for the filter chips, in name order (D-110). */
    val projects: List<ProjectUi> = emptyList(),
    /** The project whose threads show; null shows all threads. */
    val selectedProjectId: String? = null,
    /** Models a project can start its threads with, for the project dialog. */
    val projectModelOptions: List<ProjectModelOption> = emptyList(),
    /** "Left to set up": what the first-run cards did not get; empty hides the list (D-184). */
    val setupLeft: List<SetupLeft> = emptyList(),
)

/** One thing the first-run cards left undone. */
enum class SetupLeft {
    MODEL,
    WEB_SEARCH,
    NOTIFICATIONS,
}

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
    val projectId: String? = null,
    /** Shown before the last line when all threads show. */
    val projectName: String? = null,
    /** Marked with a lock; deleted a day after its last message (D-111). */
    val incognito: Boolean = false,
)

/** A project that groups threads (D-110). */
@Immutable
data class ProjectUi(
    val id: String,
    val name: String,
    val instructions: String,
    /** Null starts new threads with the default model. */
    val modelKey: String?,
    /** The model's display name, for the project header; null when unset. */
    val modelName: String?,
)

/** What the project dialog saves. */
@Immutable
data class ProjectDraft(
    val name: String,
    val instructions: String,
    val modelKey: String?,
)

@Immutable
data class ProjectModelOption(
    val key: String,
    val name: String,
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
        /**
         * Weekday and month names follow the app's [locale] (M11); digits stay
         * ASCII because java.time's default DecimalStyle does not localise them.
         */
        fun of(updatedAtMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale = Locale.ENGLISH): ThreadTimeLabel {
            if (nowMillis - updatedAtMillis in 0 until 60_000) {
                return Now
            }
            val updated = Instant.ofEpochMilli(updatedAtMillis).atZone(zone)
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val daysAgo = ChronoUnit.DAYS.between(updated.toLocalDate(), now.toLocalDate())
            val pattern = when {
                daysAgo == 0L -> "HH:mm"
                daysAgo in 1..6 -> "EEE"
                updated.year == now.year -> "d MMM"
                else -> "d MMM yyyy"
            }
            return Text(DateTimeFormatter.ofPattern(pattern, locale).format(updated))

        }
    }
}

/** The threads of one project, or every thread when [selectedProjectId] is null. */
fun threadsInProject(threads: List<ThreadRow>, selectedProjectId: String?): List<ThreadRow> {
    if (selectedProjectId == null) {
        return threads
    }
    return threads.filter { thread -> thread.projectId == selectedProjectId }
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
