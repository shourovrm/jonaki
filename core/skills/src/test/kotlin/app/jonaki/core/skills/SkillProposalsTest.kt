package app.jonaki.core.skills

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillProposalsTest {
    private val folder: File = Files.createTempDirectory("proposals-test").toFile()
    private var now = 1_000L
    private var librarySkills = setOf("report")

    private fun proposals() = SkillProposals(folder, clock = { now++ }, skillExists = { name -> name in librarySkills })

    private fun SkillProposals.proposeNew(name: String, description: String = "Does $name things.", body: String = "Step one.") =
        propose(name, description, body, replaces = null)

    private fun assertRefused(result: ProposalResult, reasonPart: String) {
        assertTrue("expected a refusal but got $result", result is ProposalResult.Refused)
        val reason = (result as ProposalResult.Refused).reason
        assertTrue("reason was: $reason", reason.contains(reasonPart))
    }

    @Test
    fun aSavedProposalIsListedWithAllItsFields() {
        val store = proposals()

        val result = store.propose("bangla-letter", "Write a formal Bangla letter.", "Use the user's name.", replaces = null)

        assertTrue(result is ProposalResult.Saved)
        val listed = store.list().single()
        assertEquals("bangla-letter", listed.name)
        assertEquals("Write a formal Bangla letter.", listed.description)
        assertEquals("Use the user's name.", listed.body)
        assertNull(listed.replaces)
        assertEquals(1_000L, listed.createdAtMillis)
    }

    @Test
    fun proposalsComeOldestFirstAndSurviveANewStoreObject() {
        proposals().proposeNew("second-skill")
        proposals().proposeNew("first-skill")

        assertEquals(listOf("second-skill", "first-skill"), proposals().list().map { proposal -> proposal.name })
    }

    @Test
    fun aSixthWaitingProposalIsRefusedWithAnAdviceToWait() {
        val store = proposals()
        for (number in 1..SkillProposals.MAX_WAITING) {
            assertTrue(store.proposeNew("skill-$number") is ProposalResult.Saved)
        }

        val result = store.proposeNew("skill-6")

        assertRefused(result, "5 proposals")
        assertTrue((result as ProposalResult.Refused).nextStep.startsWith("Wait"))
        assertEquals(5, store.list().size)
    }

    @Test
    fun aDiscardedProposalMakesRoomForAnother() {
        val store = proposals()
        for (number in 1..SkillProposals.MAX_WAITING) {
            store.proposeNew("skill-$number")
        }

        store.discard("skill-1")

        assertTrue(store.proposeNew("skill-6") is ProposalResult.Saved)
        assertEquals(5, store.list().size)
    }

    @Test
    fun proposingTheSameNameAgainReplacesTheWaitingOneEvenWhenFull() {
        val store = proposals()
        for (number in 1..SkillProposals.MAX_WAITING) {
            store.proposeNew("skill-$number")
        }

        val result = store.proposeNew("skill-3", body = "A better version.")

        assertTrue(result is ProposalResult.Saved)
        assertEquals(5, store.list().size)
        assertEquals("A better version.", store.find("skill-3")?.body)
    }

    @Test
    fun aProposalToChangeASkillKeepsItsNameAndRecordsWhichSkill() {
        val store = proposals()

        val result = store.propose("some-other-name", "Better report.", "New steps.", replaces = "report")

        assertTrue(result is ProposalResult.Saved)
        val saved = store.find("report")!!
        assertEquals("report", saved.name)
        assertEquals("report", saved.replaces)
        assertNull(store.find("some-other-name"))
    }

    @Test
    fun aChangeToASkillThatDoesNotExistIsRefused() {
        val result = proposals().propose("missing", "Text.", "Steps.", replaces = "missing")

        assertRefused(result, "no skill named missing")
    }

    @Test
    fun aNewSkillWithTheNameOfAnExistingOneIsRefusedAndPointsToReplaces() {
        val result = proposals().proposeNew("report")

        assertRefused(result, "already exists")
        assertTrue((result as ProposalResult.Refused).nextStep.contains("replaces"))
    }

    @Test
    fun badNamesAreRefusedWithTheNamingRule() {
        val store = proposals()
        val badNames = listOf("", "Report", "my skill", "../escape", "double--hyphen", "trailing-", "x".repeat(65))

        for (badName in badNames) {
            assertRefused(store.proposeNew(badName), "name must be")
        }
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun aBodyOverTheLimitIsRefusedWithItsLength() {
        val body = "x".repeat(SkillProposals.MAX_BODY_LENGTH + 1)

        assertRefused(proposals().proposeNew("long-skill", body = body), "6001 characters")
    }

    @Test
    fun aBodyAtTheLimitIsSaved() {
        val body = "x".repeat(SkillProposals.MAX_BODY_LENGTH)

        assertTrue(proposals().proposeNew("long-skill", body = body) is ProposalResult.Saved)
    }

    @Test
    fun anEmptyBodyOrDescriptionIsRefused() {
        val store = proposals()

        assertRefused(store.proposeNew("empty-body", body = "  "), "body is empty")
        assertRefused(store.proposeNew("empty-description", description = ""), "description is empty")
    }

    @Test
    fun aDescriptionOverTheLibraryLimitIsRefused() {
        val description = "d".repeat(SkillFrontMatter.MAX_DESCRIPTION_LENGTH + 1)

        assertRefused(proposals().proposeNew("long-description", description = description), "description is over")
    }

    @Test
    fun discardingRemovesTheProposalAndAnUnknownNameChangesNothing() {
        val store = proposals()
        store.proposeNew("keep-me")
        store.proposeNew("drop-me")

        store.discard("drop-me")
        store.discard("never-proposed")
        store.discard("../keep-me")

        assertEquals(listOf("keep-me"), store.list().map { proposal -> proposal.name })
    }

    @Test
    fun aDamagedProposalFileIsLeftOutOfTheList() {
        val store = proposals()
        store.proposeNew("good-skill")
        File(folder, "broken.json").writeText("{ not json")

        assertEquals(listOf("good-skill"), store.list().map { proposal -> proposal.name })
    }

    @Test
    fun theSkillFileHasFrontMatterThatTheLibraryAccepts() {
        val store = proposals()
        store.propose("quote-check", "Checks \"quotes\" and a \\ backslash.\nSecond line.", "Body text.", replaces = null)

        val markdown = store.skillMarkdownOf(store.find("quote-check")!!)
        val parsed = SkillFrontMatter.parse(markdown) as FrontMatterResult.Parsed

        assertEquals("quote-check", parsed.frontMatter.name)
        assertEquals("Checks \"quotes\" and a \\ backslash.\nSecond line.", parsed.frontMatter.description)
        assertTrue(markdown.endsWith("Body text.\n"))
    }
}
