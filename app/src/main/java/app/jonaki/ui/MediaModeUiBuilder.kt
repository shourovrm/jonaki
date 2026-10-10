package app.jonaki.ui

import app.jonaki.feature.chat.ImageModelChoiceUi
import app.jonaki.feature.chat.MediaKind
import app.jonaki.feature.chat.MediaModeUi

/**
 * Builds what the message box shows for media mode from what the chat screen
 * already knows. The model for each kind is the one its tool will resolve for
 * a call that names none, so the name on screen is the model that runs.
 */
object MediaModeUiBuilder {
    /**
     * @param availableKinds the kinds whose tool the thread is offered; empty gives null (no chips).
     * @param imageChoices the added image models, named and priced like the Settings rows.
     * @param pictureModelKey the model generate_image would use here; null when none.
     * @param vectorModelKey the model generate_vector_image would use here; null when none.
     * @param videoModelKey the model generate_video would use for a call without a model; null when none.
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
    ): MediaModeUi? {
        if (availableKinds.isEmpty()) {
            return null
        }
        val base = MediaModeUi(availableKinds = availableKinds, selected = selected, canChange = canChange)
        return when (selected) {
            null -> base
            MediaKind.PICTURE -> base.withImageModel(imageChoices, pictureModelKey)
            MediaKind.VECTOR -> base.withImageModel(imageChoices, vectorModelKey)
            MediaKind.VIDEO -> base.withVideoModel(videoStepText, videoModelKey)
        }
    }

    private fun MediaModeUi.withImageModel(imageChoices: List<ImageModelChoiceUi>, modelKey: String?): MediaModeUi {
        val choice = imageChoices.firstOrNull { candidate -> candidate.key == modelKey }
        return copy(modelName = choice?.name, priceText = choice?.priceText)
    }

    private fun MediaModeUi.withVideoModel(videoStepText: VideoStepText, modelKey: String?): MediaModeUi {
        if (modelKey == null) {
            return this
        }
        val defaults = videoStepText.defaultsFor(modelKey)
        return copy(
            modelName = videoStepText.displayNameOf(modelKey),
            videoLengthText = defaults.lengthText,
            videoResolution = defaults.resolution,
            videoCostText = defaults.estimateText,
        )
    }
}
