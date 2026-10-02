package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class McpServerListTest {
    @Test
    fun serversSurviveTheRoundTripInOrder() {
        val servers = listOf(
            SavedMcpServer(id = "a", name = "deepwiki", url = "https://mcp.deepwiki.com/mcp", headerName = null),
            SavedMcpServer(id = "b", name = "work, \"tools\"", url = "https://example.com/mcp", headerName = "X-Api-Key"),
        )
        assertEquals(servers, McpServerList.fromText(McpServerList.toText(servers)))
    }

    @Test
    fun emptyOrBrokenTextGivesNoServers() {
        assertEquals(emptyList<SavedMcpServer>(), McpServerList.fromText(""))
        assertEquals(emptyList<SavedMcpServer>(), McpServerList.fromText("{not json"))
    }

    @Test
    fun aNewValueWithoutHeaderNameUsesAuthorization() {
        assertEquals("Authorization", McpServerList.headerNameFor(typedName = "", newValue = "Bearer x"))
        assertEquals("X-Key", McpServerList.headerNameFor(typedName = "X-Key", newValue = null))
        assertEquals(null, McpServerList.headerNameFor(typedName = " ", newValue = null))
    }
}
