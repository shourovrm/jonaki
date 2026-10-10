package app.jonaki.run

import app.jonaki.core.toolapi.ToolOutput

/**
 * Reads the hand-over text of generate_video: the tool's result when a video
 * is still being made after the tool's time limit, which names a job id. The
 * tool gives that id only as text for the model (there is no field for it in
 * [ToolOutput]), so the id is read from the line "Call generate_video with
 * job_id=ID to collect it." A test runs the real tool to keep this in step
 * with the tool's wording.
 */
object VideoHandOver {
    private const val FIRST_LINE_START = "The video is still being made by "
    private val collectLine = Regex("""^Call generate_video with job_id=(\S+) to collect it\.""")

    /** The job id of a hand-over result; null for a saved video, an error or any other text. */
    fun jobIdIn(output: ToolOutput): String? {
        if (output.isError || !output.text.startsWith(FIRST_LINE_START)) {
            return null
        }
        for (line in output.text.lineSequence()) {
            val match = collectLine.find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
}
