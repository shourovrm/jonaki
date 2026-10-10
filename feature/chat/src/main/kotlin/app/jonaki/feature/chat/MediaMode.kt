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
    /** The label of the price at the bottom of the settings sheet, for example "Next picture". */
    @StringRes val nextSendRes: Int,
) {
    PICTURE(R.string.chat_media_picture, R.string.chat_picture_hint, R.string.chat_picture_send, R.string.chat_media_next_picture),
    VECTOR(R.string.chat_media_vector, R.string.chat_vector_hint, R.string.chat_vector_send, R.string.chat_media_next_vector),
    VIDEO(R.string.chat_media_video, R.string.chat_video_hint, R.string.chat_video_send, R.string.chat_media_next_video),
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
    /** One row per available kind for the media button's menu, in [availableKinds] order. */
    val kindRows: List<MediaKindRowUi> = emptyList(),
    /** What the settings sheet of the selected kind offers; null while the mode is off. */
    val settings: MediaSettingsUi? = null,
)

/** One kind in the media button's menu: the model a send would use and what it costs. */
@Immutable
data class MediaKindRowUi(
    val kind: MediaKind,
    /** Null when the model could not be named. */
    val modelName: String? = null,
    /** For example "$0.007 per image" or "about $0.12"; null when it is not known. */
    val priceText: String? = null,
)

/** One model of the selected kind in the settings sheet. */
@Immutable
data class MediaModelRowUi(
    /** "service:modelId". */
    val key: String,
    val name: String,
    /** The price in the unit billed, for example "$0.02 to $0.14 per second"; null while unknown. */
    val priceText: String? = null,
)

/**
 * The settings sheet of the selected kind. A list that is empty, or a value
 * that is null where the kind has no such setting, leaves its section out.
 */
@Immutable
data class MediaSettingsUi(
    val models: List<MediaModelRowUi>,
    val selectedModelKey: String?,
    /** Picture: true for High; null for a kind with no quality setting. */
    val isHighQuality: Boolean? = null,
    /** The shapes to offer, for example "1:1"; the sheet adds the model's own shape as the first choice. */
    val shapes: List<String> = emptyList(),
    /** Null for the model's own shape. */
    val selectedShape: String? = null,
    /** Video: the lengths the model supports, in seconds. */
    val lengthsSeconds: List<Int> = emptyList(),
    val selectedLengthSeconds: Int? = null,
    /** Video: the resolutions the model supports, for example "720p". */
    val sizes: List<String> = emptyList(),
    val selectedSize: String? = null,
)

/** One change made in the settings sheet. */
sealed interface MediaSettingChange {
    data class Model(val modelKey: String) : MediaSettingChange

    data class Quality(val isHigh: Boolean) : MediaSettingChange

    /** [shape] is null for the model's own shape. */
    data class Shape(val shape: String?) : MediaSettingChange

    data class Length(val seconds: Int) : MediaSettingChange

    data class Size(val size: String) : MediaSettingChange
}

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
     * True when the mode may be switched on. [hasAttachments] means files that
     * no kind can take; attached pictures do not count, because Picture mode
     * sends them along as references (see [kindsWithAttachments]). Nothing the
     * user attached is ever dropped.
     */
    fun canChange(hasAttachments: Boolean, isEditing: Boolean, isRunning: Boolean): Boolean =
        !hasAttachments && !isEditing && !isRunning

    /**
     * True when every attached file is a picture an image model can take as a
     * reference (png, jpeg or webp, which is what generate_image accepts).
     * False for an empty list.
     */
    fun areReferencePictures(fileNames: List<String>): Boolean =
        fileNames.isNotEmpty() && fileNames.all { name -> name.substringAfterLast('.', "").lowercase() in REFERENCE_PICTURE_ENDINGS }

    /**
     * The kinds that can be switched on while files are attached: with
     * pictures attached only Picture, whose tool takes reference pictures.
     */
    fun kindsWithAttachments(availableKinds: List<MediaKind>, attachedFileNames: List<String>): List<MediaKind> =
        if (areReferencePictures(attachedFileNames)) availableKinds.filter { kind -> kind == MediaKind.PICTURE } else availableKinds

    private val REFERENCE_PICTURE_ENDINGS = setOf("png", "jpg", "jpeg", "webp")

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

    /**
     * The left part of the details line: the model and the settings that are
     * not the model's defaults ([highQuality] is the word for High quality),
     * without the price, which [priceOf] gives. Null while the mode is off or
     * when nothing can be named.
     */
    fun detailsText(ui: MediaModeUi, highQuality: String): String? {
        val settings = ui.settings
        val parts = when (ui.selected) {
            null -> return null
            MediaKind.PICTURE -> listOf(ui.modelName, highQuality.takeIf { settings?.isHighQuality == true }, settings?.selectedShape)
            MediaKind.VECTOR -> listOf(ui.modelName, settings?.selectedShape)
            MediaKind.VIDEO -> listOf(ui.modelName, ui.videoLengthText, ui.videoResolution)
        }
        return parts.filterNotNull().joinToString(" · ").ifEmpty { null }
    }

    /**
     * The price beside the details: a picture's or vector image's price when
     * known, a video's estimate or [priceUnknown], because a send in this mode
     * shows no approval card. Null while the mode is off.
     */
    fun priceOf(ui: MediaModeUi, priceUnknown: String): String? = when (ui.selected) {
        null -> null
        MediaKind.PICTURE, MediaKind.VECTOR -> ui.priceText
        MediaKind.VIDEO -> ui.videoCostText ?: priceUnknown
    }
}
