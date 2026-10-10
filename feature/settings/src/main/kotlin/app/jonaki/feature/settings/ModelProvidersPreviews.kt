package app.jonaki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check for the providers sheet: 360 dp wide at font scale 1.3, with
// a 40-character provider name and a long variant tag.

private val sampleOptions = listOf(
    ProviderOptionUi("DeepInfra", "deepinfra/fp4", null, 0.075, 0.25, "fp4"),
    ProviderOptionUi("Fireworks", "fireworks", null, 0.15, 0.50, null),
    ProviderOptionUi(
        name = "Northern Lights Inference Cloud Services Ltd",
        tag = "northern-lights-inference-cloud/turbo-eu",
        variantTag = "northern-lights-inference-cloud/turbo-eu",
        inputPricePerMillion = 0.1875,
        outputPricePerMillion = 0.625,
        quantization = "nvfp4",
    ),
    ProviderOptionUi("Parasail", "parasail/fast", "parasail/fast", 0.1875, 0.625, "fp4"),
    ProviderOptionUi("Reka", "reka", null, 0.06, 1.60, null),
)

@Composable
private fun SheetSample(themeMode: ThemeMode, loadState: ProvidersLoadState) {
    JonakiTheme(themeMode) {
        ModelProvidersContent(
            modelName = "Qwen 4 Coder 480B A35B Instruct Turbo",
            loadState = loadState,
            chosenTags = setOf("deepinfra/fp4", "northern-lights-inference-cloud/turbo-eu"),
            allowFallbacks = true,
            onToggle = {},
            onAllowFallbacksChange = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Providers, dark", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ProvidersDarkPreview() {
    SheetSample(ThemeMode.DARK, ProvidersLoadState.Loaded(sampleOptions))
}

@Preview(name = "Providers, light", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ProvidersLightPreview() {
    SheetSample(ThemeMode.LIGHT, ProvidersLoadState.Loaded(sampleOptions))
}

@Preview(name = "Providers, Bangla", locale = "bn", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ProvidersBanglaPreview() {
    SheetSample(ThemeMode.LIGHT, ProvidersLoadState.Loaded(sampleOptions))
}

@Preview(name = "Providers, loading", widthDp = 360, heightDp = 300, fontScale = 1.3f)
@Composable
private fun ProvidersLoadingPreview() {
    SheetSample(ThemeMode.LIGHT, ProvidersLoadState.Loading)
}

@Preview(name = "Providers, empty", widthDp = 360, heightDp = 300, fontScale = 1.3f)
@Composable
private fun ProvidersEmptyPreview() {
    SheetSample(ThemeMode.LIGHT, ProvidersLoadState.Loaded(emptyList()))
}

@Preview(name = "Providers, failed", widthDp = 360, heightDp = 300, fontScale = 1.3f)
@Composable
private fun ProvidersFailedPreview() {
    SheetSample(ThemeMode.DARK, ProvidersLoadState.Failed)
}
