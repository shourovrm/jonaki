package app.jonaki.ui

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import app.jonaki.feature.memory.FactScopeUi
import org.junit.Assert.assertEquals
import org.junit.Test

/** Superseded facts and facts proposed for every thread, on the memory screen. */
class MemoryScreenStateSupersededTest {
    private fun fact(id: Long, threadId: String?, text: String, pending: Boolean = false, supersededAt: Long? = null) = MemoryEntity(
        id = id,
        scope = if (threadId == null) MemoryScope.GLOBAL else MemoryScope.THREAD,
        threadId = threadId,
        text = text,
        origin = MemoryOrigin.EXTRACTED,
        pendingReview = pending,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        supersededAtMillis = supersededAt,
    )

    private fun build(
        threadId: String?,
        threadFacts: List<MemoryEntity> = emptyList(),
        globalFacts: List<MemoryEntity> = emptyList(),
        pendingFacts: List<MemoryEntity> = emptyList(),
        supersededFacts: List<MemoryEntity> = emptyList(),
        projectName: String? = null,
    ) = MemoryScreenState.build(
        threadId = threadId,
        threadTitle = if (threadId == null) null else "Thesis plan",
        threadFacts = threadFacts,
        globalFacts = globalFacts,
        pendingFacts = pendingFacts,
        sourceMessages = emptyMap(),
        threadTitles = emptyMap(),
        reviewMode = false,
        projectName = projectName,
        supersededFacts = supersededFacts,
    )

    @Test
    fun supersededFactsListNewestFirstWithTheirDate() {
        val state = build(
            threadId = "t1",
            supersededFacts = listOf(fact(1, "t1", "Due 15 December", supersededAt = 100), fact(2, null, "Lives in Dhaka", supersededAt = 300)),
        )

        assertEquals(listOf(2L, 1L), state.supersededFacts.map { it.id })
        assertEquals(listOf(300L, 100L), state.supersededFacts.map { it.supersededAtMillis })
        assertEquals("Lives in Dhaka", state.supersededFacts.first().text)
    }

    @Test
    fun aPendingGlobalFactShowsInAThreadViewForApproval() {
        val state = build(
            threadId = "t1",
            globalFacts = listOf(fact(7, null, "Lives in Sylhet", pending = true), fact(8, null, "Name is Riad")),
        )

        assertEquals(listOf(7L), state.waitingForReview.map { it.id })
        assertEquals(FactScopeUi.GLOBAL, state.waitingForReview.single().scope)
        assertEquals(listOf(8L), state.globalFacts.map { it.id })
    }

    @Test
    fun aPendingGlobalFactShowsInAProjectViewForApproval() {
        val state = build(
            threadId = null,
            projectName = "Thesis",
            globalFacts = listOf(fact(7, null, "Lives in Sylhet", pending = true)),
        )

        assertEquals(listOf(7L), state.waitingForReview.map { it.id })
    }

    @Test
    fun aPendingGlobalFactShowsInTheSettingsView() {
        val state = build(threadId = null, pendingFacts = listOf(fact(7, null, "Lives in Sylhet", pending = true)))

        assertEquals(listOf(7L), state.waitingForReview.map { it.id })
        assertEquals(FactScopeUi.GLOBAL, state.waitingForReview.single().scope)
    }
}
