package app.jonaki.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val MessageTileSize = 88.dp
private val DraftTileSize = 72.dp
private val TileShape = RoundedCornerShape(16.dp)

/** Zoom reached by a double tap. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** What the user sent: thumbnails of its images, then the text without their paths. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserMessageWithImages(storedText: String) {
    val split = remember(storedText) { MessageAttachments.split(storedText) }
    if (LocalChatImages.current == null || split.imagePaths.isEmpty()) {
        UserBubble(storedText)
        return
    }
    var openedPath by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (path in split.imagePaths) {
                ImageTile(path, MessageTileSize, onClick = { openedPath = path })
            }
        }
        if (split.shownText.isNotBlank()) {
            UserBubble(split.shownText)
        }
    }
    openedPath?.let { path -> FullImageDialog(path, onDismiss = { openedPath = null }) }
}

/** A picked image waiting to be sent: a thumbnail with a remove control; tapping the picture opens it. */
@Composable
internal fun DraftImageChip(attachment: AttachmentUi, onRemove: (String) -> Unit) {
    val path = attachment.previewPath ?: return
    var isOpen by remember { mutableStateOf(false) }
    Box {
        ImageTile(path, DraftTileSize, onClick = { isOpen = true })
        IconButton(
            onClick = { onRemove(attachment.id) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_attachment_remove, attachment.name),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    if (isOpen) {
        FullImageDialog(path, onDismiss = { isOpen = false })
    }
}

/**
 * One square thumbnail, centre-cropped. It shows a plain tile while the
 * picture loads, and a labelled tile when the file is gone.
 */
@Composable
private fun ImageTile(path: String, size: Dp, onClick: () -> Unit) {
    val images = LocalChatImages.current
    val sidePx = with(LocalDensity.current) { size.roundToPx() }
    val tileModifier = Modifier.size(size).clip(TileShape)
    val result by produceState<ChatImageResult?>(
        initialValue = images?.cachedTile(path, sidePx),
        key1 = path,
        key2 = sidePx,
    ) {
        if (value == null) {
            value = images?.tile(path, sidePx) ?: ChatImageResult.Missing
        }
    }
    when (val shown = result) {
        is ChatImageResult.Loaded -> Image(
            bitmap = shown.image,
            contentDescription = stringResource(R.string.chat_image_open),
            contentScale = ContentScale.Crop,
            modifier = tileModifier.clickable(onClick = onClick),
        )
        ChatImageResult.Missing -> Box(
            contentAlignment = Alignment.Center,
            modifier = tileModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                stringResource(R.string.chat_image_missing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
            )
        }
        null -> Box(tileModifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/**
 * The picture on black, fitted to the screen. A dialog gives Back for free.
 * It decodes at the screen's size with room to zoom, not at full
 * resolution, so a 12-megapixel photo costs a few megabytes.
 */
@Composable
internal fun FullImageDialog(path: String, onDismiss: () -> Unit) {
    val images = LocalChatImages.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val containerWidth = constraints.maxWidth
            val containerHeight = constraints.maxHeight
            val result by produceState<ChatImageResult?>(null, path, containerWidth, containerHeight) {
                value = images?.full(path, containerWidth, containerHeight) ?: ChatImageResult.Missing
            }
            when (val shown = result) {
                is ChatImageResult.Loaded -> ZoomableImage(shown.image, containerWidth, containerHeight)
                ChatImageResult.Missing -> Text(
                    stringResource(R.string.chat_image_missing),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                null -> CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_image_close), tint = Color.White)
            }
        }
    }
}

/** Pinch to zoom, drag to pan, double tap to zoom in or back to the start. */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, containerWidth: Int, containerHeight: Int) {
    val baseScale = ImageViewFit.baseScale(bitmap.width, bitmap.height, containerWidth, containerHeight)
    val displayed = DisplayedSize((bitmap.width * baseScale).toFloat(), (bitmap.height * baseScale).toFloat())
    val startTransform = ViewTransform.initial(displayed, containerWidth, containerHeight)
    var transform by remember(bitmap, containerWidth, containerHeight) { mutableStateOf(startTransform) }
    val centre = Offset(containerWidth / 2f, containerHeight / 2f)
    val density = LocalDensity.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(displayed, containerWidth, containerHeight) {
                detectTransformGestures { gestureCentre, pan, zoom, _ ->
                    val focus = gestureCentre - centre
                    transform = transform.changed(focus.x, focus.y, zoom, pan.x, pan.y, displayed, containerWidth, containerHeight)
                }
            }
            .pointerInput(displayed, containerWidth, containerHeight) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        transform = if (transform.zoom > 1f) {
                            startTransform
                        } else {
                            val focus = tap - centre
                            transform.changed(focus.x, focus.y, DOUBLE_TAP_ZOOM, 0f, 0f, displayed, containerWidth, containerHeight)
                        }
                    },
                )
            },
    ) {
        val shownWidth = with(density) { displayed.width.toDp() }
        val shownHeight = with(density) { displayed.height.toDp() }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .requiredSize(shownWidth, shownHeight)
                .graphicsLayer {
                    scaleX = transform.zoom
                    scaleY = transform.zoom
                    translationX = transform.panX
                    translationY = transform.panY
                },
        )
    }
}
