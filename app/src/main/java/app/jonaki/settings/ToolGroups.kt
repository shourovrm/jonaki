package app.jonaki.settings

import app.jonaki.core.runtimeapi.CodeLanguage

/**
 * The tool groups the user switches in the first-run picker and in
 * Settings > Tools (plan M8 step 3). A switched-off group's tools are not
 * offered to the model, so they cost no prompt tokens. JavaScript and
 * Python are languages of the one run_code tool; run_code is offered while
 * either is on, with only the languages that are on.
 */
enum class ToolGroup(
    val toolNames: Set<String>,
    /** Files stays on: every other tool reads or writes thread files. */
    val canSwitchOff: Boolean = true,
    /** The run_code language this group stands for; null for other groups. */
    val language: CodeLanguage? = null,
) {
    FILES(
        toolNames = setOf("read_file", "write_file", "edit_file", "find_files", "search_files", "read_document", "view_image"),
        canSwitchOff = false,
    ),
    WEB(setOf("web_search", "web_fetch")),
    YOUTUBE(setOf("youtube_summarize")),
    MEMORY(setOf("memory", "search_chats")),
    SUBAGENTS(setOf("delegate")),
    REPORTS(setOf("artifact", "export_pdf")),
    SHARE(setOf("share_file")),
    PHONE(setOf("phone")),
    SCHEDULE(setOf("schedule")),
    MCP(setOf("mcp")),
    JAVASCRIPT(setOf(ToolGroups.RUN_CODE), language = CodeLanguage.JAVASCRIPT),
    PYTHON(setOf(ToolGroups.RUN_CODE), language = CodeLanguage.PYTHON),
}

object ToolGroups {
    const val RUN_CODE = "run_code"

    /** Every group is on until the user switches it off, so new groups start on too. */
    fun enabled(disabled: Set<ToolGroup>): Set<ToolGroup> =
        ToolGroup.entries.filter { group -> !group.canSwitchOff || group !in disabled }.toSet()

    /**
     * Whether the model is offered [toolName]. A tool outside every group
     * (none today) stays offered, so a new tool never silently disappears.
     */
    fun isOffered(toolName: String, enabled: Set<ToolGroup>): Boolean {
        val groups = ToolGroup.entries.filter { group -> toolName in group.toolNames }
        if (groups.isEmpty()) {
            return true
        }
        return groups.any { group -> group in enabled }
    }

    /** The run_code languages that are on. */
    fun codeLanguages(enabled: Set<ToolGroup>): Set<CodeLanguage> =
        enabled.mapNotNull { group -> group.language }.toSet()

    fun disabledFromText(text: String): Set<ToolGroup> = text.split(",")
        .mapNotNull { name -> ToolGroup.entries.firstOrNull { group -> group.name == name.trim() } }
        .toSet()

    fun disabledToText(disabled: Set<ToolGroup>): String =
        ToolGroup.entries.filter { group -> group in disabled }.joinToString(",") { group -> group.name }
}

/**
 * The first-run tool picker shows once per [CURRENT] version: on a new
 * install, and once to installs from before the picker existed (their
 * saved version is 0). Raising [CURRENT] shows it once more, for example
 * when a later version adds a group worth a look.
 */
object ToolPicker {
    // 2 adds the Phone, Schedule and MCP groups (D-120).
    const val CURRENT = 2

    fun shouldShow(seenVersion: Int): Boolean = seenVersion < CURRENT
}
