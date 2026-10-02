package app.jonaki.feature.gallery

/**
 * Builds the album list from image rows read newest first, one row at a
 * time, so the app can feed it straight from a MediaStore cursor without
 * holding every row. Albums come out in the order of their newest image,
 * and that image is the album's cover.
 */
class AlbumCollector {
    private val firstRowByAlbum = LinkedHashMap<String, GalleryAlbum>()
    private val countByAlbum = HashMap<String, Int>()

    fun add(albumId: String, albumName: String?, image: GalleryImage) {
        if (albumId !in firstRowByAlbum) {
            firstRowByAlbum[albumId] = GalleryAlbum(albumId, albumName.orEmpty(), imageCount = 0, cover = image)
        }
        countByAlbum[albumId] = (countByAlbum[albumId] ?: 0) + 1
    }

    fun albums(): List<GalleryAlbum> =
        firstRowByAlbum.values.map { album -> album.copy(imageCount = countByAlbum.getValue(album.id)) }
}
