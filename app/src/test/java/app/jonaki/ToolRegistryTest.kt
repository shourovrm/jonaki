package app.jonaki

import app.jonaki.core.searchapi.SearchBackend
import app.jonaki.core.searchapi.SearchOutcome
import app.jonaki.core.searchapi.SearchQuery
import app.jonaki.tools.memory.Fact
import app.jonaki.tools.memory.FactScope
import app.jonaki.tools.memory.ForgetResult
import app.jonaki.tools.memory.MemoryStore
import app.jonaki.tools.memory.RememberResult
import app.jonaki.tools.sharefile.DestinationResult
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.LinkedListing
import app.jonaki.tools.youtubesummarize.VideoAnswer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    private val searchBackend = object : SearchBackend {
        override val name = "Fake"

        override suspend fun search(query: SearchQuery) = SearchOutcome.Success(emptyList())
    }

    private val memoryStore = object : MemoryStore {
        override suspend fun remember(scope: FactScope, text: String) =
            RememberResult.Saved(Fact(1, scope, text, pinned = false))

        override suspend fun forget(factId: Long) = ForgetResult.NotFound

        override suspend fun recall(query: String, limit: Int) = emptyList<Fact>()
    }

    private val fileDestinations = object : FileDestinations {
        private val unused = DestinationResult.Failed("not used in this test")

        override suspend fun saveToDownloads(file: File) = unused

        override suspend fun saveAs(file: File) = unused

        override suspend fun share(file: File) = unused

        override suspend fun copyToLinkedFolder(file: File) = unused

        override suspend fun listLinkedFolder(folderPath: String) = LinkedListing.Failed(unused)

        override suspend fun copyFromLinkedFolder(path: String, target: File) = unused
    }

    private val everything = ToolServices(
        searchBackends = listOf(searchBackend),
        videoSummarizer = { VideoAnswer.Success("summary", inputTokens = null) },
        webAccessEnabled = true,
        memoryStore = memoryStore,
        fileDestinations = fileDestinations,
        modelAcceptsImages = true,
    )

    private fun namesFor(services: ToolServices) = ToolRegistry.tools(services).map { tool -> tool.name }

    @Test
    fun toolNamesAreUnique() {
        val names = namesFor(everything)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyToolIsOfferedWhenAllServicesExist() {
        assertEquals(
            setOf(
                "read_file", "write_file", "edit_file", "find_files", "search_files",
                "web_search", "web_fetch", "youtube_summarize", "memory", "artifact", "share_file",
                "read_document", "view_image",
            ),
            namesFor(everything).toSet(),
        )
    }

    @Test
    fun webAccessOffRemovesSearchAndFetchButKeepsYouTube() {
        val names = namesFor(everything.copy(webAccessEnabled = false)).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(false, "web_fetch" in names)
        assertEquals(true, "youtube_summarize" in names)
    }

    @Test
    fun webSearchNeedsAtLeastOneBackendWithAKey() {
        val names = namesFor(everything.copy(searchBackends = emptyList())).toSet()
        assertEquals(false, "web_search" in names)
        assertEquals(true, "web_fetch" in names)
    }

    @Test
    fun youTubeNeedsAGeminiKey() {
        val names = namesFor(everything.copy(videoSummarizer = null)).toSet()
        assertEquals(false, "youtube_summarize" in names)
    }

    @Test
    fun memoryNeedsAStore() {
        val names = namesFor(everything.copy(memoryStore = null)).toSet()
        assertEquals(false, "memory" in names)
    }

    @Test
    fun shareFileNeedsTheAppsFileDestinations() {
        val names = namesFor(everything.copy(fileDestinations = null)).toSet()
        assertEquals(false, "share_file" in names)
    }

    @Test
    fun viewImageNeedsAModelThatTakesImages() {
        val names = namesFor(everything.copy(modelAcceptsImages = false)).toSet()
        assertEquals(false, "view_image" in names)
        assertEquals(true, "read_document" in names)
    }
}
