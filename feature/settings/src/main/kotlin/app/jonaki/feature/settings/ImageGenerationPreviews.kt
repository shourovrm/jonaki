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

private val sampleImages = ImageGenerationUi(
    hasOpenRouterKey = true,
    models = listOf(
        ImageModelRowUi("black-forest-labs/flux.2-klein-4b", LONG_IMAGE_MODEL_NAME, "$0.014 per megapixel", isDefault = true),
        ImageModelRowUi("openai/gpt-image-1-mini", "OpenAI: GPT Image 1 Mini", null),
    ),
)

private val samplePickerModels = listOf(
    AddableModelUi("black-forest-labs/flux.2-klein-4b", LONG_IMAGE_MODEL_NAME, isAdded = true),
    AddableModelUi("google/gemini-nano-banana-2.1", "Google: Nano Banana 2.1"),
    AddableModelUi("openai/gpt-image-1-mini", "OpenAI: GPT Image 1 Mini"),
)

@Composable
private fun SectionPreview(images: ImageGenerationUi, mode: ThemeMode) {
    JonakiTheme(mode) {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ImageGenerationSection(images, SettingsActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {}))
            }
        }
    }
}

@Preview(name = "Image models, dark", widthDp = 360, heightDp = 480, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageSectionDarkPreview() = SectionPreview(sampleImages, ThemeMode.DARK)

@Preview(name = "Image models, no key, light", widthDp = 360, heightDp = 480, fontScale = 1.3f)
@Composable
private fun ImageSectionNoKeyPreview() = SectionPreview(ImageGenerationUi(), ThemeMode.LIGHT)

@Preview(name = "Image picker, loaded", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerLoadedPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen(ImagePickerState.Loaded(samplePickerModels), onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Image picker, loading", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerLoadingPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen(ImagePickerState.Loading, onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Image picker, failed", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun ImagePickerFailedPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddImageModelsScreen(ImagePickerState.Failed("HTTP 503"), onClose = {}, onRetry = {}, onDone = {})
}
