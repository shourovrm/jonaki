package app.jonaki.runtimes.javascript

import android.content.Context
import androidx.javascriptengine.EvaluationResultSizeLimitExceededException
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.MemoryLimitExceededException
import androidx.javascriptengine.SandboxDeadException
import app.jonaki.core.runtimeapi.CodeJob
import app.jonaki.core.runtimeapi.CodeLanguage
import app.jonaki.core.runtimeapi.CodeRunOutcome
import app.jonaki.core.runtimeapi.CodeRuntime
import app.jonaki.core.runtimeapi.InputFile
import app.jonaki.core.toolapi.looksBinary
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs JavaScript in JavaScriptSandbox: V8 in Android System WebView's
 * isolated process, with no network, no file access and no Android API.
 * Each run gets a fresh sandbox and isolate, so nothing carries over between
 * runs or threads.
 */
class JavaScriptRuntime(private val context: Context) : CodeRuntime {
    override val language: CodeLanguage = CodeLanguage.JAVASCRIPT

    override suspend fun run(job: CodeJob): CodeRunOutcome {
        val binaryFile = withContext(Dispatchers.IO) { job.inputFiles.firstOrNull { input -> looksBinary(input.file) } }
        if (binaryFile != null) {
            return CodeRunOutcome.Finished(
                stdout = "",
                stderr = "",
                resultValue = null,
                errorText = "${binaryFile.relativePath} is not a text file; JavaScript here reads text files only. " +
                    "Use python for it.",
                outputFiles = emptyList(),
            )
        }
        if (!JavaScriptSandbox.isSupported()) {
            return CodeRunOutcome.Unavailable(WEBVIEW_TOO_OLD)
        }
        val script = JavaScriptProgram.script(job.code, readTexts(job.inputFiles))
        // An app may hold only one sandbox connection at a time, and two threads can run code at once.
        return sandboxLock.withLock { runInNewSandbox(script, job) }
    }

    private suspend fun readTexts(inputFiles: List<InputFile>): Map<String, String> = withContext(Dispatchers.IO) {
        inputFiles.associate { input -> input.relativePath to input.file.readText() }
    }

    private suspend fun runInNewSandbox(script: String, job: CodeJob): CodeRunOutcome {
        val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext).await()
        try {
            // The runner returns a promise, so that programs may use await.
            if (!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN)) {
                return CodeRunOutcome.Unavailable(WEBVIEW_TOO_OLD)
            }
            val isolate = sandbox.createIsolate(startupParameters(sandbox))
            try {
                return evaluate(isolate, script, job)
            } finally {
                // Closing the isolate also stops a program that is still running.
                isolate.close()
            }
        } finally {
            sandbox.close()
        }
    }

    private suspend fun evaluate(isolate: JavaScriptIsolate, script: String, job: CodeJob): CodeRunOutcome {
        try {
            val reply = withTimeoutOrNull(job.timeLimit) { isolate.evaluateJavaScriptAsync(script).await() }
                ?: return CodeRunOutcome.TimedOut(stdout = "", stderr = "")
            return JavaScriptProgram.outcomeFrom(reply)
        } catch (exception: MemoryLimitExceededException) {
            return programError("the program used more than $MAX_HEAP_MEGABYTES MB of memory and was stopped")
        } catch (exception: EvaluationResultSizeLimitExceededException) {
            return programError("the program's printed output and files are over $MAX_REPLY_MEGABYTES MB together")
        } catch (exception: SandboxDeadException) {
            return CodeRunOutcome.Unavailable("the JavaScript sandbox stopped unexpectedly; try again")
        } catch (exception: JavaScriptException) {
            return programError(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun startupParameters(sandbox: JavaScriptSandbox): IsolateStartupParameters {
        val parameters = IsolateStartupParameters()
        if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
            parameters.maxHeapSizeBytes = MAX_HEAP_MEGABYTES * BYTES_PER_MEGABYTE
        }
        // Without this feature the reply is held to the Binder limit of about 1 MB.
        if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)) {
            parameters.maxEvaluationReturnSizeBytes = (MAX_REPLY_MEGABYTES * BYTES_PER_MEGABYTE).toInt()
        }
        return parameters
    }

    private fun programError(text: String) = CodeRunOutcome.Finished(
        stdout = "",
        stderr = "",
        resultValue = null,
        errorText = text,
        outputFiles = emptyList(),
    )

    private companion object {
        val sandboxLock = Mutex()
        const val MAX_HEAP_MEGABYTES = 256L
        const val MAX_REPLY_MEGABYTES = 60L
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
        const val WEBVIEW_TOO_OLD =
            "Android System WebView on this phone is too old for the JavaScript sandbox; update it in the Play Store"
    }
}

/** Waits for the future without blocking a thread, and cancels it when the coroutine is cancelled. */
internal suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(true) }
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (exception: ExecutionException) {
                continuation.resumeWithException(exception.cause ?: exception)
            } catch (exception: java.util.concurrent.CancellationException) {
                continuation.cancel(exception)
            }
        },
        { runnable -> runnable.run() },
    )
}
