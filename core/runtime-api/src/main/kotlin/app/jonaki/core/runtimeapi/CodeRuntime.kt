package app.jonaki.core.runtimeapi

import java.io.File
import kotlin.time.Duration

/**
 * One engine that runs programs in one language. Each engine is one module
 * under runtimes/ (D-007); the run_code tool receives the engines through its
 * constructor and picks the one for the language the model asks for.
 * An engine never reaches the internet and never touches the thread folder
 * itself: it reads the [CodeJob.inputFiles] and returns what the program
 * wrote, and run_code decides what is saved.
 */
interface CodeRuntime {
    val language: CodeLanguage

    suspend fun run(job: CodeJob): CodeRunOutcome
}

enum class CodeLanguage(
    /** The value of run_code's language argument. */
    val argumentValue: String,
    /** Shown to the model, for example in "Python finished". */
    val displayName: String,
) {
    JAVASCRIPT("javascript", "JavaScript"),
    PYTHON("python", "Python"),
    ;

    companion object {
        /** Accepts "Python", "python " and the short names "js" and "py" that models also write. */
        fun fromArgument(text: String): CodeLanguage? = when (text.trim().lowercase()) {
            "javascript", "js" -> JAVASCRIPT
            "python", "py" -> PYTHON
            else -> null
        }
    }
}

data class CodeJob(
    val code: String,
    /** Files the program can read, at the same relative paths as in the thread folder. */
    val inputFiles: List<InputFile>,
    /** How long the program itself may run; the engine stops it after this. */
    val timeLimit: Duration,
)

/** A thread file offered to the program as [relativePath], for example "inbox/sales.csv". */
data class InputFile(
    val relativePath: String,
    val file: File,
)

/** A file the program wrote, at its path relative to the thread folder, for example "work/summary.csv". */
class OutputFile(
    val relativePath: String,
    val content: ByteArray,
)

sealed interface CodeRunOutcome {
    /** The program ran to its end, or stopped with an error of its own ([errorText]). */
    data class Finished(
        val stdout: String,
        val stderr: String,
        /** The value of the last expression, as text; null when there is none. */
        val resultValue: String?,
        /** The program's exception with its trace; null when it ran without one. */
        val errorText: String?,
        val outputFiles: List<OutputFile>,
    ) : CodeRunOutcome

    /** The program ran past [CodeJob.timeLimit] and was stopped; files it wrote are lost. */
    data class TimedOut(
        val stdout: String,
        val stderr: String,
    ) : CodeRunOutcome

    /**
     * The engine itself is not on the phone (Python before its download).
     * The just-in-time install card (plan M8 step 4) starts from this.
     */
    data class NotInstalled(
        val downloadBytes: Long,
    ) : CodeRunOutcome

    /**
     * The program imports packages the engine knows but has not installed.
     * Like [NotInstalled], a start for the just-in-time install card.
     */
    data class MissingPackages(
        val packageNames: List<String>,
    ) : CodeRunOutcome

    /** The engine cannot run on this phone, for example because Android System WebView is too old. */
    data class Unavailable(
        val reason: String,
    ) : CodeRunOutcome
}
