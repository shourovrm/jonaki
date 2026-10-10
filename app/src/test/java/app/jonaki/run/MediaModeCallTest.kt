package app.jonaki.run

import app.jonaki.feature.chat.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaModeCallTest {
    private val everything = MediaCallOptions(
        modelKey = "openrouter:some/model",
        isHighQuality = true,
        aspectRatio = "16:9",
        durationSeconds = 6,
        resolution = "720p",
    )

    @Test
    fun withNoSettingOnlyThePromptIsSent() {
        assertEquals("""{"prompt":"a blue door"}""", MediaModeCall.arguments("  a blue door \n", MediaKind.PICTURE).toString())
    }

    @Test
    fun aPictureTakesModelQualityAndShapeButNoVideoSetting() {
        assertEquals(
            """{"prompt":"a door","model":"openrouter:some/model","quality":"high","aspect_ratio":"16:9"}""",
            MediaModeCall.arguments("a door", MediaKind.PICTURE, everything).toString(),
        )
    }

    @Test
    fun attachedPicturesGoAlongWithAPictureOnly() {
        val options = MediaCallOptions(referenceImages = listOf("inbox/photo.jpg"))

        assertEquals(
            """{"prompt":"make it night","reference_images":["inbox/photo.jpg"]}""",
            MediaModeCall.arguments("make it night", MediaKind.PICTURE, options).toString(),
        )
        assertEquals("""{"prompt":"make it night"}""", MediaModeCall.arguments("make it night", MediaKind.VIDEO, options).toString())
    }

    @Test
    fun standardQualityIsSentWhenTheUserChoseIt() {
        val arguments = MediaModeCall.arguments("a door", MediaKind.PICTURE, MediaCallOptions(isHighQuality = false))

        assertEquals("""{"prompt":"a door","quality":"standard"}""", arguments.toString())
    }

    @Test
    fun aVectorImageTakesModelAndShapeButNoQuality() {
        assertEquals(
            """{"prompt":"a logo","model":"openrouter:some/model","aspect_ratio":"16:9"}""",
            MediaModeCall.arguments("a logo", MediaKind.VECTOR, everything).toString(),
        )
    }

    @Test
    fun aVideoTakesModelLengthAndSizeButNoPictureSetting() {
        assertEquals(
            """{"prompt":"a river","model":"openrouter:some/model","duration_seconds":6,"resolution":"720p"}""",
            MediaModeCall.arguments("a river", MediaKind.VIDEO, everything).toString(),
        )
    }
}
