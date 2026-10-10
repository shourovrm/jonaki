package app.jonaki.core.modelcatalog

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenRouterProviderPoliciesTest {
    private val recorded =
        File(System.getProperty("jonaki.testdata"), "openrouter/frontend-all-providers-trimmed.json").readText()

    private val policies = OpenRouterProviderPolicies.parse(recorded)

    @Test
    fun aProviderThatNeitherTrainsNorKeepsPromptsIsPrivate() {
        for (slug in listOf("deepinfra", "fireworks", "relace", "z-ai")) {
            assertEquals(slug, ProviderPrivacy.PRIVATE, OpenRouterProviderPolicies.privacyOfTag(policies, slug))
        }
    }

    @Test
    fun aProviderThatKeepsPromptsButDoesNotTrainKeepsPrompts() {
        assertEquals(ProviderPrivacy.KEEPS_PROMPTS, OpenRouterProviderPolicies.privacyOfTag(policies, "streamlake"))
    }

    @Test
    fun aProviderThatTrainsMayTrain() {
        assertEquals(ProviderPrivacy.MAY_TRAIN, OpenRouterProviderPolicies.privacyOfTag(policies, "deepseek"))
        assertEquals(ProviderPrivacy.MAY_TRAIN, OpenRouterProviderPolicies.privacyOfTag(policies, "thinkingmachines"))
    }

    @Test
    fun trainingWinsOverKeepingPrompts() {
        assertEquals(ProviderPrivacy.MAY_TRAIN, ProviderDataPolicy(training = true, retainsPrompts = true).privacy())
        assertEquals(ProviderPrivacy.MAY_TRAIN, ProviderDataPolicy(training = true, retainsPrompts = false).privacy())
        assertEquals(ProviderPrivacy.KEEPS_PROMPTS, ProviderDataPolicy(training = false, retainsPrompts = true).privacy())
        assertEquals(ProviderPrivacy.PRIVATE, ProviderDataPolicy(training = false, retainsPrompts = false).privacy())
    }

    @Test
    fun aSlugThatIsNotListedIsUnknown() {
        assertNull(OpenRouterProviderPolicies.privacyOfTag(policies, "nobody"))
    }

    @Test
    fun theTagIsMatchedByItsPartBeforeTheFirstSlash() {
        assertEquals(ProviderPrivacy.PRIVATE, OpenRouterProviderPolicies.privacyOfTag(policies, "deepinfra/fp4"))
        assertEquals(ProviderPrivacy.PRIVATE, OpenRouterProviderPolicies.privacyOfTag(policies, "fireworks/us"))
        assertEquals(ProviderPrivacy.KEEPS_PROMPTS, OpenRouterProviderPolicies.privacyOfTag(policies, "streamlake/fp8/fast"))
        assertNull(OpenRouterProviderPolicies.privacyOfTag(policies, "nobody/fp4"))
    }

    @Test
    fun anEntryWithoutADataPolicyIsLeftOut() {
        val json = """{"data":[
            {"slug":"bare"},
            {"slug":"half","dataPolicy":{"training":false}},
            {"slug":"full","dataPolicy":{"training":false,"retainsPrompts":false}}
        ]}"""

        val parsed = OpenRouterProviderPolicies.parse(json)

        assertEquals(setOf("full"), parsed.keys)
    }

    @Test
    fun textThatIsNotTheExpectedJsonGivesNoPolicies() {
        assertTrue(OpenRouterProviderPolicies.parse("not json").isEmpty())
        assertTrue(OpenRouterProviderPolicies.parse("""{"data":{}}""").isEmpty())
        assertTrue(OpenRouterProviderPolicies.parse("""{"error":"x"}""").isEmpty())
    }

    @Test
    fun aFailedDownloadGivesNoPoliciesSoNoRowGetsAMark() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            val list = OpenRouterProviderPolicies(OkHttpClient(), server.url("/policies").toString())

            val loaded = list.load()

            assertTrue(loaded.isEmpty())
            assertNull(OpenRouterProviderPolicies.privacyOfTag(loaded, "deepinfra/fp4"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fetchFailsWithAnIoExceptionOnAnErrorAnswer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            try {
                OpenRouterProviderPolicies(OkHttpClient(), server.url("/policies").toString()).fetch()
                fail("expected an IOException")
            } catch (expected: IOException) {
                assertTrue(expected.message!!.contains("503"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun aGoodDownloadIsKeptForTheNextCallAndAFailedOneIsRetried() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody(recorded))
            val list = OpenRouterProviderPolicies(OkHttpClient(), server.url("/policies").toString())

            assertTrue(list.load().isEmpty())
            val second = list.load()
            val third = list.load()

            assertEquals(ProviderPrivacy.PRIVATE, OpenRouterProviderPolicies.privacyOfTag(second, "deepinfra/fp4"))
            assertEquals(second, third)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
