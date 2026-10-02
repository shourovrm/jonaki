package app.jonaki.run

import android.content.Context
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.runtimes.javascript.JavaScriptRuntime
import app.jonaki.runtimes.pyodide.PyodideFolder
import app.jonaki.runtimes.pyodide.PyodideRelease
import app.jonaki.runtimes.pyodide.PyodideRuntime
import java.io.File

/** The engines run_code can use. Python is always offered; without its download it says how to install it. */
object CodeRuntimes {
    fun forApp(context: Context): List<CodeRuntime> = listOf(
        JavaScriptRuntime(context),
        PyodideRuntime(context, pythonFolder(context)),
    )

    /** Where Python is installed; the installer for the settings screen (plan M8 step 4) uses the same folder. */
    fun pythonFolder(context: Context): PyodideFolder =
        PyodideFolder(File(context.filesDir, "pyodide"), PyodideRelease.PINNED)
}
