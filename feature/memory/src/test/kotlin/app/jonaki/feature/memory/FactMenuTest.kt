package app.jonaki.feature.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class FactMenuTest {
    private val threadFact = MemoryFactUi(id = 1, text = "Thesis due", pinned = false, scope = FactScopeUi.THREAD, sourcePreview = "My thesis…")

    @Test
    fun threadFactWithASourceOffersEverything() {
        assertEquals(
            listOf(FactMenuItem.EDIT, FactMenuItem.PIN, FactMenuItem.PROMOTE, FactMenuItem.OPEN_SOURCE, FactMenuItem.DELETE),
            FactMenu.itemsFor(threadFact),
        )
    }

    @Test
    fun pinnedGlobalFactWithoutSourceOffersUnpinButNoMoveOrSource() {
        val fact = threadFact.copy(pinned = true, scope = FactScopeUi.GLOBAL, sourcePreview = null)
        assertEquals(listOf(FactMenuItem.EDIT, FactMenuItem.UNPIN, FactMenuItem.DELETE), FactMenu.itemsFor(fact))
    }

    @Test
    fun threadViewIsTheOneWithATitle() {
        val state = MemoryUiState(threadTitle = null, threadFacts = emptyList(), globalFacts = emptyList(), waitingForReview = emptyList(), reviewMode = false)
        assertEquals(false, state.isThreadView)
        assertEquals(true, state.copy(threadTitle = "Plan").isThreadView)
    }

    @Test
    fun aThreadFactInAProjectCanMoveToTheProjectOrToAllThreads() {
        assertEquals(
            listOf(
                FactMenuItem.EDIT,
                FactMenuItem.PIN,
                FactMenuItem.MOVE_TO_PROJECT,
                FactMenuItem.PROMOTE,
                FactMenuItem.OPEN_SOURCE,
                FactMenuItem.DELETE,
            ),
            FactMenu.itemsFor(threadFact, hasProject = true),
        )
    }

    @Test
    fun aProjectFactCanMoveOnlyToAllThreads() {
        val projectFact = threadFact.copy(scope = FactScopeUi.PROJECT, sourcePreview = null)
        assertEquals(
            listOf(FactMenuItem.EDIT, FactMenuItem.PIN, FactMenuItem.PROMOTE, FactMenuItem.DELETE),
            FactMenu.itemsFor(projectFact, hasProject = true),
        )
    }

    @Test
    fun addScopesFollowWhereTheScreenWasOpened() {
        val base = MemoryUiState(null, emptyList(), emptyList(), emptyList(), reviewMode = false)
        assertEquals(listOf(FactScopeUi.GLOBAL), base.addScopes)
        assertEquals(listOf(FactScopeUi.PROJECT, FactScopeUi.GLOBAL), base.copy(projectName = "Thesis").addScopes)
        assertEquals(
            listOf(FactScopeUi.THREAD, FactScopeUi.PROJECT, FactScopeUi.GLOBAL),
            base.copy(threadTitle = "Chapter 2", projectName = "Thesis").addScopes,
        )
    }
}
