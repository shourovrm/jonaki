package app.jonaki.tools.mcp

import app.jonaki.core.toolapi.ToolContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to DeepWiki's free public MCP server (no key, no cost) the way the
 * model would: search, describe, call. Skipped when the machine is offline.
 */
class DeepWikiLiveTest {
    private val deepWiki = McpServer(id = "live-deepwiki", name = "deepwiki", url = "https://mcp.deepwiki.com/mcp")

    private fun isOnline(): Boolean = runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress("mcp.deepwiki.com", 443), 3_000) }
    }.isSuccess

    @Test
    fun searchDescribeAndCallAgainstDeepWiki() = runBlocking {
        assumeTrue("offline: DeepWiki live check skipped", isOnline())
        val httpClient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        val context = ToolContext(Files.createTempDirectory("thread").toFile(), httpClient)
        val tool = McpTool(listOf(deepWiki), Files.createTempDirectory("mcp-cache").toFile())
        fun arguments(json: String) = Json.parseToJsonElement(json).jsonObject

        val search = tool.run(arguments("""{"action":"search","query":"wiki structure"}"""), context)
        assertFalse(search.text, search.isError)
        assertTrue(search.text, search.text.contains("read_wiki_structure"))

        val describe = tool.run(arguments("""{"action":"describe","server":"deepwiki","tool":"read_wiki_structure"}"""), context)
        assertFalse(describe.text, describe.isError)
        assertTrue(describe.text, describe.text.contains("repoName"))

        val call = tool.run(
            arguments("""{"action":"call","server":"deepwiki","tool":"read_wiki_structure","arguments":{"repoName":"square/okhttp"}}"""),
            context,
        )
        assertFalse(call.text, call.isError)
        assertTrue(call.text, call.text.isNotBlank())
        println("DeepWiki live check, first 300 characters of the call: ${call.text.take(300)}")
    }
}
