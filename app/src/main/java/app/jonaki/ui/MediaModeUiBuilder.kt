package app.jonaki.ui

import app.jonaki.feature.chat.ImageModelChoiceUi
import app.jonaki.feature.chat.MediaKind
import app.jonaki.feature.chat.MediaKindRowUi
import app.jonaki.feature.chat.MediaModeUi
import app.jonaki.feature.chat.MediaModelRowUi
import app.jonaki.feature.chat.MediaSettingChange
import app.jonaki.feature.chat.MediaSettingsUi
import app.jonaki.run.MediaCallOptions
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the user set in the settings sheets of media mode while a chat is
 * open (D-174). A null value means "not set": the tool then uses its own
 * default, and the screen shows that default. The model of a picture or a
 * vector image is not here: it is the thread's own image model (D-167).
 */
data class MediaChoices(
    val isHighQuality: Boolean? = null,
    val pictureShape: String? = null,
    val vectorShape: String? = null,
    val videoModelKey: String? = null,
    val videoLengthSeconds: Int? = null,
    val videoResolution: String? = null,
) {
    /** The choices after [change] was made in the sheet of [kind]. */
    fun after(kind: MediaKind, change: MediaSettingChange): MediaChoices = when (change) {
        is MediaSettingChange.Quality -> copy(isHighQuality = change.isHigh)
        is MediaSettingChange.Shape -> when (kind) {
            MediaKind.PICTURE -> copy(pictureShape = change.shape)
            MediaKind.VECTOR -> copy(vectorShape = change.shape)
            MediaKind.VIDEO -> this
        }
        is MediaSettingChange.Length -> copy(videoLengthSeconds = change.seconds)
        is MediaSettingChange.Size -> copy(videoResolution = change.size)
        is MediaSettingChange.Model -> when (kind) {
            // Another model may support other lengths and sizes, so those go back to its defaults.
            MediaKind.VIDEO -> copy(videoModelKey = change.modelKey, videoLengthSeconds = null, videoResolution = null)
            MediaKind.PICTURE, MediaKind.VECTOR -> this
        }
    }

    /**
     * The arguments a send of [kind] adds to the prompt. A video model that is
     * no longer among [usableVideoModelKeys] (removed in Settings meanwhile) is
     * left out, so the tool falls back to the starred one.
     */
    fun callOptionsFor(kind: MediaKind, usableVideoModelKeys: List<String>): MediaCallOptions = when (kind) {
        MediaKind.PICTURE -> MediaCallOptions(isHighQuality = isHighQuality, aspectRatio = pictureShape)
        MediaKind.VECTOR -> MediaCallOptions(aspectRatio = vectorShape)
        MediaKind.VIDEO -> MediaCallOptions(
            modelKey = videoModelKey?.takeIf { key -> key in usableVideoModelKeys },
            durationSeconds = videoLengthSeconds,
            resolution = videoResolution,
        )
    }
}

/**
 * Builds what the message box shows for media mode from what the chat screen
 * already knows. The model for each kind is the one its tool will resolve for
 * the call the send makes, so the name on screen is the model that runs.
 */
object MediaModeUiBuilder {
    /** The shapes the sheet offers beside the model's own; four, so the row fits a 360 dp phone at font scale 1.3. */
    val SHAPES = listOf("1:1", "3:4", "16:9", "9:16")

    /** The most choices one row of the sheet holds without cutting a label at 360 dp. */
    const val MAX_CHOICES_IN_A_ROW = 5

