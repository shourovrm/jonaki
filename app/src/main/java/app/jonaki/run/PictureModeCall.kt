package app.jonaki.run

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The generate_image call that a send in picture mode makes: the user's text,
 * exactly as typed (trimmed), is the prompt, and nothing else is set, so the
 * tool uses the thread's own image model, else the starred one.
 */
object PictureModeCall {
    fun arguments(typedText: String): JsonObject = buildJsonObject { put("prompt", typedText.trim()) }
}
