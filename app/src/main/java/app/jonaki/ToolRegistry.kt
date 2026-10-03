package app.jonaki

import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.settings.ToolGroup
import app.jonaki.settings.ToolGroups
import app.jonaki.tools.artifact.ArtifactTool
import app.jonaki.tools.delegate.DelegateTool
import app.jonaki.tools.editfile.EditFileTool
import app.jonaki.tools.findfiles.FindFilesTool
import app.jonaki.tools.mcp.McpServer
import app.jonaki.tools.mcp.McpTool
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.MemoryTool
import app.jonaki.tools.phone.Phone
import app.jonaki.tools.phone.PhoneTool
import app.jonaki.tools.readdocument.ReadDocumentTool
import app.jonaki.tools.readfile.ReadFileTool
import app.jonaki.tools.runcode.RunCodeTool
import app.jonaki.tools.schedule.ScheduleTool
import app.jonaki.tools.schedule.TaskScheduler
import app.jonaki.tools.searchfiles.SearchFilesTool
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.ShareFileTool
import app.jonaki.tools.viewimage.ViewImageTool
import app.jonaki.tools.webfetch.PageRenderer
import app.jonaki.tools.webfetch.WebFetchTool
import app.jonaki.tools.websearch.WebSearchTool
import app.jonaki.tools.writefile.WriteFileTool
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import app.jonaki.tools.youtubesummarize.YouTubeSummarizeTool
import java.io.File

/** What the optional tools need; built from the user's keys and the thread's settings. */
data class ToolServices(
    /** Search backends that have a key, in the user's order. */
    val searchBackends: List<SearchBackend>,
    /** Null when there is no Gemini key. */
    val videoSummarizer: VideoSummarizer?,
    /** The per-thread switch from the D-011 amendment; off removes web_search and web_fetch. */
    val webAccessEnabled: Boolean,
    /** The thread's view of the fact store (D-009); null leaves the memory tool out. */
    val memoryStore: MemoryStore?,
    /** Downloads, pickers, the share sheet and the linked folder (D-017); null leaves share_file out. */
    val fileDestinations: FileDestinations? = null,
    /** From the model catalog; view_image is offered only to models that take images (D-050). */
    val modelAcceptsImages: Boolean = false,
    /** The app's calendar, alarms, clipboard and launcher; null leaves the phone tool out (D-020). */
    val phone: Phone? = null,
    /** The thread's scheduled tasks on WorkManager; null leaves the schedule tool out. */
    val taskScheduler: TaskScheduler? = null,
    /** Servers from Settings; none leaves the mcp tool out (D-103). */
    val mcpServers: List<McpServer> = emptyList(),
    /** Where the mcp tool caches tool lists (D-102). */
    val mcpToolListFolder: File? = null,
    /** One engine per language for run_code; empty leaves run_code out. */
    val codeRuntimes: List<CodeRuntime> = emptyList(),
    /** The off-screen WebView that lets web_fetch run a page's JavaScript (D-131); null reads pages without it. */
    val pageRenderer: PageRenderer? = null,
    /** The groups switched on in the picker or Settings > Tools; the tools of the others are left out. */
    val enabledGroups: Set<ToolGroup> = ToolGroup.entries.toSet(),
    /** When set, only these tools are offered; a local model's thread uses ToolGroups.LOCAL_MODEL_TOOLS (D-133). */
    val onlyTools: Set<String>? = null,
)

/** Every tool the app offers. Adding a tool is one module plus one line here (D-007). */
object ToolRegistry {
    fun tools(services: ToolServices): List<Tool> {
        val tools = mutableListOf<Tool>(
            ReadFileTool(),
            WriteFileTool(),
            EditFileTool(),
            FindFilesTool(),
            SearchFilesTool(),
            ArtifactTool(),
            ReadDocumentTool(),
        )
        if (services.modelAcceptsImages) {
            tools += ViewImageTool()
        }
        if (services.webAccessEnabled && services.searchBackends.isNotEmpty()) {
            tools += WebSearchTool(services.searchBackends)
        }
        if (services.webAccessEnabled) {
            tools += WebFetchTool(services.pageRenderer)
        }
        if (services.videoSummarizer != null) {
            tools += YouTubeSummarizeTool(services.videoSummarizer)
        }
        if (services.memoryStore != null) {
            tools += MemoryTool(services.memoryStore)
        }
        if (services.fileDestinations != null) {
            tools += ShareFileTool(services.fileDestinations)
        }
        if (services.phone != null) {
            tools += PhoneTool(services.phone)
        }
        if (services.taskScheduler != null) {
            tools += ScheduleTool(services.taskScheduler)
        }
        if (services.mcpServers.isNotEmpty() && services.mcpToolListFolder != null) {
            tools += McpTool(services.mcpServers, services.mcpToolListFolder)
        }
        val languages = ToolGroups.codeLanguages(services.enabledGroups)
        val codeRuntimes = services.codeRuntimes.filter { runtime -> runtime.language in languages }
        if (codeRuntimes.isNotEmpty()) {
            tools += RunCodeTool(codeRuntimes)
        }
        val offered = tools.filter { tool -> ToolGroups.isOffered(tool.name, services.enabledGroups) }
        val onlyTools = services.onlyTools ?: return offered
        return offered.filter { tool -> tool.name in onlyTools }
    }

    /**
     * The delegate tool joins last, because its subagents get the thread's
     * other tools (M7); a subagent never gets delegate itself. Empty while
     * Subagents is switched off, and for a thread whose [ToolServices.onlyTools]
     * leaves it out (a local model's, D-133).
     */
    fun delegateTools(launcher: SubagentLauncher, services: ToolServices): List<Tool> {
        if (ToolGroup.SUBAGENTS !in services.enabledGroups) {
            return emptyList()
        }
        val onlyTools = services.onlyTools
        if (onlyTools != null && DELEGATE !in onlyTools) {
            return emptyList()
        }
        return listOf(DelegateTool(launcher))
    }

    private const val DELEGATE = "delegate"
}
