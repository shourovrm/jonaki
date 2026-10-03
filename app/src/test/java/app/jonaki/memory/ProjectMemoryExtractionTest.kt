package app.jonaki.memory

import app.jonaki.core.storage.MemoryEntity
import app.jonaki.core.storage.MemoryOrigin
import app.jonaki.core.storage.MemoryScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Background extraction in a project thread (D-135). */
class ProjectMemoryExtractionTest {
    private fun projectFact(id: Long, text: String, pinned: Boolean = false, origin: String = MemoryOrigin.EXTRACTED) =
        MemoryEntity(
            id = id,
            scope = MemoryScope.PROJECT,
            threadId = null,
            projectId = "p1",
            text = text,
            pinned = pinned,
            origin = origin,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )

    private val messages = listOf(
        ExtractionMessage(id = "msg-a", isUser = true, text = "My supervisor wants APA style for the thesis."),
    )

    private fun plan(modelOutput: String, projectFacts: List<MemoryEntity>?) =
        MemoryExtraction.plan(MemoryExtraction.parse(modelOutput), emptyList(), emptyList(), messages, projectFacts)

    @Test
    fun aFactMarkedForTheProjectBecomesAProjectFact() {
        val plan = plan("""{"operations":[{"op":"add","text":"Supervisor wants APA style","source":"m1","scope":"project"}]}""", emptyList())

        assertTrue(plan.adds.single().forProject)
        assertEquals("msg-a", plan.adds.single().sourceMessageId)
    }

    @Test
    fun withoutAProjectAFactMarkedForTheProjectStaysWithTheThread() {
        val plan = plan("""{"operations":[{"op":"add","text":"Supervisor wants APA style","scope":"project"}]}""", projectFacts = null)

        assertFalse(plan.adds.single().forProject)
    }

    @Test
    fun aFactTheProjectAlreadyKnowsIsNotAddedAgain() {
        val plan = plan(
            """{"operations":[{"op":"add","text":"supervisor wants APA style.","scope":"project"}]}""",
            listOf(projectFact(4, "Supervisor wants APA style")),
        )

        assertTrue(plan.adds.isEmpty())
        assertEquals(1, plan.skipped)
    }

    @Test
    fun extractedProjectFactsCanBeUpdatedButPinnedOrUserWrittenOnesCannot() {
        val plan = plan(
            """{"operations":[{"op":"update","id":4,"text":"Supervisor wants Harvard style"},""" +
                """{"op":"delete","id":5},{"op":"delete","id":6}]}""",
            listOf(
                projectFact(4, "Supervisor wants APA style"),
                projectFact(5, "Deadline is 15 December", pinned = true),
                projectFact(6, "Dataset has 1,200 rows", origin = MemoryOrigin.USER),
            ),
        )

        assertEquals(4L, plan.updates.single().factId)
        assertTrue(plan.deletes.isEmpty())
        assertEquals(2, plan.skipped)
    }

    @Test
    fun theProjectRuleAndBlockAppearOnlyForAProjectThread() {
        assertTrue(MemoryExtraction.systemPrompt(inProject = true).contains("\"scope\":\"project\""))
        assertEquals(MemoryExtraction.SYSTEM_PROMPT, MemoryExtraction.systemPrompt(inProject = false))

        val withProject = MemoryExtraction.userPrompt(messages, emptyList(), emptyList(), listOf(projectFact(4, "Uses APA")))
        val withoutProject = MemoryExtraction.userPrompt(messages, emptyList(), emptyList(), null)
        assertTrue(withProject.contains("Project facts:\n- [4] Uses APA"))
        assertFalse(withoutProject.contains("Project facts"))
    }
}
