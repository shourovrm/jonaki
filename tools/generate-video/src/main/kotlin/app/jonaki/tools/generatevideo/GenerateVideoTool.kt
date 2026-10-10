package app.jonaki.tools.generatevideo

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.GeneratedVideos
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.VideoChoiceResult
import app.jonaki.core.toolapi.VideoChoices
import app.jonaki.core.toolapi.VideoCheckOutcome
import app.jonaki.core.toolapi.VideoDownloadOutcome
import app.jonaki.core.toolapi.VideoFailure
import app.jonaki.core.toolapi.VideoGenerator
import app.jonaki.core.toolapi.VideoModelFacts
import app.jonaki.core.toolapi.VideoRequest
import app.jonaki.core.toolapi.VideoStartOutcome
import app.jonaki.core.toolapi.booleanArgument
import app.jonaki.core.toolapi.intArgument
import app.jonaki.core.toolapi.stringArgument
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Makes one video from a text description with a video model the user added
 * in Settings, and saves it in the thread's videos/ folder. Each call costs
 * real money (a few seconds of video cost cents to a few dollars) and writes
 * a file, so every call asks the user first.
 *
 * A video takes minutes and the job goes on, and is charged, at the service
 * even when this tool stops. So the tool writes the job's id to
 * `videos/pending-jobs.json` when the job starts. If the job is still running
 * 30 seconds before the tool's limit, the tool returns a text with the id and
 * the next call (`job_id`); a Stop cannot return text, but the id is already
 * in the file, and the next call without `job_id` is told about it.
 *
 * [modelKeys] are the models the user added, as "service:modelId". [facts]
 * has what the service's list says about them (empty when the list could not
 * be loaded; then values are sent unchecked). [onJobCompleted] saves the
 * job's real cost on the thread; the tool calls it exactly once per job, when
 * the job is first seen complete. [wait] and [nowMillis] are injected so that
 * tests need no real time.
 */
