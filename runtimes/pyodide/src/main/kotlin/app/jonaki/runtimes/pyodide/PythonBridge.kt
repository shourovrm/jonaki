package app.jonaki.runtimes.pyodide

import android.webkit.JavascriptInterface
import app.jonaki.core.runtimeapi.CappedText
import app.jonaki.core.runtimeapi.CodeRunLimits
import app.jonaki.core.runtimeapi.OutputFile
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive

/**
 * The harness page's only way into the app (harness.js). It hands over the
 * job and collects what the program prints and writes. WebView calls these
 * methods on its own background thread, hence the locking.
 */
internal class PythonBridge(private val jobJson: String) {
    private val lock = Any()
    private val stdoutText = CappedText(CappedText.DEFAULT_MAX_CHARACTERS)
    private val stderrText = CappedText(CappedText.DEFAULT_MAX_CHARACTERS)
    private val filesInProgress = mutableMapOf<String, ByteArrayOutputStream>()
    private val finishedFiles = mutableListOf<OutputFile>()
    private var receivedFileBytes = 0L

    private val currentPhase = MutableStateFlow(PHASE_STARTING)

    /** starting, loading, packages, running, then done. */
    val phase: StateFlow<String> = currentPhase.asStateFlow()

    /** The worker's final message as JSON text (python-worker.js, run's return value). */
    val outcome = CompletableDeferred<String>()

    @JavascriptInterface
    fun job(): String = jobJson

    @JavascriptInterface
    fun stdout(text: String) {
        synchronized(lock) { stdoutText.append(text) }
    }

    @JavascriptInterface
    fun stderr(text: String) {
        synchronized(lock) { stderrText.append(text) }
    }

    @JavascriptInterface
    fun phase(name: String) {
        currentPhase.value = name
    }

    @JavascriptInterface
    fun fileStarted(path: String) {
        synchronized(lock) { filesInProgress[path] = ByteArrayOutputStream() }
    }

    /** Chunks are whole groups of four Base64 characters, so each decodes on its own. */
    @JavascriptInterface
    fun fileChunk(path: String, base64: String) {
        val bytes = Base64.getDecoder().decode(base64)
        synchronized(lock) {
            val stream = filesInProgress[path] ?: return
            receivedFileBytes += bytes.size
            if (receivedFileBytes > CodeRunLimits.MAX_TOTAL_BYTES) {
                // Holding more would only risk the app's memory; run_code reports the dropped file.
                filesInProgress.remove(path)
                stderrText.append("[$path was not kept: the run's files are over the size limit]\n")
                return
            }
            stream.write(bytes)
        }
    }

    @JavascriptInterface
    fun fileFinished(path: String) {
        synchronized(lock) {
            val stream = filesInProgress.remove(path) ?: return
            finishedFiles += OutputFile(path, stream.toByteArray())
        }
    }

    @JavascriptInterface
    fun done(outcomeJson: String) {
        currentPhase.value = PHASE_DONE
        outcome.complete(outcomeJson)
    }

    /** For failures the page cannot report itself, such as a crashed renderer. */
    fun fail(reason: String) {
        currentPhase.value = PHASE_DONE
        outcome.complete("""{"setupError": ${JsonPrimitive(reason)}}""")
    }

    fun printedStdout(): String = synchronized(lock) { stdoutText.toString() }

    fun printedStderr(): String = synchronized(lock) { stderrText.toString() }

    fun outputFiles(): List<OutputFile> = synchronized(lock) { finishedFiles.toList() }

    companion object {
        const val NAME = "JonakiBridge"
        const val PHASE_STARTING = "starting"
        const val PHASE_RUNNING = "running"
        const val PHASE_DONE = "done"
    }
}
