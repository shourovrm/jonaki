package app.jonaki.feature.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownFactsTest {
    private fun fact(id: Long, scope: FactScopeUi = FactScopeUi.GLOBAL) =
        MemoryFactUi(id = id, text = "fact $id", pinned = false, scope = scope, sourcePreview = null)

    private val state = MemoryUiState(
        threadTitle = null,
        threadFacts = listOf(fact(1, FactScopeUi.THREAD)),
        globalFacts = listOf(fact(2)),
        waitingForReview = listOf(fact(3)),
        reviewMode = true,
        projectName = null,
        projectFacts = listOf(fact(4, FactScopeUi.PROJECT)),
        supersededFacts = listOf(fact(5)),
    )

    @Test
    fun settingsViewListsReviewAndGlobalFactsOnly() {
        assertEquals(listOf(3L, 2L), shownFactsOf(state, supersededOpen = false).map { it.id })
    }

    @Test
    fun threadViewAddsTheThreadFacts() {
        val threadState = state.copy(threadTitle = "Chat")
        assertEquals(listOf(3L, 1L, 2L), shownFactsOf(threadState, supersededOpen = false).map { it.id })
    }

    @Test
    fun projectFactsShowWhenTheScreenHasAProject() {
        val projectState = state.copy(projectName = "Home")
        assertEquals(listOf(3L, 4L, 2L), shownFactsOf(projectState, supersededOpen = false).map { it.id })
    }

    @Test
    fun supersededFactsCountOnlyWhileTheSectionIsOpen() {
        assertEquals(listOf(3L, 2L, 5L), shownFactsOf(state, supersededOpen = true).map { it.id })
    }
}
