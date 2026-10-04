package app.jonaki.skills

import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillProposals
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillProposalReviewTest {
    private val root: File = Files.createTempDirectory("review").toFile()
    private val library = SkillLibrary(File(root, "skills"), File(root, "state.json"))
    private val proposals = SkillProposals(File(root, "proposals"), skillExists = { name -> library.readText(name) != null })
    private val review = SkillProposalReview(library, proposals)

    private fun installReport(extraFiles: Map<String, ByteArray> = emptyMap()) {
        val skillText = "---\nname: report\ndescription: Old report skill.\n---\nOld steps.\n"
        library.install(mapOf(SkillLibrary.SKILL_FILE to skillText.toByteArray()) + extraFiles, replace = false)
    }

    @Test
    fun addingANewProposalWritesItIntoTheLibraryAndRemovesTheProposal() {
        proposals.propose("bangla-letter", "Write a formal Bangla letter.", "Use the user's name.", replaces = null)

        val outcome = review.add("bangla-letter")

        assertEquals(ProposalAddOutcome.Added("bangla-letter"), outcome)
        assertTrue(library.readText("bangla-letter")!!.contains("Use the user's name."))
        assertEquals("Write a formal Bangla letter.", library.list().single().description)
        assertNull(proposals.find("bangla-letter"))
    }

    @Test
    fun addingAChangeReplacesTheSkillFileAndKeepsTheSkillsOtherFiles() {
        installReport(extraFiles = mapOf("templates/page.html" to "<html></html>".toByteArray()))
        proposals.propose("report", "New report skill.", "New steps.", replaces = "report")

        val outcome = review.add("report")

        assertEquals(ProposalAddOutcome.Added("report"), outcome)
        assertTrue(library.readText("report")!!.contains("New steps."))
        assertEquals("New report skill.", library.list().single().description)
        assertTrue(File(root, "skills/report/templates/page.html").isFile)
        assertNull(proposals.find("report"))
    }

    @Test
    fun addingAChangeWhoseSkillWasDeletedMeanwhileAddsItAsANewSkill() {
        installReport()
        proposals.propose("report", "New report skill.", "New steps.", replaces = "report")
        library.delete("report")

        val outcome = review.add("report")

        assertEquals(ProposalAddOutcome.Added("report"), outcome)
        assertTrue(library.readText("report")!!.contains("New steps."))
    }

    @Test
    fun addingANewSkillWhoseNameWasTakenMeanwhileFailsAndKeepsTheProposal() {
        proposals.propose("bangla-letter", "Write a formal Bangla letter.", "Mine.", replaces = null)
        library.install(
            mapOf(SkillLibrary.SKILL_FILE to "---\nname: bangla-letter\ndescription: Theirs.\n---\nTheirs.\n".toByteArray()),
            replace = false,
        )

        val outcome = review.add("bangla-letter")

        assertTrue(outcome.toString(), outcome is ProposalAddOutcome.Failed)
        assertTrue(library.readText("bangla-letter")!!.contains("Theirs."))
        assertNotNull(proposals.find("bangla-letter"))
    }

    @Test
    fun addingAProposalThatIsGoneFails() {
        assertTrue(review.add("never-proposed") is ProposalAddOutcome.Failed)
    }

    @Test
    fun discardingRemovesTheProposalAndTouchesNoSkill() {
        installReport()
        proposals.propose("report", "New report skill.", "New steps.", replaces = "report")

        review.discard("report")

        assertNull(proposals.find("report"))
        assertTrue(library.readText("report")!!.contains("Old steps."))
    }

    @Test
    fun theSinkSavesThroughTheStoreAndPassesItsRefusalsOn() = runBlocking {
        val sink = LibrarySkillProposalSink(proposals)

        val saved = sink.propose("bangla-letter", "Write a formal Bangla letter.", "Steps.", replaces = null)
        val refused = sink.propose("Bad Name", "Text.", "Steps.", replaces = null)

        assertEquals(app.jonaki.tools.proposeskill.SkillProposalOutcome.Saved("bangla-letter", null), saved)
        assertTrue(refused is app.jonaki.tools.proposeskill.SkillProposalOutcome.Refused)
        assertEquals(SkillProposals.MAX_BODY_LENGTH, sink.maxBodyLength)
        assertEquals(SkillProposals.MAX_WAITING, sink.maxWaiting)
    }
}
