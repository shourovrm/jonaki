package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeRunLimits
import app.jonaki.core.runtimeapi.InputFile
import app.jonaki.core.runtimeapi.OutputFile
import app.jonaki.core.toolapi.ThreadPaths
import java.io.File

/**
 * The rules for files moving between a thread folder and a program run by
 * run_code. In: the files the model names, at their thread paths. Out: only
 * new or changed files under work/ and artifacts/, so that a program can
 * never alter the user's originals in inbox/ or anything else in the thread.
 */
class ThreadFileExchange(threadFolder: File) {
    private val paths = ThreadPaths(threadFolder)

    fun inputsFor(requestedPaths: List<String>): InputSelection {
        val chosen = linkedMapOf<String, File>()
        for (requestedPath in requestedPaths) {
            val target = paths.resolve(requestedPath)
                ?: return InputSelection.Refused("$requestedPath is outside the thread folder")
            if (!target.exists()) {
                return InputSelection.Refused("$requestedPath does not exist")
            }
            for (file in filesAt(target)) {
                chosen.putIfAbsent(paths.relativePath(file), file)
            }
        }
        return checkedSizes(chosen)
    }

    private fun filesAt(target: File): List<File> {
        if (target.isFile) {
            return listOf(target)
        }
        return target.walkTopDown()
            .filter { file -> file.isFile }
            .sortedBy { file -> file.path }
            .toList()
    }

    private fun checkedSizes(chosen: Map<String, File>): InputSelection {
        var totalBytes = 0L
        for ((relativePath, file) in chosen) {
            if (file.length() > CodeRunLimits.MAX_FILE_BYTES) {
                return InputSelection.Refused("$relativePath is over $MAX_FILE_MEGABYTES MB")
            }
            totalBytes += file.length()
        }
        if (totalBytes > CodeRunLimits.MAX_TOTAL_BYTES) {
            return InputSelection.Refused("the files are over $MAX_TOTAL_MEGABYTES MB together")
        }
        val inputFiles = chosen.map { (relativePath, file) -> InputFile(relativePath, file) }
        return InputSelection.Ready(inputFiles)
    }

    fun save(outputFiles: List<OutputFile>): SaveReport {
        val saved = mutableListOf<SavedFile>()
        val refused = mutableListOf<RefusedFile>()
        var savedBytes = 0L
        for (outputFile in outputFiles) {
            val problem = problemWith(outputFile, savedBytes)
            if (problem != null) {
                refused += RefusedFile(outputFile.relativePath, problem)
                continue
            }
            // problemWith has checked that the path resolves.
            val target = paths.resolve(outputFile.relativePath)!!
            if (target.isFile && target.readBytes().contentEquals(outputFile.content)) {
                continue
            }
            val wasNew = !target.exists()
            target.parentFile?.mkdirs()
            target.writeBytes(outputFile.content)
            savedBytes += outputFile.content.size
            saved += SavedFile(paths.relativePath(target), outputFile.content.size.toLong(), wasNew)
        }
        return SaveReport(saved, refused)
    }

    private fun problemWith(outputFile: OutputFile, savedBytesSoFar: Long): String? {
        val target = paths.resolve(outputFile.relativePath) ?: return "outside the thread folder"
        // The resolved path counts, so "work/../inbox/a.csv" is judged as inbox/a.csv.
        val resolvedPath = paths.relativePath(target)
        val isInsideResultFolder = resolvedPath.contains('/') && resolvedPath.substringBefore('/') in RESULT_FOLDERS
        if (!isInsideResultFolder) {
            return "only files under work/ and artifacts/ are saved"
        }
        if (target.isDirectory) {
            return "a folder of that name exists"
        }
        if (outputFile.content.size > CodeRunLimits.MAX_FILE_BYTES) {
            return "over $MAX_FILE_MEGABYTES MB"
        }
        if (savedBytesSoFar + outputFile.content.size > CodeRunLimits.MAX_TOTAL_BYTES) {
            return "the run's files are over $MAX_TOTAL_MEGABYTES MB together"
        }
        return null
    }

    companion object {
        val RESULT_FOLDERS = setOf("work", "artifacts")
        private const val MAX_FILE_MEGABYTES = CodeRunLimits.MAX_FILE_BYTES / (1024 * 1024)
        private const val MAX_TOTAL_MEGABYTES = CodeRunLimits.MAX_TOTAL_BYTES / (1024 * 1024)
    }
}

sealed interface InputSelection {
    data class Ready(val files: List<InputFile>) : InputSelection

    data class Refused(val problem: String) : InputSelection
}

data class SaveReport(
    val saved: List<SavedFile>,
    val refused: List<RefusedFile>,
)

data class SavedFile(
    val relativePath: String,
    val sizeBytes: Long,
    val wasNew: Boolean,
)

data class RefusedFile(
    val relativePath: String,
    val reason: String,
)
