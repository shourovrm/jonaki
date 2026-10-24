package app.jonaki.providers.gemini

import app.jonaki.core.providerapi.FailureKind
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/** The 429 bodies are the user's own, from a free-plan key asking for gemini-3.1-pro (2026-10-25). */
class GeminiFailuresTest {
    private val freePlanBody = """{"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_input_token_count, limit: 0, model: gemini-3.1-pro\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-3.1-pro\nPlease retry in 30m18.745137782s.","status":"RESOURCE_EXHAUSTED"}}"""

    private val busyBody = """{"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details.\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 10, model: gemini-3.8-flash\nPlease retry in 12.5s.","status":"RESOURCE_EXHAUSTED"}}"""

    @Test
    fun `a free-tier limit of 0 means the free plan does not include the model`() {
        assertEquals(FailureKind.FREE_PLAN_MODEL, GeminiFailures.kindOf(429, freePlanBody))
    }

    @Test
    fun `a free-tier limit above 0 is a rate limit`() {
        assertEquals(FailureKind.RATE_LIMIT, GeminiFailures.kindOf(429, busyBody))
    }

    @Test
    fun `other statuses have no kind`() {
        assertNull(GeminiFailures.kindOf(400, """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}"""))
        assertNull(GeminiFailures.kindOf(503, """{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}"""))
    }

    @Test
    fun `the short text names the model and the plan`() {
        assertEquals(
            "Gemini's free plan does not include gemini-3.1-pro.",
            GeminiFailures.shortText(FailureKind.FREE_PLAN_MODEL, requestedModel = "gemini-3.1-pro"),
        )
    }
}
