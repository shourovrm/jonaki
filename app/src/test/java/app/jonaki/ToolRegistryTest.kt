package app.jonaki

import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolGroups
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.tools.memory.Fact
import app.jonaki.tools.memory.FactScope
import app.jonaki.tools.memory.ForgetResult
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.RememberResult
import app.jonaki.tools.sharefile.DestinationResult
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.LinkedListing
import app.jonaki.tools.youtubesummarize.VideoAnswer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    private val searchBackend = object : SearchBackend {
        override val name = "Fake"

        override suspend fun search(query: SearchQuery) = SearchOutcome.Success(emptyList())
    }

    private val memoryStore = object : MemoryStore {
        override suspend fun remember(scope: FactScope, text: String) =
            RememberResult.Saved(Fact(1, scope, text, pinned = false))

        override suspend fun forget(factId: Long) = ForgetResult.NotFound

        override suspend fun recall(query: String, limit: Int) = emptyList<Fact>()
    }

    private val fileDestinations = object : FileDestinations {
        private val unused = DestinationResult.Failed("not used in this test")

        override suspend fun saveToDownloads(file: File) = unused

        override suspend fun saveAs(file: File) = unused

        override suspend fun share(file: File) = unused

        override suspend fun copyToLinkedFolder(file: File) = unused

        override suspend fun listLinkedFolder(folderPath: String) = LinkedListing.Failed(unused)

        override suspend fun copyFromLinkedFolder(path: String, target: File) = unused
    }

    private class FakeRuntime(override val language: CodeLanguage) : CodeRuntime {
        override suspend fun run(job: CodeJob): CodeRunOutcome = CodeRunOutcome.Unavailable("not used in this test")
    }

    private val launcher = object : SubagentLauncher {
        override val agentTypes = emptyList<SubagentTypeInfo>()
        override val models = emptyList<SubagentModelInfo>()
        override val extraToolNames = emptyList<String>()

        override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext) = emptyList<SubagentReport>()
    }

    private val everything = ToolServices(
        searchBackends = listOf(searchBackend),
        videoSummarizer = { VideoAnswer.Success("summary", inputTokens = null) },
        webAccessEnabled = true,
        memoryStore = memoryStore,
        fileDestinations = fileDestinations,
        modelAcceptsImages = true,
        codeRuntimes = listOf(FakeRuntime(CodeLanguage.JAVASCRIPT), FakeRuntime(CodeLanguage.PYTHON)),
    )

    private fun withGroupsOff(vararg groups: ToolGroup) =
        everything.copy(enabledGroups = ToolGroups.enabled(groups.toSet()))

    private fun namesFor(services: ToolServices) = ToolRegistry.tools(services).map { tool -> tool.name }

    @Test
    fun toolNamesAreUnique() {
        val names = namesFor(everything)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyToolIsOfferedWhenAllServicesExist() {
        assertEquals(
            setOf(
                "read_file", "write_file", "edit_file", "find_files", "search_files",
                "web_search", "web_fetch", "youtube_summarize", "memory", "artifact", "share_file",
                "read_document", "view_image", "run_code",
            ),
            namesFor(everything).toSet(),
        )
    }

    @Test
    fun webAccessOffRemovesSearchAndFetchButKeepsYouTube() {
        val names = namesFor(everything.copy(webAccessEnabled = false)).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(false, "web_fetch" in names)
        assertEquals(true, "youtube_summarize" in names)
    }

    @Test
    fun webSearchNeedsAtLeastOneBackendWithAKey() {
        val names = namesFor(everything.copy(searchBackends = emptyList())).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(true, "web_fetch" in names)
    }

    @Test
    fun youTubeNeedsAGeminiKey() {
        val names = namesFor(everything.copy(videoSummarizer = null)).toSet()
        assertEquals(false, "youtube_summarize" in names)
    }

    @Test
    fun memoryNeedsAStore() {
        val names = namesFor(everything.copy(memoryStore = null)).toSet()
        assertEquals(false, "memory" in names)
    }

    @Test
    fun shareFileNeedsTheAppsFileDestinations() {
        val names = namesFor(everything.copy(fileDestinations = null)).toSet()
        assertEquals(false, "share_file" in names)
    }

    @Test
    fun viewImageNeedsAModelThatTakesImages() {
        val names = namesFor(everything.copy(modelAcceptsImages = false)).toSet()
        assertEquals(false, "view_image" in names)
        assertEquals(true, "read_document" in names)
    }

    @Test
    fun everyToolBelongsToAGroup() {
        val names = namesFor(everything) + ToolRegistry.delegateTools(launcher, everything.enabledGroups).map { tool -> tool.name }
        val grouped = ToolGroup.entries.flatMap { group -> group.toolNames }.toSet()

        assertEquals(emptyList<String>(), names.filter { name -> name !in grouped })
        assertEquals(true, "delegate" in names)
    }

    @Test
    fun aSwitchedOffGroupLeavesItsToolsOut() {
        val switchable = ToolGroup.entries.filter { group -> group.canSwitchOff && group.language == null }
        for (group in switchable) {
            val names = namesFor(withGroupsOff(group)).toSet()
            assertEquals(group.name, emptySet<String>(), names.intersect(group.toolNames))
            assertEquals(group.name, true, "read_file" in names)
        }
    }

    @Test
    fun filesCannotBeSwitchedOff() {
        val names = namesFor(withGroupsOff(ToolGroup.FILES)).toSet()

        assertEquals(true, ToolGroup.FILES.toolNames.all { name -> name in names })
    }

    @Test
    fun runCodeOffersOnlyTheLanguagesThatAreOn() {
        val pythonOff = ToolRegistry.tools(withGroupsOff(ToolGroup.PYTHON)).first { tool -> tool.name == "run_code" }
        val javaScriptOff = ToolRegistry.tools(withGroupsOff(ToolGroup.JAVASCRIPT)).first { tool -> tool.name == "run_code" }

        assertEquals(false, pythonOff.parameterSchema.toString().contains("python"))
        assertEquals(false, javaScriptOff.parameterSchema.toString().contains("javascript"))
    }

    @Test
    fun bothLanguagesOffLeaveRunCodeOut() {
        val names = namesFor(withGroupsOff(ToolGroup.JAVASCRIPT, ToolGroup.PYTHON)).toSet()

        assertEquals(false, "run_code" in names)
    }

    @Test
    fun subagentsOffLeavesDelegateOut() {
        val groups = ToolGroups.enabled(setOf(ToolGroup.SUBAGENTS))

        assertEquals(emptyList<String>(), ToolRegistry.delegateTools(launcher, groups).map { tool -> tool.name })
    }
}
