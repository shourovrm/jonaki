package app.jonaki

import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.core.toolapi.SubagentLimitSettings
import app.jonaki.core.toolapi.SubagentModelInfo
import app.jonaki.core.toolapi.SubagentReport
import app.jonaki.core.toolapi.SubagentTask
import app.jonaki.core.toolapi.SubagentTypeInfo
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.settings.LocalModelToolList
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolGroups
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.tools.mcp.McpServer
import app.jonaki.tools.memory.Fact
import app.jonaki.tools.memory.FactScope
import app.jonaki.tools.memory.ForgetResult
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.RememberResult
import app.jonaki.tools.phone.CalendarEvent
import app.jonaki.tools.phone.LaunchableApp
import app.jonaki.tools.phone.NewCalendarEvent
import app.jonaki.tools.phone.Phone
import app.jonaki.tools.phone.PhoneAnswer
import app.jonaki.tools.phone.ReminderTiming
import app.jonaki.tools.searchchats.ChatSearchResult
import app.jonaki.tools.searchchats.ChatSearchStore
import app.jonaki.tools.schedule.ScheduledTask
import app.jonaki.tools.schedule.TaskCreated
import app.jonaki.tools.schedule.TaskRequest
import app.jonaki.tools.schedule.TaskScheduler
import app.jonaki.tools.exportpdf.PdfPageSize
import app.jonaki.tools.exportpdf.PdfRenderer
import app.jonaki.tools.exportpdf.RenderedPdf
import app.jonaki.tools.sharefile.DestinationResult
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.LinkedListing
import app.jonaki.tools.youtubesummarize.VideoAnswer
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    private val searchBackend = object : SearchBackend {
        override val name = "Fake"

        override suspend fun search(query: SearchQuery) = SearchOutcome.Success(emptyList())
    }

    private val chatSearchStore = object : ChatSearchStore {
        override suspend fun search(words: String, thisThreadOnly: Boolean, limit: Int) = ChatSearchResult(emptyList(), 0)
    }

    private val memoryStore = object : MemoryStore {
        override suspend fun remember(scope: FactScope, text: String, keywords: String) =
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

    private val phone = object : Phone {
        private val unused = PhoneAnswer.Failed("not used in this test")

        override suspend fun calendarEvents(from: ZonedDateTime, to: ZonedDateTime): PhoneAnswer<List<CalendarEvent>> = unused

        override suspend fun addCalendarEvent(event: NewCalendarEvent): PhoneAnswer<String> = unused

        override suspend fun setReminder(text: String, at: ZonedDateTime): PhoneAnswer<ReminderTiming> = unused

        override suspend fun notify(title: String, text: String): PhoneAnswer<Unit> = unused

        override suspend fun readClipboard(): PhoneAnswer<String> = unused

        override suspend fun writeClipboard(text: String): PhoneAnswer<Unit> = unused

        override suspend fun launchableApps(): PhoneAnswer<List<LaunchableApp>> = unused

        override suspend fun openApp(packageName: String): PhoneAnswer<Unit> = unused
    }

    private val taskScheduler = object : TaskScheduler {
        override suspend fun create(request: TaskRequest): TaskCreated = error("not used in this test")

        override suspend fun list(): List<ScheduledTask> = emptyList()

        override suspend fun cancel(taskId: String): Boolean = false
    }

    private class FakeRuntime(override val language: CodeLanguage) : CodeRuntime {
        override suspend fun run(job: CodeJob): CodeRunOutcome = CodeRunOutcome.Unavailable("not used in this test")
    }

    private val launcher = object : SubagentLauncher {
        override val agentTypes = emptyList<SubagentTypeInfo>()
        override val models = emptyList<SubagentModelInfo>()
        override val extraToolNames = emptyList<String>()
        override val startedThisRun = 0
        override val limitSettings = SubagentLimitSettings()

        override suspend fun launch(tasks: List<SubagentTask>, context: ToolContext) = emptyList<SubagentReport>()
    }

    private val pdfRenderer = object : PdfRenderer {
        override suspend fun render(
            threadFolder: java.io.File,
            htmlPath: String,
            outputFile: java.io.File,
            pageSize: PdfPageSize,
            timeLimit: kotlin.time.Duration,
        ): RenderedPdf = error("not used in this test")
    }

    private val everything = ToolServices(
        searchBackends = listOf(searchBackend),
        videoSummarizer = { VideoAnswer.Success("summary", inputTokens = null) },
        webAccessEnabled = true,
        memoryStore = memoryStore,
        chatSearchStore = chatSearchStore,
        fileDestinations = fileDestinations,
        modelAcceptsImages = true,
        phone = phone,
        taskScheduler = taskScheduler,
        codeRuntimes = listOf(FakeRuntime(CodeLanguage.JAVASCRIPT), FakeRuntime(CodeLanguage.PYTHON)),
        pdfRenderer = pdfRenderer,
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
                "web_search", "web_fetch", "youtube_summarize", "memory", "search_chats", "artifact", "share_file",
                "read_document", "view_image", "phone", "schedule", "run_code", "export_pdf",
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
    fun searchChatsNeedsAStoreSoIncognitoThreadsHaveNone() {
        val names = namesFor(everything.copy(chatSearchStore = null)).toSet()
        assertEquals(false, "search_chats" in names)
    }

    @Test
    fun searchChatsFollowsTheMemoryGroupSwitch() {
        assertEquals(false, "search_chats" in namesFor(withGroupsOff(ToolGroup.MEMORY)).toSet())
        assertEquals(true, "search_chats" in namesFor(withGroupsOff(ToolGroup.WEB)).toSet())
    }

    @Test
    fun aLocalModelNeverGetsSearchChatsEvenWhenSavedInItsList() {
        val saved = LocalModelToolList.DEFAULT + "search_chats"
        assertEquals(LocalModelToolList.DEFAULT, localNames(forLocalModel(everything, saved)))
    }

    @Test
    fun shareFileNeedsTheAppsFileDestinations() {
        val names = namesFor(everything.copy(fileDestinations = null)).toSet()
        assertEquals(false, "share_file" in names)
    }

    @Test
    fun exportPdfNeedsTheAppsPdfRenderer() {
        val names = namesFor(everything.copy(pdfRenderer = null)).toSet()
        assertEquals(false, "export_pdf" in names)
    }

    @Test
    fun exportPdfGoesWithTheReportsGroup() {
        assertEquals(false, "export_pdf" in namesFor(withGroupsOff(ToolGroup.REPORTS)).toSet())
        assertEquals(true, "export_pdf" in namesFor(withGroupsOff(ToolGroup.SHARE)).toSet())
    }

    @Test
    fun phoneAndScheduleNeedTheAppsServices() {
        val names = namesFor(everything.copy(phone = null, taskScheduler = null)).toSet()
        assertEquals(false, "phone" in names)
        assertEquals(false, "schedule" in names)
    }

    @Test
    fun viewImageNeedsAModelThatTakesImages() {
        val names = namesFor(everything.copy(modelAcceptsImages = false)).toSet()
        assertEquals(false, "view_image" in names)
        assertEquals(true, "read_document" in names)
    }

    @Test
    fun mcpNeedsAtLeastOneServer() {
        val folder = File("unused")
        val server = McpServer(id = "a", name = "deepwiki", url = "https://mcp.deepwiki.com/mcp")
        assertEquals(false, "mcp" in namesFor(everything.copy(mcpToolListFolder = folder)))
        assertEquals(true, "mcp" in namesFor(everything.copy(mcpServers = listOf(server), mcpToolListFolder = folder)))
    }

    @Test
    fun everyToolBelongsToAGroup() {
        val names = namesFor(everything) + ToolRegistry.delegateTools(launcher, everything).map { tool -> tool.name }
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

    /** As AgentRunner builds a local model's thread: the saved list, without what is never offered. */
    private fun forLocalModel(services: ToolServices, saved: Set<String> = LocalModelToolList.DEFAULT) =
        services.copy(onlyTools = LocalModelToolList.offered(saved))

    private fun localNames(services: ToolServices): Set<String> {
        val delegate = ToolRegistry.delegateTools(launcher, services).map { tool -> tool.name }
        return (namesFor(services) + delegate).toSet()
    }

    @Test
    fun aLocalModelGetsTheSmallDefaultSet() {
        assertEquals(setOf("web_search", "web_fetch", "phone", "read_file", "read_document"), localNames(forLocalModel(everything)))
    }

    @Test
    fun aLocalModelGetsTheOptionalToolsTheUserAdds() {
        val saved = LocalModelToolList.DEFAULT + "memory" + "edit_file"
        assertEquals(
            setOf("web_search", "web_fetch", "phone", "read_file", "read_document", "memory", "edit_file"),
            localNames(forLocalModel(everything, saved)),
        )
    }

    @Test
    fun aLocalModelNeverGetsDelegateRunCodeViewImageOrArtifact() {
        val mcpServer = McpServer(id = "a", name = "deepwiki", url = "https://mcp.deepwiki.com/mcp")
        val withMcp = everything.copy(mcpServers = listOf(mcpServer), mcpToolListFolder = File("unused"))
        val saved = LocalModelToolList.DEFAULT + LocalModelToolList.NEVER
        assertEquals(LocalModelToolList.DEFAULT, localNames(forLocalModel(withMcp, saved)))
    }

    @Test
    fun aLocalModelStillFollowsTheGroupSwitchesAndTheThreadsWebSwitch() {
        val webAndPhoneOff = forLocalModel(withGroupsOff(ToolGroup.WEB, ToolGroup.PHONE))
        assertEquals(setOf("read_file", "read_document"), localNames(webAndPhoneOff))
        val webOffInThread = forLocalModel(everything.copy(webAccessEnabled = false))
        assertEquals(setOf("phone", "read_file", "read_document"), localNames(webOffInThread))
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
        val subagentsOff = withGroupsOff(ToolGroup.SUBAGENTS)

        assertEquals(emptyList<String>(), ToolRegistry.delegateTools(launcher, subagentsOff).map { tool -> tool.name })
    }
}
