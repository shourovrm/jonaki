package app.jonaki.tools.schedule

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.TimeArguments
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Runs a prompt later in this thread, once, daily or weekly, and posts a
 * notification with the answer (plan M9 step 2). Creating and cancelling
 * need the user's approval; listing does not (D-M9-1).
 */
class ScheduleTool(
    private val scheduler: TaskScheduler,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now,
) : Tool {
    override val name: String = "schedule"

    override val promptLine: String =
        "schedule: run a prompt later in this thread (once, daily or weekly) and notify the user with the answer"

    override val guidelines: List<String> = listOf(
        "schedule's prompt runs later as a new message in this thread; write it as a full instruction. " +
            "Times are the user's local time; start times can be a few minutes late.",
        "For a plain alert at a time, a phone reminder is enough; schedule is for work the agent must do then.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    for (action in Action.entries) {
                        add(action.argument)
                    }
                }
            }
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "create: what the agent does at that time")
            }
            putJsonObject("at") {
                put("type", "string")
                put("description", "create: first run, local time, 2026-10-04T08:00 or 08:00 for the next 08:00")
            }
            putJsonObject("repeat") {
                put("type", "string")
                putJsonArray("enum") {
                    for (repeat in Repeat.entries) {
                        add(repeat.argument)
                    }
                }
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "create: a few words for the notification and the list")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "cancel: the task's id from list")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES

    override fun sideEffectOf(arguments: JsonObject): SideEffect =
        if (arguments.stringArgument("action") == Action.LIST.argument) SideEffect.READ_ONLY else SideEffect.CHANGES

    override val requiredCapabilities: Set<Capability> = emptySet()

    /** Long enough for the user to answer Android's notification permission dialog. */
    override val timeLimit: Duration = 3.minutes

    private enum class Action(val argument: String) {
        CREATE("create"),
        LIST("list"),
        CANCEL("cancel"),
    }

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val actionArgument = arguments.stringArgument("action")
        return when (Action.entries.firstOrNull { it.argument == actionArgument }) {
            Action.CREATE -> create(arguments)
            Action.LIST -> list()
            Action.CANCEL -> cancel(arguments.stringArgument("id")?.trim().orEmpty())
            null -> {
                val whatFailed = if (actionArgument == null) "argument action is missing" else "action $actionArgument is unknown"
                ToolOutput.error(whatFailed, "Use one of: create, list, cancel.")
            }
        }
    }

    private suspend fun create(arguments: JsonObject): ToolOutput {
        val prompt = arguments.stringArgument("prompt")?.trim().orEmpty()
        if (prompt.isEmpty()) {
            return ToolOutput.error("argument prompt is missing", "Give the instruction the agent should carry out at that time.")
        }
        val repeatArgument = arguments.stringArgument("repeat")
        val repeat = repeatFrom(repeatArgument)
            ?: return ToolOutput.error("repeat $repeatArgument is unknown", "Use none, daily or weekly.")
        val atArgument = arguments.stringArgument("at")?.trim().orEmpty()
        val now = clock()
        val at = TimeArguments.parse(atArgument, now)
            ?: return ToolOutput.error(
                if (atArgument.isEmpty()) "argument at is missing" else "at $atArgument is not a time",
                "Give a local time such as 2026-10-04T08:00, or 08:00 for the next 08:00.",
            )
        val anchor = at.toLocalDateTime()
        val firstRun = NextRun.after(anchor, repeat, now)
            ?: return ToolOutput.error("$atArgument has already passed", "Give a time in the future.")
        if (scheduler.list().size >= MAX_TASKS) {
            return ToolOutput.error(
                "there are already $MAX_TASKS scheduled tasks",
                "Cancel one first; use action list to see them.",
            )
        }
        val title = arguments.stringArgument("title")?.trim()?.takeIf { it.isNotEmpty() } ?: titleFrom(prompt)
        val created = scheduler.create(TaskRequest(title, prompt, anchor, repeat, firstRun))
        val task = created.task
        val notificationNote = if (created.notificationsOn) {
            "posts a notification with the answer"
        } else {
            "keeps the answer there; notifications are off for Jonaki, so the user is not alerted"
        }
        return ToolOutput.success(
            "Scheduled task ${task.id} \"${task.title}\": ${describeRepeat(task)}, first run ${formatTime(task.nextRun)}. " +
                "It runs in this thread and $notificationNote. Start times can be a few minutes late.",
        )
    }

    private suspend fun list(): ToolOutput {
        val tasks = scheduler.list()
        if (tasks.isEmpty()) {
            return ToolOutput.success("No scheduled tasks.")
        }
        val lines = tasks.map { task ->
            val where = if (task.isInThisThread) "this thread" else "another thread"
            "- ${task.id} \"${task.title}\": ${describeRepeat(task)}, next ${formatTime(task.nextRun)}, $where. " +
                "Prompt: ${shortened(task.prompt, PROMPT_PREVIEW_CHARACTERS)}"
        }
        return ToolOutput.success("Scheduled tasks:\n" + lines.joinToString("\n"))
    }

    private suspend fun cancel(taskId: String): ToolOutput {
        if (taskId.isEmpty()) {
            return ToolOutput.error("argument id is missing", "Use action list to see the task ids.")
        }
        if (!scheduler.cancel(taskId)) {
            return ToolOutput.error("no scheduled task has id $taskId", "Use action list to see the task ids.")
        }
        return ToolOutput.success("Cancelled task $taskId.")
    }

    /** A missing repeat means once; an unknown one is null. */
    private fun repeatFrom(argument: String?): Repeat? {
        if (argument.isNullOrBlank()) {
            return Repeat.NONE
        }
        return Repeat.fromArgument(argument)
    }

    private fun describeRepeat(task: ScheduledTask): String {
        val time = task.anchor.toLocalTime().format(TIME_OF_DAY)
        return when (task.repeat) {
            Repeat.NONE -> "once"
            Repeat.DAILY -> "daily at $time"
            Repeat.WEEKLY -> "every ${task.anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} at $time"
        }
    }

    private fun formatTime(time: ZonedDateTime): String = time.format(DATE_AND_TIME) + " (${time.zone.id})"

    private fun titleFrom(prompt: String): String = shortened(prompt.lineSequence().first(), TITLE_CHARACTERS)

    private fun shortened(text: String, limit: Int): String =
        if (text.length <= limit) text else text.take(limit - 1).trimEnd() + "…"

    private companion object {
        /** A guard against a model that schedules in a loop; a person rarely needs more. */
        const val MAX_TASKS = 20
        const val TITLE_CHARACTERS = 40
        const val PROMPT_PREVIEW_CHARACTERS = 120
        val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ENGLISH)
    }
}
