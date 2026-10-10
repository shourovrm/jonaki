package app.jonaki.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: 360 dp wide, font scale 1.3, a model name of 40 characters.

private const val LONG_IMAGE_MODEL_NAME = "Black Forest Labs: FLUX.2 Klein 4B Turbo!"
private const val FLUX_ID = "black-forest-labs/flux.2-klein-4b"

private val sampleImages = ImageGenerationUi(
    services = listOf(
        ImageServiceCardUi(
            serviceKey = "openrouter",
            displayName = "OpenRouter",
            apiKey = KeySlot("OPENROUTER", isSet = true, maskedKey = "sk-o••••"),
            models = listOf(
                ImageModelRowUi("openrouter:$FLUX_ID", FLUX_ID, LONG_IMAGE_MODEL_NAME, "$0.014 per megapixel", isDefault = true),
                ImageModelRowUi("openrouter:openai/gpt-image-1-mini", "openai/gpt-image-1-mini", "OpenAI: GPT Image 1 Mini", null),
            ),
        ),
        ImageServiceCardUi(
            serviceKey = "gemini",
            displayName = "Gemini",
            apiKey = KeySlot("GEMINI", isSet = true, maskedKey = "AIz••••"),
            models = listOf(ImageModelRowUi("gemini:gemini-2.5-flash-image", "gemini-2.5-flash-image", "gemini-2.5-flash-image")),
        ),
    ),
    vectorModels = listOf(
        ImageModelRowUi(
            "openrouter:recraft/recraft-v4-pro-vector", "recraft/recraft-v4-pro-vector", "Recraft: Recraft V4 Pro Vector Illustration",
            "\$0.3 per image", isDefault = true, isVector = true,
        ),
    ),
    canAddVectorModels = true,
)

private val sampleImagesWithoutKey = ImageGenerationUi(
    services = listOf(
        ImageServiceCardUi("gemini", "Gemini", KeySlot("GEMINI", isSet = false)),
    ),
    addableServices = listOf(AddableServiceUi("openrouter", "OpenRouter", "openrouter.ai")),
)

private val samplePickerModels = listOf(
    AddableModelUi(FLUX_ID, LONG_IMAGE_MODEL_NAME, isAdded = true, imagePrice = ImagePriceUi.Known("$0.014 per megapixel")),
    AddableModelUi("recraft/recraft-v4.1-flash", "Recraft: V4.1 Flash", imagePrice = ImagePriceUi.Known("$0.007 per image")),
    AddableModelUi("google/gemini-nano-banana-2.1", "Google: Nano Banana 2.1", imagePrice = ImagePriceUi.Known("$30 per 1M image tokens")),
    AddableModelUi("openai/gpt-image-1-mini", "OpenAI: GPT Image 1 Mini", imagePrice = ImagePriceUi.Loading),
    AddableModelUi("stability/unknown-price", "Stability: Unknown Price", imagePrice = ImagePriceUi.Unknown),
    AddableModelUi("recraft/recraft-v4-vector", "Recraft: Recraft V4 Vector Illustration", imagePrice = ImagePriceUi.Known("\$0.08 per image"), isVector = true),
)

private val sampleGeminiSuggestions = listOf(
    AddableModelUi("gemini-2.5-flash-image", "Gemini 2.5 Flash Image"),
    AddableModelUi("gemini-3.1-flash-image", "Gemini 3.1 Flash Image", isAdded = true),
)

@Composable
private fun SectionPreview(images: ImageGenerationUi, mode: ThemeMode) {
    JonakiTheme(mode) {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ImageGenerationSection(images, SettingsActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {}))
                VectorImageGenerationSection(images, SettingsActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {}))
            }
        }
    }
}

@Preview(name = "Image services, dark", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageSectionDarkPreview() = SectionPreview(sampleImages, ThemeMode.DARK)

@Preview(name = "Image services, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImageSectionBanglaPreview() = SectionPreview(sampleImages, ThemeMode.LIGHT)

@Preview(name = "Image service without key, light", widthDp = 360, heightDp = 360, fontScale = 1.3f)
@Composable
private fun ImageSectionNoKeyPreview() = SectionPreview(sampleImagesWithoutKey, ThemeMode.LIGHT)

@Preview(name = "Image services, none added", widthDp = 360, heightDp = 200, fontScale = 1.3f)
@Composable
private fun ImageSectionEmptyPreview() = SectionPreview(
    ImageGenerationUi(addableServices = listOf(AddableServiceUi("openrouter", "OpenRouter", "openrouter.ai"))),
    ThemeMode.LIGHT,
)

@Preview(name = "Image picker, prices", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerLoadedPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen("OpenRouter", ImagePickerState.Loaded(samplePickerModels), onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Image picker, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerBanglaPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen("OpenRouter", ImagePickerState.Loaded(samplePickerModels), onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Image picker, Gemini suggestions", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerGeminiPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen(
        "Gemini",
        ImagePickerState.Loaded(sampleGeminiSuggestions, allowsTypedId = true),
        onClose = {},
        onRetry = {},
        onDone = {},
    )
}

@Preview(name = "Image picker, loading", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerLoadingPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen("OpenRouter", ImagePickerState.Loading, onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Image picker, failed", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerFailedPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen("OpenRouter", ImagePickerState.Failed("HTTP 503"), onClose = {}, onRetry = {}, onDone = {})
}
