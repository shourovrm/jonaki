package app.jonaki.tools.runcode

import app.jonaki.core.runtimeapi.CodeLanguage

/** What a run_code result asks to install: Python itself when [packageNames] is empty. */
data class InstallNeed(val packageNames: List<String>)

/**
 * The first sentence of run_code's NotInstalled and MissingPackages errors,
 * written and read back in this one place. The model gets the text (text
 * in, text out); the chat reads the saved result with [of] to show its
 * install card (plan M8 step 4), so the step's result is the only record.
 */
object InstallNeeds {
    private const val ERROR_PREFIX = "Error: "
    private val missingPackages = Regex("""^Error: the Python packages (.+?) are not installed\.""")

    fun notInstalledText(language: CodeLanguage): String = "${language.displayName} is not installed"

    fun missingPackagesText(language: CodeLanguage, packageNames: List<String>): String =
        "the ${language.displayName} packages ${packageNames.joinToString(", ")} are not installed"

    /** Null for every result that is not a missing Python or missing Python packages. */
    fun of(resultText: String): InstallNeed? {
        if (resultText.startsWith(ERROR_PREFIX + notInstalledText(CodeLanguage.PYTHON) + ".")) {
            return InstallNeed(emptyList())
        }
        val match = missingPackages.find(resultText) ?: return null
        val names = match.groupValues[1].split(", ").map { name -> name.trim() }.filter { name -> name.isNotEmpty() }
        if (names.isEmpty()) {
            return null
        }
        return InstallNeed(names)
    }
}
