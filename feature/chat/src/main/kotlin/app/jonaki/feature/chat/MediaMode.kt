package app.jonaki.feature.chat

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.jonaki.core.ui.JonakiIcons

/**
 * The kinds of media that the message box can make straight from the typed
 * text. The order here is the order of the chips.
 */
enum class MediaKind(
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int,
    @StringRes val sendDescriptionRes: Int,
) {
    PICTURE(R.string.chat_media_picture, R.string.chat_picture_hint, R.string.chat_picture_send),
    VECTOR(R.string.chat_media_vector, R.string.chat_vector_hint, R.string.chat_vector_send),
    VIDEO(R.string.chat_media_video, R.string.chat_video_hint, R.string.chat_video_send),
    ;

    val icon: ImageVector
        get() = when (this) {
            PICTURE -> JonakiIcons.Image
            VECTOR -> JonakiIcons.Shapes
            VIDEO -> JonakiIcons.Videocam
        }
}

/**
 * What the message box shows for media mode. A [ChatUiState] without one
 * (null) shows no chips, because no kind is available to the thread.
 */
@Immutable
data class MediaModeUi(
    /** The kinds that can be made in this thread, in chip order; never empty. */
    val availableKinds: List<MediaKind>,
    /** The kind that is on; null when the mode is off. */
    val selected: MediaKind?,
    /** False while the box holds files, a sent message is being edited or a run is going. */
    val canChange: Boolean,
    /** The model the next call uses, as the user knows it; null when it could not be named. */
    val modelName: String? = null,
    /** Picture and Vector: the model's price, for example "$0.08 per image"; null when it is not known. */
    val priceText: String? = null,
    /** Video: the length the tool will ask for, for example "4 s"; null when the model's list is not loaded. */
    val videoLengthText: String? = null,
    /** Video: the resolution the tool will ask for, for example "720p". */
    val videoResolution: String? = null,
    /** Video: the estimated cost, for example "about $0.12"; null when no estimate can be made. */
    val videoCostText: String? = null,
)

/**
 * The rules of media mode, without any screen: the user's text goes straight
 * to the generating tool of the chosen kind, so a kind needs its model, an
 * empty file tray, a new message (not an edit) and an idle thread.
 */
object MediaMode {
    /** The kinds to show a chip for, from which tools the thread is offered. */
    fun availableKinds(pictureAvailable: Boolean, vectorAvailable: Boolean, videoAvailable: Boolean): List<MediaKind> =
        listOfNotNull(
            MediaKind.PICTURE.takeIf { pictureAvailable },
            MediaKind.VECTOR.takeIf { vectorAvailable },
            MediaKind.VIDEO.takeIf { videoAvailable },
        )

    /**
     * True when the mode may be switched on. A later change will pass attached
     * pictures to the image model as references; until then files and media
     * mode do not combine, and nothing the user attached is dropped.
     */
    fun canChange(hasAttachments: Boolean, isEditing: Boolean, isRunning: Boolean): Boolean =
        !hasAttachments && !isEditing && !isRunning

    /**
     * The kind that is on after a tap on [tapped]. A tap on the selected chip
     * switches the mode off, even when changes are otherwise refused; a tap on
     * another chip selects it only when [canChange]; a refused tap changes nothing.
     */
    fun afterTap(selected: MediaKind?, tapped: MediaKind, canChange: Boolean): MediaKind? {
        if (selected == tapped) {
            return null
        }
        if (!canChange) {
            return selected
        }
        return tapped
    }

    /** Media mode is for one call: it is off again after each send, so a later ordinary message cannot spend money. */
    fun afterSend(): MediaKind? = null

    /**
     * The kind to keep when the conditions change, for example when a file is
     * attached while it is on, or when the key of its model is removed.
     */
    fun settled(selected: MediaKind?, availableKinds: List<MediaKind>, canChange: Boolean): MediaKind? {
        if (selected == null || selected !in availableKinds || !canChange) {
            return null
        }
        return selected
    }

    /**
     * The line under the chips while a kind is on: the model, then for a
     * video its length, resolution and estimated cost (or [priceUnknown] when
     * no estimate can be made, because there is no approval card to show it),
     * for a picture or vector image its price when known. Null when it would be empty.
     */
    fun detailsLine(ui: MediaModeUi, priceUnknown: String): String? {
        val parts = when (ui.selected) {
            null -> return null
            MediaKind.PICTURE, MediaKind.VECTOR -> listOf(ui.modelName, ui.priceText)
            MediaKind.VIDEO -> listOf(ui.modelName, ui.videoLengthText, ui.videoResolution, ui.videoCostText ?: priceUnknown)
        }
        return parts.filterNotNull().joinToString(" · ").ifEmpty { null }
    }
}
