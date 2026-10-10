package app.jonaki.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode
import androidx.compose.ui.unit.dp

private val GeneratedTileSize = 200.dp

/**
 * A picture the model made: the existing square thumbnail (tap opens it full
 * size on black, with pinch zoom), the file name on one line, and Save and
 * Share. The app does the saving and sharing, as for share_file.
 */
@Composable
internal fun GeneratedImageCard(path: String, onSave: (String) -> Unit, onShare: (String) -> Unit) {
    var isOpen by remember(path) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        ImageTile(path, GeneratedTileSize, onClick = { isOpen = true })
        Text(
            path.substringAfterLast('/'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = { onSave(path) }) { Text(stringResource(R.string.chat_generated_image_save)) }
            TextButton(onClick = { onShare(path) }) { Text(stringResource(R.string.chat_generated_image_share)) }
        }
    }
    if (isOpen) {
        FullImageDialog(path, onDismiss = { isOpen = false })
    }
}

// D-029 check: 360 dp wide, font scale 1.3, a long file name. The preview has no picture loader, so the tile shows "Image missing".
@Preview(name = "Generated image", widthDp = 360, fontScale = 1.3f)
@Composable
private fun GeneratedImageCardPreview() {
    JonakiTheme(ThemeMode.DARK) {
        Surface {
            GeneratedImageCard("images/a-very-long-name-for-a-generated-picture.jpg", onSave = {}, onShare = {})
        }
    }
}
