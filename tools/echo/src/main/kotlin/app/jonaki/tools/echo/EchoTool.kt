package app.jonaki.tools.echo

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Template for a tool module: returns its `text` argument unchanged. It
 * proves the module layout of D-007 and is removed when M2 adds real tools.
 */
class EchoTool : Tool {
    override val name: String = "echo"
    override val promptLine: String = "echo: return the given text unchanged"
    override val guidelines: List<String> = emptyList()
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") { put("type", "string") }
        }
        putJsonArray("required") { add(JsonPrimitive("text")) }
    }
    override val sideEffect: SideEffect = SideEffect.READ_ONLY
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 5.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val text = (arguments["text"] as? JsonPrimitive)?.contentOrNull
        if (text == null) {
            return ToolOutput.error("argument text is missing", "Call echo again with a text argument.")
        }
        return ToolOutput.success(text)
    }
}
