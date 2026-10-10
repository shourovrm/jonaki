package app.jonaki.core.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class NeverGivenToolsTest {
    @Test
    fun subagentsGetNeitherNestingNorMemoryNorEarlierChats() {
        assertEquals(setOf("delegate", "memory", "search_chats", "propose_skill", "generate_image"), AgentTypes.NEVER_GIVEN)
    }
}
