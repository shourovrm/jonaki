package app.jonaki.tools.mcp

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpToolTest {
    private lateinit var server: MockWebServer
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val cacheFolder: File = Files.createTempDirectory("mcp-cache").toFile()
    private val context = ToolContext(threadFolder, OkHttpClient())
    private var now = 1_000_000_000L

    private val wikiTools = """[
        {"name":"ask_question","description":"Ask any question about a GitHub repository","inputSchema":{"type":"object","properties":{"repoName":{"type":"string"},"question":{"type":"string"}},"required":["repoName","question"]}},
        {"name":"read_structure","description":"List the documentation topics of a repository wiki","inputSchema":{"type":"object","properties":{"repoName":{"type":"string"}}}}
    ]"""

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun wiki(url: String = server.url("/mcp").toString()) = McpServer(id = "w1", name = "deepwiki", url = url)

    private fun tool(vararg servers: McpServer) = McpTool(servers.toList(), cacheFolder, clock = { now })

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun enqueueSession() {
        server.enqueue(json("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{}}}"""))
        server.enqueue(MockResponse().setResponseCode(202))
    }

    private fun enqueueToolList(id: Int = 2, tools: String = wikiTools) {
        server.enqueue(json("""{"jsonrpc":"2.0","id":$id,"result":{"tools":$tools}}"""))
    }

    @Test
    fun searchFindsToolsByKeywordAndCachesTheList() = runBlocking {
        enqueueSession()
        enqueueToolList()
        val mcp = tool(wiki())

        val first = mcp.run(arguments("""{"action":"search","query":"documentation topics"}"""), context)
        val requestsAfterFirst = server.requestCount
        val second = mcp.run(arguments("""{"action":"search","query":"question"}"""), context)

        assertFalse(first.text, first.isError)
        assertTrue(first.text, first.text.contains("read_structure (server deepwiki)"))
        assertFalse("no match for ask_question", first.text.contains("ask_question"))
        assertTrue(second.text, second.text.contains("ask_question (server deepwiki): Ask any question"))
        assertEquals("the second search reads the cache", requestsAfterFirst, server.requestCount)
    }

    @Test
    fun aNameMatchRanksAboveADescriptionMatch() {
        val tools = listOf(
            McpToolInfo("list_pages", "Shows every repository page", JsonObject(emptyMap())),
            McpToolInfo("repository_info", "Basic facts", JsonObject(emptyMap())),
        )
        val ranked = ToolSearch.rank("repository", mapOf("s" to tools), limit = 10)
        assertEquals(listOf("repository_info", "list_pages"), ranked.map { match -> match.tool.name })
    }

    @Test
    fun aStaleListIsFetchedAgain() = runBlocking {
        enqueueSession()
        enqueueToolList()
        enqueueSession()
        enqueueToolList(tools = """[{"name":"new_tool","description":"Fresh","inputSchema":{"type":"object"}}]""")
        val mcp = tool(wiki())

        mcp.run(arguments("""{"action":"search","query":""}"""), context)
        now += 25 * 60 * 60 * 1000L
        val later = mcp.run(arguments("""{"action":"search","query":""}"""), context)

        assertTrue(later.text, later.text.contains("new_tool"))
    }

    @Test
    fun aChangedAddressIgnoresTheOldCache() = runBlocking {
        enqueueSession()
        enqueueToolList()
        tool(wiki()).run(arguments("""{"action":"search","query":""}"""), context)
        enqueueSession()
        enqueueToolList(tools = """[{"name":"other","description":"","inputSchema":{"type":"object"}}]""")

        val moved = tool(wiki(url = server.url("/mcp2").toString())).run(arguments("""{"action":"search","query":""}"""), context)

        assertTrue(moved.text, moved.text.contains("other"))
        assertFalse(moved.text, moved.text.contains("ask_question"))
    }

    @Test
    fun describeShowsTheFullSchemaAndTheNextCall() = runBlocking {
        enqueueSession()
        enqueueToolList()

        val output = tool(wiki()).run(arguments("""{"action":"describe","server":"DeepWiki","tool":"ask_question"}"""), context)

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("\"required\""))
        assertTrue(output.text, output.text.contains("\"question\""))
        assertTrue(output.text, output.text.contains("action=call server=deepwiki tool=ask_question"))
    }

    @Test
    fun describeOfAnUnknownToolPointsToSearch() = runBlocking {
        enqueueSession()
        enqueueToolList()
        enqueueSession()
        enqueueToolList()

        val output = tool(wiki()).run(arguments("""{"action":"describe","server":"deepwiki","tool":"nope"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("action=search"))
    }

    @Test
    fun callReturnsTheTextContent() = runBlocking {
        enqueueSession()
        server.enqueue(
            json(
                """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"React uses a fiber tree."},{"type":"image","data":"AAAA","mimeType":"image/png"}]}}""",
            ),
        )

        val output = tool(wiki()).run(
            arguments("""{"action":"call","server":"deepwiki","tool":"ask_question","arguments":{"repoName":"facebook/react","question":"How?"}}"""),
            context,
        )

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("React uses a fiber tree."))
        assertTrue(output.text, output.text.contains("[image/png image, not shown]"))
        server.takeRequest()
        server.takeRequest()
        val call = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val sent = call["params"]!!.jsonObject["arguments"]!!.jsonObject
        assertEquals("facebook/react", sent["repoName"]!!.jsonPrimitive.content)
    }

    @Test
    fun aToolThatReportsAnErrorFailsLoudly() = runBlocking {
        enqueueSession()
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"result":{"isError":true,"content":[{"type":"text","text":"Repository not found"}]}}"""))

        val output = tool(wiki()).run(arguments("""{"action":"call","server":"deepwiki","tool":"ask_question","arguments":{}}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Repository not found"))
    }

    @Test
    fun aRefusedCallRefreshesTheToolList() = runBlocking {
        enqueueSession()
        server.enqueue(json("""{"jsonrpc":"2.0","id":2,"error":{"code":-32602,"message":"Unknown tool: ask"}}"""))
        enqueueToolList(id = 3)
        val mcp = tool(wiki())

        val output = mcp.run(arguments("""{"action":"call","server":"deepwiki","tool":"ask","arguments":{}}"""), context)
        val requestsAfterCall = server.requestCount
        val search = mcp.run(arguments("""{"action":"search","query":"question"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("Unknown tool: ask"))
        assertTrue(output.text, output.text.contains("has no tool named ask"))
        assertTrue(search.text, search.text.contains("ask_question"))
        assertEquals("the refreshed list was cached", requestsAfterCall, server.requestCount)
    }

    @Test
    fun anUnknownServerNamesTheConfiguredOnes() = runBlocking {
        val output = tool(wiki()).run(arguments("""{"action":"describe","server":"github","tool":"x"}"""), context)

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("deepwiki"))
    }

    @Test
    fun searchStillListsReachableServersWhenOneFails() = runBlocking {
        enqueueSession()
        enqueueToolList()
        val broken = McpServer(id = "b1", name = "broken", url = "http://127.0.0.1:1/mcp")

        val output = tool(wiki(), broken).run(arguments("""{"action":"search","query":"question"}"""), context)

        assertFalse(output.text, output.isError)
        assertTrue(output.text, output.text.contains("ask_question"))
        assertTrue(output.text, output.text.contains("broken"))
    }

    @Test
    fun onlyCallChangesSomething() {
        val mcp = tool(wiki())
        assertEquals(SideEffect.CHANGES, mcp.sideEffect)
        assertTrue(mcp.isReadOnlyCall(arguments("""{"action":"search"}""")))
        assertTrue(mcp.isReadOnlyCall(arguments("""{"action":"describe"}""")))
        assertFalse(mcp.isReadOnlyCall(arguments("""{"action":"call"}""")))
        assertFalse(mcp.isReadOnlyCall(arguments("""{}""")))
    }

    @Test
    fun thePromptLineNamesTheServers() {
        assertTrue(tool(wiki()).promptLine.contains("deepwiki"))
    }
}
