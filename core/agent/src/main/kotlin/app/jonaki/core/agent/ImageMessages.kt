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
        val prepared = mutableListOf<Message>()
        val viewedInThisBlock = mutableListOf<ImageSource>()
        for (message in messages) {
            if (message.role != Role.TOOL && viewedInThisBlock.isNotEmpty()) {
                prepared += viewedImagesMessage(viewedInThisBlock)
                viewedInThisBlock.clear()
            }
            when (message.role) {
                Role.USER -> prepared += withAttachedImages(message)
                Role.TOOL -> {
                    prepared += message
                    viewedSource(message, toolNamesByCallId)?.let(viewedInThisBlock::add)
                }
                Role.ASSISTANT -> prepared += message
            }
        }
        if (viewedInThisBlock.isNotEmpty()) {
            prepared += viewedImagesMessage(viewedInThisBlock)
        }
        return prepared
    }

    private fun withAttachedImages(message: Message): Message {
        val imagePaths = AttachmentLine.pathsIn(message.text).filter(ViewedImages::isImagePath)
        if (imagePaths.isEmpty()) {
            return message
        }
        val images = mutableListOf<ImagePart>()
        val notes = mutableListOf<String>()
        for (path in imagePaths) {
            val image = imageOrNull(ImageSource(path))
            when {
                !modelAcceptsImages -> notes += "[$path is an image; this model cannot see images.]"
                image == null -> notes += "[$path could not be opened as an image.]"
                else -> images += image
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

    private fun viewedImagesMessage(sources: List<ImageSource>): Message {
        val images = mutableListOf<ImagePart>()
        val lines = mutableListOf<String>()
        for (source in sources) {
            val image = imageOrNull(source)
            when {
                !modelAcceptsImages -> lines += "[${source.reference}: this model cannot see images.]"
                image == null -> lines += "[${source.reference} could not be opened as an image.]"
                else -> {
                    lines += "[${ViewedImages.TOOL_NAME}: ${source.reference}]"
                    images += image
                }
            }
        }
        return Message(Role.USER, lines.joinToString("\n"), images = images)
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
}
