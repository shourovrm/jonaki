package app.jonaki.guards.jev

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.Guard
import app.jonaki.core.guardapi.ResultVerdict
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Jev guard: TypeSafe's Jev model, reached through OpenRouter, answers
 * typed questions about an action or about a text from outside. Jev never
 * writes text and this class never denies an action.
 *
 * Every failure (no key, HTTP error, timeout, malformed answer, an answer
 * outside the declared options) ends in the safe answer: the card is shown
 * and the result is not flagged. The reason says what failed.
 */
class JevGuard(
    private val apiKey: String,
    httpClient: OkHttpClient,
    /** Tests point this at a local server. */
    private val url: String = DECISIONS_URL,
    private val timeLimit: Duration = DEFAULT_TIME_LIMIT,
) : Guard {
    // The key is only ever put into the Authorization header: never into a
    // body, a reason text or a log line.
    private val httpClient: OkHttpClient = httpClient.newBuilder().callTimeout(timeLimit.toJavaDuration()).build()

    override suspend fun judgeAction(userRequest: String, toolName: String, arguments: JsonObject): ActionVerdict {
        if (apiKey.isBlank()) {
            return ActionVerdict.ShowCard(NO_KEY_REASON)
        }
        val state = buildJsonObject {
            put("request", userRequest)
            putJsonObject("action") {
                put("tool", toolName)
                put("arguments", arguments)
            }
        }
        return when (val reply = ask(state, JevQuestions.action)) {
            is Reply.Failed -> ActionVerdict.ShowCard(reply.reason)
            is Reply.Answers -> actionVerdictOf(reply.answers, reply.costUsd)
        }
    }

    override suspend fun screenResult(source: String, text: String): ResultVerdict {
        if (apiKey.isBlank()) {
            return notFlagged(NO_KEY_REASON)
        }
        // Chunks are asked about in parallel, so a long page costs one time limit, not several.
        val chunkVerdicts = coroutineScope {
            splitIntoChunks(text).map { chunk -> async { screenChunk(source, chunk) } }.awaitAll()
        }
        return combine(chunkVerdicts)
    }

    private suspend fun screenChunk(source: String, chunk: String): ResultVerdict {
        val state = buildJsonObject {
            put("source", source)
            put("text", chunk)
        }
        return when (val reply = ask(state, JevQuestions.result)) {
            is Reply.Failed -> notFlagged(reply.reason)
            is Reply.Answers -> resultVerdictOf(reply.answers, reply.costUsd)
        }
    }

    private fun actionVerdictOf(answers: JsonObject, costUsd: Double?): ActionVerdict {
        val effect = answers[JevQuestions.EFFECT] as? JsonObject
        val choice = (effect?.get("choice") as? JsonPrimitive)?.contentOrNull
        val confidence = numberOf(effect?.get("confidence"))
        val servesRequest = numberOf((answers[JevQuestions.SERVES_REQUEST] as? JsonObject)?.get("noul"))
        if (choice == null || confidence == null || servesRequest == null) {
            return ActionVerdict.ShowCard("Jev's answer is missing effect, confidence or serves_request", costUsd)
        }
        if (choice !in JevQuestions.EFFECT_CHOICES) {
            return ActionVerdict.ShowCard("Jev answered effect \"$choice\", which is not an option", costUsd)
        }
        if (confidence !in 0.0..1.0 || servesRequest !in 0.0..1.0) {
            return ActionVerdict.ShowCard("Jev's confidence or serves_request is outside 0 to 1", costUsd)
        }

        val effectIsSafe = choice == JevQuestions.READ_ONLY || choice == JevQuestions.REVERSIBLE
        val effectIsSure = confidence >= MINIMUM_EFFECT_CONFIDENCE
        val requestIsServed = servesRequest >= MINIMUM_SERVES_REQUEST
        val summary = "effect $choice, confidence $confidence, serves_request $servesRequest"
        if (effectIsSafe && effectIsSure && requestIsServed) {
            return ActionVerdict.MayRunWithoutCard(summary, costUsd)
        }
        return ActionVerdict.ShowCard(summary, costUsd)
    }

    private fun resultVerdictOf(answers: JsonObject, costUsd: Double?): ResultVerdict {
        val probability = numberOf((answers[JevQuestions.IS_INJECTION] as? JsonObject)?.get("noul"))
        if (probability == null || probability !in 0.0..1.0) {
            return ResultVerdict(false, null, "Jev's is_injection answer is missing or outside 0 to 1", costUsd)
        }
        val isFlagged = probability >= MINIMUM_INJECTION_PROBABILITY
        return ResultVerdict(isFlagged, probability, "is_injection $probability", costUsd)
    }

    /** Flagged if any chunk is; a failed chunk counts as not flagged but its reason is kept. */
    private fun combine(chunkVerdicts: List<ResultVerdict>): ResultVerdict {
        val highestProbability = chunkVerdicts.mapNotNull { verdict -> verdict.injectionProbability }.maxOrNull()
        val costs = chunkVerdicts.mapNotNull { verdict -> verdict.costUsd }
        val totalCostUsd = if (costs.isEmpty()) null else costs.sum()
        val isFlagged = chunkVerdicts.any { verdict -> verdict.isFlagged }
        val reason = if (chunkVerdicts.size == 1) {
            chunkVerdicts.single().reason
        } else {
            val reasons = chunkVerdicts.mapIndexed { index, verdict -> "part ${index + 1}: ${verdict.reason}" }
            reasons.joinToString("; ")
        }
        return ResultVerdict(isFlagged, highestProbability, reason, totalCostUsd)
    }

    private fun notFlagged(reason: String) = ResultVerdict(false, null, reason)

    private sealed interface Reply {
        data class Answers(val answers: JsonObject, val costUsd: Double?) : Reply

        data class Failed(val reason: String) : Reply
    }

    private suspend fun ask(state: JsonObject, questions: JsonObject): Reply = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            put("state", state)
            put("questions", questions)
            // Refuse rather than reach a provider that keeps or trains on prompts.
            putJsonObject("provider") {
                put("data_collection", "deny")
                put("zdr", true)
            }
        }
        val call = httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        // OkHttp's blocking call does not see coroutine cancellation by itself.
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    readReply(bodyText)
                } else {
                    Reply.Failed("Jev answered HTTP ${response.code}")
                }
            }
        } catch (networkError: IOException) {
            // A cancelled coroutine also lands here, as "Canceled"; it must stay cancelled.
            coroutineContext.ensureActive()
            // A call over the time limit ends here too, as an InterruptedIOException.
            Reply.Failed("Could not reach Jev within ${timeLimit.inWholeMilliseconds} ms: ${networkError.javaClass.simpleName}")
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun readReply(bodyText: String): Reply {
        val root = try {
            Json.parseToJsonElement(bodyText) as? JsonObject
        } catch (error: SerializationException) {
            null
        } ?: return Reply.Failed("Jev's answer is not a JSON object")
        val answers = root["answers"] as? JsonObject ?: return Reply.Failed("Jev's answer has no answers")
        val usage = root["usage"] as? JsonObject
        return Reply.Answers(answers, numberOf(usage?.get("cost")))
    }

    /** A JSON number only; the string "0.9" is not an answer. */
    private fun numberOf(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) {
            return null
        }
        return primitive.doubleOrNull
    }

    companion object {
        const val DECISIONS_URL = "https://openrouter.ai/api/alpha/decisions"
        const val MODEL = "typesafe/jev-1.13"

        // Thresholds from spikes/jev-guard/results.md (40 labelled cases): at
        // these values all 12 safe actions ran without a card and all 12
        // risky ones got a card; the closest case, a22, was stopped by two
        // conditions at once. The spike had 0 missed injections and 0 false
        // alarms at 0.5, 0.65 and 0.8, so 0.65 sits in the middle.

        /** An effect answer is trusted only at this confidence or above. */
        const val MINIMUM_EFFECT_CONFIDENCE = 0.9

        /** The action must serve the user's request with at least this probability. */
        const val MINIMUM_SERVES_REQUEST = 0.65

        /** A text is flagged at this probability or above. */
        const val MINIMUM_INJECTION_PROBABILITY = 0.65

        /** The spike's longest text was 300 characters, so this size is a cost and speed choice, not a measured limit. */
        const val CHUNK_SIZE = 8_000

        val DEFAULT_TIME_LIMIT: Duration = 3.seconds

        private const val NO_KEY_REASON = "No OpenRouter key, so Jev was not asked"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Cuts [text] into pieces of at most [CHUNK_SIZE] characters that together are exactly [text]. */
        fun splitIntoChunks(text: String): List<String> {
            if (text.length <= CHUNK_SIZE) {
                return listOf(text)
            }
            val chunks = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = minOf(start + CHUNK_SIZE, text.length)
                // Do not cut an emoji or other pair of surrogate chars in half.
                if (end < text.length && text[end - 1].isHighSurrogate()) {
                    end -= 1
                }
                chunks.add(text.substring(start, end))
                start = end
            }
            return chunks
        }
    }
}
