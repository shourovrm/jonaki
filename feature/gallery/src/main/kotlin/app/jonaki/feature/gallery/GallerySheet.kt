package app.jonaki.feature.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What the gallery sheet needs from the app, apart from its callbacks. */
class GallerySource(
    val library: PhotoLibrary,
    val thumbnails: ThumbnailSource,
)

/**
 * Jonaki's own photo picker (D-085): Recent, a grid of the newest images,
 * and Collections, the phone's albums. Images are picked across both tabs
 * with numbered marks; "Add N" hands them over in that order. [someAccess]
 * shows the Android 14 note with Select more and Allow all (D-086).
 * [reloadKey] changes when the access changed, so the lists load again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(
    source: GallerySource,
    someAccess: Boolean,
    reloadKey: Int,
    onSelectMore: () -> Unit,
    onAllowAll: () -> Unit,
    onAdd: (List<GalleryImage>) -> Unit,
    onDismiss: () -> Unit,
) {
    // The grid is the point of the sheet, so it opens at full height.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        GalleryContent(
            source = source,
            someAccess = someAccess,
            reloadKey = reloadKey,
            onSelectMore = onSelectMore,
            onAllowAll = onAllowAll,
            onAdd = onAdd,
        )
    }
}

private enum class GalleryTab {
    RECENT,
    COLLECTIONS,
}

/** The sheet's inside, apart from the sheet itself, so a preview can show it. */
@Composable
internal fun GalleryContent(
    source: GallerySource,
    someAccess: Boolean,
    reloadKey: Int,
    onSelectMore: () -> Unit,
    onAllowAll: () -> Unit,
    onAdd: (List<GalleryImage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(GalleryTab.RECENT) }
    var openAlbum by remember { mutableStateOf<GalleryAlbum?>(null) }
    var selection by remember { mutableStateOf(GallerySelection()) }
    val toggle: (GalleryImage) -> Unit = { image -> selection = selection.toggled(image) }

    BackHandler(enabled = tab == GalleryTab.COLLECTIONS && openAlbum != null) {
        openAlbum = null
    }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            Tab(
                selected = tab == GalleryTab.RECENT,
                onClick = { tab = GalleryTab.RECENT },
                text = { Text(stringResource(R.string.gallery_recent), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            Tab(
                selected = tab == GalleryTab.COLLECTIONS,
                onClick = {
                    // Tapping Collections again goes back from an album to the list.
                    if (tab == GalleryTab.COLLECTIONS) {
                        openAlbum = null
                    }
                    tab = GalleryTab.COLLECTIONS
                },
                text = { Text(stringResource(R.string.gallery_collections), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        if (someAccess) {
            SomeAccessNote(onSelectMore = onSelectMore, onAllowAll = onAllowAll)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val album = openAlbum
            when {
                tab == GalleryTab.RECENT -> PagedImageGrid(
                    loadKey = "recent/$reloadKey",
                    loadPage = source.library::recentImages,
                    selection = selection,
                    thumbnails = source.thumbnails,
                    onToggle = toggle,
                )
                album == null -> AlbumGrid(
                    reloadKey = reloadKey,
                    library = source.library,
                    thumbnails = source.thumbnails,
                    onOpen = { picked -> openAlbum = picked },
                )
                else -> Column(Modifier.fillMaxSize()) {
                    AlbumHeader(album, onBack = { openAlbum = null })
                    PagedImageGrid(
                        loadKey = "album/${album.id}/$reloadKey",
                        loadPage = { offset, limit -> source.library.albumImages(album.id, offset, limit) },
                        selection = selection,
                        thumbnails = source.thumbnails,
                        onToggle = toggle,
                    )
                }
            }
        }
        Button(
            onClick = { onAdd(selection.images) },
            enabled = selection.count > 0,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 48.dp),
        ) {
            val label = if (selection.count > 0) {
                stringResource(R.string.gallery_add_count, selection.count)
            } else {
                stringResource(R.string.gallery_add)
            }
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Android 14 "Select photos": only the chosen photos show, so say so and offer the two ways to more (D-086). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SomeAccessNote(onSelectMore: () -> Unit, onAllowAll: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.gallery_some_access),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onSelectMore) {
                    Text(stringResource(R.string.gallery_select_more))
                }
                TextButton(onClick = onAllowAll) {
                    Text(stringResource(R.string.gallery_allow_all))
                }
            }
        }
    }
}

/**
 * A grid that loads the next page as the user nears the end of what is
 * loaded. [loadKey] names the list; a new key starts again from the top.
 */
@Composable
private fun PagedImageGrid(
    loadKey: String,
    loadPage: suspend (offset: Int, limit: Int) -> List<GalleryImage>,
    selection: GallerySelection,
    thumbnails: ThumbnailSource,
    onToggle: (GalleryImage) -> Unit,
) {
    var pages by remember(loadKey) { mutableStateOf(ImagePages()) }
    val gridState = remember(loadKey) { LazyGridState() }
    LaunchedEffect(loadKey) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisibleIndex ->
                // A tall screen can show the whole first page at once, so keep loading until the end is out of reach.
                while (pages.needsMore(lastVisibleIndex)) {
                    val page = loadPage(pages.nextOffset, ImagePages.PAGE_SIZE)
                    pages = pages.withPage(page, ImagePages.PAGE_SIZE)
                }
            }
    }
    val current = pages
    when {
        current.images.isEmpty() && current.endReached -> EmptyNote()
        current.images.isEmpty() -> LoadingNote()
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PHOTO_CELL_MIN),
            state = gridState,
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(current.images, key = { image -> image.id }) { image ->
                PhotoCell(
                    image = image,
                    number = selection.numberOf(image.id),
                    thumbnails = thumbnails,
                    onToggle = { onToggle(image) },
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    image: GalleryImage,
    number: Int?,
    thumbnails: ThumbnailSource,
    onToggle: () -> Unit,
) {
    val photoLabel = stringResource(R.string.gallery_photo)
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = number != null, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = photoLabel },
    ) {
        Thumbnail(image, thumbnails, Modifier.fillMaxSize())
        if (number != null) {
            // A light veil tells picked cells apart even before the eye finds the number.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        SelectionMark(number, Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

/** An empty ring when not picked; a filled circle with the pick's number when picked. */
@Composable
private fun SelectionMark(number: Int?, modifier: Modifier = Modifier) {
    val markModifier = modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp)
    if (number == null) {
        Box(
            markModifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.2f))
                .border(2.dp, Color.White, CircleShape),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = markModifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White, CircleShape)
            .padding(horizontal = 6.dp),
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

/** Loads off the main thread through [thumbnails]; a cell scrolled away cancels its load. */
@Composable
private fun Thumbnail(image: GalleryImage, thumbnails: ThumbnailSource, modifier: Modifier = Modifier) {
    val sizePx = thumbnailSizePx()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, image.id, sizePx) {
        value = thumbnails.thumbnail(image, sizePx)
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

/**
 * One size for every cell, in steps of 64 pixels, so each image has one
 * cache entry whatever the column width works out to.
 */
@Composable
private fun thumbnailSizePx(): Int {
    val cellPx = with(LocalDensity.current) { THUMBNAIL_CELL.roundToPx() }
    val step = 64
    return ((cellPx + step - 1) / step) * step
}

@Composable
private fun AlbumGrid(
    reloadKey: Int,
    library: PhotoLibrary,
    thumbnails: ThumbnailSource,
    onOpen: (GalleryAlbum) -> Unit,
) {
    val albums by produceState<List<GalleryAlbum>?>(initialValue = null, reloadKey) {
        value = library.albums()
    }
    val loaded = albums
    when {
        loaded == null -> LoadingNote()
        loaded.isEmpty() -> EmptyNote()
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = ALBUM_CELL_MIN),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(loaded, key = { album -> album.id }) { album ->
                AlbumCell(album, thumbnails, onClick = { onOpen(album) })
            }
        }
    }
}

/** A list item: the name keeps one line and ends in "…", the count gets a line of its own (D-029). */
@Composable
internal fun AlbumCell(album: GalleryAlbum, thumbnails: ThumbnailSource, onClick: () -> Unit) {
    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Thumbnail(album.cover, thumbnails, Modifier.fillMaxSize())
        }
        Text(
            albumName(album),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            pluralStringResource(R.plurals.gallery_album_count, album.imageCount, album.imageCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

/** An opened album shows one item, but it heads a grid, so its name still keeps one line. */
@Composable
private fun AlbumHeader(album: GalleryAlbum, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.gallery_back))
        }
        Text(
            albumName(album),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun albumName(album: GalleryAlbum): String =
    album.name.ifBlank { stringResource(R.string.gallery_album_no_name) }

@Composable
private fun EmptyNote() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.gallery_no_photos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingNote() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Three columns on a 360 dp phone, four on a large one. */
private val PHOTO_CELL_MIN = 96.dp

/** Two album columns on a 360 dp phone. */
private val ALBUM_CELL_MIN = 140.dp

/** About the widest a photo cell gets on a phone; albums reuse the same size. */
private val THUMBNAIL_CELL = 128.dp
