package app.jonaki.runtimes.pyodide

/**
 * The Python modules the app ships itself, under resources/.../pyodide/python/.
 * The list is kept here, not found by scanning the classpath, because the
 * request filter and the worker must agree on exactly these names and a
 * classpath scan is unreliable inside an APK. Adding a module is a new file
 * in that folder plus its name here.
 */
object BundledModules {
    /** Module names without ".py"; each is a top-level module a program can import. */
    val NAMES: List<String> = listOf("jonaki_docs")

    /** The folder, in the resources and in request paths ("python/jonaki_docs.py"). */
    const val FOLDER = "python"

    fun fileNameOf(moduleName: String): String = "$moduleName.py"
}
