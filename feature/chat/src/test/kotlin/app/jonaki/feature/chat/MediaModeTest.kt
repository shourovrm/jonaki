package app.jonaki.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModeTest {
    private fun canBeOn(
        imageGenerationAvailable: Boolean = true,
        hasAttachments: Boolean = false,
        isEditing: Boolean = false,
        isRunning: Boolean = false,
    ) = MediaMode.canBeOn(imageGenerationAvailable, hasAttachments, isEditing, isRunning)

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
        assertTrue(MediaMode.afterToggle(isOn = false, canBeOn = true))
        assertFalse(MediaMode.afterToggle(isOn = true, canBeOn = true))
    }

    @Test
    fun aRefusedTapLeavesItOff() {
        assertFalse(MediaMode.afterToggle(isOn = false, canBeOn = false))
    }

    @Test
    fun itIsOffAfterEachSend() {
        assertFalse(MediaMode.afterSend())
    }

    @Test
    fun attachingAFileWhileOnSwitchesItOff() {
        assertFalse(MediaMode.settled(isOn = true, canBeOn = false))
        assertTrue(MediaMode.settled(isOn = true, canBeOn = true))
        assertFalse(MediaMode.settled(isOn = false, canBeOn = true))
    }
}
