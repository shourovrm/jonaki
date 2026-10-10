package app.jonaki.run

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceImageGeneratorTest {
    private val asked = mutableListOf<String>()

    private fun generatorNamed(name: String) = ImageGenerator { request ->
        asked += "$name:${request.modelId}"
        ImageOutcome.Success(ByteArray(1), "image/png", null)
    }

    private val dispatcher = ServiceImageGenerator(
        mapOf("openrouter" to generatorNamed("openrouter"), "gemini" to generatorNamed("gemini")),
    )

    @Test
    fun aRequestGoesToTheGeneratorOfItsService() = runBlocking {
        dispatcher.generate(ImageRequest("gemini", "gemini-2.5-flash-image", "a door"))
        dispatcher.generate(ImageRequest("openrouter", "x/y:free", "a door"))

        assertEquals(listOf("gemini:gemini-2.5-flash-image", "openrouter:x/y:free"), asked)
    }

    @Test
    fun anUnknownServiceIsAFailureAndAsksNobody() = runBlocking {
        val outcome = dispatcher.generate(ImageRequest("nosuch", "m", "p"))

        assertTrue(outcome is ImageOutcome.Failed)
        assertEquals(ImageFailure.OTHER, (outcome as ImageOutcome.Failed).kind)
        assertTrue(asked.isEmpty())
    }
}
