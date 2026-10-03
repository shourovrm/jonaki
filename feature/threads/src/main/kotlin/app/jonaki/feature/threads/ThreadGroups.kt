package app.jonaki.feature.threads

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The label above a run of threads in the list (D-123), by the day a thread last changed. */
enum class ThreadGroup {
    TODAY,
    YESTERDAY,
    LAST_SEVEN_DAYS,
    OLDER,
    ;

    companion object {
        fun of(updatedAtMillis: Long, nowMillis: Long, zone: ZoneId): ThreadGroup {
            val updatedDay = Instant.ofEpochMilli(updatedAtMillis).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(updatedDay, today)
            // A clock set back can put a thread in the future; it still belongs to today.
            return when {
                daysAgo <= 0L -> TODAY
                daysAgo == 1L -> YESTERDAY
                daysAgo < 7L -> LAST_SEVEN_DAYS
                else -> OLDER
            }
        }
    }
}

/** One line of the thread list: a group label or a thread. */
sealed interface ThreadListEntry {
    val key: String

    data class Label(val group: ThreadGroup, override val key: String) : ThreadListEntry

    data class Thread(val row: ThreadRow) : ThreadListEntry {
        override val key: String get() = row.id
    }
}

/**
 * The threads in their given order, with a label before each run of threads
 * from the same group. The list comes newest first, so each group shows once.
 */
fun withGroupLabels(threads: List<ThreadRow>, nowMillis: Long, zone: ZoneId): List<ThreadListEntry> {
    val entries = mutableListOf<ThreadListEntry>()
    var previousGroup: ThreadGroup? = null
    for (thread in threads) {
        val group = ThreadGroup.of(thread.updatedAtMillis, nowMillis, zone)
        if (group != previousGroup) {
            // The position keeps the key unique should a group come back in an unsorted list.
            entries += ThreadListEntry.Label(group, key = "label-${group.name}-${entries.size}")
            previousGroup = group
        }
        entries += ThreadListEntry.Thread(thread)
    }
    return entries
}

/** The Incognito chip's view: only incognito threads (D-111, D-123). */
fun incognitoThreads(threads: List<ThreadRow>): List<ThreadRow> = threads.filter { thread -> thread.incognito }
