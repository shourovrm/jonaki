package app.jonaki.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McpServerFormTest {
    private val existing = listOf(McpServerUi(id = "a", name = "DeepWiki", url = "https://mcp.deepwiki.com/mcp"))

    @Test
    fun aNewServerWithNameAndHttpsAddressIsValid() {
        assertNull(McpServerForm.problem("github", "https://api.githubcopilot.com/mcp/", existing, editingId = null))
    }

    @Test
    fun aBlankNameIsRefused() {
        assertEquals(McpServerForm.Problem.NAME_MISSING, McpServerForm.problem("  ", "https://x.io/mcp", existing, editingId = null))
    }

    @Test
    fun aNameTakenByAnotherServerIsRefusedIgnoringCase() {
        assertEquals(McpServerForm.Problem.NAME_TAKEN, McpServerForm.problem("deepwiki", "https://x.io/mcp", existing, editingId = null))
    }

    @Test
    fun aServerKeepsItsOwnNameWhenEdited() {
        assertNull(McpServerForm.problem("DeepWiki", "https://mcp.deepwiki.com/mcp", existing, editingId = "a"))
    }

    @Test
    fun anAddressMustBeHttpOrHttpsWithAHost() {
        assertEquals(McpServerForm.Problem.URL_INVALID, McpServerForm.problem("x", "mcp.deepwiki.com", existing, editingId = null))
        assertEquals(McpServerForm.Problem.URL_INVALID, McpServerForm.problem("x", "https://", existing, editingId = null))
        assertEquals(McpServerForm.Problem.URL_INVALID, McpServerForm.problem("x", "ftp://host/mcp", existing, editingId = null))
        assertNull(McpServerForm.problem("x", "http://192.168.1.5:8000/mcp", existing, editingId = null))
    }
}
