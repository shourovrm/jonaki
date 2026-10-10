package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModelsTest {
    private val veo = "google/veo-3.1-lite"
    private val grok = "x-ai/grok-imagine-video-1.5-lite"

    @Test
    fun theFirstModelAddedIsStarredAndKeysCarryTheServiceName() {
        val models = VideoModels().addModel("openrouter", veo).addModel("openrouter", grok)

        assertEquals(listOf("openrouter:$veo", "openrouter:$grok"), models.modelKeys)
        assertEquals("openrouter:$veo", models.defaultModelKey)
        assertEquals(listOf(veo, grok), models.modelIdsOf("openrouter"))
        assertTrue(models.modelIdsOf("another").isEmpty())
    }

    @Test
    fun blankAndRepeatedIdsChangeNothing() {
        val models = VideoModels().addModel("openrouter", veo)

        assertEquals(models, models.addModel("openrouter", "  "))
        assertEquals(models, models.addModel("openrouter", " $veo "))
    }

    @Test
    fun starringMovesTheStarAndIgnoresUnlistedKeys() {
        val models = VideoModels().addModel("openrouter", veo).addModel("openrouter", grok)

        assertEquals("openrouter:$grok", models.setDefault("openrouter:$grok").defaultModelKey)
        assertEquals(models, models.setDefault("openrouter:unknown/model"))
    }

    @Test
    fun removingTheStarredModelStarsTheFirstLeftAndRemovingTheLastClearsTheStar() {
        val models = VideoModels().addModel("openrouter", veo).addModel("openrouter", grok)

        val afterFirst = models.removeModel("openrouter:$veo")
        assertEquals("openrouter:$grok", afterFirst.defaultModelKey)
        assertNull(afterFirst.removeModel("openrouter:$grok").defaultModelKey)
        assertEquals("openrouter:$veo", models.removeModel("openrouter:$grok").defaultModelKey)
    }

    @Test
    fun storingAndReadingGivesTheSameModels() {
        val models = VideoModels().addModel("openrouter", veo).addModel("openrouter", grok).setDefault("openrouter:$grok")

        val stored = VideoModels.toStored(models)

        assertEquals("openrouter:$veo\nopenrouter:$grok", stored.modelKeysText)
        assertEquals(models, VideoModels.fromStored(stored.modelKeysText, stored.defaultModelKey))
        assertEquals(models, VideoModels.fromStored(stored.modelKeysText, stored.defaultModelKey, stored.servicesText))
    }

    @Test
    fun aServiceCanBeAddedBeforeItHasAModelAndRemovingItDropsItsModels() {
        val added = VideoModels().addService("openrouter")
        val stored = VideoModels.toStored(added)

        assertEquals(listOf("openrouter"), added.serviceKeys)
        assertEquals(added, VideoModels.fromStored(stored.modelKeysText, stored.defaultModelKey, stored.servicesText))

        val removed = added.addModel("openrouter", veo).removeService("openrouter")
        assertEquals(VideoModels(), removed)
    }

    @Test
    fun settingsSavedBeforeServicesHadCardsTakeTheServicesFromTheModels() {
        val read = VideoModels.fromStored("openrouter:$veo", null, servicesText = null)

        assertEquals(listOf("openrouter"), read.serviceKeys)
    }

    @Test
    fun readingToleratesNothingSavedBlankLinesRepeatsAndAStarOnAnUnlistedModel() {
        assertEquals(VideoModels(), VideoModels.fromStored(null, null))

        val read = VideoModels.fromStored("openrouter:$veo\n\n  \nopenrouter:$veo\nnot-a-key", "openrouter:gone/model")

        assertEquals(listOf("openrouter:$veo"), read.modelKeys)
        assertEquals("openrouter:$veo", read.defaultModelKey)
    }
}
