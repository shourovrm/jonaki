package app.jonaki.ui

import app.jonaki.feature.chat.StepPromptUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptStepTextTest {
    private val imageWords = ImageStepWords(
        high = "high",
        referenceCount = { count -> if (count == 1) "1 reference picture" else "$count reference pictures" },
    )

    private val videoText = VideoStepText(
        emptyMap(),
        "openrouter:a/video",
        VideoStepWords({ jobId -> "Collect $jobId" }, { dollars -> "about $dollars" }, "per token", { seconds -> "$seconds s" }),
    )

    private fun words(imageDefaultIsHigh: Boolean = false) = StepDetail.Words(
        readCalendar = "", addToCalendar = "", reminder = "", notify = "", readClipboard = "", copyToClipboard = "", openApp = "",
        schedule = "", cancelTask = "", listTasks = "", toDownloads = "", saveAs = "", share = "", toLinkedFolder = "",
        listLinkedFolder = "", fromLinkedFolder = "", lineCount = { "" },
        defaultImageModel = "openrouter:flux",
        defaultVectorImageModel = "openrouter:recraft/recraft-v4-vector",
        video = videoText,
        image = ImageStepWords(imageWords.high, imageWords.referenceCount, imageDefaultIsHigh),
    )

    private val longPrompt = "Portrait poster titled \"Morning stretches\".\n" + "Panel text, word for word: \"Reach up\". ".repeat(20)

    @Test
    fun theLineCutsALongPromptOnOneLineAndTheApprovalCardAndSheetKeepAllOfIt() {
        val json = """{"prompt":${kotlinx.serialization.json.JsonPrimitive(longPrompt)},"model":"openrouter:openai/gpt-image-2.5-sunburst"}"""

        val detail = StepDetail.of("generate_image", json, words())

        val line = detail.target!!
        assertTrue(line, line.endsWith("…"))
        assertFalse(line.contains("\n"))
        assertEquals("openrouter:openai/gpt-image-2.5-sunburst · ".length + PromptStepText.LINE_PROMPT_CHARACTERS + 1, line.length)
        assertTrue(detail.approvalText!!.endsWith(longPrompt.trim()))
        assertTrue(detail.approvalText!!.contains("\n"))
        assertEquals(longPrompt.trim(), detail.prompt!!.prompt)
    }

    @Test
    fun aShortPromptIsNotChangedAndGetsNoCutMark() {
        val detail = StepDetail.of("generate_image", """{"prompt":"A blue door"}""", words())

        assertEquals("openrouter:flux · A blue door", detail.target)
        assertEquals(detail.target, detail.approvalText)
    }

    @Test
    fun highQualityAndTheReferenceCountJoinTheLineAndTheApprovalCard() {
        val json = """{"prompt":"make the title bigger","quality":"high","reference_images":["images/a.png","inbox/b.jpg"]}"""

        val detail = StepDetail.of("generate_image", json, words())

        assertEquals("openrouter:flux · high · 2 reference pictures · make the title bigger", detail.target)
        assertEquals(detail.target, detail.approvalText)
    }

    @Test
    fun theSettingsDefaultHighShowsUnlessTheCallAsksForStandard() {
        assertEquals("openrouter:flux · high · A door", StepDetail.of("generate_image", """{"prompt":"A door"}""", words(true)).target)
        assertEquals(
            "openrouter:flux · A door",
            StepDetail.of("generate_image", """{"prompt":"A door","quality":"standard"}""", words(true)).target,
        )
        assertEquals("openrouter:flux · A door", StepDetail.of("generate_image", """{"prompt":"A door"}""", words(false)).target)
    }

    @Test
    fun theOpenedImageStepListsPromptModelQualityAspectRatioAndReferencePaths() {
        val json = """{"prompt":"A door","quality":"high","aspect_ratio":"9:16","reference_images":["images/a.png","inbox/b.jpg"]}"""

        val opened = StepDetail.of("generate_image", json, words()).prompt

        assertEquals(
            StepPromptUi("A door", "openrouter:flux", true, "9:16", listOf("images/a.png", "inbox/b.jpg")),
            opened,
        )
    }

    @Test
    fun theOpenedImageStepOmitsQualityAndReferencesWhenThereAreNone() {
        val opened = StepDetail.of("generate_image", """{"prompt":"A door"}""", words()).prompt!!

        assertFalse(opened.isHighQuality)
        assertNull(opened.aspectRatio)
        assertTrue(opened.referencePaths.isEmpty())
    }

    @Test
    fun theVectorStepShowsThePromptInFullButNeverQualityOrReferences() {
        val json = """{"prompt":${kotlinx.serialization.json.JsonPrimitive(longPrompt)},"quality":"high","reference_images":["images/a.png"]}"""

        val detail = StepDetail.of("generate_vector_image", json, words(true))

        assertEquals("openrouter:recraft/recraft-v4-vector", detail.prompt!!.model)
        assertFalse(detail.prompt!!.isHighQuality)
        assertTrue(detail.prompt!!.referencePaths.isEmpty())
        assertFalse(detail.target!!.contains("high"))
        assertFalse(detail.target!!.contains("reference"))
        assertTrue(detail.target!!.endsWith("…"))
        assertEquals(longPrompt.trim(), detail.prompt!!.prompt)
        assertTrue(detail.approvalText!!.endsWith(longPrompt.trim()))
    }

    @Test
    fun theVideoStepCutsItsLineAndOpensWithPromptModelAndAspectRatio() {
        val json = """{"prompt":${kotlinx.serialization.json.JsonPrimitive(longPrompt)},"aspect_ratio":"16:9"}"""

        val detail = StepDetail.of("generate_video", json, words())

        assertTrue(detail.target!!, detail.target!!.startsWith("openrouter:a/video · "))
        assertTrue(detail.target!!.endsWith("…"))
        assertTrue(detail.approvalText!!.endsWith(longPrompt.trim()))
        assertEquals(StepPromptUi(longPrompt.trim(), "openrouter:a/video", false, "16:9", emptyList()), detail.prompt)
    }

    @Test
    fun aCallThatOnlyCollectsAVideoJobHasNoSheet() {
        val detail = StepDetail.of("generate_video", """{"job_id":"job-1"}""", words())

        assertEquals("Collect job-1", detail.target)
        assertNull(detail.prompt)
    }

    @Test
    fun withoutWordsTheImageLineHasOnlyModelAndPrompt() {
        val detail = StepDetail.of("generate_image", """{"prompt":"A door","quality":"high"}""", englishStepWords)

        assertEquals("A door", detail.target)
        assertFalse(detail.prompt!!.isHighQuality)
    }
}
