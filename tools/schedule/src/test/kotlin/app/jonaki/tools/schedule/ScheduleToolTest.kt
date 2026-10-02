package app.jonaki.tools.schedule

import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleToolTest {
    private class FakeScheduler : TaskScheduler {
        val tasks = mutableListOf<ScheduledTask>()
        var notificationsOn = true

        override suspend fun create(request: TaskRequest): TaskCreated {
            val task = ScheduledTask(
                id = "task${tasks.size + 1}",
                title = request.title,
                prompt = request.prompt,
                repeat = request.repeat,
                anchor = request.anchor,
                nextRun = request.firstRun,
                isInThisThread = true,
            )
            tasks += task
            return TaskCreated(task, notificationsOn)
        }

        override suspend fun list(): List<ScheduledTask> = tasks.toList()

        override suspend fun cancel(taskId: String): Boolean = tasks.removeIf { task -> task.id == taskId }
    }

    private val dhaka = ZoneId.of("Asia/Dhaka")

    /** Saturday 3 October 2026, 14:30. */
    private val now = ZonedDateTime.of(2026, 10, 3, 14, 30, 0, 0, dhaka)
    private val scheduler = FakeScheduler()
    private val tool = ScheduleTool(scheduler, clock = { now })
    private val context = ToolContext(Files.createTempDirectory("thread").toFile(), OkHttpClient())

    private fun run(vararg arguments: Pair<String, String>): ToolOutput = runBlocking {
        tool.run(JsonObject(arguments.associate { (key, value) -> key to JsonPrimitive(value) }), context)
    }

    @Test
    fun aDailyTaskAtATimeOfDayStartsAtTheNextSuchTime() {
        val output = run("action" to "create", "prompt" to "Summarise the news on X", "at" to "08:00", "repeat" to "daily")

        assertFalse(output.text, output.isError)
        val task = scheduler.tasks.single()
        assertEquals(ZonedDateTime.of(2026, 10, 4, 8, 0, 0, 0, dhaka), task.nextRun)
        assertEquals(Repeat.DAILY, task.repeat)
        assertTrue(output.text, output.text.contains("daily at 08:00, first run Sun 4 Oct 2026 08:00 (Asia/Dhaka)"))
    }

    @Test
    fun aMissingTitleIsTakenFromThePrompt() {
        run("action" to "create", "prompt" to "Check the weather in Dhaka and tell me if I need an umbrella", "at" to "08:00")

        assertEquals("Check the weather in Dhaka and tell me…", scheduler.tasks.single().title)
    }

    @Test
    fun aOneOffTaskInThePastIsRefused() {
        val output = run("action" to "create", "prompt" to "Ping", "at" to "2026-10-03T09:00")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("has already passed"))
        assertTrue(scheduler.tasks.isEmpty())
    }

    @Test
    fun aDailyTaskWithAPastFirstDayStartsAtTheNextRun() {
        run("action" to "create", "prompt" to "Ping", "at" to "2026-10-01T09:00", "repeat" to "daily")

        assertEquals(ZonedDateTime.of(2026, 10, 4, 9, 0, 0, 0, dhaka), scheduler.tasks.single().nextRun)
    }

    @Test
    fun anUnreadableTimeSaysWhichFormsWork() {
        val output = run("action" to "create", "prompt" to "Ping", "at" to "tomorrow")

        assertTrue(output.isError)
        assertTrue(output.text, output.text.contains("2026-10-04T08:00"))
    }

    @Test
    fun anUnknownRepeatIsAnError() {
        val output = run("action" to "create", "prompt" to "Ping", "at" to "08:00", "repeat" to "hourly")

        assertTrue(output.isError)
        assertTrue(scheduler.tasks.isEmpty())
    }

    @Test
    fun theResultSaysWhenNotificationsAreOff() {
        scheduler.notificationsOn = false

        val output = run("action" to "create", "prompt" to "Ping", "at" to "08:00")

        assertTrue(output.text, output.text.contains("notifications are off"))
    }

    @Test
    fun listShowsIdsAndNextRuns() {
        run("action" to "create", "prompt" to "Ping", "at" to "08:00", "repeat" to "weekly", "title" to "Weekly ping")

        val output = run("action" to "list")

        assertTrue(output.text, output.text.contains("task1 \"Weekly ping\": every Sunday at 08:00, next Sun 4 Oct 2026 08:00"))
    }

    @Test
    fun cancelRemovesTheTaskAndAnUnknownIdIsAnError() {
        run("action" to "create", "prompt" to "Ping", "at" to "08:00")

        assertFalse(run("action" to "cancel", "id" to "task1").isError)
        assertTrue(scheduler.tasks.isEmpty())
        assertTrue(run("action" to "cancel", "id" to "task1").isError)
    }

    @Test
    fun onlyListRunsWithoutApproval() {
        fun costOf(action: String) = tool.sideEffectOf(JsonObject(mapOf("action" to JsonPrimitive(action))))

        assertEquals(SideEffect.READ_ONLY, costOf("list"))
        assertEquals(SideEffect.CHANGES, costOf("create"))
        assertEquals(SideEffect.CHANGES, costOf("cancel"))
    }
}
