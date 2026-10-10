package app.jonaki.run

import app.jonaki.core.modelcatalog.ImageModelInfo
import app.jonaki.core.toolapi.ImageQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageModelFactsBuilderTest {
    @Test
    fun theListedParametersBecomeFactsByServiceAndModelKey() {
        val facts = ImageModelFactsBuilder.byKey(
            listOf(
                ImageModelInfo("a/b", "B", qualityValues = listOf("low", "high"), referenceRange = 1..5),
                ImageModelInfo("c/d", "D"),
            ),
        )

        val declared = facts.getValue("openrouter:a/b")
        assertEquals(listOf("low", "high"), declared.qualityValues)
        assertNull(declared.resolutionValues)
        assertEquals(1, declared.minReferences)
        assertEquals(5, declared.maxReferences)
        val bare = facts.getValue("openrouter:c/d")
        assertNull(bare.qualityValues)
        assertNull(bare.maxReferences)
        assertEquals(0, bare.minReferences)
    }

    @Test
    fun theSettingsDefaultIsStandardWhenTheKeyIsAbsent() {
        assertEquals(ImageQuality.STANDARD, ImageQuality.fromStored(null))
    }
}