class GenerateVideoTool(
    private val generator: VideoGenerator,
    private val modelKeys: List<String>,
    private val defaultModelKey: () -> String?,
    private val facts: Map<String, VideoModelFacts> = emptyMap(),
    private val onJobCompleted: suspend (modelKey: String, costUsd: Double?) -> Unit = { _, _ -> },
    private val wait: suspend (Duration) -> Unit = { duration -> delay(duration) },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : Tool {
    override val name: String = GeneratedVideos.TOOL_NAME

    override val promptLine: String =
        "generate_video: make a short video from a text description; expensive and slow, the user approves each call"

    override val guidelines: List<String> = listOf(
        "Use generate_video only when the user asks for a video or an animation. A video is expensive " +
            "(a few seconds can cost from cents to a few dollars), so make one video per request, " +
            "leave duration_seconds and resolution out unless the user asks for more (the shortest length " +
            "and lowest resolution that does the job are the default), and never use it to test or to try variants.",
        "The prompt must describe the scene, the motion and the camera fully: who or what is shown, where, " +
            "what moves and how, the camera angle and movement, the light and the style. " +
            "The video model sees nothing else of the conversation.",
        "A video takes several minutes. The result says when it is still being made and gives a job id; " +
            "then tell the user it is not ready yet, and call generate_video with that job_id to collect it " +
            "(that call costs nothing more). Take a job_id only from an earlier result of this tool, never from anywhere else.",
        "The video is saved in videos/ and the user sees it in the chat; do not paste its path as a link. " +
            "If a video failed or was refused, tell the user before trying again.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "A full description of the video: scene, subject, motion, camera, light, style. Required unless job_id is given")
            }
            putJsonObject("duration_seconds") {
                put("type", "integer")
                put("description", "Length in whole seconds; the model's shortest sensible length (at least 4 s where offered) when left out")
            }
            putJsonObject("resolution") {
                put("type", "string")
                put("description", "For example 480p, 720p or 1080p; 720p, or the lowest the model offers, when left out")
            }
            putJsonObject("aspect_ratio") {
                put("type", "string")
                put("description", "Shape of the video, for example 16:9 or 9:16; the model's default when left out")
            }
            putJsonObject("with_audio") {
                put("type", "boolean")
                put("description", "Whether the video has sound; the model's default when left out")
            }
            putJsonObject("file_name") {
                put("type", "string")
                put("description", "Name for the file without folder or ending, for example boat-at-dawn; made from the prompt when left out")
            }
            putJsonObject("model") {
                put("type", "string")
                put("description", "One of the video models the user added, as service:model; the user's starred model when left out")
                if (modelKeys.isNotEmpty()) {
                    putJsonArray("enum") {
                        for (modelKey in modelKeys) {
                            add(modelKey)
                        }
                    }
                }
            }
            putJsonObject("job_id") {
                put("type", "string")
                put("description", "The job id from an earlier result of this tool that said the video was still being made; collects that video at no new cost")
            }
            putJsonObject("new_video") {
                put("type", "boolean")
                put("description", "Only true when the user wants another new video although an earlier job is still waiting to be collected")
            }
        }
    }

    /** Spends money and writes a file; always asks, in the Auto mode too. */
    override val sideEffect: SideEffect = SideEffect.CHANGES

    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Video takes minutes. The tool hands over 30 seconds before this limit, so that the agent loop never has to kill it. */
    override val timeLimit: Duration = TIME_LIMIT

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val startedAt = nowMillis()
        val jobIdArgument = arguments.stringArgument("job_id")?.trim()?.ifEmpty { null }
        if (jobIdArgument != null) {
            return collectEarlierJob(jobIdArgument, arguments, context, startedAt)
        }
        return startNewJob(arguments, context, startedAt)
    }

    private suspend fun startNewJob(arguments: JsonObject, context: ToolContext, startedAt: Long): ToolOutput {
        val prompt = arguments.stringArgument("prompt")?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return ToolOutput.error("argument prompt is missing", "Call generate_video again with a full description of the video.")
        }
        val requestedModel = arguments.stringArgument("model")
        val modelKey = chooseModel(requestedModel) ?: return modelError(requestedModel)
        val pending = PendingVideoJobs(context.threadFolder)
        if (arguments.booleanArgument("new_video") != true) {
            earlierJobBlocking(pending.all(), startedAt)?.let { earlier -> return earlierJobText(earlier, startedAt) }
        }
        val choice = when (
            val result = VideoChoices.resolve(
                facts = facts[modelKey],
                durationSeconds = arguments.intArgument("duration_seconds"),
                resolution = arguments.stringArgument("resolution")?.trim()?.ifEmpty { null },
                aspectRatio = arguments.stringArgument("aspect_ratio")?.trim()?.ifEmpty { null },
                withAudio = arguments.booleanArgument("with_audio"),
            )
        ) {
            is VideoChoiceResult.Refused -> return ToolOutput.error(
                result.message,
                "Nothing was requested and nothing was charged. Call generate_video again with a supported value, or leave it out.",
            )
            is VideoChoiceResult.Chosen -> result.choice
        }
        // The first colon ends the service key; a model id may hold more colons, such as "x/y:free".
        val serviceKey = modelKey.substringBefore(':')
        val modelId = modelKey.substringAfter(':')
        val request = VideoRequest(serviceKey, modelId, prompt, choice.durationSeconds, choice.resolution, choice.aspectRatio, choice.withAudio)
        val jobId = when (val started = generator.start(request)) {
            is VideoStartOutcome.Failed -> return failureText(started.kind, started.message, modelKey, jobId = null)
            is VideoStartOutcome.Started -> started.jobId
        }
        val job = PendingVideoJob(
            threadId = context.threadFolder.name,
            jobId = jobId,
            modelKey = modelKey,
            startedAtMillis = startedAt,
            durationSeconds = choice.durationSeconds,
            resolution = choice.resolution,
            baseName = VideoFiles.baseName(arguments.stringArgument("file_name"), prompt),
        )
        // Written before the first wait: from here on the job is charged whatever happens to this call.
        val savedNote = try {
            pending.add(job)
            null
        } catch (problem: IOException) {
            "The job id could not be saved in the thread folder (${problem.message})."
        }
        return waitForJob(job, context, startedAt, checkFirst = false, note = savedNote)
    }

    private suspend fun collectEarlierJob(jobId: String, arguments: JsonObject, context: ToolContext, startedAt: Long): ToolOutput {
        val job = PendingVideoJobs(context.threadFolder).find(jobId)
            ?: return ToolOutput.error(
                "job $jobId is not a video job started in this thread, or it was already collected",
                "Use only a job id from an earlier result of generate_video. Do not start a new video unless the user asks for one.",
            )
        val renamed = arguments.stringArgument("file_name")?.let { name -> VideoFiles.baseName(name, null) }
        return waitForJob(if (renamed == null) job else job.copy(baseName = renamed), context, startedAt, checkFirst = true, note = null)
    }

    /** Checks the job on the schedule until it ends, or hands it over 30 seconds before the tool's limit. */
    private suspend fun waitForJob(job: PendingVideoJob, context: ToolContext, startedAt: Long, checkFirst: Boolean, note: String?): ToolOutput {
        val serviceKey = job.modelKey.substringBefore(':')
        var failedChecksInARow = 0
        var mustCheckNow = checkFirst
        while (true) {
            if (!mustCheckNow) {
                wait(intervalAfter(nowMillis() - startedAt))
            }
            mustCheckNow = false
            when (val outcome = generator.check(serviceKey, job.jobId)) {
                is VideoCheckOutcome.Completed -> return collect(job, outcome, serviceKey, context)
                is VideoCheckOutcome.JobEnded -> {
                    forget(job, context)
                    return ToolOutput.error(
                        "the video job ${job.jobId} ${endedPhrase(outcome.status)}: ${outcome.reason}",
                        "No video was saved. Tell the user what the service said; a new try is a new charge, so ask first unless the user already asked for it.",
                    )
                }
                is VideoCheckOutcome.Failed -> {
                    failedChecksInARow++
                    val isLasting = outcome.kind == VideoFailure.KEY_PROBLEM || outcome.kind == VideoFailure.OUT_OF_CREDIT
                    if (isLasting || failedChecksInARow >= MAX_FAILED_CHECKS_IN_A_ROW) {
                        return failureText(outcome.kind, outcome.message, job.modelKey, jobId = job.jobId)
                    }
                }
                is VideoCheckOutcome.Working -> failedChecksInARow = 0
            }
            if (nowMillis() - startedAt >= HAND_OVER_AFTER.inWholeMilliseconds) {
                return stillRunningText(job, note)
            }
        }
    }

    /** Every 10 seconds in the first minute, every 20 seconds after that. */
    private fun intervalAfter(elapsedMillis: Long): Duration =
        if (elapsedMillis < FAST_POLLING_FOR.inWholeMilliseconds) FAST_POLL_INTERVAL else SLOW_POLL_INTERVAL

    private fun stillRunningText(job: PendingVideoJob, note: String?): ToolOutput {
        val lines = listOfNotNull(
            "The video is still being made by ${job.modelKey}. Job id: ${job.jobId}.",
            "Call generate_video with job_id=${job.jobId} to collect it. That call makes no new charge.",
            "Tell the user it is not ready yet. Do not start another video for the same request.",
            note,
        )
        return ToolOutput.success(lines.joinToString("\n"))
    }

    private suspend fun collect(job: PendingVideoJob, completed: VideoCheckOutcome.Completed, serviceKey: String, context: ToolContext): ToolOutput {
        val pending = PendingVideoJobs(context.threadFolder)
        // Once per job: the flag in the pending file survives a failed download and a later collecting call.
        val alreadyRecorded = pending.find(job.jobId)?.costRecorded == true
        if (!alreadyRecorded) {
            withContext(NonCancellable) {
                onJobCompleted(job.modelKey, completed.costUsd)
                runCatching { pending.markCostRecorded(job.jobId) }
            }
        }
        val folder = File(context.threadFolder, VideoFiles.FOLDER)
        withContext(Dispatchers.IO) { folder.mkdirs() }
        val partFile = File(folder, ".${safeForFileName(job.jobId)}.part")
        val saved = when (val downloaded = generator.download(serviceKey, completed.contentUrls.first(), partFile)) {
            is VideoDownloadOutcome.Failed -> {
                partFile.delete()
                return ToolOutput.error(
                    "the video was made and charged but could not be downloaded: ${downloaded.message}",
                    "Job id: ${job.jobId}. Call generate_video with job_id=${job.jobId} to try the download again; that call makes no new charge. " +
                        "Tell the user if it fails twice.",
                )
            }
            is VideoDownloadOutcome.Saved -> downloaded
        }
        return withContext(Dispatchers.IO) { place(job, saved, partFile, folder, completed, context) }
    }

    private fun place(
        job: PendingVideoJob,
        saved: VideoDownloadOutcome.Saved,
        partFile: File,
        folder: File,
        completed: VideoCheckOutcome.Completed,
        context: ToolContext,
    ): ToolOutput {
        val header = partFile.inputStream().use { input -> input.readNBytes(VideoFiles.HEADER_BYTES) }
        val extension = VideoFiles.extensionFor(saved.mediaType, header)
        if (extension == null) {
            partFile.delete()
            return ToolOutput.error(
                "the service sent a file of type ${saved.mediaType.ifBlank { "unknown" }}, not an mp4 or webm video",
                "Nothing was saved. Job id: ${job.jobId}. The video was charged; call generate_video with job_id=${job.jobId} to try the download once more, and tell the user if it fails again.",
            )
        }
        val file = VideoFiles.freeFile(folder, job.baseName, extension)
        if (!partFile.renameTo(file)) {
            partFile.delete()
            return ToolOutput.error(
                "the video could not be saved in the thread folder",
                "Job id: ${job.jobId}. The video was charged; call generate_video with job_id=${job.jobId} to try again.",
            )
        }
        runCatching { PendingVideoJobs(context.threadFolder).remove(job.jobId) }
        return ToolOutput.success(describeResult(context.paths.relativePath(file), file, job, completed))
    }

    private fun describeResult(path: String, file: File, job: PendingVideoJob, completed: VideoCheckOutcome.Completed): String {
        val length = listOfNotNull(job.durationSeconds?.let { seconds -> "$seconds s" }, job.resolution)
            .joinToString(", ").ifEmpty { "not known" }
        val cost = completed.costUsd?.let { dollars -> String.format(Locale.ENGLISH, "$%.4f", dollars) } ?: "not reported by the service"
        return listOf(
            GeneratedVideos.firstLine(path),
            "Length: $length",
            "Size: ${IncomingFiles.describeSize(file.length())}",
            "Model: ${job.modelKey}",
            "Cost: $cost",
            "The user sees the video in the chat.",
        ).joinToString("\n")
    }

    /** The job id comes from the service; anything but letters, digits, "-" and "_" is replaced, so that it can never name a path. */
    private fun safeForFileName(jobId: String): String =
        jobId.map { character -> if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_' }
            .joinToString("").take(MAX_JOB_ID_IN_FILE_NAME)

    private fun forget(job: PendingVideoJob, context: ToolContext) {
        runCatching { PendingVideoJobs(context.threadFolder).remove(job.jobId) }
    }

    /** A job started less than [BLOCKS_NEW_VIDEO_FOR] ago, which a call without job_id must not forget. */
    private fun earlierJobBlocking(jobs: List<PendingVideoJob>, now: Long): PendingVideoJob? =
        jobs.filter { job -> now - job.startedAtMillis in 0 until BLOCKS_NEW_VIDEO_FOR.inWholeMilliseconds }
            .maxByOrNull { job -> job.startedAtMillis }

    private fun earlierJobText(job: PendingVideoJob, now: Long): ToolOutput {
        val minutesAgo = ((now - job.startedAtMillis) / 60_000L).coerceAtLeast(0)
        return ToolOutput.error(
            "an earlier video job of this thread is still waiting to be collected: ${job.jobId} (${job.modelKey}, started $minutesAgo min ago)",
            "No new video was started and nothing was charged. Call generate_video with job_id=${job.jobId} to collect it. " +
                "If the user really wants another new video as well, call again with new_video=true.",
        )
    }

    private fun endedPhrase(status: String): String = when (status) {
        "expired" -> "expired"
        "cancelled", "canceled" -> "was cancelled"
        else -> "failed"
    }

    /**
     * The added model the call names: the full "service:model" key, or just
     * the model id when only one added model has that id. Null when nothing
     * matches or the bare id is ambiguous.
     */
    private fun chooseModel(requested: String?): String? {
        val wanted = requested?.trim().orEmpty()
        if (wanted.isEmpty()) {
            return defaultModelKey()?.takeIf { it in modelKeys } ?: modelKeys.firstOrNull()
        }
        modelKeys.firstOrNull { modelKey -> modelKey.equals(wanted, ignoreCase = true) }?.let { return it }
        val sameId = modelKeys.filter { modelKey -> modelKey.substringAfter(':').equals(wanted, ignoreCase = true) }
        return sameId.singleOrNull()
    }

    private fun modelError(requested: String?): ToolOutput {
        if (modelKeys.isEmpty()) {
            return ToolOutput.error(
                "no video model is added",
                "Tell the user to add a video model under Settings > Models > Video generation.",
            )
        }
        return ToolOutput.error(
            "model ${requested.orEmpty().trim()} is not one of the user's video models, or more than one service has it",
            "Use one of: ${modelKeys.joinToString(", ")}. Or leave model out to use the starred one.",
        )
    }

    /** [jobId] is set when a job was already started, so the text can say that it is charged and how to collect it. */
    private fun failureText(kind: VideoFailure, message: String, modelKey: String, jobId: String?): ToolOutput {
        val said = message.trim().ifEmpty { "no reason given" }
        val serviceKey = modelKey.substringBefore(':')
        val jobNote = if (jobId == null) "" else " The job (id $jobId) may still be running; call generate_video with job_id=$jobId later to collect it."
        return when (kind) {
            VideoFailure.KEY_PROBLEM -> ToolOutput.error(
                "$serviceKey did not accept the request: $said",
                "Tell the user to save a working key for $serviceKey in Settings. Do not retry.$jobNote",
            )
            VideoFailure.OUT_OF_CREDIT -> ToolOutput.error(
                "$serviceKey has no credit or quota for this video: $said",
                "Tell the user to add credit or check the quota with $serviceKey. Do not retry.$jobNote",
            )
            VideoFailure.SERVICE_LIMIT -> ToolOutput.error(
                "$modelKey is over a limit at the service, not at the user's account: $said",
                if (jobId == null) {
                    "No video was made and nothing was charged. The user's key and credit are fine. " +
                        "Tell the user that, and that they can try again later or pick another video model. Do not retry now."
                } else {
                    "The user's key and credit are fine; the limit is the service's. Tell the user that.$jobNote"
                },
            )
            VideoFailure.BLOCKED -> ToolOutput.error(
                "$modelKey refused the prompt: $said",
                "Nothing was charged. Tell the user. Rephrase the prompt only if it can be done without the refused content; a retry can cost money.",
            )
            VideoFailure.TIMED_OUT -> ToolOutput.error(
                "$modelKey did not answer in time: $said",
                "Tell the user; try once more later, or another video model if they added one.$jobNote",
            )
            VideoFailure.OTHER -> ToolOutput.error(
                "video generation with $modelKey failed: $said",
                "Tell the user what the service said; try again later or with another video model.$jobNote",
            )
        }
    }

    companion object {
        val TIME_LIMIT: Duration = 8.minutes

        /** The tool returns this long after it started, which leaves 30 seconds of the limit. */
        val HAND_OVER_AFTER: Duration = 7.minutes + 30.seconds

        private val FAST_POLLING_FOR: Duration = 1.minutes
        private val FAST_POLL_INTERVAL: Duration = 10.seconds
        private val SLOW_POLL_INTERVAL: Duration = 20.seconds

        /** A few failed questions in a row end the wait; the job itself is not lost. */
        private const val MAX_FAILED_CHECKS_IN_A_ROW = 3

        private val BLOCKS_NEW_VIDEO_FOR: Duration = 60.minutes
        private const val MAX_JOB_ID_IN_FILE_NAME = 40
    }
}
