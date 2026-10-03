package app.jonaki.providers.localllama

import app.jonaki.core.providerapi.ChatProvider
import app.jonaki.core.providerapi.ChatRequest
import app.jonaki.core.providerapi.StreamEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs GGUF models on the phone with llama.cpp (D-133). The model id is the
 * file name in [modelFolder]. One model stays loaded; asking for another
 * replaces it, and [unload] frees it when Android is short of memory.
 *
 * All native work runs on [engineDispatcher], which must have one thread:
 * llama.cpp's context is not thread-safe, and one thread also queues a new
 * request behind a cancelled one until the native call has returned.
 */
class LocalLlamaProvider(
    private val modelFolder: File,
    private val engine: LlamaEngine,
    private val engineDispatcher: CoroutineDispatcher,
    private val contextTokens: Int = CONTEXT_TOKENS,
    private val threads: Int = THREADS,
    private val newToolCallId: () -> String = ::randomToolCallId,
) : ChatProvider {
    override val id: String = "local-llama"

    /** Only read and written on [engineDispatcher]. */
    private var loadedModelFile: File? = null

    override fun stream(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val worker = launch(engineDispatcher) {
            // The buffer below is unlimited, so trySend never drops an event.
            val send: (StreamEvent) -> Unit = { event -> trySend(event) }
            try {
                runTurn(request, send)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                send(StreamEvent.Failed("The local model failed: ${failure.message}", retryable = false))
            }
            channel.close()
        }
        awaitClose {
            // The native call does not see coroutine cancellation; this stops it between tokens.
            if (!worker.isCompleted) {
                engine.cancel()
            }
        }
    }.buffer(Channel.UNLIMITED)

    /** Frees the loaded model after any running turn has ended. */
    suspend fun unload() {
        withContext(engineDispatcher) {
            engine.unload()
            loadedModelFile = null
        }
    }

    private suspend fun runTurn(request: ChatRequest, send: (StreamEvent) -> Unit) {
        val modelFile = File(modelFolder, request.model)
        // The id comes from the thread's saved model key, so it must not reach outside the folder.
        if (modelFile.parentFile != modelFolder || !modelFile.isFile) {
            send(StreamEvent.Failed("The model file ${request.model} is not on this phone", retryable = false))
            return
        }
        engine.clearCancel()
        // A cancel that came before clearCancel has already cancelled this coroutine.
        currentCoroutineContext().ensureActive()
        val loadFailure = loadIfNeeded(modelFile)
        if (loadFailure != null) {
            send(StreamEvent.Failed(loadFailure, retryable = false))
            return
        }
        val assembler = LocalReplyAssembler(newToolCallId)
        val requestJson = LocalChatRequest.build(request).toString()
        val result = engine.generate(requestJson) { content, reasoning ->
            assembler.snapshot(content, reasoning).forEach(send)
        }
        assembler.finish(result).forEach(send)
    }

    /** Null when [modelFile] is loaded, else why it could not be. */
    private fun loadIfNeeded(modelFile: File): String? {
        if (loadedModelFile == modelFile) {
            return null
        }
        loadedModelFile = null
        return try {
            engine.load(modelFile, contextTokens, threads)
            loadedModelFile = modelFile
            null
        } catch (failure: IllegalStateException) {
            failure.message ?: "llama.cpp could not load ${modelFile.name}"
        } catch (failure: UnsatisfiedLinkError) {
            "The local model library could not be loaded: ${failure.message}"
        }
    }

    companion object {
        /** D-133: 8,192 tokens hold the local tool set's prompt and a working conversation. */
        const val CONTEXT_TOKENS = 8192

        /** D-133: four beat six on the Snapdragon 7s Gen 3, whose other cores are slow. */
        const val THREADS = 4

        /**
         * A single thread with a large stack: chat templates and the output
         * parser recurse through long conversations.
         */
        fun newEngineDispatcher(): CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(null, task, "jonaki-llama", ENGINE_STACK_BYTES)
        }.asCoroutineDispatcher()

        private const val ENGINE_STACK_BYTES = 16L * 1024 * 1024

        private fun randomToolCallId(): String = "call_" + UUID.randomUUID().toString().replace("-", "").take(24)
    }
}
