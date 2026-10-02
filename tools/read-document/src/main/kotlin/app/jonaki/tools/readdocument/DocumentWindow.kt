package app.jonaki.tools.readdocument

/** One page, slide or sheet of a document, as text. */
internal data class Section(
    /** Counting from 1. */
    val number: Int,
    /** For example "Page 3" or "Sheet 2: Sales". */
    val heading: String,
    /** Empty when the section holds no text. */
    val text: String,
)

/**
 * The part of a document one call shows. [unitName] is "page", "slide" or
 * "sheet"; [totalUnits] counts all of them, shown or not. A Word document
 * has no such units and comes as one section without a heading.
 */
internal data class DocumentWindow(
    val kindName: String,
    val unitName: String?,
    val totalUnits: Int,
    val sections: List<Section>,
)

/** A document the tool can name but not read; the message says why. */
internal class UnreadableDocumentException(val whatFailed: String, val whatToTryNext: String) : Exception(whatFailed)
