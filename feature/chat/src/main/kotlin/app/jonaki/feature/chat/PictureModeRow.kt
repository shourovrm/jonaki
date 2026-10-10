package app.jonaki.feature.chat

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiIcons
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

/**
 * The slim line above the message box: a chip that switches picture mode, and
 * while the mode is on, the image model the picture will be made with. It is
 * a line of its own, so the attach, Stop and Send buttons keep their room at
 * 360 dp and font scale 1.3.
 */
@Composable
internal fun PictureModeRow(
    pictureMode: PictureModeUi,
    onChange: (isOn: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        FilterChip(
            selected = pictureMode.isOn,
            // A mode that is on can always be switched off.
            enabled = pictureMode.canChange || pictureMode.isOn,
            onClick = { onChange(!pictureMode.isOn) },
            label = { Text(stringResource(R.string.chat_picture_mode), maxLines = 1) },
            leadingIcon = { Icon(JonakiIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        val modelName = pictureMode.modelName
        if (pictureMode.isOn && modelName != null) {
            Text(
                stringResource(R.string.chat_picture_model, modelName),
                style = MaterialTheme.typography.labelMedium,
                color = JonakiTheme.colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// The D-029 check: a 360 dp wide phone at font scale 1.3, with a 40-character model name.
private const val LONG_IMAGE_MODEL_NAME = "black-forest-labs/flux.2-klein-4b-preview"

private val pictureModeOnState = ChatSample.finished.copy(
    draft = "",
    pictureMode = PictureModeUi(isOn = true, canChange = true, modelName = LONG_IMAGE_MODEL_NAME),
)

@Composable
private fun PictureModePreviewScreen(state: ChatUiState) {
    ChatScreen(
        state = state,
        onBack = {},
        onDraftChange = {},
        onSend = {},
        onStop = {},
        onApprovalChoice = { _, _ -> },
        onRetry = {},
        onWebSearchChange = {},
    )
}

@Preview(name = "Picture mode on, dark", widthDp = 360, heightDp = 640, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PictureModeOnDarkPreview() {
    JonakiTheme(ThemeMode.DARK) { PictureModePreviewScreen(pictureModeOnState) }
}

@Preview(name = "Picture mode on, light", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PictureModeOnLightPreview() {
    JonakiTheme(ThemeMode.LIGHT) { PictureModePreviewScreen(pictureModeOnState) }
}

@Preview(name = "Picture mode on, Bangla", locale = "bn", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PictureModeOnBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) { PictureModePreviewScreen(pictureModeOnState) }
}

@Preview(name = "Picture mode off and typing, Bangla", locale = "bn", widthDp = 360, fontScale = 1.3f)
@Composable
private fun PictureModeOffBanglaPreview() {
    JonakiTheme(ThemeMode.LIGHT) {
        Surface {
            Column {
                PictureModeRow(PictureModeUi(isOn = false, canChange = true, modelName = LONG_IMAGE_MODEL_NAME), onChange = {})
                PictureModeRow(PictureModeUi(isOn = false, canChange = false, modelName = null), onChange = {})
            }
        }
    }
}
