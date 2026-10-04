package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryMarkdownTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 5)

    private fun fact(
        id: Long,
        text: String,
        threadId: String? = null,
        projectId: String? = null,
        pinned: Boolean = false,
        pendingReview: Boolean = false,
        supersededAtMillis: Long? = null,
    ) = MemoryEntity(
        id = id,
        scope = when {
            threadId != null -> MemoryScope.THREAD
            projectId != null -> MemoryScope.PROJECT
            else -> MemoryScope.GLOBAL
        },
        threadId = threadId,
        projectId = projectId,
        text = text,
        pinned = pinned,
        origin = MemoryOrigin.TOOL,
        pendingReview = pendingReview,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        supersededAtMillis = supersededAtMillis,
    )

    @Test
    fun factsAreGroupedByScopeWithPinnedFirst() {
        val text = MemoryMarkdown.of(
            facts = listOf(
                fact(1, "নাম রিয়াদ"),
                fact(2, "Writes in Bangla and English", pinned = true),
                fact(3, "Supervisor wants APA style", projectId = "p1"),
                fact(4, "Thesis due 20 December", threadId = "t1"),
            ),
            threadTitles = mapOf("t1" to "Thesis plan"),
            projectNames = mapOf("p1" to "Thesis"),
            exportedOn = today,
            zone = utc,
        )

        assertEquals(
            """
            # Jonaki memory

            Exported 2026-10-05

            ## All threads

            - 1970-01-01 (pinned): Writes in Bangla and English
            - 1970-01-01: নাম রিয়াদ

            ## Project: Thesis

            - 1970-01-01: Supervisor wants APA style

            ## Thread: Thesis plan

            - 1970-01-01: Thesis due 20 December
            """.trimIndent() + "\n",
            text,
        )
    }

    @Test
    fun factsWaitingForReviewAndSupersededFactsAreLeftOut() {
        val facts = listOf(
            fact(1, "waiting", pendingReview = true),
            fact(2, "old", supersededAtMillis = 5),
        )

        val text = MemoryMarkdown.of(facts, emptyMap(), emptyMap(), today, utc)

        assertFalse(text.contains("waiting"))
        assertFalse(text.contains("old"))
        assertTrue(MemoryMarkdown.isEmpty(facts))
    }

    @Test
    fun aFactOfADeletedThreadKeepsItsSection() {
        val text = MemoryMarkdown.of(listOf(fact(1, "Orphan", threadId = "gone")), emptyMap(), emptyMap(), today, utc)

        assertTrue(text.contains("## Thread: (deleted)\n\n- 1970-01-01: Orphan"))
    }
}