    /**
     * @param availableKinds the kinds whose tool the thread is offered; empty gives null (no media button).
     * @param imageChoices the added image models, named and priced like the Settings rows.
     * @param pictureModelKey the model generate_image would use here; null when none.
     * @param vectorModelKey the model generate_vector_image would use here; null when none.
     * @param videoModelKey the model generate_video would use for a call without a model; null when none.
     * @param videoModels the added video models that have a key, named and priced like the Settings rows.
     * @param defaultIsHighQuality the "Image quality" of Settings, used until the user picks one in the sheet.
     */
    fun build(
        availableKinds: List<MediaKind>,
        selected: MediaKind?,
        canChange: Boolean,
        imageChoices: List<ImageModelChoiceUi>,
        pictureModelKey: String?,
        vectorModelKey: String?,
        videoModelKey: String?,
        videoStepText: VideoStepText,
        videoModels: List<MediaModelRowUi> = emptyList(),
        choices: MediaChoices = MediaChoices(),
        defaultIsHighQuality: Boolean = false,
    ): MediaModeUi? {
        if (availableKinds.isEmpty()) {
            return null
        }
        val pictureModel = imageChoices.firstOrNull { candidate -> candidate.key == pictureModelKey }
        val vectorModel = imageChoices.firstOrNull { candidate -> candidate.key == vectorModelKey }
        val chosenVideoModelKey = videoModelKeyOf(choices, videoModels, videoModelKey)
        val videoCall = videoStepText.callFor(chosenVideoModelKey, choices.videoLengthSeconds, choices.videoResolution)
        val videoModelName = chosenVideoModelKey?.let(videoStepText::displayNameOf)
        val kindRows = availableKinds.map { kind ->
            when (kind) {
                MediaKind.PICTURE -> MediaKindRowUi(kind, pictureModel?.name, pictureModel?.priceText)
                MediaKind.VECTOR -> MediaKindRowUi(kind, vectorModel?.name, vectorModel?.priceText)
                MediaKind.VIDEO -> MediaKindRowUi(kind, videoModelName, videoCall.estimateText)
            }
        }
        val base = MediaModeUi(availableKinds = availableKinds, selected = selected, canChange = canChange, kindRows = kindRows)
        return when (selected) {
            null -> base
            MediaKind.PICTURE -> base.copy(
                modelName = pictureModel?.name,
                priceText = pictureModel?.priceText,
                settings = MediaSettingsUi(
                    models = imageModelRows(imageChoices, isVector = false),
                    selectedModelKey = pictureModelKey,
                    isHighQuality = choices.isHighQuality ?: defaultIsHighQuality,
                    shapes = SHAPES,
                    selectedShape = choices.pictureShape,
                ),
            )
            MediaKind.VECTOR -> base.copy(
                modelName = vectorModel?.name,
                priceText = vectorModel?.priceText,
                settings = MediaSettingsUi(
                    models = imageModelRows(imageChoices, isVector = true),
                    selectedModelKey = vectorModelKey,
                    shapes = SHAPES,
                    selectedShape = choices.vectorShape,
                ),
            )
            MediaKind.VIDEO -> {
                val options = videoStepText.optionsFor(chosenVideoModelKey)
                // The model's own default length stays among the choices, so the row does not change as one is picked.
                val defaultLengthSeconds = videoStepText.defaultsFor(chosenVideoModelKey).durationSeconds
                base.copy(
                    modelName = videoModelName,
                    videoLengthText = videoCall.lengthText,
                    videoResolution = videoCall.resolution,
                    videoCostText = videoCall.estimateText,
                    settings = MediaSettingsUi(
                        models = videoModels,
                        selectedModelKey = chosenVideoModelKey,
                        lengthsSeconds = spread(options.lengthsSeconds, MAX_CHOICES_IN_A_ROW, mustInclude = defaultLengthSeconds),
                        selectedLengthSeconds = videoCall.durationSeconds,
                        sizes = options.resolutions.take(MAX_CHOICES_IN_A_ROW),
                        selectedSize = videoCall.resolution,
                    ),
                )
            }
        }
    }

    /** The video model the next send uses: the one picked in the sheet while it is still added, else the starred one. */
    fun videoModelKeyOf(choices: MediaChoices, videoModels: List<MediaModelRowUi>, defaultModelKey: String?): String? {
        val picked = choices.videoModelKey?.takeIf { key -> videoModels.any { model -> model.key == key } }
        return picked ?: defaultModelKey
    }

    private fun imageModelRows(imageChoices: List<ImageModelChoiceUi>, isVector: Boolean): List<MediaModelRowUi> =
        imageChoices
            .filter { choice -> choice.isVector == isVector }
            .map { choice -> MediaModelRowUi(choice.key, choice.name, choice.priceText) }

    /**
     * At most [count] of the sorted [values], spread evenly from the first to
     * the last. A model may support every length from 2 to 30 seconds; a row of
     * 29 buttons cannot be shown. [mustInclude], the value in use, is always kept.
     */
    fun spread(values: List<Int>, count: Int, mustInclude: Int?): List<Int> {
        if (values.size <= count) {
            return values
        }
        val lastIndex = values.size - 1
        val picks = (0 until count).map { step -> values[(step * lastIndex.toDouble() / (count - 1)).roundToInt()] }.toMutableList()
        if (mustInclude != null && mustInclude in values && mustInclude !in picks) {
            // It takes the place of the middle pick nearest to it, so the first and the last stay.
            val middle = picks.subList(1, picks.size - 1)
            val nearest = middle.minBy { pick -> abs(pick - mustInclude) }
            middle[middle.indexOf(nearest)] = mustInclude
        }
        return picks.sorted()
    }
}
