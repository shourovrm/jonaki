package app.jonaki.feature.onboarding

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

// The D-029 check: 360 dp wide, font scale 1.3, a name of 40 characters.

private const val LONG_MODEL_NAME = "Black Forest Labs: FLUX.2 Klein 4B Turbo!"

private val sampleServices = listOf(
    SetupServiceUi("openrouter", "OpenRouter"),
    SetupServiceUi("gemini", "Gemini"),
    SetupServiceUi("deepseek", "DeepSeek"),
    SetupServiceUi("openai", "OpenAI"),
    SetupServiceUi("glm", "GLM (Z.ai)"),
)

private val sampleSearch = SetupSearchCardUi(
    services = listOf(SetupServiceUi("TAVILY", "Tavily"), SetupServiceUi("SERPER", "Serper"), SetupServiceUi("EXA", "Exa"), SetupServiceUi("OLLAMA", "Ollama")),
    selectedServiceKey = "TAVILY",
    selectedServiceName = "Tavily",
)

@Composable
private fun DeckPreview(state: SetupDeckUi, mode: ThemeMode) {
    JonakiTheme(mode) {
        Surface { SetupDeckScreen(state, SetupDeckActions()) }
    }
}

@Preview(name = "Setup, service", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SetupServicePreview() = DeckPreview(
    SetupDeckUi(model = SetupModelCardUi(services = sampleServices, selectedServiceKey = "openrouter")),
    ThemeMode.DARK,
)

@Preview(name = "Setup, key and model", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun SetupKeyPreview() = DeckPreview(
    SetupDeckUi(
        model = SetupModelCardUi(
            services = sampleServices,
            selectedServiceKey = "openrouter",
            showsKeyStep = true,
            selectedServiceName = "OpenRouter",
            keySite = "openrouter.ai",
            savedKeyPreview = "sk-o••••",
            chosenModelName = LONG_MODEL_NAME,
        ),
    ),
    ThemeMode.LIGHT,
)

@Preview(name = "Setup, search, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun SetupSearchPreview() = DeckPreview(SetupDeckUi(card = SetupCard.SEARCH, search = sampleSearch), ThemeMode.LIGHT)

@Preview(name = "Setup, approvals", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SetupApprovalsPreview() = DeckPreview(SetupDeckUi(card = SetupCard.APPROVALS), ThemeMode.DARK)

@Preview(name = "Setup, notifications, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun SetupNotificationsPreview() = DeckPreview(SetupDeckUi(card = SetupCard.NOTIFICATIONS), ThemeMode.LIGHT)
