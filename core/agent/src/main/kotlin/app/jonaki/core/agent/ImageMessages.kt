package app.jonaki.core.agent

import app.jonaki.core.model.ImagePart
import app.jonaki.core.model.Message
import app.jonaki.core.model.Role
import app.jonaki.core.toolapi.ImageSource
import app.jonaki.core.toolapi.ViewedImages

/** Turns an image in the thread folder into what the model is sent; the app implements it. */
fun interface ImageLoader {
    /**
     * The image shrunk and encoded, the same bytes for the same file on
     * every call, so the prompt cache holds; null when it cannot be decoded.
     */
    fun load(source: ImageSource): ImagePart?
}

/**
 * Adds images to a conversation just before a request (D-049, D-050). The
 * database keeps only text, so the images are found again from the text on
 * every request: the attachment line of a user message, and view_image
 * results, which get a user message with the images after them. The same
 * conversation always gives the same messages.
 *
 * Only the newest user turns carry their images (D-GAP-4); older images
 * become a note that names the view_image call, so the model can look
 * again. The cut moves in steps of [KEPT_IMAGE_TURNS] turns, so between two
 * steps every earlier message keeps its bytes and the prompt cache holds.
 */
class ImageMessages(
    private val loader: ImageLoader,
    private val modelAcceptsImages: Boolean,
) {
    // One run sends the same images on every request; each is read once.
    private val loadedImages = mutableMapOf<ImageSource, ImagePart?>()

    fun prepare(messages: List<Message>): List<Message> {
        val toolNamesByCallId = messages.flatMap { message -> message.toolCalls }
            .associate { toolCall -> toolCall.id to toolCall.toolName }
        val firstTurnWithImages = firstTurnWithImages(messages.count(::isUserTurn))
        val prepared = mutableListOf<Message>()
        val viewedInThisBlock = mutableListOf<ImageSource>()
        // Messages before the first user turn count as turn 0.
        var turn = 0
        var userTurnsSeen = 0
        for (message in messages) {
            if (message.role != Role.TOOL && viewedInThisBlock.isNotEmpty()) {
                prepared += viewedImagesMessage(viewedInThisBlock, sendImages = turn >= firstTurnWithImages)
                viewedInThisBlock.clear()
            }
            if (isUserTurn(message)) {
                turn = userTurnsSeen
                userTurnsSeen += 1
            }
            when (message.role) {
                Role.USER -> prepared += withAttachedImages(message, sendImages = turn >= firstTurnWithImages)
                Role.TOOL -> {
                    prepared += message
                    viewedSource(message, toolNamesByCallId)?.let(viewedInThisBlock::add)
                }
                Role.ASSISTANT -> prepared += message
            }
        }
        if (viewedInThisBlock.isNotEmpty()) {
            prepared += viewedImagesMessage(viewedInThisBlock, sendImages = turn >= firstTurnWithImages)
        }
        return prepared
    }

    /** The budget notice is added within a run and never saved, so it must not move the cut. */
    private fun isUserTurn(message: Message): Boolean =
        message.role == Role.USER && message.text != AgentLoop.BUDGET_NOTICE

    private fun withAttachedImages(message: Message, sendImages: Boolean): Message {
        val imagePaths = AttachmentLine.pathsIn(message.text).filter(ViewedImages::isImagePath)
        if (imagePaths.isEmpty()) {
            return message
        }
        val images = mutableListOf<ImagePart>()
        val notes = mutableListOf<String>()
        for (path in imagePaths) {
            val source = ImageSource(path)
            if (!modelAcceptsImages) {
                notes += "[$path is an image; this model cannot see images.]"
                continue
            }
            if (!sendImages) {
                notes += notRepeatedNote(source)
                continue
            }
            val image = imageOrNull(source)
            if (image == null) {
                notes += "[$path could not be opened as an image.]"
            } else {
                images += image
            }
        }
        val text = if (notes.isEmpty()) message.text else message.text + "\n" + notes.joinToString("\n")
        return message.copy(text = text, images = images)
    }

    private fun viewedSource(message: Message, toolNamesByCallId: Map<String, String>): ImageSource? {
        if (toolNamesByCallId[message.toolCallId] != ViewedImages.TOOL_NAME) {
            return null
        }
        return ViewedImages.sourceIn(message.text)
    }

    private fun viewedImagesMessage(sources: List<ImageSource>, sendImages: Boolean): Message {
        val images = mutableListOf<ImagePart>()
        val lines = mutableListOf<String>()
        for (source in sources) {
            if (!modelAcceptsImages) {
                lines += "[${source.reference}: this model cannot see images.]"
                continue
            }
            if (!sendImages) {
                lines += notRepeatedNote(source)
                continue
            }
            val image = imageOrNull(source)
            if (image == null) {
                lines += "[${source.reference} could not be opened as an image.]"
            } else {
                lines += "[${ViewedImages.TOOL_NAME}: ${source.reference}]"
                images += image
            }
        }
        return Message(Role.USER, lines.joinToString("\n"), images = images)
    }

    /** Stands in for an image of an older turn; it names the exact call that shows the image again. */
    private fun notRepeatedNote(source: ImageSource): String {
        val pageArgument = if (source.pdfPage == null) "" else " page=${source.pdfPage}"
        return "[${source.reference} is not repeated; call ${ViewedImages.TOOL_NAME} with " +
            "path=${source.path}$pageArgument to see it again.]"
    }

    /** Never loads for a model that cannot see images, so no time is spent shrinking them. */
    private fun imageOrNull(source: ImageSource): ImagePart? {
        if (!modelAcceptsImages) {
            return null
        }
        // Not getOrPut: it would load an undecodable image again on every request.
        if (source !in loadedImages) {
            loadedImages[source] = loader.load(source)
        }
        return loadedImages[source]
    }

    companion object {
        /** The newest this many to twice this many minus one user turns carry their images. */
        const val KEPT_IMAGE_TURNS = 3

        /**
         * The first user turn (counting from 0) whose images are sent. It
         * stays 0 up to five turns, then moves to 3 at six turns, 6 at nine
         * turns and so on, so it changes only once every three turns.
         */
        fun firstTurnWithImages(userTurnCount: Int): Int {
            val turnsBeyondKept = userTurnCount - KEPT_IMAGE_TURNS
            if (turnsBeyondKept < KEPT_IMAGE_TURNS) {
                return 0
            }
            return (turnsBeyondKept / KEPT_IMAGE_TURNS) * KEPT_IMAGE_TURNS
        }
    }
}
