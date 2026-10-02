package app.jonaki

import app.jonaki.core.toolapi.Tool
import app.jonaki.tools.editfile.EditFileTool
import app.jonaki.tools.findfiles.FindFilesTool
import app.jonaki.tools.readfile.ReadFileTool
import app.jonaki.tools.searchfiles.SearchFilesTool
import app.jonaki.tools.writefile.WriteFileTool

/** Every tool the app offers. Adding a tool is one module plus one line here (D-007). */
object ToolRegistry {
    val allTools: List<Tool> = listOf(
        ReadFileTool(),
        WriteFileTool(),
        EditFileTool(),
        FindFilesTool(),
        SearchFilesTool(),
    )
}
