package app.jonaki.tools.sharefile

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFileToolTest {
    /** Records what was asked and answers with [answer]; the linked folder is a map of path to content. */
    private class FakeDestinations : FileDestinations {
        var answer: DestinationResult = DestinationResult.Done("Downloads/Jonaki/report.pdf")
        val linkedFiles = mutableMapOf<String, String>()
        var listing: LinkedListing = LinkedListing.Entries("Documents", emptyList())
        val calls = mutableListOf<String>()
        var listedFolder: String? = null

        override suspend fun saveToDownloads(file: File): DestinationResult {
            calls += "downloads ${file.name}"
            return answer
        }

        override suspend fun saveAs(file: File): DestinationResult {
            calls += "save_as ${file.name}"
            return answer
        }

        override suspend fun share(file: File): DestinationResult {
            calls += "share ${file.name}"
            return answer
        }

        override suspend fun copyToLinkedFolder(file: File): DestinationResult {
            calls += "linked ${file.name}"
            return answer
        }

        override suspend fun listLinkedFolder(folderPath: String): LinkedListing {
            listedFolder = folderPath
            return listing
        }

        override suspend fun copyFromLinkedFolder(path: String, target: File): DestinationResult {
            val content = linkedFiles[path] ?: return DestinationResult.NotFound
            target.writeText(content)
            return DestinationResult.Done(path)
        }
    }

    private val threadFolder = Files.createTempDirectory("thread").toFile()
    private val destinations = FakeDestinations()
    private val tool = ShareFileTool(destinations)
    private val context = ToolContext(threadFolder, OkHttpClient())

    init {
        File(threadFolder, "artifacts").mkdirs()
        File(threadFolder, "artifacts/report.pdf").writeText("pdf")
        File(threadFolder, "inbox").mkdirs()
    }

    private fun call(vararg arguments: Pair<String, String>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
    }

    @Test
    fun changesSomethingOutsideTheAppSoItNeedsApproval() {
        assertEquals(SideEffect.CHANGES, tool.sideEffect)
        assertEquals("share_file", tool.name)
    }

    @Test
    fun downloadsSavesTheThreadFileAndSaysWhere() {
        val output = call("action" to "downloads", "path" to "artifacts/report.pdf")
        assertFalse(output.isError)
        assertEquals(listOf("downloads report.pdf"), destinations.calls)
        assertEquals("Saved artifacts/report.pdf to Downloads/Jonaki/report.pdf.", output.text)
    }

    @Test
    fun eachExportActionReachesItsDestination() {
        call("action" to "save_as", "path" to "artifacts/report.pdf")
        call("action" to "share", "path" to "artifacts/report.pdf")
        call("action" to "linked_folder", "path" to "artifacts/report.pdf")
        assertEquals(listOf("save_as report.pdf", "share report.pdf", "linked report.pdf"), destinations.calls)
    }

    @Test
    fun shareSaysTheSheetIsOpenNotThatTheFileWasSent() {
        destinations.answer = DestinationResult.Done("share sheet")
        val output = call("action" to "share", "path" to "artifacts/report.pdf")
        assertFalse(output.isError)
        assertTrue(output.text.contains("share sheet"))
    }

    @Test
    fun pathOutsideTheThreadFolderIsRefusedBeforeAnythingHappens() {
        val output = call("action" to "downloads", "path" to "../other-thread/secret.txt")
        assertTrue(output.isError)
        assertTrue(output.text.contains("outside the thread folder"))
        assertTrue(destinations.calls.isEmpty())
    }

    @Test
    fun missingFileAndFolderAreErrorsThatNameTheNextStep() {
        val missing = call("action" to "downloads", "path" to "artifacts/nothing.pdf")
        assertTrue(missing.isError)
        assertTrue(missing.text.contains("find_files"))
        val folder = call("action" to "downloads", "path" to "artifacts")
        assertTrue(folder.isError)
        assertTrue(folder.text.contains("is a folder"))
        assertTrue(destinations.calls.isEmpty())
    }

    @Test
    fun missingOrUnknownActionIsAnError() {
        assertTrue(call("path" to "artifacts/report.pdf").isError)
        val unknown = call("action" to "email", "path" to "artifacts/report.pdf")
        assertTrue(unknown.isError)
        assertTrue(unknown.text.contains("downloads"))
    }

    @Test
    fun exportActionsNeedAPath() {
        val output = call("action" to "share")
        assertTrue(output.isError)
        assertTrue(output.text.contains("path"))
    }

    @Test
    fun cancelledPickerIsAnErrorSoTheModelDoesNotClaimTheFileWasSaved() {
        destinations.answer = DestinationResult.Cancelled
        val output = call("action" to "save_as", "path" to "artifacts/report.pdf")
        assertTrue(output.isError)
        assertTrue(output.text.contains("closed"))
    }

    @Test
    fun appNotOnScreenTellsTheModelToAskTheUserToOpenJonaki() {
        destinations.answer = DestinationResult.AppNotOnScreen
        val output = call("action" to "share", "path" to "artifacts/report.pdf")
        assertTrue(output.isError)
        assertTrue(output.text.contains("open Jonaki"))
    }

    @Test
    fun noLinkedFolderPointsToSettings() {
        destinations.answer = DestinationResult.NoLinkedFolder
        val output = call("action" to "linked_folder", "path" to "artifacts/report.pdf")
        assertTrue(output.isError)
        assertTrue(output.text.contains("Settings"))
    }

    @Test
    fun listLinkedShowsFoldersAndSizes() {
        destinations.listing = LinkedListing.Entries(
            "Documents",
            listOf(
                LinkedEntry("Invoices", isFolder = true, sizeBytes = null),
                LinkedEntry("sales.csv", isFolder = false, sizeBytes = 12_288),
                LinkedEntry("notes.txt", isFolder = false, sizeBytes = null),
            ),
        )
        val output = call("action" to "list_linked")
        assertFalse(output.isError)
        assertEquals("", destinations.listedFolder)
        assertEquals(
            "Linked folder Documents, top level:\nInvoices/\nsales.csv (12 KB)\nnotes.txt\n" +
                "Use action import_linked with a path to copy a file into inbox/.",
            output.text,
        )
    }

    @Test
    fun listLinkedOfASubfolderPassesItOn() {
        destinations.listing = LinkedListing.Entries("Documents", emptyList())
        val output = call("action" to "list_linked", "path" to "Invoices/2026/")
        assertEquals("Invoices/2026", destinations.listedFolder)
        assertEquals("Linked folder Documents, Invoices/2026: empty.", output.text)
    }

    @Test
    fun listLinkedProblemIsAnError() {
        destinations.listing = LinkedListing.Failed(DestinationResult.LinkedFolderGone)
        val output = call("action" to "list_linked")
        assertTrue(output.isError)
        assertTrue(output.text.contains("Settings"))
    }

    @Test
    fun importLinkedCopiesIntoInboxAndNamesTheNewPath() {
        destinations.linkedFiles["Invoices/sales.csv"] = "a,b"
        val output = call("action" to "import_linked", "path" to "Invoices/sales.csv")
        assertFalse(output.isError)
        assertEquals("a,b", File(threadFolder, "inbox/sales.csv").readText())
        assertEquals("Copied Invoices/sales.csv to inbox/sales.csv (3 bytes).", output.text)
    }

    @Test
    fun importLinkedNeverReplacesAnInboxFile() {
        File(threadFolder, "inbox/sales.csv").writeText("old")
        destinations.linkedFiles["sales.csv"] = "new"
        val output = call("action" to "import_linked", "path" to "sales.csv")
        assertEquals("old", File(threadFolder, "inbox/sales.csv").readText())
        assertEquals("new", File(threadFolder, "inbox/sales (2).csv").readText())
        assertTrue(output.text.contains("inbox/sales (2).csv"))
    }

    @Test
    fun importLinkedOfAMissingFileSuggestsListing() {
        val output = call("action" to "import_linked", "path" to "nothing.csv")
        assertTrue(output.isError)
        assertTrue(output.text.contains("list_linked"))
    }

    @Test
    fun importLinkedTooLargeNamesTheLimit() {
        val tooLarge = object : FileDestinations by destinations {
            override suspend fun copyFromLinkedFolder(path: String, target: File) =
                DestinationResult.TooLarge(25L * 1024 * 1024)
        }
        val output = runBlocking {
            ShareFileTool(tooLarge).run(
                JsonObject(mapOf("action" to JsonPrimitive("import_linked"), "path" to JsonPrimitive("video.mp4"))),
                context,
            )
        }
        assertTrue(output.isError)
        assertTrue(output.text.contains("25 MB"))
        assertFalse(File(threadFolder, "inbox/video.mp4").exists())
    }
}
