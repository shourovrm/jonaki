package app.jonaki.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelUnavailableTest {
    @Test
    fun openRouterNoEndpointsFoundIsUnavailable() {
        assertTrue(ModelUnavailable.isUnavailable("OpenRouter answered HTTP 404: No endpoints found for stealth/space-bunny-alpha."))
    }

    @Test
    fun otherServicesModelNotFoundAnswersAreUnavailable() {
        assertTrue(ModelUnavailable.isUnavailable("DeepSeek answered HTTP 400: Model Not Exist"))
        assertTrue(ModelUnavailable.isUnavailable("OpenAI answered HTTP 404: The model `gpt-9` does not exist"))
        assertTrue(ModelUnavailable.isUnavailable("Gemini answered HTTP 404: models/gemini-9 is not found for API version v1beta"))
        assertTrue(ModelUnavailable.isUnavailable("Ollama answered HTTP 404: model 'llama9' not found"))
    }

    @Test
    fun aDataPolicyRejectionIsNotAWithdrawnModel() {
        assertFalse(
            ModelUnavailable.isUnavailable("OpenRouter answered HTTP 404: No endpoints found matching your data policy (Free model publication)."),
        )
    }

    @Test
    fun badKeyQuotaAndNetworkErrorsAreNotUnavailable() {
        assertFalse(ModelUnavailable.isUnavailable("OpenRouter answered HTTP 401: No auth credentials found"))
        assertFalse(ModelUnavailable.isUnavailable("OpenRouter answered HTTP 402: Insufficient credits"))
        assertFalse(ModelUnavailable.isUnavailable("OpenRouter answered HTTP 429: Rate limit exceeded"))
        assertFalse(ModelUnavailable.isUnavailable("Could not reach OpenRouter: Unable to resolve host"))
    }

    @Test
    fun aNotFoundAnswerWithoutStatus404Or400IsNotUnavailable() {
        assertFalse(ModelUnavailable.isUnavailable("OpenRouter answered HTTP 500: No endpoints found"))
    }
}
