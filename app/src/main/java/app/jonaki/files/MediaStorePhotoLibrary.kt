package app.jonaki.files

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import app.jonaki.feature.gallery.AlbumCollector
import app.jonaki.feature.gallery.GalleryAlbum
import app.jonaki.feature.gallery.GalleryImage
import app.jonaki.feature.gallery.PhotoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone's images through MediaStore, newest first (D-085). With
 * Android 14's "Select photos" MediaStore itself answers only the chosen
 * images, so nothing here checks the grant. A query refused for lack of
 * permission answers an empty list.
 */
class MediaStorePhotoLibrary(private val contentResolver: ContentResolver) : PhotoLibrary {
    private val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Every storage volume, an SD card included.
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    override suspend fun recentImages(offset: Int, limit: Int): List<GalleryImage> = withContext(Dispatchers.IO) {
        readImages(selection = null, selectionArgs = null, offset = offset, limit = limit)
    }

    override suspend fun albumImages(albumId: String, offset: Int, limit: Int): List<GalleryImage> =
        withContext(Dispatchers.IO) {
            readImages(selection = "${MediaStore.Images.ImageColumns.BUCKET_ID} = ?", selectionArgs = arrayOf(albumId), offset, limit)
        }

    override suspend fun albums(): List<GalleryAlbum> = withContext(Dispatchers.IO) {
        val collector = AlbumCollector()
        val projection = IMAGE_COLUMNS + arrayOf(
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
        // One pass over every image with four short columns; it runs only when Collections opens.
        val cursor = queryOrNull { contentResolver.query(collection, projection, null, null, MediaQueries.NEWEST_FIRST) }
        cursor?.use {
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            val reader = ImageRowReader(cursor)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdColumn) ?: continue
                collector.add(bucketId, cursor.getString(bucketNameColumn), reader.image())
            }
        }
        collector.albums()
    }

    private fun readImages(selection: String?, selectionArgs: Array<String>?, offset: Int, limit: Int): List<GalleryImage> {
        val cursor = queryOrNull { queryPage(selection, selectionArgs, offset, limit) } ?: return emptyList()
        return cursor.use {
            val reader = ImageRowReader(cursor)
            val images = ArrayList<GalleryImage>(cursor.count)
            while (cursor.moveToNext()) {
                images += reader.image()
            }
            images
        }
    }

    /** Android 11 refuses LIMIT inside the sort order and takes query arguments instead; older versions take only the former. */
    private fun queryPage(selection: String?, selectionArgs: Array<String>?, offset: Int, limit: Int): Cursor? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val arguments = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaQueries.NEWEST_FIRST)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            return contentResolver.query(collection, IMAGE_COLUMNS, arguments, null)
        }
        val sortOrder = MediaQueries.newestFirstPage(offset = offset, limit = limit)
        return contentResolver.query(collection, IMAGE_COLUMNS, selection, selectionArgs, sortOrder)
    }

    /** Access taken back while the sheet is open makes MediaStore throw; the grid then shows nothing. */
    private fun queryOrNull(query: () -> Cursor?): Cursor? = try {
        query()
    } catch (noAccess: SecurityException) {
        null
    }

    /** Reads the columns of [IMAGE_COLUMNS] from the cursor's current row. */
    private inner class ImageRowReader(private val cursor: Cursor) {
        private val idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID)
        private val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION)

        fun image(): GalleryImage {
            val id = cursor.getLong(idColumn)
            return GalleryImage(
                id = id,
                uri = ContentUris.withAppendedId(collection, id).toString(),
                orientationDegrees = cursor.getInt(orientationColumn),
            )
        }
    }

    private companion object {
        val IMAGE_COLUMNS = arrayOf(
            BaseColumns._ID,
            MediaStore.Images.ImageColumns.ORIENTATION,
        )
    }
}

/** The SQL pieces of the MediaStore queries, apart so a JVM test can read them. */
object MediaQueries {
    /** Newest added first; the id breaks ties, so pages never swap two images with one time. */
    const val NEWEST_FIRST = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${BaseColumns._ID} DESC"

    /** Android 8 to 10 take a page only as LIMIT and OFFSET appended to the sort order. */
    fun newestFirstPage(offset: Int, limit: Int): String = "$NEWEST_FIRST LIMIT $limit OFFSET $offset"
}
