package app.jonaki.feature.chat

import androidx.compose.runtime.Immutable

/**
 * What the message box shows for picture mode. A [ChatUiState] without one
 * (null) has no toggle, because no image model with a saved key is added.
 */
@Immutable
data class PictureModeUi(
    val isOn: Boolean,
    /** False while the box holds files, a sent message is being edited or a run is going. */
    val canChange: Boolean,
    /** The image model the next picture uses; null when none could be named. */
    val modelName: String?,
)

/**
 * The rules of picture mode, without any screen: the user's text goes straight
 * to the image model, so it needs an image model, an empty file tray, a new
 * message (not an edit) and an idle thread.
 */
object PictureMode {
    /**
     * True when picture mode may be on. A later change will pass attached
     * pictures to the image model as references; until then files and picture
     * mode do not combine, and nothing the user attached is dropped.
     */
    fun canBeOn(imageGenerationAvailable: Boolean, hasAttachments: Boolean, isEditing: Boolean, isRunning: Boolean): Boolean =
        imageGenerationAvailable && !hasAttachments && !isEditing && !isRunning

    /** The new switch position after a tap; a tap that is refused changes nothing. */
    fun afterToggle(isOn: Boolean, canBeOn: Boolean): Boolean {
        if (isOn) {
            return false
        }
        return canBeOn
    }

    /** Picture mode is for one picture: it is off again after each send, so a later ordinary message cannot spend money. */
    fun afterSend(): Boolean = false

    /** The switch position to keep when the conditions change, for example when a file is attached while it is on. */
    fun settled(isOn: Boolean, canBeOn: Boolean): Boolean = isOn && canBeOn
}
