package app.jonaki

import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.SubagentLauncher
import app.jonaki.tools.artifact.ArtifactTool
import app.jonaki.tools.delegate.DelegateTool
import app.jonaki.tools.editfile.EditFileTool
import app.jonaki.tools.findfiles.FindFilesTool
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.MemoryTool
import app.jonaki.tools.readdocument.ReadDocumentTool
import app.jonaki.tools.readfile.ReadFileTool
import app.jonaki.tools.searchfiles.SearchFilesTool
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.ShareFileTool
import app.jonaki.tools.viewimage.ViewImageTool
import app.jonaki.tools.webfetch.WebFetchTool
import app.jonaki.tools.websearch.WebSearchTool
import app.jonaki.tools.writefile.WriteFileTool
import app.jonaki.tools.youtubesummarize.VideoSummarizer
import app.jonaki.tools.youtubesummarize.YouTubeSummarizeTool

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
            tools += WebFetchTool()
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
        return tools
    }

    /**
     * The delegate tool joins last, because its subagents get the thread's
     * other tools (M7); a subagent never gets delegate itself.
     */
    fun delegateTool(launcher: SubagentLauncher): Tool = DelegateTool(launcher)
}
