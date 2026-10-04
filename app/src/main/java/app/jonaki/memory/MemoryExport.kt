package app.jonaki.memory

import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.tools.sharefile.DestinationResult
import app.jonaki.tools.sharefile.FileDestinations
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Writes the saved facts as a Markdown file into Downloads/Jonaki (Settings > Memory and skills). */
class MemoryExport(
    private val database: JonakiDatabase,
    private val destinations: FileDestinations,
    /** Where the file is written before it is copied out; the app's cache, so Android may clear it. */
    private val workFolder: File,
) {
    /** Null when there is no fact to export. */
    suspend fun saveToDownloads(): DestinationResult? {
        val facts = database.memoryDao().listAll()
        if (MemoryMarkdown.isEmpty(facts)) {
            return null
        }
        val threadTitles = facts.mapNotNull { fact -> fact.threadId }.distinct()
            .mapNotNull { threadId -> database.threadDao().find(threadId) }
            .associate { thread -> thread.id to thread.title }
        val projectNames = facts.mapNotNull { fact -> fact.projectId }.distinct()
            .mapNotNull { projectId -> database.projectDao().find(projectId) }
            .associate { project -> project.id to project.name }
        val today = LocalDate.now()
        val text = MemoryMarkdown.of(facts, threadTitles, projectNames, exportedOn = today, zone = ZoneId.systemDefault())
        workFolder.mkdirs()
        val file = File(workFolder, "jonaki-memory-$today.md")
        file.writeText(text)
        return destinations.saveToDownloads(file)
    }
}
