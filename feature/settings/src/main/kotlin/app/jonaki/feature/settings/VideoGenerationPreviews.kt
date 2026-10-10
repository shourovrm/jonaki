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

private const val LONG_VIDEO_MODEL_NAME = "SpaceXAI: Grok Imagine Video 1.5 Lite 4K!"
private const val GROK_ID = "x-ai/grok-imagine-video-1.5-lite"

private val sampleVideos = VideoGenerationUi(
    hasKey = true,
    models = listOf(
        VideoModelRowUi("openrouter:$GROK_ID", GROK_ID, LONG_VIDEO_MODEL_NAME, "$0.02 to $0.14 per second", "1 to 15 s", isDefault = true),
        VideoModelRowUi("openrouter:google/veo-3.1-lite", "google/veo-3.1-lite", "Google: Veo 3.1 Lite", "$0.03 to $0.05 per second", "4, 6, 8 s"),
        VideoModelRowUi("openrouter:bytedance/seedance-2.0", "bytedance/seedance-2.0", "ByteDance: Seedance 2.0", "per token", "4 to 15 s"),
        VideoModelRowUi("openrouter:runway/gen-4.5", "runway/gen-4.5", "runway/gen-4.5"),
    ),
)

private val samplePicker = VideoPickerState.Loaded(
    listOf(
        VideoPickModelUi(GROK_ID, LONG_VIDEO_MODEL_NAME, isAdded = true, priceText = "$0.02 to $0.14 per second", lengthsText = "1 to 15 s"),
        VideoPickModelUi("runway/gen-4.5", "Runway: Gen-4.5", priceText = "$0.12 per second", lengthsText = "2 to 10 s"),
        VideoPickModelUi("bytedance/seedance-2.0", "ByteDance: Seedance 2.0", priceText = "per token", lengthsText = "4 to 15 s"),
        VideoPickModelUi("black-forest-labs/flux-video-edit", "Black Forest Labs: FLUX Video Edit"),
    ),
)

@Composable
private fun SectionPreview(video: VideoGenerationUi, mode: ThemeMode) {
    JonakiTheme(mode) {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VideoGenerationSection(video, SettingsActions({}, { _, _ -> }, {}, { _, _ -> }, {}, {}))
            }
        }
    }
}

@Preview(name = "Video models, dark", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoSectionDarkPreview() = SectionPreview(sampleVideos, ThemeMode.DARK)

@Preview(name = "Video models, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoSectionBanglaPreview() = SectionPreview(sampleVideos, ThemeMode.LIGHT)

@Preview(name = "Video models, no key", widthDp = 360, heightDp = 200, fontScale = 1.3f)
@Composable
private fun VideoSectionNoKeyPreview() = SectionPreview(VideoGenerationUi(hasKey = false), ThemeMode.LIGHT)

@Preview(name = "Video picker", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoPickerPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddVideoModelsScreen("OpenRouter", samplePicker, onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Video picker, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoPickerBanglaPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddVideoModelsScreen("OpenRouter", samplePicker, onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Video picker, loading", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoPickerLoadingPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddVideoModelsScreen("OpenRouter", VideoPickerState.Loading, onClose = {}, onRetry = {}, onDone = {})
}

@Preview(name = "Video picker, failed", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun VideoPickerFailedPreview() = JonakiTheme(ThemeMode.LIGHT) {
    AddVideoModelsScreen("OpenRouter", VideoPickerState.Failed("HTTP 503"), onClose = {}, onRetry = {}, onDone = {})
}
