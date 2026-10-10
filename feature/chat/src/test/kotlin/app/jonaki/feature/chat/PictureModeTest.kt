package app.jonaki.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureModeTest {
    private fun canBeOn(
        imageGenerationAvailable: Boolean = true,
        hasAttachments: Boolean = false,
        isEditing: Boolean = false,
        isRunning: Boolean = false,
    ) = PictureMode.canBeOn(imageGenerationAvailable, hasAttachments, isEditing, isRunning)

    @Test
    fun isAvailableWhenImageGenerationIsAvailableAndTheBoxIsFree() {
        assertTrue(canBeOn())
    }

    @Test
    fun isNotAvailableWithoutImageGeneration() {
        assertFalse(canBeOn(imageGenerationAvailable = false))
    }

    @Test
    fun isNotAvailableWhileFilesAreAttached() {
        assertFalse(canBeOn(hasAttachments = true))
    }

    @Test
    fun isNotAvailableWhileEditingAnEarlierMessage() {
        assertFalse(canBeOn(isEditing = true))
    }

    @Test
    fun isNotAvailableWhileARunIsGoing() {
        assertFalse(canBeOn(isRunning = true))
    }

    @Test
    fun aTapTurnsItOnWhenAllowedAndOffWhenOn() {
        assertTrue(PictureMode.afterToggle(isOn = false, canBeOn = true))
        assertFalse(PictureMode.afterToggle(isOn = true, canBeOn = true))
    }

    @Test
    fun aRefusedTapLeavesItOff() {
        assertFalse(PictureMode.afterToggle(isOn = false, canBeOn = false))
    }

    @Test
    fun itIsOffAfterEachSend() {
        assertFalse(PictureMode.afterSend())
    }

    @Test
    fun attachingAFileWhileOnSwitchesItOff() {
        assertFalse(PictureMode.settled(isOn = true, canBeOn = false))
        assertTrue(PictureMode.settled(isOn = true, canBeOn = true))
        assertFalse(PictureMode.settled(isOn = false, canBeOn = true))
    }
}
