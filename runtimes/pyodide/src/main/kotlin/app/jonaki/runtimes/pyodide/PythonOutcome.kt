package app.jonaki.runtimes.pyodide

import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.OutputFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Turns the worker's final message (python-worker.js) into a [CodeRunOutcome]. */
internal object PythonOutcome {
    fun from(outcomeJson: String, stdout: String, stderr: String, outputFiles: List<OutputFile>): CodeRunOutcome {
        val message = Json.parseToJsonElement(outcomeJson).jsonObject
        val missingPackages = message["missingPackages"] as? JsonArray
        if (missingPackages != null) {
            return CodeRunOutcome.MissingPackages(missingPackages.mapNotNull { name -> (name as? JsonPrimitive)?.content })
        }
        val setupError = message.textOf("setupError")
        if (setupError != null) {
            return CodeRunOutcome.Unavailable("Python could not start: $setupError")
        }
        return CodeRunOutcome.Finished(
            stdout = stdout,
            stderr = stderr,
            resultValue = message.textOf("result"),
            errorText = message.textOf("error"),
            outputFiles = outputFiles,
        )
    }

    private fun JsonObject.textOf(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
