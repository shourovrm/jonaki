package app.jonaki.runtimes.javascript

import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.OutputFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the script the sandbox evaluates and reads its reply. The runner
 * (run-program.js) gives the program `console` and `files`; the files travel
 * inside the script as text, because the sandbox has no file access.
 */
internal object JavaScriptProgram {
    // Absolute, because R8 moves this class to another package in release builds.
    private const val RUNNER_RESOURCE = "/app/jonaki/runtimes/javascript/run-program.js"

    private val runner: String by lazy {
        val stream = checkNotNull(JavaScriptProgram::class.java.getResourceAsStream(RUNNER_RESOURCE)) {
            "$RUNNER_RESOURCE is missing from the APK"
        }
        stream.bufferedReader().use { reader -> reader.readText() }
    }

    fun script(code: String, inputTexts: Map<String, String>): String {
        val job = buildJsonObject {
            put("code", code)
            putJsonObject("files") {
                for ((path, text) in inputTexts) {
                    put(path, text)
                }
            }
        }
        // JSON is valid JavaScript, so the job needs no escaping beyond Json's own.
        return "const jonakiJob = $job;\n$runner"
    }

    fun outcomeFrom(replyText: String): CodeRunOutcome.Finished {
        val reply = Json.parseToJsonElement(replyText).jsonObject
        val droppedCharacters = reply.textOf("droppedCharacters")?.toLongOrNull() ?: 0L
        var stdout = reply.textOf("stdout").orEmpty()
        if (droppedCharacters > 0) {
            stdout += "\n[$droppedCharacters more characters were dropped]"
        }
        val written = reply["written"] as? JsonObject ?: JsonObject(emptyMap())
        val outputFiles = written.map { (path, text) ->
            OutputFile(path, text.jsonPrimitive.content.encodeToByteArray())
        }
        return CodeRunOutcome.Finished(
            stdout = stdout,
            stderr = reply.textOf("stderr").orEmpty(),
            resultValue = reply.textOf("result"),
            errorText = reply.textOf("error"),
            outputFiles = outputFiles,
        )
    }

    private fun JsonObject.textOf(key: String): String? {
        val value = this[key]
        if (value == null || value is JsonNull) {
            return null
        }
        val primitive = value as? JsonPrimitive ?: return value.toString()
        // Never longOrNull here: it reads a leading number and stops at the first
        // separator, so a printed "0, 1, 1" became "0" (D-200).
        return primitive.contentOrNull
    }
}
