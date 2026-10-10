package app.jonaki.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModeTest {
    private val allKinds = listOf(MediaKind.PICTURE, MediaKind.VECTOR, MediaKind.VIDEO)

    @Test
    fun oneChipForEachAvailableKindInAFixedOrder() {
        assertEquals(allKinds, MediaMode.availableKinds(pictureAvailable = true, vectorAvailable = true, videoAvailable = true))
        assertEquals(
            listOf(MediaKind.PICTURE, MediaKind.VIDEO),
            MediaMode.availableKinds(pictureAvailable = true, vectorAvailable = false, videoAvailable = true),
        )
        assertEquals(
            listOf(MediaKind.VECTOR),
            MediaMode.availableKinds(pictureAvailable = false, vectorAvailable = true, videoAvailable = false),
        )
        assertEquals(
            listOf(MediaKind.VIDEO),
            MediaMode.availableKinds(pictureAvailable = false, vectorAvailable = false, videoAvailable = true),
        )
    }

    @Test
    fun noKindMeansNoChips() {
        assertTrue(MediaMode.availableKinds(pictureAvailable = false, vectorAvailable = false, videoAvailable = false).isEmpty())
    }

    @Test
    fun changesAreAllowedOnlyWithAFreeBoxAnIdleThreadAndANewMessage() {
        assertTrue(MediaMode.canChange(hasAttachments = false, isEditing = false, isRunning = false))
        assertFalse(MediaMode.canChange(hasAttachments = true, isEditing = false, isRunning = false))
        assertFalse(MediaMode.canChange(hasAttachments = false, isEditing = true, isRunning = false))
        assertFalse(MediaMode.canChange(hasAttachments = false, isEditing = false, isRunning = true))
    }

    @Test
    fun aTapSelectsAKindWhenOff() {
        assertEquals(MediaKind.VECTOR, MediaMode.afterTap(selected = null, tapped = MediaKind.VECTOR, canChange = true))
    }

    @Test
    fun aTapOnAnotherChipSwitchesToIt() {
        assertEquals(MediaKind.VIDEO, MediaMode.afterTap(selected = MediaKind.PICTURE, tapped = MediaKind.VIDEO, canChange = true))
    }

    @Test
    fun aTapOnTheSelectedChipSwitchesTheModeOff() {
        assertNull(MediaMode.afterTap(selected = MediaKind.PICTURE, tapped = MediaKind.PICTURE, canChange = true))
    }

    @Test
    fun aModeThatIsOnCanAlwaysBeSwitchedOffEvenWhenChangesAreRefused() {
        assertNull(MediaMode.afterTap(selected = MediaKind.VIDEO, tapped = MediaKind.VIDEO, canChange = false))
    }

    @Test
    fun aRefusedTapChangesNothing() {
        assertNull(MediaMode.afterTap(selected = null, tapped = MediaKind.PICTURE, canChange = false))
        assertEquals(MediaKind.PICTURE, MediaMode.afterTap(selected = MediaKind.PICTURE, tapped = MediaKind.VIDEO, canChange = false))
    }

    @Test
    fun itIsOffAfterEachSend() {
        assertNull(MediaMode.afterSend())
    }

    @Test
    fun attachingAFileWhileOnSwitchesItOff() {
        assertNull(MediaMode.settled(MediaKind.PICTURE, allKinds, canChange = false))
        assertEquals(MediaKind.PICTURE, MediaMode.settled(MediaKind.PICTURE, allKinds, canChange = true))
        assertNull(MediaMode.settled(null, allKinds, canChange = true))
    }

    @Test
    fun removingTheKeyOfTheSelectedKindSwitchesItOff() {
        assertNull(MediaMode.settled(MediaKind.VIDEO, listOf(MediaKind.PICTURE), canChange = true))
    }

    @Test
    fun eachKindHasItsOwnPlaceholderSendDescriptionAndLabel() {
        assertEquals(R.string.chat_picture_hint, MediaKind.PICTURE.hintRes)
        assertEquals(R.string.chat_vector_hint, MediaKind.VECTOR.hintRes)
        assertEquals(R.string.chat_video_hint, MediaKind.VIDEO.hintRes)
        assertEquals(R.string.chat_picture_send, MediaKind.PICTURE.sendDescriptionRes)
        assertEquals(R.string.chat_vector_send, MediaKind.VECTOR.sendDescriptionRes)
        assertEquals(R.string.chat_video_send, MediaKind.VIDEO.sendDescriptionRes)
        assertEquals(3, MediaKind.entries.map { kind -> kind.labelRes }.toSet().size)
        assertEquals(3, MediaKind.entries.map { kind -> kind.hintRes }.toSet().size)
        assertEquals(3, MediaKind.entries.map { kind -> kind.sendDescriptionRes }.toSet().size)
    }

    private fun ui(selected: MediaKind?, block: (MediaModeUi) -> MediaModeUi = { it }) =
        block(MediaModeUi(availableKinds = allKinds, selected = selected, canChange = true))

    @Test
    fun noDetailsWhileTheModeIsOff() {
        assertNull(MediaMode.detailsLine(ui(null) { it.copy(modelName = "FLUX.2 Klein") }, "price unknown"))
    }

    @Test
    fun aPictureLineHasTheModelAndItsPriceWhenKnown() {
        val line = MediaMode.detailsLine(
            ui(MediaKind.PICTURE) { it.copy(modelName = "FLUX.2 Klein", priceText = "$0.08 per image") },
            "price unknown",
        )
        assertEquals("FLUX.2 Klein · $0.08 per image", line)
    }

    @Test
    fun aPictureLineHasOnlyTheModelWhenThePriceIsNotKnown() {
        val line = MediaMode.detailsLine(ui(MediaKind.PICTURE) { it.copy(modelName = "FLUX.2 Klein") }, "price unknown")
        assertEquals("FLUX.2 Klein", line)
    }

    @Test
    fun aVectorLineIsBuiltLikeAPictureLine() {
        val line = MediaMode.detailsLine(
            ui(MediaKind.VECTOR) { it.copy(modelName = "Recraft V3 Vector", priceText = "$0.08 per image") },
            "price unknown",
        )
        assertEquals("Recraft V3 Vector · $0.08 per image", line)
    }

    @Test
    fun aVideoLineHasModelLengthResolutionAndCost() {
        val line = MediaMode.detailsLine(
            ui(MediaKind.VIDEO) {
                it.copy(modelName = "Grok Imagine Video 1.5 Lite", videoLengthText = "4 s", videoResolution = "720p", videoCostText = "about $0.12")
            },
            "price unknown",
        )
        assertEquals("Grok Imagine Video 1.5 Lite · 4 s · 720p · about $0.12", line)
    }

    @Test
    fun aVideoLineSaysPriceUnknownWhenNoEstimateCanBeMade() {
        val line = MediaMode.detailsLine(
            ui(MediaKind.VIDEO) { it.copy(modelName = "Grok Imagine Video 1.5 Lite", videoLengthText = "4 s", videoResolution = "720p") },
            "price unknown",
        )
        assertEquals("Grok Imagine Video 1.5 Lite · 4 s · 720p · price unknown", line)
    }

    @Test
    fun aVideoLineWithoutTheModelListStillSaysPriceUnknown() {
        val line = MediaMode.detailsLine(ui(MediaKind.VIDEO) { it.copy(modelName = "google/veo-3.1-lite") }, "price unknown")
        assertEquals("google/veo-3.1-lite · price unknown", line)
    }
}
