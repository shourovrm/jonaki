package app.jonaki.run

import android.content.Context
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.runtimes.javascript.JavaScriptRuntime

/** The engines run_code can use; Python joins when it is installed (plan M8 step 2). */
object CodeRuntimes {
    fun forApp(context: Context): List<CodeRuntime> = listOf(
        JavaScriptRuntime(context),
    )
}
