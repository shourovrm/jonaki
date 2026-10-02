package app.jonaki.feature.gallery

import androidx.compose.ui.graphics.ImageBitmap

/**
 * One image in the phone's photo library. [uri] is its MediaStore content
 * link as text, so this class and the logic around it run in JVM tests.
 * [orientationDegrees] is MediaStore's turn for the photo, needed only where
 * the app draws thumbnails itself (Android 9 and older).
 */
data class GalleryImage(
    val id: Long,
    val uri: String,
    val orientationDegrees: Int = 0,
)

/** A MediaStore bucket (a folder such as Camera or Screenshots) with its newest image as the cover. */
data class GalleryAlbum(
    val id: String,
    /** Empty when MediaStore has no name for the folder. */
    val name: String,
    val imageCount: Int,
    val cover: GalleryImage,
)

/**
 * The phone's images, newest first (D-085). The app implements it over
 * MediaStore; every call does its own work off the main thread and answers
 * an empty list when the images cannot be read.
 */
interface PhotoLibrary {
    suspend fun recentImages(offset: Int, limit: Int): List<GalleryImage>

    suspend fun albums(): List<GalleryAlbum>

    suspend fun albumImages(albumId: String, offset: Int, limit: Int): List<GalleryImage>
}

/** Small square pictures for the grid, [sizePx] on each side; null when the image cannot be read. */
fun interface ThumbnailSource {
    suspend fun thumbnail(image: GalleryImage, sizePx: Int): ImageBitmap?
}
