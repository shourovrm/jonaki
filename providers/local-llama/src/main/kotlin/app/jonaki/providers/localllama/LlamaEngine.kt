package app.jonaki.providers.localllama

import java.io.File

/**
 * The native side of the local provider (D-133): llama.cpp holding one
 * model. An interface, so that the provider's tests run on the JVM with a
 * fake. Every call except [cancel] comes from the provider's one engine thread.
 */
interface LlamaEngine {
    /** Replaces any loaded model; throws [IllegalStateException] with the reason when it cannot load. */
    fun load(modelFile: File, contextTokens: Int, threads: Int)

    fun unload()

    /**
     * Runs one model turn and blocks until it ends. [onSnapshot] gets the
     * whole parsed reply text and reasoning so far after each token. Returns
     * the result as JSON: finish ("stop", "length", "cancelled" or "error"),
     * error, content, reasoning, tool_calls (name, arguments, id),
     * prompt_tokens, cached_tokens and completion_tokens.
     */
    fun generate(requestJson: String, onSnapshot: (content: String, reasoning: String) -> Unit): String

    /** Any thread: stops the running generate, or the next one if none runs yet. */
    fun cancel()

    /** Undoes an earlier [cancel]; called before each load and generate. */
    fun clearCancel()
}
