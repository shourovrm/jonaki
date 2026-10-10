package app.jonaki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.modelcatalog.VideoModelInfo
import app.jonaki.core.modelcatalog.VideoModelListResult
import app.jonaki.feature.settings.AddVideoModelsScreen
import app.jonaki.feature.settings.VideoGenerationUi
import app.jonaki.feature.settings.VideoModelRowUi
import app.jonaki.feature.settings.VideoPickModelUi
import app.jonaki.feature.settings.VideoPickerState
import app.jonaki.run.VideoToolSetup
import app.jonaki.settings.VideoModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenRouter's video model list for a screen: the cache file at once (read
 * off the main thread), then the fresh list when it has loaded. Empty while
 * [needed] is false, so a thread with no video model never reads the file.
 */
@Composable
private fun rememberVideoModelInfos(application: JonakiApplication, needed: Boolean): List<VideoModelInfo> {
    val models by produceState(emptyList<VideoModelInfo>(), needed) {
        if (!needed) {
            return@produceState
        }
        value = withContext(Dispatchers.IO) { application.videoModelList.cachedModels() }
        val loaded = application.videoModelList.load()
        if (loaded is VideoModelListResult.Loaded) {
            value = loaded.models
        }
    }
    return models
}

@Composable
private fun videoListWords(): VideoListWords {
    val resources = LocalContext.current.resources
    return VideoListWords(
        priceRange = { lowest, highest -> resources.getString(R.string.video_price_range, lowest, highest) },
        priceSingle = { price -> resources.getString(R.string.video_price_single, price) },
        perToken = stringResource(R.string.video_price_per_token),
        lengthsRun = { first, last -> resources.getString(R.string.video_lengths_run, first, last) },
        lengthsList = { seconds -> resources.getString(R.string.video_lengths_list, seconds) },
    )
}

/** The words and facts of the approval card's and the step line's generate_video text. */
@Composable
internal fun rememberVideoStepText(application: JonakiApplication, videoModels: VideoModels): VideoStepText {
    val resources = LocalContext.current.resources
    val infos = rememberVideoModelInfos(application, needed = videoModels.modelKeys.isNotEmpty())
    return VideoStepText(
        factsByModelKey = VideoToolSetup.factsByKey(infos),
        defaultModelKey = videoModels.defaultModelKey,
        words = VideoStepWords(
            collectsEarlierJob = { jobId -> resources.getString(R.string.step_video_collects, jobId) },
            aboutCost = { dollars -> resources.getString(R.string.step_video_cost_about, dollars) },
            priceIsPerToken = resources.getString(R.string.step_video_cost_per_token),
            seconds = { seconds -> resources.getString(R.string.step_video_seconds, seconds) },
        ),
        displayNamesByModelKey = infos.associate { info -> ModelKey.of(VideoToolSetup.OPENROUTER, info.id) to info.name },
    )
}

/**
 * The Settings section: the added models, named, priced and with their
 * supported lengths from the list (cached for a day). Until the list has
 * loaded, or when it cannot be, a row shows its id and no price.
 */
@Composable
internal fun videoGenerationFor(application: JonakiApplication, videoModels: VideoModels, hasKey: Boolean): VideoGenerationUi {
    val infos = rememberVideoModelInfos(application, needed = videoModels.modelKeys.isNotEmpty())
    val words = videoListWords()
    val infoById = infos.associateBy { info -> info.id }
    val rows = videoModels.modelKeys.map { modelKey ->
        val modelId = ModelKey.modelOf(modelKey)
        val info = infoById[modelId]
        VideoModelRowUi(
            key = modelKey,
            id = modelId,
            name = info?.name ?: modelId,
            priceText = info?.let { VideoListText.price(it, words) },
            lengthsText = info?.let { VideoListText.lengths(it, words) },
            isDefault = modelKey == videoModels.defaultModelKey,
        )
    }
    return VideoGenerationUi(hasKey = hasKey, models = rows)
}

/** The video model picker; the list needs no key and no further request for prices. */
@Composable
internal fun AddVideoModelsRoute(application: JonakiApplication, onFinished: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val listResult by produceState<VideoModelListResult?>(null, attempt) {
        value = null
        value = application.videoModelList.load()
    }
    val snapshot by application.settings.snapshot.collectAsState()
    val alreadyAdded = snapshot.videoModels.modelIdsOf(VideoToolSetup.OPENROUTER).toSet()
    val words = videoListWords()
    val pickerState = when (val result = listResult) {
        null -> VideoPickerState.Loading
        is VideoModelListResult.Failed -> VideoPickerState.Failed(result.reason)
        is VideoModelListResult.Loaded -> VideoPickerState.Loaded(
            result.models.map { model ->
                VideoPickModelUi(
                    id = model.id,
                    name = model.name,
                    isAdded = model.id in alreadyAdded,
                    priceText = VideoListText.price(model, words),
                    lengthsText = VideoListText.lengths(model, words),
                )
            },
        )
    }
    AddVideoModelsScreen(
        serviceName = "OpenRouter",
        state = pickerState,
        onClose = onFinished,
        onRetry = { attempt += 1 },
        onDone = { modelIds ->
            application.settings.update { current ->
                current.copy(
                    videoModels = modelIds.fold(current.videoModels) { updated, modelId -> updated.addModel(VideoToolSetup.OPENROUTER, modelId) },
                )
            }
            onFinished()
        },
    )
}
