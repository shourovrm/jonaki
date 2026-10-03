package app.jonaki.settings

import app.jonaki.core.toolapi.Tool

/**
 * Which tools a model on the phone is offered (D-133). The phone reads a
 * prompt at about 180 tokens a second, so a local thread starts from five
 * tools (about 1,450 tokens with the base prompt) instead of all of them
 * (4,875). The user can add the optional ones; the Settings > Tools group
 * switches and the thread's web switch still apply on top.
 */
object LocalModelToolList {
    val DEFAULT: Set<String> = setOf("web_search", "web_fetch", "phone", "read_file", "read_document")

    val OPTIONAL: Set<String> = setOf(
        "memory",
        "write_file",
        "youtube_summarize",
        "schedule",
        "find_files",
        "search_files",
        "share_file",
        "edit_file",
    )

    /**
     * Never offered to a local model: delegate and mcp lead to long chains
     * of calls, run_code and artifact need long precise output, and no local
     * model is marked as taking images, which view_image needs.
     */
    val NEVER: Set<String> = setOf("delegate", "mcp", "run_code", "view_image", "artifact")

    /** What the Local models page lists, defaults first. */
    val CHOOSABLE: List<String> = DEFAULT.toList() + OPTIONAL.toList()

    /**
     * Offered only in a thread whose folder or project folder holds files
     * (user ruling, 2026-10-03): most phone chats have none, and leaving the
     * tool out saves its prompt tokens there.
     */
    val ONLY_WITH_FILES: Set<String> = setOf("read_document")

    /**
     * The saved choice without names that may not be offered (or no longer
     * exist), and without [ONLY_WITH_FILES] when the thread has no files.
     */
    fun offered(saved: Set<String>, threadHasFiles: Boolean = true): Set<String> =
        saved.filter { toolName ->
            val isChoosable = toolName in CHOOSABLE
            val needsFiles = toolName in ONLY_WITH_FILES
            isChoosable && (threadHasFiles || !needsFiles)
        }.toSet()

    /** [saved] with [toolName] switched on or off; a name outside [CHOOSABLE] changes nothing. */
    fun withTool(saved: Set<String>, toolName: String, enabled: Boolean): Set<String> {
        if (toolName !in CHOOSABLE) {
            return saved
        }
        return if (enabled) saved + toolName else saved - toolName
    }

    /**
     * Rough prompt tokens one tool adds: its prompt line, guidelines and
     * argument schema in characters, divided by 4, the usual characters per
     * token of English text.
     */
    fun tokenCost(tool: Tool): Int {
        val characters = tool.promptLine.length +
            tool.guidelines.sumOf { guideline -> guideline.length } +
            tool.parameterSchema.toString().length
        return characters / CHARACTERS_PER_TOKEN
    }

    private const val CHARACTERS_PER_TOKEN = 4
}

/**
 * The local tool list for the Local models page: what is on, a switch per
 * tool, and each tool's rough token cost. [toolsForCosts] gives the app's
 * tools as a thread would get them; a tool that needs a missing key (web
 * search, YouTube) is absent there and so has no cost.
 */
class LocalModelTools(
    private val settings: AppSettings,
    private val toolsForCosts: () -> List<Tool>,
) {
    val choices: List<String> get() = LocalModelToolList.CHOOSABLE

    fun enabled(): Set<String> = LocalModelToolList.offered(settings.snapshot.value.localModelTools)

    fun setEnabled(toolName: String, enabled: Boolean) {
        settings.update { current ->
            current.copy(localModelTools = LocalModelToolList.withTool(current.localModelTools, toolName, enabled))
        }
    }

    /** Tool name to rough prompt tokens, for the tools this app can offer now. */
    fun tokenCosts(): Map<String, Int> = toolsForCosts()
        .filter { tool -> tool.name in LocalModelToolList.CHOOSABLE }
        .associate { tool -> tool.name to LocalModelToolList.tokenCost(tool) }
}
