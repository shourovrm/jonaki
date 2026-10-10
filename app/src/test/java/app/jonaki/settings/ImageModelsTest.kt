package app.jonaki.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageModelsTest {
    private val flux = "black-forest-labs/flux.2-klein-4b"
    private val gpt = "openai/gpt-image-1-mini"
    private val nanoBanana = "gemini-2.5-flash-image"

    @Test
    fun servicesKeepTheOrderTheyWereAdded() {
        val models = ImageModels().addService(ImageService.GEMINI).addService(ImageService.OPENROUTER).addService(ImageService.GEMINI)

        assertEquals(listOf(ImageService.GEMINI, ImageService.OPENROUTER), models.addedServices)
        assertTrue(models.allModelKeys.isEmpty())
        assertNull(models.defaultModelKey)
    }

    @Test
    fun theFirstModelAddedIsStarredAndAddsItsService() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux).addModel(ImageService.OPENROUTER, gpt)

        assertEquals(listOf(ImageService.OPENROUTER), models.addedServices)
        assertEquals(listOf("openrouter:$flux", "openrouter:$gpt"), models.allModelKeys)
        assertEquals("openrouter:$flux", models.defaultModelKey)
    }

    @Test
    fun blankAndRepeatedIdsChangeNothing() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux)

        assertEquals(models, models.addModel(ImageService.OPENROUTER, "  "))
        assertEquals(models, models.addModel(ImageService.OPENROUTER, " $flux "))
    }

    @Test
    fun theSameIdUnderTwoServicesGivesTwoModels() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, "google/x").addModel(ImageService.GEMINI, "google/x")

        assertEquals(listOf("openrouter:google/x", "gemini:google/x"), models.allModelKeys)
    }

    @Test
    fun starringMovesTheStarAcrossServicesAndIgnoresUnlistedKeys() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux).addModel(ImageService.GEMINI, nanoBanana)

        assertEquals("gemini:$nanoBanana", models.setDefault("gemini:$nanoBanana").defaultModelKey)
        assertEquals("openrouter:$flux", models.setDefault("gemini:other").defaultModelKey)
    }

    @Test
    fun removingTheStarredModelStarsTheFirstLeft() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux).addModel(ImageService.GEMINI, nanoBanana)
            .setDefault("gemini:$nanoBanana")

        val afterRemoving = models.removeModel("gemini:$nanoBanana")

        assertEquals(listOf("openrouter:$flux"), afterRemoving.allModelKeys)
        assertEquals("openrouter:$flux", afterRemoving.defaultModelKey)
        assertNull(afterRemoving.removeModel("openrouter:$flux").defaultModelKey)
    }

    @Test
    fun removingAModelKeepsItsServiceCard() {
        val models = ImageModels().addModel(ImageService.GEMINI, nanoBanana).removeModel("gemini:$nanoBanana")

        assertEquals(listOf(ImageService.GEMINI), models.addedServices)
    }

    @Test
    fun removingAServiceDropsItsModelsAndMovesTheStar() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux).addModel(ImageService.GEMINI, nanoBanana)

        val afterRemoving = models.removeService(ImageService.OPENROUTER)

        assertEquals(listOf(ImageService.GEMINI), afterRemoving.addedServices)
        assertEquals(listOf("gemini:$nanoBanana"), afterRemoving.allModelKeys)
        assertEquals("gemini:$nanoBanana", afterRemoving.defaultModelKey)
    }

    @Test
    fun theTextFormKeepsServicesModelsOrderAndStar() {
        val models = ImageModels().addService(ImageService.GEMINI)
            .addModel(ImageService.OPENROUTER, flux).addModel(ImageService.OPENROUTER, "x/y:free")
            .addModel(ImageService.GEMINI, nanoBanana).setDefault("openrouter:x/y:free")

        val stored = ImageModels.toStored(models)
        val restored = ImageModels.fromStored(stored.servicesText, stored.modelKeysText, stored.defaultModelKey, null, null)

        assertEquals(models, restored)
    }

    @Test
    fun aServiceWithoutModelsSurvivesTheTextForm() {
        val models = ImageModels().addService(ImageService.GEMINI)

        val stored = ImageModels.toStored(models)

        assertEquals(models, ImageModels.fromStored(stored.servicesText, stored.modelKeysText, stored.defaultModelKey, null, null))
    }

    @Test
    fun anUnknownServiceOrAStaleStarInTheTextIsDropped() {
        val restored = ImageModels.fromStored(
            servicesText = "openrouter\nnosuch",
            modelKeysText = "openrouter:$flux\nnosuch:abc\nnocolon",
            defaultModelKey = "gemini:gone",
            legacyModelsText = null,
            legacyDefaultModel = null,
        )

        assertEquals(listOf(ImageService.OPENROUTER), restored.addedServices)
        assertEquals(listOf("openrouter:$flux"), restored.allModelKeys)
        assertEquals("openrouter:$flux", restored.defaultModelKey)
    }

    @Test
    fun aPhoneThatStoredOpenRouterModelsOnlyKeepsThemAsOpenRouterModelsWithTheSameStar() {
        // image_models and image_default_model as the first version wrote them.
        val migrated = ImageModels.fromStored(
            servicesText = null,
            modelKeysText = null,
            defaultModelKey = null,
            legacyModelsText = "$flux\n$gpt",
            legacyDefaultModel = gpt,
        )

        assertEquals(listOf(ImageService.OPENROUTER), migrated.addedServices)
        assertEquals(listOf("openrouter:$flux", "openrouter:$gpt"), migrated.allModelKeys)
        assertEquals("openrouter:$gpt", migrated.defaultModelKey)
    }

    @Test
    fun theTestPhoneSettingsMigrate() {
        val migrated = ImageModels.fromStored(null, null, null, "black-forest-labs/flux.2-klein-4b", "black-forest-labs/flux.2-klein-4b")

        assertEquals("openrouter:black-forest-labs/flux.2-klein-4b", migrated.defaultModelKey)
        assertEquals(listOf(ImageService.OPENROUTER), migrated.addedServices)
    }

    @Test
    fun theNewFormatWinsOverLeftoverOldValuesEvenWhenEmpty() {
        // After the user removed every model the new keys are saved empty; the old ones must not come back.
        val restored = ImageModels.fromStored("", "", null, "$flux", flux)

        assertTrue(restored.addedServices.isEmpty())
        assertFalse(restored.allModelKeys.contains("openrouter:$flux"))
    }

    @Test
    fun noStoredValuesGiveAnEmptyValue() {
        assertEquals(ImageModels(), ImageModels.fromStored(null, null, null, null, null))
    }

    @Test
    fun onlyModelsOfServicesWithAKeyAreUsable() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, flux).addModel(ImageService.GEMINI, nanoBanana)

        assertEquals(listOf("gemini:$nanoBanana"), models.usableModelKeys { service -> service == ImageService.GEMINI })
        assertTrue(models.usableModelKeys { false }.isEmpty())
        assertTrue(ImageModels().addService(ImageService.GEMINI).usableModelKeys { true }.isEmpty())
    }

    @Test
    fun servicesShareSecretsWithChatServices() {
        assertEquals(ChatService.OPENROUTER.secret, ImageService.OPENROUTER.secret)
        assertEquals(ChatService.GEMINI.secret, ImageService.GEMINI.secret)
    }
}
