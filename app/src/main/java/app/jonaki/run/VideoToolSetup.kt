package app.jonaki.run

import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.modelcatalog.VideoModelInfo
import app.jonaki.core.modelcatalog.VideoModelList
import app.jonaki.core.providerapi.Usage
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.providers.openaicompatible.OpenRouterVideoGenerator
import app.jonaki.settings.VideoModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** What generate_video needs for one thread; see [VideoToolSetup.servicesFor]. */
class VideoToolServices(
    val generator: VideoGenerator,
    /** The added models of services that have a saved key, as "service:modelId". */
    val modelKeys: List<String>,
    val defaultModelKey: () -> String?,
    /** What the service's list says about each added model; empty when no list was ever loaded. */
    val facts: Map<String, VideoModelFacts>,
    /** Saves a finished job's cost on the thread; the tool calls it once per job. */
    val onJobCompleted: suspend (modelKey: String, costUsd: Double?) -> Unit,
)

/**
 * Builds the services of generate_video for a thread: the OpenRouter
 * generator, the added models that can be used now, their supported values
 * from the cached model list, and the cost row. The list is never awaited
 * here: a run uses the cache file as it is and a refresh starts in the
 * background, so a missing list only means the tool sends values unchecked.
 */
class VideoToolSetup(
    private val readOpenRouterKey: () -> String?,
    private val readVideoModels: () -> VideoModels,
    private val httpClient: OkHttpClient,
    private val videoModelList: VideoModelList,
    private val scope: CoroutineScope,
    /** The hidden row that carries a call's usage; `BackgroundModel.saveUsage`. */
    private val saveUsage: suspend (threadId: String, modelKey: String, usage: Usage, costUsd: Double?) -> Unit,
) {
    /** Null leaves generate_video out: no OpenRouter key saved, or no video model added. */
    fun servicesFor(threadId: String): VideoToolServices? {
        val modelKeys = usableModelKeys(hasOpenRouterKey = readOpenRouterKey() != null, videoModels = readVideoModels())
        if (modelKeys.isEmpty()) {
            return null
        }
        // Fills or renews the cache for the next run; this run does not wait for it.
        scope.launch { videoModelList.load() }
        return VideoToolServices(
            generator = OpenRouterVideoGenerator(readOpenRouterKey, httpClient),
            modelKeys = modelKeys,
            defaultModelKey = { readVideoModels().defaultModelKey },
            facts = factsByKey(videoModelList.cachedModels()),
            onJobCompleted = { modelKey, costUsd ->
                // The job is already paid for, so a Stop that arrives now must not lose its cost.
                withContext(NonCancellable) { saveUsage(threadId, modelKey, Usage(inputTokens = 0, outputTokens = 0), costUsd) }
            },
        )
    }

    companion object {
        const val OPENROUTER = "openrouter"

        /**
         * The added video models that can be used now: only OpenRouter serves
         * video, and only with a saved key. generate_video is offered exactly
         * when this is not empty; the chat screen asks the same question to
         * show the Video chip.
         */
        fun usableModelKeys(hasOpenRouterKey: Boolean, videoModels: VideoModels): List<String> {
            if (!hasOpenRouterKey) {
                return emptyList()
            }
            return videoModels.modelKeys.filter { key -> ModelKey.serviceOf(key) == OPENROUTER }
        }

        /** The model a generate_video call without a model uses: the starred one if usable, else the first usable one. */
        fun modelKeyOfPlainCall(usableModelKeys: List<String>, videoModels: VideoModels): String? =
            videoModels.defaultModelKey?.takeIf { key -> key in usableModelKeys } ?: usableModelKeys.firstOrNull()

        fun factsByKey(models: List<VideoModelInfo>): Map<String, VideoModelFacts> =
            models.associate { model -> ModelKey.of(OPENROUTER, model.id) to factsOf(model) }

        fun factsOf(model: VideoModelInfo) = VideoModelFacts(
            modelKey = ModelKey.of(OPENROUTER, model.id),
            supportedDurations = model.supportedDurations,
            supportedResolutions = model.supportedResolutions,
            supportedAspectRatios = model.supportedAspectRatios,
            generatesAudio = model.generatesAudio,
            priceSkus = model.priceSkus,
        )
    }
}
