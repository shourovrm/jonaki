package app.jonaki.tools.proposeskill

import app.jonaki.core.toolapi.Capability
import app.jonaki.core.toolapi.SideEffect
import app.jonaki.core.toolapi.Tool
import app.jonaki.core.toolapi.ToolContext
import app.jonaki.core.toolapi.ToolOutput
import app.jonaki.core.toolapi.stringArgument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Where a proposal goes; the app keeps it outside the skill library until the user accepts it. */
interface SkillProposalSink {
    val maxBodyLength: Int

    /** Proposals that can wait for the user at one time. */
    val maxWaiting: Int

    suspend fun propose(name: String, description: String, body: String, replaces: String?): SkillProposalOutcome
}

sealed interface SkillProposalOutcome {
    /** [name] is the skill's name; [replaces] is the skill it would change, or null for a new skill. */
    data class Saved(val name: String, val replaces: String?) : SkillProposalOutcome

    data class Refused(val reason: String, val nextStep: String) : SkillProposalOutcome
}

/**
 * Suggests a skill for the user to review. Jonaki never creates skills on its
 * own: the proposal waits outside the library, so it is not listed in the
 * prompt and cannot be loaded, until the user adds it on the Skills screen.
 * That is why the tool runs without an approval card.
 */
class ProposeSkillTool(private val sink: SkillProposalSink) : Tool {
    override val name: String = "propose_skill"

    override val promptLine: String =
        "propose_skill: suggest a reusable skill for the user to review; nothing changes until the user adds it"

    override val guidelines: List<String> = listOf(
        "Propose a skill only after a task that took many steps, hit a dead end that was then solved, or was corrected by the user, " +
            "and that is likely to come up again. Make one proposal per task at most.",
        "First check the skills listed in this prompt. When one already covers the task, propose a change to it with replaces " +
            "set to its name instead of a new skill.",
        "The body is the SKILL.md instructions, at most ${formatted(sink.maxBodyLength)} characters. " +
            "Never put keys, passwords or personal data in a skill.",
        "When the tool saves the proposal, tell the user in one sentence that you proposed a skill and where to review it.",
    )

    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") {
                put("type", "string")
                put("description", "Skill name: lowercase letters, digits and single hyphens, for example bangla-formal-letter")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "One or two sentences that say when to use the skill; listed in every prompt")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "The SKILL.md instructions, without the front matter")
            }
            putJsonObject("replaces") {
                put("type", "string")
                put("description", "Name of an existing skill this would change; the skill keeps that name")
            }
        }
        putJsonArray("required") {
            add("name")
            add("description")
            add("body")
        }
    }

    override val sideEffect: SideEffect = SideEffect.CHANGES_APP_DATA
    override val requiredCapabilities: Set<Capability> = emptySet()
    override val timeLimit: Duration = 10.seconds

    override suspend fun run(arguments: JsonObject, context: ToolContext): ToolOutput {
        val skillName = argumentOrNull(arguments, "name")
            ?: return missing("name")
        val description = argumentOrNull(arguments, "description")
            ?: return missing("description")
        val body = argumentOrNull(arguments, "body")
            ?: return missing("body")
        val replaces = argumentOrNull(arguments, "replaces")
        return when (val outcome = sink.propose(skillName, description, body, replaces)) {
            is SkillProposalOutcome.Saved -> ToolOutput.success(savedText(outcome))
            is SkillProposalOutcome.Refused -> ToolOutput.error(outcome.reason, outcome.nextStep)
        }
    }

    private fun argumentOrNull(arguments: JsonObject, key: String): String? =
        arguments.stringArgument(key)?.trim()?.ifEmpty { null }

    private fun missing(key: String): ToolOutput =
        ToolOutput.error("argument $key is missing", "Call propose_skill again with name, description and body.")

    private fun savedText(saved: SkillProposalOutcome.Saved): String {
        val kind = if (saved.replaces == null) "a new skill" else "a change to the skill \"${saved.replaces}\""
        return "Saved a proposal for $kind named \"${saved.name}\". It is not active: it waits on the Skills screen " +
            "(Settings, or the chat menu) for the user to read and add or discard. " +
            "Tell the user in one sentence that you proposed a skill and where to review it, and propose nothing more for this task."
    }

    private fun formatted(number: Int): String = "%,d".format(java.util.Locale.ROOT, number)
}
