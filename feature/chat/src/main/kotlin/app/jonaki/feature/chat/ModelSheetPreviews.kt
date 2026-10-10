package app.jonaki.feature.chat

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode
import app.jonaki.core.ui.ThinkingChoice

// The D-029 check for the model sheet: 360 dp wide at font scale 1.3, with a
// 40-character chat model name and a 40-character image model name.

@Composable
private fun ModelSheetPreviewContent(state: ChatUiState) {
    Surface {
        ModelSheetContent(
            choices = state.modelChoices,
            selectedKey = state.selectedModelKey,
            imageChoices = state.imageModelChoices,
            selectedImageKey = state.selectedImageModelKey,
            thinking = ThinkingChoice.DEFAULT,
            onThinkingChange = {},
            onSelect = {},
            onSelectImage = {},
            onEditModels = {},
        )
    }
}

@Preview(name = "Model sheet with image models, dark", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ModelSheetDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { ModelSheetPreviewContent(ChatSample.withUsage) }
}

@Preview(name = "Model sheet, a thread's own image pick", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ModelSheetOwnPickPreview() {
    val state = ChatSample.withUsage.copy(selectedImageModelKey = ChatSample.imageModels[1].key)
    JonakiTheme(ThemeMode.LIGHT) { ModelSheetPreviewContent(state) }
}

@Preview(name = "Model sheet without image models", widthDp = 360, heightDp = 700, fontScale = 1.3f)
@Composable
private fun ModelSheetWithoutImageModelsPreview() {
    val state = ChatSample.withUsage.copy(imageModelChoices = emptyList(), selectedImageModelKey = null)
    JonakiTheme(ThemeMode.LIGHT) { ModelSheetPreviewContent(state) }
}

@Preview(name = "Model sheet with image models, Bangla", locale = "bn", widthDp = 360, heightDp = 900, fontScale = 1.3f)
@Composable
private fun ModelSheetBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) { ModelSheetPreviewContent(ChatSample.withUsage) }
}
