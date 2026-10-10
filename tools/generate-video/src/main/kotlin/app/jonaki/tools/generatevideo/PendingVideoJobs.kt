package app.jonaki.tools.generatevideo

import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A video job that was started in this thread and is not yet collected or ended. */
data class PendingVideoJob(
    val threadId: String,
    val jobId: String,
    /** "service:model", for example "openrouter:google/veo-3.1-lite". */
    val modelKey: String,
    val startedAtMillis: Long,
    val durationSeconds: Int?,
    val resolution: String?,
    /** The file name the video gets when it is collected, without ending. */
    val baseName: String,
    /** True once the job's cost was saved on the thread, so that collecting again does not count it twice. */
    val costRecorded: Boolean = false,
)

/**
 * The jobs in `videos/pending-jobs.json` of one thread folder. A job goes on
 * (and is charged) at the service when the tool is stopped, and a stopped
 * tool can return no text, so the id is written here the moment the job
 * starts. The file is the thread's own state, readable by anyone who reads
 * the thread folder.
 */
class PendingVideoJobs(threadFolder: File) {
    private val file = File(File(threadFolder, VideoFiles.FOLDER), VideoFiles.PENDING_FILE)

    fun all(): List<PendingVideoJob> {
        val root = runCatching { Json.parseToJsonElement(file.readText()) as? JsonArray }.getOrNull() ?: return emptyList()
        return root.mapNotNull { element -> jobFrom(element as? JsonObject) }
    }

    fun find(jobId: String): PendingVideoJob? = all().firstOrNull { job -> job.jobId == jobId }

    /** Throws [IOException] when the file cannot be written. */
    fun add(job: PendingVideoJob) = write(all().filter { it.jobId != job.jobId } + job)

    fun markCostRecorded(jobId: String) = write(all().map { job -> if (job.jobId == jobId) job.copy(costRecorded = true) else job })

    fun remove(jobId: String) = write(all().filter { job -> job.jobId != jobId })

    private fun write(jobs: List<PendingVideoJob>) {
        if (jobs.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        // Written beside the file and moved over it, so that a crash never leaves half a file.
        val temporary = File(file.parentFile, "${VideoFiles.PENDING_FILE}.tmp")
        temporary.writeText(buildJsonArray { jobs.forEach { job -> add(jsonOf(job)) } }.toString())
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) {
                throw IOException("could not write ${file.name}")
            }
        }
    }

    private fun jsonOf(job: PendingVideoJob): JsonObject = buildJsonObject {
        put("threadId", job.threadId)
        put("jobId", job.jobId)
        put("model", job.modelKey)
        put("startedAt", job.startedAtMillis)
        job.durationSeconds?.let { put("durationSeconds", it) }
        job.resolution?.let { put("resolution", it) }
        put("baseName", job.baseName)
        put("costRecorded", job.costRecorded)
    }

    private fun jobFrom(entry: JsonObject?): PendingVideoJob? {
        if (entry == null) return null
        val jobId = entry.text("jobId") ?: return null
        val modelKey = entry.text("model") ?: return null
        return PendingVideoJob(
            threadId = entry.text("threadId").orEmpty(),
            jobId = jobId,
            modelKey = modelKey,
            startedAtMillis = (entry["startedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
            durationSeconds = (entry["durationSeconds"] as? JsonPrimitive)?.intOrNull,
            resolution = entry.text("resolution"),
            baseName = entry.text("baseName") ?: "video",
            costRecorded = (entry["costRecorded"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
