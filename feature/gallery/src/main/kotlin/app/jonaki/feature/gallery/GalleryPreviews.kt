package app.jonaki.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jonaki.core.ui.JonakiTheme

// The D-029 check: a 360 dp wide phone at font scale 1.3, with a 40-character album name.

private const val LONG_ALBUM_NAME = "Family trip to Sylhet tea gardens 2026-1"

private val previewImages = (1L..30L).map { id -> GalleryImage(id, "content://media/external/images/media/$id") }

private object PreviewLibrary : PhotoLibrary {
    override suspend fun recentImages(offset: Int, limit: Int): List<GalleryImage> =
        previewImages.drop(offset).take(limit)

    override suspend fun albums(): List<GalleryAlbum> = listOf(
        GalleryAlbum("1", LONG_ALBUM_NAME, imageCount = 12_345, cover = previewImages[0]),
        GalleryAlbum("2", "Camera", imageCount = 1, cover = previewImages[1]),
    )

    override suspend fun albumImages(albumId: String, offset: Int, limit: Int): List<GalleryImage> =
        recentImages(offset, limit)
}

private val previewSource = GallerySource(PreviewLibrary, ThumbnailSource { _, _ -> null })

@Preview(name = "Gallery, some access", widthDp = 360, heightDp = 640, fontScale = 1.3f)
@Composable
private fun GallerySomeAccessPreview() {
    JonakiTheme {
        Surface {
            GalleryContent(
                source = previewSource,
                someAccess = true,
                reloadKey = 0,
                onSelectMore = {},
                onAllowAll = {},
                onAdd = {},
            )
        }
    }
}

@Preview(name = "Album cells", widthDp = 360, fontScale = 1.3f)
@Composable
private fun AlbumCellsPreview() {
    JonakiTheme {
        Surface {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
                for (album in listOf(
                    GalleryAlbum("1", LONG_ALBUM_NAME, imageCount = 12_345, cover = previewImages[0]),
                    GalleryAlbum("2", "", imageCount = 1, cover = previewImages[1]),
                )) {
                    Row(Modifier.weight(1f)) {
                        AlbumCell(album, previewSource.thumbnails, onClick = {})
                    }
                }
            }
        }
    }
}
