package app.jonaki.guards.jev

import app.jonaki.core.guardapi.ActionVerdict
import app.jonaki.core.guardapi.GuardUsage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JevGuardTest {
    private val secretKey = "sk-or-test-secret-0123456789"

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun guard(
        apiKey: String = secretKey,
        timeLimit: kotlin.time.Duration = 3_000.milliseconds,
        thresholds: JevThresholds = JevThresholds.BALANCED,
    ) = JevGuard(apiKey, OkHttpClient(), server.url("/api/alpha/decisions").toString(), timeLimit, thresholds)

    private val noArguments: JsonObject = buildJsonObject { }

    private fun recordedCases(): JsonArray {
        val file = File(System.getProperty("jonaki.testdata"), "jev-guard/results.json")
        return Json.parseToJsonElement(file.readText()).jsonArray
    }

    private fun casesExpecting(expectation: String): List<JsonObject> =
        recordedCases().map { record -> record.jsonObject }.filter { record ->
            record["case"]!!.jsonObject["expect"]!!.jsonPrimitive.content == expectation
        }

    private fun replay(record: JsonObject) {
        server.enqueue(MockResponse().setBody(record["response"]!!.toString()))
    }

    private fun actionCaseVerdict(record: JsonObject): ActionVerdict = runBlocking {
        val case = record["case"]!!.jsonObject
        replay(record)
        guard().judgeAction(
            userRequest = case["request"]!!.jsonPrimitive.content,
            toolName = case["tool"]!!.jsonPrimitive.content,
            arguments = case["arguments"]!!.jsonObject,
        )
    }

    private fun resultCaseVerdict(record: JsonObject) = runBlocking {
        val case = record["case"]!!.jsonObject
        replay(record)
        guard().screenResult(case["source"]!!.jsonPrimitive.content, case["text"]!!.jsonPrimitive.content)
    }

    private fun answerBody(
        choice: String = "reversible",
        confidence: Double = 1.0,
        servesRequest: Double = 0.98,
    ): String = """
        {"answers": {
          "effect": {"type": "choice", "choice": "$choice", "confidence": $confidence},
          "serves_request": {"type": "noul", "noul": $servesRequest}},
         "usage": {"input_tokens": 657, "output_tokens": 73, "cost": 2.7594e-05}}
    """.trimIndent()

    private fun injectionBody(probability: Double) =
        """{"answers": {"is_injection": {"type": "noul", "noul": $probability}}, "usage": {"cost": 1.0e-05}}"""

    private fun judgeWith(body: String): ActionVerdict = runBlocking {
        server.enqueue(MockResponse().setBody(body))
        guard().judgeAction("remind me at 8", "phone", noArguments)
    }

    private fun assertShowsCard(verdict: ActionVerdict) {
        assertTrue("expected a card but got $verdict", verdict is ActionVerdict.ShowCard)
    }

    @Test
    fun recordedFileHasTheExpectedCaseCounts() {
        assertEquals(12, casesExpecting("allow").size)
        assertEquals(12, casesExpecting("card").size)
        assertEquals(8, casesExpecting("clean").size)
        assertEquals(8, casesExpecting("injection").size)
    }

    @Test
    fun everyRecordedSafeActionRunsWithoutACard() {
        for (record in casesExpecting("allow")) {
            val verdict = actionCaseVerdict(record)
            val id = record["case"]!!.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue("case $id: $verdict", verdict is ActionVerdict.MayRunWithoutCard)
        }
    }

    @Test
    fun everyRecordedRiskyActionShowsTheCard() {
        for (record in casesExpecting("card")) {
            val verdict = actionCaseVerdict(record)
            val id = record["case"]!!.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue("case $id: $verdict", verdict is ActionVerdict.ShowCard)
        }
    }

    @Test
    fun caseA22IsStoppedByConfidenceAndByServesRequest() {
        val record = casesExpecting("card").first { it["case"]!!.jsonObject["id"]!!.jsonPrimitive.content == "a22" }

        val verdict = actionCaseVerdict(record)

        assertTrue(verdict is ActionVerdict.ShowCard)
        assertTrue(verdict.reason, verdict.reason.contains("confidence"))
        assertTrue(verdict.reason, verdict.reason.contains("serves_request"))
    }

    @Test
    fun everyRecordedCleanTextIsNotFlagged() {
        for (record in casesExpecting("clean")) {
            val verdict = resultCaseVerdict(record)
            val id = record["case"]!!.jsonObject["id"]!!.jsonPrimitive.content
            assertFalse("case $id: $verdict", verdict.isFlagged)
        }
    }

    @Test
    fun everyRecordedInjectedTextIsFlagged() {
        for (record in casesExpecting("injection")) {
            val verdict = resultCaseVerdict(record)
            val id = record["case"]!!.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue("case $id: $verdict", verdict.isFlagged)
        }
    }

    @Test
    fun costOfTheCallIsReported() {
        val verdict = judgeWith(answerBody())

        assertEquals(2.7594e-05, verdict.costUsd!!, 1e-12)
    }

    @Test
    fun tokensOfTheCallAreReported() {
        val verdict = judgeWith(answerBody())

        assertEquals(GuardUsage(inputTokens = 657, outputTokens = 73), verdict.usage)
    }

    @Test
    fun anActionThatRunsWithoutACardCarriesItsNote() {
        val verdict = judgeWith(answerBody(choice = "reversible", confidence = 0.97, servesRequest = 0.81))

        assertEquals("Jev: ran without a card (reversible 0.97, asked for 0.81)", verdict.note)
    }

    @Test
    fun anActionThatGetsACardCarriesItsNote() {
        val verdict = judgeWith(answerBody(servesRequest = 0.41))

        assertEquals("Jev: card shown (not sure the user asked, 0.41)", verdict.note)
    }

    @Test
    fun aFailedActionCallCarriesTheNoAnswerNote() {
        server.enqueue(MockResponse().setResponseCode(500))

        val verdict = runBlocking { guard().judgeAction("remind me at 8", "phone", noArguments) }

        assertEquals("Jev: no answer, card shown", verdict.note)
    }

    @Test
    fun aResultNoteGivesTheHighestProbabilityOverAllParts() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.83)))
        server.enqueue(MockResponse().setBody(injectionBody(0.04)))
        val twoParts = "a".repeat(JevGuard.CHUNK_SIZE + 1)

        val verdict = guard().screenResult("web_fetch", twoParts)

        assertEquals("Jev: result flagged (0.83)", verdict.note)
    }

    @Test
    fun aClearResultCarriesItsNoteAndAFailedOneSaysNoAnswer() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.04)))
        server.enqueue(MockResponse().setResponseCode(500))

        assertEquals("Jev: result clear (0.04)", guard().screenResult("web_fetch", "text").note)
        assertEquals("Jev: no answer, result not checked", guard().screenResult("web_fetch", "text").note)
    }

    @Test
    fun confidenceBoundaryIsInclusiveAtZeroPointNine() {
        assertTrue(judgeWith(answerBody(confidence = 0.9)) is ActionVerdict.MayRunWithoutCard)
        assertShowsCard(judgeWith(answerBody(confidence = 0.89)))
    }

    @Test
    fun servesRequestBoundaryIsInclusiveAtZeroPointSixtyFive() {
        assertTrue(judgeWith(answerBody(servesRequest = 0.65)) is ActionVerdict.MayRunWithoutCard)
        assertShowsCard(judgeWith(answerBody(servesRequest = 0.64)))
    }

    @Test
    fun readOnlyAndReversibleMayRunButIrreversibleAndSendsOutDoNot() {
        assertTrue(judgeWith(answerBody(choice = "read_only")) is ActionVerdict.MayRunWithoutCard)
        assertTrue(judgeWith(answerBody(choice = "reversible")) is ActionVerdict.MayRunWithoutCard)
        assertShowsCard(judgeWith(answerBody(choice = "irreversible")))
        assertShowsCard(judgeWith(answerBody(choice = "sends_out")))
    }

    @Test
    fun injectionBoundaryIsInclusiveAtZeroPointSixtyFive() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.65)))
        server.enqueue(MockResponse().setBody(injectionBody(0.64)))

        assertTrue(guard().screenResult("web_fetch", "text").isFlagged)
        assertFalse(guard().screenResult("web_fetch", "text").isFlagged)
    }

    private fun judgeWith(thresholds: JevThresholds, body: String): ActionVerdict = runBlocking {
        server.enqueue(MockResponse().setBody(body))
        guard(thresholds = thresholds).judgeAction("remind me at 8", "phone", noArguments)
    }

    @Test
    fun carefulNeedsMoreConfidenceThanBalancedAndRelaxedLess() {
        val fairlySure = answerBody(confidence = 0.92, servesRequest = 0.7)
        assertShowsCard(judgeWith(JevThresholds.CAREFUL, fairlySure))
        assertTrue(judgeWith(JevThresholds.BALANCED, fairlySure) is ActionVerdict.MayRunWithoutCard)

        val lessSure = answerBody(confidence = 0.82, servesRequest = 0.55)
        assertShowsCard(judgeWith(JevThresholds.BALANCED, lessSure))
        assertTrue(judgeWith(JevThresholds.RELAXED, lessSure) is ActionVerdict.MayRunWithoutCard)
    }

    @Test
    fun noPresetLetsAnIrreversibleActionRunWithoutACard() {
        assertShowsCard(judgeWith(JevThresholds.RELAXED, answerBody(choice = "irreversible")))
        assertShowsCard(judgeWith(JevThresholds.RELAXED, answerBody(choice = "sends_out")))
    }

    @Test
    fun carefulFlagsATextThatRelaxedLetsPass() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.6)))
        server.enqueue(MockResponse().setBody(injectionBody(0.6)))

        assertTrue(guard(thresholds = JevThresholds.CAREFUL).screenResult("web_fetch", "text").isFlagged)
        assertFalse(guard(thresholds = JevThresholds.RELAXED).screenResult("web_fetch", "text").isFlagged)
    }

    @Test
    fun unknownChoiceShowsTheCard() {
        val verdict = judgeWith(answerBody(choice = "mostly_harmless"))

        assertShowsCard(verdict)
        assertTrue(verdict.reason, verdict.reason.contains("mostly_harmless"))
    }

    @Test
    fun httpErrorsShowTheCardAndNameTheStatus() {
        for (status in listOf(401, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":"nope"}"""))
            val verdict = runBlocking { guard().judgeAction("remind me at 8", "phone", noArguments) }

            assertShowsCard(verdict)
            assertTrue(verdict.reason, verdict.reason.contains("HTTP $status"))
        }
    }

    @Test
    fun httpErrorsLeaveAResultNotFlagged() {
        for (status in listOf(401, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(status))
            val verdict = runBlocking { guard().screenResult("web_fetch", "text") }

            assertFalse(verdict.isFlagged)
            assertTrue(verdict.reason, verdict.reason.contains("HTTP $status"))
        }
    }

    @Test
    fun timeoutShowsTheCardAndLeavesAResultNotFlagged() = runBlocking {
        server.enqueue(MockResponse().setBody(answerBody()).setBodyDelay(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody(injectionBody(0.99)).setBodyDelay(2, TimeUnit.SECONDS))
        val impatient = guard(timeLimit = 200.milliseconds)

        val actionVerdict = impatient.judgeAction("remind me at 8", "phone", noArguments)
        val resultVerdict = impatient.screenResult("web_fetch", "text")

        assertShowsCard(actionVerdict)
        assertFalse(resultVerdict.isFlagged)
        assertTrue(actionVerdict.reason, actionVerdict.reason.contains("200"))
    }

    @Test
    fun malformedJsonFailsSafe() {
        for (body in listOf("not json at all", "[1, 2]", """{"answers": 5}""", """{"nothing": true}""")) {
            assertShowsCard(judgeWith(body))
            server.enqueue(MockResponse().setBody(body))
            assertFalse(runBlocking { guard().screenResult("web_fetch", "text") }.isFlagged)
        }
    }

    @Test
    fun missingOrWrongTypedAnswersFailSafe() {
        val noConfidence = """{"answers": {"effect": {"type": "choice", "choice": "read_only"}, "serves_request": {"type": "noul", "noul": 0.9}}}"""
        val noServes = """{"answers": {"effect": {"type": "choice", "choice": "read_only", "confidence": 1}}}"""
        val outOfRange = """{"answers": {"effect": {"type": "choice", "choice": "read_only", "confidence": 1}, "serves_request": {"type": "noul", "noul": 7}}}"""

        assertShowsCard(judgeWith(noConfidence))
        assertShowsCard(judgeWith(noServes))
        assertShowsCard(judgeWith(outOfRange))
    }

    @Test
    fun malformedInjectionAnswerIsNotFlagged() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"answers": {"is_injection": {"type": "noul", "noul": "high"}}}"""))
        server.enqueue(MockResponse().setBody("""{"answers": {"is_injection": {"type": "noul", "noul": 1.5}}}"""))

        assertFalse(guard().screenResult("web_fetch", "text").isFlagged)
        assertFalse(guard().screenResult("web_fetch", "text").isFlagged)
    }

    @Test
    fun missingKeyMakesNoCallAndFailsSafe() = runBlocking {
        val withoutKey = guard(apiKey = "  ")

        val actionVerdict = withoutKey.judgeAction("remind me at 8", "phone", noArguments)
        val resultVerdict = withoutKey.screenResult("web_fetch", "text")

        assertShowsCard(actionVerdict)
        assertFalse(resultVerdict.isFlagged)
        assertTrue(actionVerdict.reason, actionVerdict.reason.contains("key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun requestCarriesThePrivacyFilterTheModelTheStateAndTheKeyOnlyInTheHeader() {
        judgeWith(answerBody())

        val request = server.takeRequest()
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val provider = body["provider"]!!.jsonObject

        assertEquals("POST", request.method)
        assertEquals("/api/alpha/decisions", request.path)
        assertEquals("Bearer $secretKey", request.getHeader("Authorization"))
        assertEquals("typesafe/jev-1.13", body["model"]!!.jsonPrimitive.content)
        assertEquals("deny", provider["data_collection"]!!.jsonPrimitive.content)
        assertEquals("true", provider["zdr"]!!.jsonPrimitive.content)
        assertEquals("remind me at 8", body["state"]!!.jsonObject["request"]!!.jsonPrimitive.content)
        assertEquals(setOf("effect", "serves_request"), body["questions"]!!.jsonObject.keys)
        assertFalse(body.toString().contains(secretKey))
    }

    @Test
    fun resultRequestCarriesThePrivacyFilterAndTheText() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.02)))

        guard().screenResult("web_fetch", "a page")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("deny", body["provider"]!!.jsonObject["data_collection"]!!.jsonPrimitive.content)
        assertEquals("web_fetch", body["state"]!!.jsonObject["source"]!!.jsonPrimitive.content)
        assertEquals("a page", body["state"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(setOf("is_injection"), body["questions"]!!.jsonObject.keys)
    }

    @Test
    fun failureReasonsNeverContainTheKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key $secretKey"))
        server.enqueue(MockResponse().setBody("not json"))

        val reasons = listOf(
            guard().judgeAction("remind me at 8", "phone", noArguments).reason,
            guard().screenResult("web_fetch", "text").reason,
        )

        for (reason in reasons) {
            assertFalse(reason, reason.contains(secretKey))
        }
    }

    /** Answers "injection" for a chunk that holds the marker and "clean" for every other chunk. */
    private fun answerByMarker(marker: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val text = Json.parseToJsonElement(request.body.readUtf8())
                .jsonObject["state"]!!.jsonObject["text"]!!.jsonPrimitive.content
            val probability = if (text.contains(marker)) 0.97 else 0.02
            return MockResponse().setBody(injectionBody(probability))
        }
    }

    @Test
    fun twentyThousandCharactersAreSplitIntoThreeChunksAndOneFlaggedChunkFlagsTheWhole() = runBlocking {
        server.dispatcher = answerByMarker("PLANTED")
        val text = "a".repeat(9_000) + "PLANTED" + "b".repeat(20_000 - 9_007)

        val verdict = guard().screenResult("web_fetch", text)

        assertEquals(20_000, text.length)
        assertEquals(3, server.requestCount)
        assertTrue(verdict.isFlagged)
        assertEquals(0.97, verdict.injectionProbability!!, 1e-9)
        assertEquals(3e-5, verdict.costUsd!!, 1e-12)
    }

    @Test
    fun longCleanTextIsNotFlagged() = runBlocking {
        server.dispatcher = answerByMarker("PLANTED")

        val verdict = guard().screenResult("web_fetch", "a".repeat(20_000))

        assertEquals(3, server.requestCount)
        assertFalse(verdict.isFlagged)
    }

    @Test
    fun chunksTogetherHoldExactlyTheOriginalText() {
        val text = "x".repeat(8_000) + "y".repeat(8_000) + "z".repeat(10)

        val chunks = JevGuard.splitIntoChunks(text)

        assertEquals(listOf(8_000, 8_000, 10), chunks.map { it.length })
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun textOfExactlyTheChunkSizeIsOneCall() = runBlocking {
        server.enqueue(MockResponse().setBody(injectionBody(0.02)))

        guard().screenResult("web_fetch", "a".repeat(8_000))

        assertEquals(1, server.requestCount)
    }

    @Test
    fun chunkSplitNeverCutsASurrogatePair() {
        // "😀" is two chars; put its first half at index 7,999 so that a plain cut would split it.
        val text = "a".repeat(7_999) + "😀" + "b".repeat(100)

        val chunks = JevGuard.splitIntoChunks(text)

        assertEquals(text, chunks.joinToString(""))
        assertFalse(chunks.first().last().isHighSurrogate())
    }

    @Test
    fun oneFailedChunkDoesNotHideAFlaggedOne() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val text = Json.parseToJsonElement(request.body.readUtf8())
                    .jsonObject["state"]!!.jsonObject["text"]!!.jsonPrimitive.content
                return when {
                    text.startsWith("a") -> MockResponse().setResponseCode(500)
                    else -> MockResponse().setBody(injectionBody(0.99))
                }
            }
        }

        val verdict = guard().screenResult("web_fetch", "a".repeat(8_000) + "b".repeat(100))

        assertTrue(verdict.isFlagged)
    }

    @Test
    fun cancellationIsRespectedAndNotSwallowed() = runBlocking {
        server.enqueue(MockResponse().setBody(answerBody()).setBodyDelay(2, TimeUnit.SECONDS))
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            guard().judgeAction("remind me at 8", "phone", noArguments)
        }
        delay(100)

        pending.cancelAndJoin()

        assertTrue(pending.isCancelled)
    }
}
