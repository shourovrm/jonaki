package app.jonaki.ui

import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.feature.chat.StepPromptUi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The short line under a step in the step track. */
data class StepDetail(
    /** For web_search: the exact text sent to the search service (D-011 amendment). */
    val query: String?,
    /** A path, link or site the step works on. */
    val target: String?,
    /** The approval card's text when it differs from [target]: the same line with the whole prompt. */
    val fullTarget: String? = null,
    /** What the step's sheet shows (the whole prompt and the call's settings); null for a step without such a sheet. */
    val prompt: StepPromptUi? = null,
) {
    /** What the approval card shows. */
    val approvalText: String? get() = fullTarget ?: target

    /** The labels the approval card and step track show, from string resources so they follow the app's language. */
    class Words(
        val readCalendar: String,
        val addToCalendar: String,
        val reminder: String,
        val notify: String,
        val readClipboard: String,
        val copyToClipboard: String,
        val openApp: String,
        val schedule: String,
        val cancelTask: String,
        val listTasks: String,
        val toDownloads: String,
        val saveAs: String,
        val share: String,
        val toLinkedFolder: String,
        val listLinkedFolder: String,
        val fromLinkedFolder: String,
        val lineCount: (lines: Int) -> String,
        /** The starred image model, named on generate_image's card when the call names none; null while none is added. */
        val defaultImageModel: String? = null,
        /** The first vector model, named on generate_vector_image's card when the call names none; null while none is added. */
        val defaultVectorImageModel: String? = null,
        /** The model, length, resolution and price estimate of a generate_video call; null shows only the prompt. */
        val video: VideoStepText? = null,
        /** The quality and reference words of a generate_image line; null leaves them out. */
        val image: ImageStepWords? = null,
    )

    companion object {
        fun of(toolName: String, argumentsJson: String, words: Words): StepDetail {
            val arguments = parse(argumentsJson) ?: return StepDetail(query = null, target = null)
            return when (toolName) {
                "web_search" -> StepDetail(query = arguments.text("query"), target = arguments.text("site"))
                "web_fetch", "youtube_summarize" -> StepDetail(query = null, target = arguments.text("url")?.let(::withoutScheme))
                "find_files", "search_files" -> StepDetail(query = null, target = arguments.text("pattern") ?: arguments.text("path"))
                "share_file" -> StepDetail(query = null, target = shareFileTarget(arguments.text("action"), arguments.text("path"), words))
                "export_pdf" -> StepDetail(query = null, target = exportPdfTarget(arguments.text("path"), arguments.text("output")))
                "phone" -> StepDetail(query = null, target = phoneTarget(arguments, words))
                "schedule" -> StepDetail(query = null, target = scheduleTarget(arguments, words))
                "mcp" -> mcpDetail(arguments)
                "delegate" -> StepDetail(query = null, target = delegateTarget(arguments))
                "request_tool" -> StepDetail(query = null, target = arguments.text("name"))
                "ask_parent" -> StepDetail(query = arguments.text("question"), target = null)
                "run_code" -> StepDetail(query = null, target = runCodeTarget(arguments, words))
                "generate_image" -> imageDetail(arguments, words.defaultImageModel, words.image, withImageOptions = true)
                "generate_vector_image" -> imageDetail(arguments, words.defaultVectorImageModel, words.image, withImageOptions = false)
                "generate_video" -> videoDetail(arguments, words.video)
                else -> StepDetail(query = null, target = arguments.text("path"))
            }
        }

        /** The step shows the PDF that will be made, which is the default name beside the HTML file when no output is given. */
        private fun exportPdfTarget(htmlPath: String?, output: String?): String? {
            if (output != null) {
                return output
            }
            return htmlPath?.let { path -> path.substringBeforeLast('.', path) + ".pdf" }
        }

        /** The approval card names what the phone action does and to what (plan M9). */
        private fun phoneTarget(arguments: JsonObject, words: Words): String? = when (arguments.text("action")) {
            "calendar_list" -> words.readCalendar
            "calendar_add" -> labelled(words.addToCalendar, arguments.text("title"))
            "reminder" -> labelled(words.reminder, arguments.text("text"))
            "notify" -> labelled(words.notify, arguments.text("title") ?: arguments.text("text"))
            "clipboard_read" -> words.readClipboard
            "clipboard_write" -> words.copyToClipboard
            "open_app" -> labelled(words.openApp, arguments.text("app"))
            else -> null
        }

        private fun scheduleTarget(arguments: JsonObject, words: Words): String? = when (arguments.text("action")) {
            "create" -> labelled(words.schedule, arguments.text("title") ?: arguments.text("prompt"))
            "cancel" -> labelled(words.cancelTask, arguments.text("id"))
            "list" -> words.listTasks
            else -> null
        }

        private fun labelled(label: String, subject: String?): String = if (subject == null) label else "$label: $subject"

        /** The approval card must say where the file goes, not only which file (D-044). */
        private fun shareFileTarget(action: String?, path: String?, words: Words): String? {
            val where = when (action) {
                "downloads" -> words.toDownloads
                "save_as" -> words.saveAs
                "share" -> words.share
                "linked_folder" -> words.toLinkedFolder
                "list_linked" -> words.listLinkedFolder
                "import_linked" -> words.fromLinkedFolder
                else -> null
            }
            if (where == null) {
                return path
            }
            if (path == null) {
                return where
            }
            return "$where: $path"
        }

        /** search shows its words; describe and call show "server: tool", so the approval card names what runs where. */
        private fun mcpDetail(arguments: JsonObject): StepDetail {
            if (arguments.text("action") == "search") {
                return StepDetail(query = arguments.text("query"), target = null)
            }
            val server = arguments.text("server")
            val tool = arguments.text("tool")
            val target = if (server == null) tool else "$server: ${tool.orEmpty()}"
            return StepDetail(query = null, target = target)
        }

        /**
         * The line names the model that will be paid, the quality when it is High, the reference pictures and the
         * prompt: "black-forest-labs/flux.2-klein-4b · high · 2 reference pictures · A blue door". The line cuts a long
         * prompt; the approval card and the step's sheet show all of it.
         */
        private fun imageDetail(arguments: JsonObject, defaultModel: String?, words: ImageStepWords?, withImageOptions: Boolean): StepDetail =
            StepDetail(
                query = null,
                target = PromptStepText.imageLine(arguments, defaultModel, words, withImageOptions, PromptStepText::cut),
                fullTarget = PromptStepText.imageLine(arguments, defaultModel, words, withImageOptions) { prompt -> prompt.trim() },
                prompt = PromptStepText.imageDetail(arguments, defaultModel, words, withImageOptions),
            )

        private fun videoDetail(arguments: JsonObject, video: VideoStepText?): StepDetail {
            if (video == null) {
                val prompt = arguments.text("prompt")
                return StepDetail(query = null, target = prompt?.let(PromptStepText::cut), fullTarget = prompt?.trim())
            }
            return StepDetail(query = null, target = video.target(arguments), fullTarget = video.fullTarget(arguments), prompt = video.detail(arguments))
        }

        /** "Python · 12 lines": the code itself is too long for one line, and its sheet shows it (D-090). */
        private fun runCodeTarget(arguments: JsonObject, words: Words): String? {
            val code = arguments.text("code") ?: return null
            val lines = words.lineCount(programLineCount(code))
            val language = arguments.text("language")?.let(CodeLanguage::fromArgument) ?: return lines
            return "${language.displayName} · $lines"
        }

        /** Trailing line breaks are not lines of the program; the code sheet counts the same way. */
        fun programLineCount(code: String): Int = code.trimEnd('\n', '\r').lines().size

        /** "researcher, scout" for parallel tasks, else the one agent type. */
        private fun delegateTarget(arguments: JsonObject): String? {
            val tasks = arguments["tasks"] as? JsonArray
            if (tasks != null && tasks.isNotEmpty()) {
                return tasks.mapNotNull { task -> (task as? JsonObject)?.text("agent") }.joinToString(", ")
            }
            return arguments.text("agent")
        }

        private fun parse(argumentsJson: String): JsonObject? =
            runCatching { Json.parseToJsonElement(argumentsJson) as? JsonObject }.getOrNull()

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        private fun withoutScheme(url: String): String =
            url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }
}
