package app.jonaki.feature.threads

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectFilterTest {
    private fun row(id: String, projectId: String?) =
        ThreadRow(id, id, "", updatedAtMillis = 0, runState = ThreadRunState.Idle, projectId = projectId)

    private val threads = listOf(row("a", "thesis"), row("b", null), row("c", "trip"), row("d", "thesis"))

    @Test
    fun allShowsEveryThread() {
        assertEquals(listOf("a", "b", "c", "d"), threadsInProject(threads, selectedProjectId = null).map { it.id })
    }

    @Test
    fun aProjectShowsOnlyItsThreads() {
        assertEquals(listOf("a", "d"), threadsInProject(threads, selectedProjectId = "thesis").map { it.id })
    }
}
