package app.jonaki.runtimes.pyodide

import android.content.Context
import android.webkit.WebView
import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Runs Python with Pyodide in a hidden WebView made for each run (D-013,
 * D-069). The core files are checked against their pinned SHA-256 before
 * every run; Pyodide checks each package against the lock file as it loads.
 */
class PyodideRuntime(
    private val context: Context,
    private val folder: PyodideFolder,
) : CodeRuntime {
    override val language: CodeLanguage = CodeLanguage.PYTHON

    override suspend fun run(job: CodeJob): CodeRunOutcome {
        val installation = withContext(Dispatchers.IO) { checkInstallation() }
        val lock = when (installation) {
            is Installation.Missing -> return CodeRunOutcome.NotInstalled(folder.release.coreDownloadBytes)
            is Installation.Damaged -> return CodeRunOutcome.Unavailable(
                "Python's files are damaged (${installation.fileNames.joinToString(", ")}); " +
                    "remove Python in Settings and install it again",
            )
            is Installation.Ready -> installation.lock
        }
        val installedPackages = lock.all.filter { lockPackage -> folder.packageFile(lockPackage).isFile }
        val bridge = PythonBridge(jobJson(job, lock, installedPackages.map { lockPackage -> lockPackage.name }))
        val requests = PyodideRequests(folder, installedPackages, job.inputFiles)
        return runInWebView(requests, bridge, job.timeLimit)
    }

    private fun checkInstallation(): Installation {
        if (!folder.isCoreInstalled()) {
            return Installation.Missing
        }
        val damaged = folder.damagedCoreFiles()
        if (damaged.isNotEmpty()) {
            return Installation.Damaged(damaged)
        }
        return Installation.Ready(checkNotNull(folder.lock()))
    }

    private fun jobJson(job: CodeJob, lock: PyodideLock, installedPackageNames: List<String>): String =
        buildJsonObject {
            put("code", job.code)
            putJsonArray("inputPaths") { job.inputFiles.forEach { input -> add(input.relativePath) } }
            putJsonArray("installedPackages") { installedPackageNames.forEach { name -> add(name) } }
            putJsonObject("packageForImport") {
                for ((importName, packageName) in lock.packageForImport()) {
                    put(importName, packageName)
                }
            }
        }.toString()

    private suspend fun runInWebView(requests: PyodideRequests, bridge: PythonBridge, timeLimit: Duration): CodeRunOutcome {
        var webView: WebView? = null
        try {
            webView = withContext(Dispatchers.Main) { PythonWebView.create(context, requests, bridge) }
            // Starting Python and loading packages does not count against the program's own time.
            val started = withTimeoutOrNull(STARTUP_LIMIT) {
                bridge.phase.first { phase -> phase == PythonBridge.PHASE_RUNNING || phase == PythonBridge.PHASE_DONE }
            }
            if (started == null) {
                return CodeRunOutcome.Unavailable("Python did not start within ${STARTUP_LIMIT.inWholeSeconds} seconds")
            }
            val outcomeJson = withTimeoutOrNull(timeLimit) { bridge.outcome.await() }
            if (outcomeJson == null) {
                withContext(Dispatchers.Main) { PythonWebView.stopProgram(checkNotNull(webView)) }
                return CodeRunOutcome.TimedOut(bridge.printedStdout(), bridge.printedStderr())
            }
            return PythonOutcome.from(outcomeJson, bridge.printedStdout(), bridge.printedStderr(), bridge.outputFiles())
        } finally {
            val madeWebView = webView
            if (madeWebView != null) {
                // Also after the Stop button: destroying the WebView ends the page and its worker.
                withContext(NonCancellable + Dispatchers.Main) { madeWebView.destroy() }
            }
        }
    }

    private sealed interface Installation {
        data object Missing : Installation

        data class Damaged(val fileNames: List<String>) : Installation

        data class Ready(val lock: PyodideLock) : Installation
    }

    private companion object {
        val STARTUP_LIMIT: Duration = 45.seconds
    }
}
