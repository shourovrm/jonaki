package app.jonaki.skills

import app.jonaki.core.skills.InstallResult
import app.jonaki.core.skills.SkillArchive
import app.jonaki.core.skills.SkillDownloader
import app.jonaki.core.skills.SkillFilesResult
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportOutcome {
    data class Added(val name: String) : ImportOutcome

    /** A skill of this name exists; [files] go in if the user agrees to replace it. */
    data class NameTaken(val name: String, val files: Map<String, ByteArray>) : ImportOutcome

    data class Failed(val reason: String) : ImportOutcome
}

/** Brings a skill into the library from a link or a picked file (D-041). */
class SkillImporter(private val library: SkillLibrary, private val downloader: SkillDownloader) {

    suspend fun fromLink(link: String): ImportOutcome {
        val source = SkillSource.parse(link)
        if (source == SkillSource.NotALink) {
            return ImportOutcome.Failed("this is not a link")
        }
        return install(downloader.download(source))
    }

    suspend fun fromFile(bytes: ByteArray): ImportOutcome = install(SkillArchive.filesOf(bytes))

    /** The user agreed to replace the skill of the same name. */
    suspend fun replace(files: Map<String, ByteArray>): ImportOutcome = withContext(Dispatchers.IO) {
        outcomeOf(library.install(files, replace = true), files)
    }

    private suspend fun install(result: SkillFilesResult): ImportOutcome {
        val files = when (result) {
            is SkillFilesResult.Failed -> return ImportOutcome.Failed(result.reason)
            is SkillFilesResult.Files -> result.files
        }
        return withContext(Dispatchers.IO) { outcomeOf(library.install(files, replace = false), files) }
    }

    private fun outcomeOf(result: InstallResult, files: Map<String, ByteArray>): ImportOutcome = when (result) {
        is InstallResult.Installed -> ImportOutcome.Added(result.name)
        is InstallResult.AlreadyExists -> ImportOutcome.NameTaken(result.name, files)
        is InstallResult.Invalid -> ImportOutcome.Failed(result.reason)
    }
}
