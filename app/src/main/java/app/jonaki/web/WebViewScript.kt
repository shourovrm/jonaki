package app.jonaki.web

import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Runs [script] in the page and returns its result as text; a JavaScript
 * string comes back unquoted. Call it on the main thread.
 */
internal suspend fun WebView.evaluateToText(script: String): String {
    val json = suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
    val value = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonPrimitive
    return value?.content.orEmpty()
}
