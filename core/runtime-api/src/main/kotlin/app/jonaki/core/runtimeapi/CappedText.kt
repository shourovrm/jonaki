package app.jonaki.core.runtimeapi

/**
 * Collects a program's printed output up to a cap, so that a loop printing
 * forever cannot fill the phone's memory. run_code cuts the text again for
 * the model with OutputLimiter; this cap only bounds what is held at all.
 */
class CappedText(private val maxCharacters: Int) {
    private val kept = StringBuilder()
    private var droppedCharacters = 0L

    fun append(text: String) {
        val room = maxCharacters - kept.length
        if (text.length <= room) {
            kept.append(text)
            return
        }
        kept.append(text, 0, room)
        droppedCharacters += text.length - room
    }

    override fun toString(): String {
        if (droppedCharacters == 0L) {
            return kept.toString()
        }
        return "$kept\n[$droppedCharacters more characters were dropped]"
    }

    companion object {
        /** Per stream (stdout, stderr); far above what run_code shows the model. */
        const val DEFAULT_MAX_CHARACTERS = 1_000_000
    }
}
