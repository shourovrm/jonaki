package app.jonaki.feature.gallery

/**
 * The images picked in the gallery, in the order they were tapped. That
 * order numbers the marks on the grid and orders the attachment chips.
 * One selection spans Recent and every album, so an image picked in one
 * shows its number in the other.
 */
data class GallerySelection(val images: List<GalleryImage> = emptyList()) {
    val count: Int get() = images.size

    /** Picks [image], or unpicks it when it is picked already; the later images move up one number. */
    fun toggled(image: GalleryImage): GallerySelection {
        val alreadyPicked = images.any { picked -> picked.id == image.id }
        if (alreadyPicked) {
            return GallerySelection(images.filter { picked -> picked.id != image.id })
        }
        return GallerySelection(images + image)
    }

    /** The mark's number, counted from 1, or null when the image is not picked. */
    fun numberOf(imageId: Long): Int? {
        val index = images.indexOfFirst { picked -> picked.id == imageId }
        if (index < 0) {
            return null
        }
        return index + 1
    }
}
