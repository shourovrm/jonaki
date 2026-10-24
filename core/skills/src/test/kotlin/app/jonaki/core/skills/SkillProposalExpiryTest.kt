package app.jonaki.core.skills

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-188: a proposal older than 14 days is dropped, so five stale ones cannot block new ones. */
class SkillProposalExpiryTest {
    private val folder: File = Files.createTempDirectory("proposals").toFile()
    private val day = 24 * 3_600_000L
    private var now = 100 * day

    private val proposals = SkillProposals(folder, clock = { now })

    @Test
    fun `a proposal from thirteen days ago is still listed`() {
        proposals.propose("fresh-skill", "d", "body", replaces = null)
        now += 13 * day
        assertEquals(listOf("fresh-skill"), proposals.list().map { proposal -> proposal.name })
    }

    @Test
    fun `a proposal from fifteen days ago is dropped and its file deleted`() {
        proposals.propose("old-skill", "d", "body", replaces = null)
        now += 15 * day
        assertTrue(proposals.list().isEmpty())
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `the limit is fourteen days`() {
        assertEquals(14 * day, SkillProposals.MAX_AGE_MILLIS)
    }
}
