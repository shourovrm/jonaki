package app.jonaki.tools.sharefile

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Sends a thread file out of the app (Downloads, "Save as", the share sheet,
 * the linked folder) and brings files in from the linked folder (D-017).
 * Every action reaches outside the app, so each call needs the user's
 * approval (D-044, proposed), except a copy saved to Downloads/Jonaki, which
 * the user can delete again and which runs in the Auto mode.
 */
class ShareFileTool(private val destinations: FileDestinations) : Tool {
    override val name: String = "share_file"

    override val promptLine: String =
        "share_file: save or share a thread file outside the app, or copy a file in from the user's linked folder"

    override val guidelines: List<String> = listOf(
        "share_file sends one file the user asked for: downloads (Downloads/Jonaki), save_as (the user picks the place), " +
            "share (Android share sheet) or linked_folder (the folder linked in Settings).",
        "list_linked and import_linked read the user's linked folder; import_linked copies a file into inbox/.",
        "Files the user attaches or shares arrive in inbox/ and the message names them.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    for (action in Action.entries) {
                        add(action.argument)
                    }
                }
            }
            putJsonObject("path") {
                put("type", "string")
                put(
                    "description",
                    "downloads, save_as, share, linked_folder: a file in the thread folder, for example artifacts/report.pdf. " +
                        "list_linked: a folder inside the linked folder (empty for its top). " +
                        "import_linked: a file inside the linked folder, as list_linked shows it.",
                )
            }
        }
        putJsonArray("required") { add("action") }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES

    override fun sideEffectOf(arguments: JsonObject): SideEffect = when (actionOf(arguments)) {
        Action.DOWNLOADS.argument -> SideEffect.CHANGES_REVERSIBLE
        else -> SideEffect.CHANGES
    }

    /** Sharing sends the file to another app; the linked folder may already hold a file of that name. */
    override fun isVeryRiskyOf(arguments: JsonObject): Boolean =
        actionOf(arguments) == Action.SHARE.argument || actionOf(arguments) == Action.LINKED_FOLDER.argument

    /** Listing and importing only look at the linked folder or bring a file in; nothing leaves. */
    override fun sendsOutOf(arguments: JsonObject): Boolean = when (actionOf(arguments)) {
        Action.LIST_LINKED.argument, Action.IMPORT_LINKED.argument, Action.DOWNLOADS.argument -> false
        else -> true
    }

    override fun actionOf(arguments: JsonObject): String? = arguments.stringArgument("action")

    override val ruleActions: List<String> = Action.entries.map { action -> action.argument }

    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Long, because "Save as" waits while the user picks a folder and a name. */
    override val timeLimit: Duration = 5.minutes

    private enum class Action(val argument: String) {
        DOWNLOADS("downloads"),
        SAVE_AS("save_as"),
        SHARE("share"),
        LINKED_FOLDER("linked_folder"),
        LIST_LINKED("list_linked"),
        IMPORT_LINKED("import_linked"),
    }

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val actionArgument = arguments.stringArgument("action")
        val action = Action.entries.firstOrNull { it.argument == actionArgument }
            ?: return unknownAction(actionArgument)
        val path = arguments.stringArgument("path")?.trim().orEmpty()
        return when (action) {
            Action.LIST_LINKED -> listLinked(path, context)
            Action.IMPORT_LINKED -> importLinked(path, context)
            else -> export(action, path, context)
        }
    }

    private fun unknownAction(actionArgument: String?): ToolOutput {
        val whatFailed = if (actionArgument == null) "argument action is missing" else "action $actionArgument is unknown"
        return ToolOutput.error(whatFailed, "Use one of: ${Action.entries.joinToString(", ") { it.argument }}.")
    }

    private suspend fun export(action: Action, path: String, context: ToolContext): ToolOutput {
        if (path.isEmpty()) {
            return ToolOutput.error(
                "argument path is missing",
                "Call share_file with the path of a thread file, for example artifacts/report.pdf.",
            )
        }
        val file = context.paths.resolve(path)
            ?: return ToolOutput.error(
                "$path is outside the thread folder",
                "share_file only sends files of this thread; copy the content into the thread folder first.",
            )
        if (file.isDirectory) {
            return ToolOutput.error("$path is a folder", "Send one file, for example $path/report.pdf.")
        }
        if (!file.isFile) {
            return ToolOutput.error("$path does not exist", "Use find_files to see the thread's files.")
        }
        val relativePath = context.paths.relativePath(file)
        val result = when (action) {
            Action.DOWNLOADS -> destinations.saveToDownloads(file)
            Action.SAVE_AS -> destinations.saveAs(file)
            Action.SHARE -> destinations.share(file)
            else -> destinations.copyToLinkedFolder(file)
        }
        if (result !is DestinationResult.Done) {
            return errorFor(result, relativePath)
        }
        val text = when (action) {
            Action.SHARE -> "Opened the share sheet for $relativePath. The user picks the app; " +
                "whether they sent it is not known."
            else -> "Saved $relativePath to ${result.location}."
        }
        return ToolOutput.success(text)
    }

    private suspend fun listLinked(folderPath: String, context: ToolContext): ToolOutput {
        val folder = folderPath.trim('/')
        return when (val listing = destinations.listLinkedFolder(folder)) {
            is LinkedListing.Failed -> errorFor(listing.problem, folder.ifEmpty { "the linked folder" })
            is LinkedListing.Entries -> ToolOutput.success(
                context.outputLimiter.limit(describeListing(listing, folder), MAX_LISTING_CHARACTERS, name),
            )
        }
    }

    private fun describeListing(listing: LinkedListing.Entries, folder: String): String {
        val where = folder.ifEmpty { "top level" }
        if (listing.entries.isEmpty()) {
            return "Linked folder ${listing.folderName}, $where: empty."
        }
        val lines = listing.entries.map { entry ->
            val size = entry.sizeBytes
            when {
                entry.isFolder -> entry.path + "/"
                size == null -> entry.path
                else -> "${entry.path} (${IncomingFiles.describeSize(size)})"
            }
        }
        return "Linked folder ${listing.folderName}, $where:\n" + lines.joinToString("\n") +
            "\nUse action import_linked with a path to copy a file into inbox/."
    }

    private suspend fun importLinked(path: String, context: ToolContext): ToolOutput {
        val linkedPath = path.trim('/')
        if (linkedPath.isEmpty()) {
            return ToolOutput.error(
                "argument path is missing",
                "Call share_file with action list_linked to see the linked folder, then import_linked with a file's path.",
            )
        }
        val target = withContext(Dispatchers.IO) { freeInboxFile(context, linkedPath) }
        val result = destinations.copyFromLinkedFolder(linkedPath, target)
        if (result !is DestinationResult.Done) {
            withContext(Dispatchers.IO) { target.delete() }
            return errorFor(result, linkedPath)
        }
        val size = IncomingFiles.describeSize(target.length())
        return ToolOutput.success("Copied $linkedPath to ${context.paths.relativePath(target)} ($size).")
    }

    private fun freeInboxFile(context: ToolContext, linkedPath: String): File {
        val inbox = File(context.threadFolder, "inbox")
        inbox.mkdirs()
        return IncomingFiles.freeFileIn(inbox, IncomingFiles.safeName(linkedPath.substringAfterLast('/')))
    }

    private fun errorFor(result: DestinationResult, subject: String): ToolOutput = when (result) {
        is DestinationResult.Done -> error("errorFor is only called for failures")
        DestinationResult.Cancelled -> ToolOutput.error(
            "the user closed the picker without choosing a place for $subject",
            "Nothing was saved. Ask the user before trying again.",
        )
        DestinationResult.AppNotOnScreen -> ToolOutput.error(
            "this needs Jonaki on screen and it is in the background",
            "Ask the user to open Jonaki, then call share_file again, or use action downloads.",
        )
        DestinationResult.NoLinkedFolder -> ToolOutput.error(
            "no folder is linked",
            "Ask the user to link a folder in Settings > Files, or use action downloads.",
        )
        DestinationResult.LinkedFolderGone -> ToolOutput.error(
            "the linked folder can no longer be reached (moved, deleted or access removed)",
            "Ask the user to link the folder again in Settings > Files.",
        )
        DestinationResult.NotFound -> ToolOutput.error(
            "$subject is not in the linked folder",
            "Call share_file with action list_linked to see what is there.",
        )
        is DestinationResult.TooLarge -> ToolOutput.error(
            "$subject is over the ${IncomingFiles.describeSize(result.limitBytes)} limit for files coming in",
            "Ask the user for a smaller file or a part of it.",
        )
        is DestinationResult.Failed -> ToolOutput.error(
            "$subject could not be handled: ${result.reason}",
            "Tell the user what failed; try action downloads if another action failed.",
        )
    }

    private companion object {
        /** About 2,000 tokens: a few hundred names, enough to find a file without flooding the context. */
        const val MAX_LISTING_CHARACTERS = 8_000
    }
}
