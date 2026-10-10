package app.jonaki.run

import app.jonaki.core.toolapi.ImageFailure
import app.jonaki.core.toolapi.ImageGenerator
import app.jonaki.core.toolapi.ImageOutcome
import app.jonaki.core.toolapi.ImageRequest

/**
 * The one generator the generate_image tool sees: it hands each request to
 * the generator of the request's image service. This keeps the tool module
 * free of any service, as it may depend only on core/tool-api.
 */
class ServiceImageGenerator(private val generatorsByServiceKey: Map<String, ImageGenerator>) : ImageGenerator {
    override suspend fun generate(request: ImageRequest): ImageOutcome {
        val generator = generatorsByServiceKey[request.serviceKey]
            ?: return ImageOutcome.Failed(ImageFailure.OTHER, "no image service called ${request.serviceKey}")
        return generator.generate(request)
    }
}
