package app.jonaki.files

import java.io.File

/**
 * Names one cached thumbnail: the file's path, length and last-modified time
 * with the tile size. An edited or replaced file changes the length or the
 * time, so it is decoded again instead of showing the old picture.
 */
data class TileCacheKey(
    val path: String,
    val lengthBytes: Long,
    val lastModifiedMillis: Long,
    val sidePx: Int,
) {
    companion object {
        fun of(file: File, sidePx: Int) = TileCacheKey(file.path, file.length(), file.lastModified(), sidePx)
    }
}
