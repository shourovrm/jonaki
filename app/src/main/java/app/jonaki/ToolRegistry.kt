package app.jonaki

import app.jonaki.core.toolapi.Tool
import app.jonaki.tools.echo.EchoTool

/** Every tool the app offers. Adding a tool is one module plus one line here (D-007). */
object ToolRegistry {
    val allTools: List<Tool> = listOf(
        EchoTool(),
    )
}
