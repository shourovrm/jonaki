package app.jonaki.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

private val VectorTileSize = 200.dp

/**
 * A vector picture the model made: a 200 dp square preview drawn from the
 * SVG (tap opens it full size, with pinch zoom), the file name on one line,
 * and Save and Share, as for a raster picture.
 */
@Composable
internal fun GeneratedVectorImageCard(path: String, onSave: (String) -> Unit, onShare: (String) -> Unit) {
    var isOpen by remember(path) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        SvgView(
            path = path,
            allowsZoom = false,
            modifier = Modifier.size(VectorTileSize).clip(RoundedCornerShape(16.dp)),
            onClick = { isOpen = true },
        )
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
        FullSvgDialog(path, onDismiss = { isOpen = false })
    }
}

/** The SVG fitted to the screen on the theme's surface colour; the WebView's own pinch zoom and drag work here. A dialog gives Back for free. */
@Composable
private fun FullSvgDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            SvgView(path = path, allowsZoom = true, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_image_close))
            }
        }
    }
}

// D-029 check: 360 dp wide, font scale 1.3, a long file name. The preview has no image loader, so the tile shows "Image missing".
@Preview(name = "Generated vector image", widthDp = 360, fontScale = 1.3f)
@Composable
private fun GeneratedVectorImageCardPreview() {
    JonakiTheme(ThemeMode.DARK) {
        Surface {
            GeneratedVectorImageCard("images/a-very-long-name-for-a-generated-logo.svg", onSave = {}, onShare = {})
        }
    }
}
