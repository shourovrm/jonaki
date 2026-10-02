package app.jonaki.feature.gallery

/**
 * Images loaded page by page as the grid scrolls. A photo taken while the
 * user scrolls shifts every later offset by one, so the next page can
 * repeat an image already shown; repeats are dropped, because the grid's
 * item keys must be unique. [nextOffset] counts every row read, repeats
 * included, so a page of nothing but repeats still moves the query on.
 */
data class ImagePages(
    val images: List<GalleryImage> = emptyList(),
    val nextOffset: Int = 0,
    val endReached: Boolean = false,
) {
    /** Adds a loaded page; a page shorter than [pageSize] is the last one. */
    fun withPage(page: List<GalleryImage>, pageSize: Int): ImagePages {
        val shownIds = images.mapTo(HashSet()) { image -> image.id }
        val newImages = page.filter { image -> image.id !in shownIds }
        return ImagePages(
            images = images + newImages,
            nextOffset = nextOffset + page.size,
            endReached = page.size < pageSize,
        )
    }

    /**
     * True when the grid shows an image within [PREFETCH_DISTANCE] of the
     * end of what is loaded, so the next page arrives before the user gets
     * there. [lastVisibleIndex] is -1 before the grid has laid anything out.
     */
    fun needsMore(lastVisibleIndex: Int): Boolean {
        if (endReached) {
            return false
        }
        return lastVisibleIndex >= images.size - PREFETCH_DISTANCE
    }

    companion object {
        /** About ten screens of three columns; one MediaStore query costs a few milliseconds. */
        const val PAGE_SIZE = 120
        const val PREFETCH_DISTANCE = 30
    }
}
