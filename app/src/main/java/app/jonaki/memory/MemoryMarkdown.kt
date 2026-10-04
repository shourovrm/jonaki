package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.tools.memory.FactScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The saved facts as one Markdown file the user can read, keep or open in a
 * notes app: the facts for all threads first, then each project's, then each
 * thread's. Facts waiting for review and superseded facts are left out, as
 * the model does not see them either.
 */
object MemoryMarkdown {
    private const val TITLE = "# Jonaki memory"

    /** A thread or project deleted while its facts were read. */
    private const val UNKNOWN_NAME = "(deleted)"

    fun of(
        facts: List<MemoryEntity>,
        threadTitles: Map<String, String>,
        projectNames: Map<String, String>,
        exportedOn: LocalDate,
        zone: ZoneId,
    ): String {
        val inUse = facts.filter { fact -> !fact.pendingReview && fact.supersededAtMillis == null }
        val lines = mutableListOf(TITLE, "", "Exported $exportedOn", "")
        val global = inUse.filter { fact -> RoomMemoryStore.scopeOf(fact) == FactScope.GLOBAL }
        addSection(lines, "## All threads", global, zone)
        val byProject = inUse.filter { fact -> RoomMemoryStore.scopeOf(fact) == FactScope.PROJECT }.groupBy { fact -> fact.projectId }
        for ((projectId, projectFacts) in byProject.entries.sortedBy { entry -> projectNames[entry.key].orEmpty() }) {
            addSection(lines, "## Project: " + (projectNames[projectId] ?: UNKNOWN_NAME), projectFacts, zone)
        }
        val byThread = inUse.filter { fact -> RoomMemoryStore.scopeOf(fact) == FactScope.THREAD }.groupBy { fact -> fact.threadId }
        for ((threadId, threadFacts) in byThread.entries.sortedBy { entry -> threadTitles[entry.key].orEmpty() }) {
            addSection(lines, "## Thread: " + (threadTitles[threadId] ?: UNKNOWN_NAME), threadFacts, zone)
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    /** True when [of] would hold no fact at all. */
    fun isEmpty(facts: List<MemoryEntity>): Boolean =
        facts.none { fact -> !fact.pendingReview && fact.supersededAtMillis == null }

    private fun addSection(lines: MutableList<String>, heading: String, facts: List<MemoryEntity>, zone: ZoneId) {
        if (facts.isEmpty()) {
            return
        }
        lines += heading
        lines += ""
        // Pinned first, as the Memory screen shows them, then in the order they were saved.
        for (fact in facts.sortedWith(compareByDescending<MemoryEntity> { it.pinned }.thenBy { it.id })) {
            lines += lineOf(fact, zone)
        }
        lines += ""
    }

    private fun lineOf(fact: MemoryEntity, zone: ZoneId): String {
        val day = Instant.ofEpochMilli(fact.updatedAtMillis).atZone(zone).toLocalDate()
        val pinned = if (fact.pinned) " (pinned)" else ""
        return "- $day$pinned: " + fact.text.replace('\n', ' ')
    }
}
