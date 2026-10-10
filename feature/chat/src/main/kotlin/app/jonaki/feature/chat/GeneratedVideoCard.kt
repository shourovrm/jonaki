package app.jonaki.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.core.ui.ThemeMode

private val VideoTileWidth = 240.dp
private val VideoTileShape = RoundedCornerShape(12.dp)

/**
 * A video the model made: a preview frame with a play mark (a plain tile when
 * no frame could be made), the file name on one line, the length and size,
 * and Play, Save and Share. There is no player in the app: Play hands the
 * file to the phone's own video player, and Save and Share work as under a
 * generated image. A deleted file shows "Video missing" and no buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeneratedVideoCard(
    path: String,
    onPlay: (String) -> Unit,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val videos = LocalChatVideos.current
    val tileWidthPx = with(LocalDensity.current) { VideoTileWidth.roundToPx() }
    val result by produceState<ChatVideoResult?>(initialValue = null, key1 = path, key2 = tileWidthPx) {
        value = videos?.info(path, tileWidthPx) ?: ChatVideoResult.Loaded(frame = null, durationSeconds = null, sizeBytes = 0L)
    }
    val shown = result
    Column(Modifier.fillMaxWidth()) {
        if (shown is ChatVideoResult.Missing) {
            MissingVideoTile()
        } else {
            VideoTile(shown as? ChatVideoResult.Loaded, onClick = { onPlay(path) })
        }
        Text(
            path.substringAfterLast('/'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (shown is ChatVideoResult.Loaded) {
            lengthAndSize(shown)?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlowRow {
                TextButton(onClick = { onPlay(path) }) { Text(stringResource(R.string.chat_video_play)) }
                TextButton(onClick = { onSave(path) }) { Text(stringResource(R.string.chat_generated_image_save)) }
                TextButton(onClick = { onShare(path) }) { Text(stringResource(R.string.chat_generated_image_share)) }
            }
        }
    }
}

/** "4 s · 1.2 MB"; the size is left out while it is not known, and the whole line when neither is. */
@Composable
private fun lengthAndSize(loaded: ChatVideoResult.Loaded): String? {
    val length = loaded.durationSeconds?.let { seconds -> stringResource(R.string.chat_video_seconds, seconds) }
    val size = loaded.sizeBytes.takeIf { it > 0 }?.let { bytes -> IncomingFiles.describeSize(bytes) }
    return listOfNotNull(length, size).joinToString(" · ").ifEmpty { null }
}

@Composable
private fun VideoTile(loaded: ChatVideoResult.Loaded?, onClick: () -> Unit) {
    val tileModifier = Modifier
        .width(VideoTileWidth)
        .aspectRatio(16f / 9f)
        .clip(VideoTileShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick)
    Box(tileModifier, contentAlignment = Alignment.Center) {
        val frame = loaded?.frame
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (loaded != null) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.chat_video_play),
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun MissingVideoTile() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(VideoTileWidth)
            .aspectRatio(16f / 9f)
            .clip(VideoTileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            stringResource(R.string.chat_video_missing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

// D-029 check: 360 dp wide, font scale 1.3, a long file name; English, Bangla and the deleted-file tile.
private const val LONG_VIDEO_NAME = "videos/a-very-long-name-for-a-generated-video-clip.mp4"

private class PreviewVideos(private val result: ChatVideoResult) : ChatVideos {
    override suspend fun info(path: String, widthPx: Int): ChatVideoResult = result
}

@Composable
private fun CardPreview(result: ChatVideoResult, mode: ThemeMode) {
    JonakiTheme(mode) {
        Surface {
            CompositionLocalProvider(LocalChatVideos provides PreviewVideos(result)) {
                GeneratedVideoCard(LONG_VIDEO_NAME, onPlay = {}, onSave = {}, onShare = {})
            }
        }
    }
}

@Preview(name = "Generated video", widthDp = 360, fontScale = 1.3f)
@Composable
private fun GeneratedVideoCardPreview() =
    CardPreview(ChatVideoResult.Loaded(frame = null, durationSeconds = 4, sizeBytes = 1_300_000L), ThemeMode.DARK)

@Preview(name = "Generated video, Bangla", locale = "bn", widthDp = 360, fontScale = 1.3f)
@Composable
private fun GeneratedVideoCardBanglaPreview() =
    CardPreview(ChatVideoResult.Loaded(frame = null, durationSeconds = 4, sizeBytes = 1_300_000L), ThemeMode.LIGHT)

@Preview(name = "Generated video, file deleted", widthDp = 360, fontScale = 1.3f)
@Composable
private fun GeneratedVideoCardMissingPreview() = CardPreview(ChatVideoResult.Missing, ThemeMode.LIGHT)
