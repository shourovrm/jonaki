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

    private val vectorV4 = "recraft/recraft-v4-vector"
    private val vectorPro = "recraft/recraft-v4-pro-vector"

    @Test
    fun aModelAddedAsVectorIsRememberedAndOthersAreNot() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true)

        assertTrue(models.isVector("openrouter:someone/draw-svg"))
        assertFalse(models.isVector("openrouter:$flux"))
        assertEquals(setOf("openrouter:someone/draw-svg"), models.vectorModelKeys)
    }

    @Test
    fun withoutAStoredFlagAnOpenRouterIdEndingInVectorCounts() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, vectorV4).addModel(ImageService.OPENROUTER, vectorPro)
            .addModel(ImageService.OPENROUTER, "vendor/vectorize-photo").addModel(ImageService.GEMINI, "vector")

        assertTrue(models.isVector("openrouter:$vectorV4"))
        assertTrue(models.isVector("openrouter:$vectorPro"))
        // "vectorize" does not end with "vector"; Gemini ids are never matched by name.
        assertFalse(models.isVector("openrouter:vendor/vectorize-photo"))
        assertFalse(models.isVector("gemini:vector"))
    }

    @Test
    fun theTwoToolsGetTheirOwnKindOfModelOnly() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true)
            .addModel(ImageService.OPENROUTER, vectorV4)
            .addModel(ImageService.GEMINI, nanoBanana)
        val everyServiceHasAKey = { _: ImageService -> true }

        assertEquals(listOf("openrouter:$flux", "gemini:$nanoBanana"), models.usableRasterModelKeys(everyServiceHasAKey))
        assertEquals(listOf("openrouter:someone/draw-svg", "openrouter:$vectorV4"), models.usableVectorModelKeys(everyServiceHasAKey))
        assertEquals(listOf("openrouter:$flux", "gemini:$nanoBanana"), models.usableRasterModelKeys { it == ImageService.GEMINI || it == ImageService.OPENROUTER })
        assertEquals(listOf("gemini:$nanoBanana"), models.usableRasterModelKeys { it == ImageService.GEMINI })
        assertTrue(models.usableVectorModelKeys { it == ImageService.GEMINI }.isEmpty())
    }

    @Test
    fun aToolIsOfferedOnlyWhenItsKindHasAUsableModel() {
        val onlyRaster = ImageModels().addModel(ImageService.OPENROUTER, flux)
        val onlyVector = ImageModels().addModel(ImageService.OPENROUTER, vectorV4, isVector = true)

        assertTrue(onlyRaster.usableVectorModelKeys { true }.isEmpty())
        assertTrue(onlyVector.usableRasterModelKeys { true }.isEmpty())
        assertEquals(listOf("openrouter:$vectorV4"), onlyVector.usableVectorModelKeys { true })
    }

    @Test
    fun removingAModelOrItsServiceClearsItsVectorFlag() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true)
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.GEMINI, nanoBanana)

        val withoutModel = models.removeModel("openrouter:someone/draw-svg")
        assertTrue(withoutModel.vectorModelKeys.isEmpty())
        assertFalse(withoutModel.isVector("openrouter:someone/draw-svg"))

        val withoutService = models.removeVectorService(ImageService.OPENROUTER)
        assertTrue(withoutService.vectorModelKeys.isEmpty())
        // Adding the same id again as a raster model must not inherit the old flag.
        assertFalse(withoutService.addModel(ImageService.OPENROUTER, "someone/draw-svg").isVector("openrouter:someone/draw-svg"))
    }

    @Test
    fun theStarIsNeverOnAVectorModel() {
        val onlyVector = ImageModels().addModel(ImageService.OPENROUTER, vectorV4, isVector = true)
        val models = onlyVector.addModel(ImageService.OPENROUTER, flux)

        assertNull(onlyVector.defaultModelKey)
        assertEquals("openrouter:$flux", models.defaultModelKey)
        assertEquals(models, models.setDefault("openrouter:$vectorV4"))
    }

    @Test
    fun aServiceIsAddedToEachSectionOnItsOwn() {
        val onlyVector = ImageModels().addVectorService(ImageService.OPENROUTER).addModel(ImageService.OPENROUTER, vectorV4, isVector = true)

        assertTrue(onlyVector.addedServices.isEmpty())
        assertEquals(listOf(ImageService.OPENROUTER), onlyVector.vectorServices)
        assertEquals(listOf("openrouter:$vectorV4"), onlyVector.usableVectorModelKeys { true })

        val onlyRaster = ImageModels().addModel(ImageService.OPENROUTER, flux)
        assertTrue(onlyRaster.vectorServices.isEmpty())
    }

    @Test
    fun removingAServiceFromOneSectionKeepsItsModelsOfTheOther() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.OPENROUTER, vectorV4, isVector = true)

        val withoutPictures = models.removeService(ImageService.OPENROUTER)
        assertEquals(listOf("openrouter:$vectorV4"), withoutPictures.allModelKeys)
        assertNull(withoutPictures.defaultModelKey)

        val withoutVectors = models.removeVectorService(ImageService.OPENROUTER)
        assertEquals(listOf("openrouter:$flux"), withoutVectors.allModelKeys)
        assertEquals(listOf(ImageService.OPENROUTER), withoutVectors.addedServices)
    }

    @Test
    fun theVectorServicesSurviveTheTextFormAndOlderSettingsDeriveThemFromTheModels() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true)
            .removeService(ImageService.OPENROUTER)
        val stored = ImageModels.toStored(models)

        val restored = ImageModels.fromStored(
            stored.servicesText, stored.modelKeysText, stored.defaultModelKey, null, null, stored.vectorModelKeysText, stored.vectorServicesText,
        )
        assertEquals(models, restored)

        // Saved by 1.4.6: the vector model sits under the image service and no vector service is stored.
        val older = ImageModels.fromStored("openrouter", "openrouter:$flux\nopenrouter:someone/draw-svg", null, null, null, "openrouter:someone/draw-svg", null)
        assertEquals(listOf(ImageService.OPENROUTER), older.addedServices)
        assertEquals(listOf(ImageService.OPENROUTER), older.vectorServices)
        assertEquals("openrouter:$flux", older.defaultModelKey)
    }

    @Test
    fun aVectorServiceWithoutModelsSurvivesTheTextForm() {
        val models = ImageModels().addVectorService(ImageService.OPENROUTER)
        val stored = ImageModels.toStored(models)

        assertEquals(
            models,
            ImageModels.fromStored(stored.servicesText, stored.modelKeysText, stored.defaultModelKey, null, null, stored.vectorModelKeysText, stored.vectorServicesText),
        )
    }

    @Test
    fun theVectorFlagsSurviveTheTextForm() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, flux)
            .addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true)
            .addModel(ImageService.GEMINI, nanoBanana)

        val stored = ImageModels.toStored(models)
        val restored = ImageModels.fromStored(
            stored.servicesText, stored.modelKeysText, stored.defaultModelKey, null, null, stored.vectorModelKeysText,
        )

        assertEquals(models, restored)
        assertEquals("openrouter:someone/draw-svg", stored.vectorModelKeysText)
    }

    @Test
    fun settingsSavedBeforeVectorModelsExistedLoadWithNoStoredFlag() {
        // The key image_vector_model_keys is absent, so the text is null.
        val restored = ImageModels.fromStored(
            servicesText = "openrouter",
            modelKeysText = "openrouter:$flux\nopenrouter:$vectorV4",
            defaultModelKey = "openrouter:$flux",
            legacyModelsText = null,
            legacyDefaultModel = null,
            vectorModelKeysText = null,
        )

        assertTrue(restored.vectorModelKeys.isEmpty())
        assertEquals("openrouter:$flux", restored.defaultModelKey)
        assertEquals(listOf("openrouter:$flux", "openrouter:$vectorV4"), restored.allModelKeys)
        // The name fallback still sorts the old model into the vector kind.
        assertEquals(listOf("openrouter:$vectorV4"), restored.usableVectorModelKeys { true })
    }

    @Test
    fun aStoredVectorFlagForAModelThatIsGoneIsDropped() {
        val restored = ImageModels.fromStored("openrouter", "openrouter:$flux", null, null, null, "openrouter:gone/model-v\nopenrouter:$flux")

        assertEquals(setOf("openrouter:$flux"), restored.vectorModelKeys)
    }

    @Test
    fun addingAnAlreadyListedModelAsVectorSetsItsFlag() {
        val models = ImageModels().addModel(ImageService.OPENROUTER, "someone/draw-svg")

        assertTrue(models.addModel(ImageService.OPENROUTER, "someone/draw-svg", isVector = true).isVector("openrouter:someone/draw-svg"))
    }

    @Test
    fun servicesShareSecretsWithChatServices() {
        assertEquals(ChatService.OPENROUTER.secret, ImageService.OPENROUTER.secret)
        assertEquals(ChatService.GEMINI.secret, ImageService.GEMINI.secret)
    }

    @Test
    fun theVectorDefaultIsTheFirstVectorModelAndCanBeChanged() {
        val models = ImageModels()
            .addModel(ImageService.OPENROUTER, "maker/raster-one")
            .addModel(ImageService.OPENROUTER, "maker/first-vector", isVector = true)
            .addModel(ImageService.OPENROUTER, "maker/second-vector", isVector = true)

        val changed = models.setVectorDefault("openrouter:maker/second-vector")

        assertEquals("openrouter:maker/first-vector", models.usableVectorModelKeys { true }.first())
        assertEquals("openrouter:maker/second-vector", changed.usableVectorModelKeys { true }.first())
        assertEquals(models.defaultModelKey, changed.defaultModelKey)
        assertEquals(models, models.setVectorDefault("openrouter:maker/not-listed"))
    }
}
