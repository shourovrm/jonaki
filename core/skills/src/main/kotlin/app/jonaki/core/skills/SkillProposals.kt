package app.jonaki.core.skills

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A skill the agent suggests and the user has not yet accepted. It lives in
 * [SkillProposals]' own folder, never in the library, so it is not listed in
 * the prompt and the model cannot load it.
 */
data class SkillProposal(
    /** The skill's name; for a change, the name of the skill it changes. */
    val name: String,
    val description: String,
    /** The instructions that follow the front matter in SKILL.md. */
    val body: String,
    /** The name of the existing skill this proposal would change; null for a new skill. */
    val replaces: String?,
    val createdAtMillis: Long,
)

sealed interface ProposalResult {
    data class Saved(val proposal: SkillProposal) : ProposalResult

    /** [reason] says what is wrong; [nextStep] says what the model can do instead. Both are text for the model. */
    data class Refused(val reason: String, val nextStep: String) : ProposalResult
}

/**
 * The skill proposals waiting for the user, one JSON file per proposal in
 * [folder]. Nothing here changes the library: the user's Add is what writes
 * a skill, through the library's own install or save path.
 *
 * [skillExists] tells whether the library holds a skill of that name, so that
 * a change can be checked against it and a new skill cannot take a taken name.
 */
class SkillProposals(
    private val folder: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val skillExists: (name: String) -> Boolean = { false },
) {

    /** Waiting proposals, oldest first. A file that cannot be read is left out. */
    @Synchronized
    fun list(): List<SkillProposal> {
        val files = folder.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }.orEmpty()
        return files.mapNotNull { file -> readProposal(file) }.sortedBy { proposal -> proposal.createdAtMillis }
    }

    @Synchronized
    fun find(name: String): SkillProposal? = fileOf(name)?.let { file -> if (file.isFile) readProposal(file) else null }

    /**
     * Saves a proposal. With [replaces] set the proposal changes that skill
     * and takes its name, whatever [name] says. A proposal that has the name
     * of one already waiting replaces it and does not count towards the limit.
     */
    @Synchronized
    fun propose(name: String, description: String, body: String, replaces: String?): ProposalResult {
        val skillName = (replaces ?: name).trim()
        val refusal = refusalFor(skillName, description.trim(), body.trim(), isChange = replaces != null)
        if (refusal != null) {
            return refusal
        }
        val replacesWaitingOne = find(skillName) != null
        if (!replacesWaitingOne && list().size >= MAX_WAITING) {
            return ProposalResult.Refused(
                "$MAX_WAITING proposals are already waiting for the user's review",
                "Wait until the user has reviewed them, and do not propose another skill in this thread.",
            )
        }
        val proposal = SkillProposal(skillName, description.trim(), body.trim(), replaces?.trim(), clock())
        folder.mkdirs()
        File(folder, skillName + FILE_SUFFIX).writeText(toJson(proposal).toString())
        return ProposalResult.Saved(proposal)
    }

    /** Deletes a proposal; an unknown or invalid name changes nothing. */
    @Synchronized
    fun discard(name: String) {
        fileOf(name)?.delete()
    }

    /** The text of the SKILL.md that Add writes into the library. */
    fun skillMarkdownOf(proposal: SkillProposal): String =
        "---\nname: ${proposal.name}\ndescription: ${quoted(proposal.description)}\n---\n\n${proposal.body}\n"

    private fun refusalFor(skillName: String, description: String, body: String, isChange: Boolean): ProposalResult.Refused? {
        if (!SkillFrontMatter.isValidName(skillName)) {
            return ProposalResult.Refused(
                "the name must be a-z, 0-9 and single hyphens, up to ${SkillFrontMatter.MAX_NAME_LENGTH} characters",
                "Call propose_skill again with a name such as \"bangla-formal-letter\".",
            )
        }
        if (isChange && !skillExists(skillName)) {
            return ProposalResult.Refused(
                "there is no skill named $skillName to change",
                "Check the skill names in the prompt, or propose it as a new skill without replaces.",
            )
        }
        if (!isChange && skillExists(skillName)) {
            return ProposalResult.Refused(
                "a skill named $skillName already exists",
                "Propose a change to it with replaces set to \"$skillName\", or choose another name.",
            )
        }
        if (description.isEmpty()) {
            return ProposalResult.Refused("the description is empty", "Give one or two sentences that say when to use the skill.")
        }
        if (description.length > SkillFrontMatter.MAX_DESCRIPTION_LENGTH) {
            return ProposalResult.Refused(
                "the description is over ${SkillFrontMatter.MAX_DESCRIPTION_LENGTH} characters",
                "Shorten the description; it is listed in every prompt.",
            )
        }
        if (body.isEmpty()) {
            return ProposalResult.Refused("the body is empty", "Write the skill's instructions as the body.")
        }
        if (body.length > MAX_BODY_LENGTH) {
            return ProposalResult.Refused(
                "the body is ${body.length} characters, over the limit of $MAX_BODY_LENGTH",
                "Shorten the instructions to what the next run needs.",
            )
        }
        return null
    }

    /** A double-quoted YAML value, which the library's front matter reader understands. */
    private fun quoted(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun fileOf(name: String): File? =
        if (SkillFrontMatter.isValidName(name)) File(folder, name + FILE_SUFFIX) else null

    private fun toJson(proposal: SkillProposal): JsonObject = buildJsonObject {
        put("name", proposal.name)
        put("description", proposal.description)
        put("body", proposal.body)
        if (proposal.replaces != null) {
            put("replaces", proposal.replaces)
        }
        put("createdAtMillis", proposal.createdAtMillis)
    }

    private fun readProposal(file: File): SkillProposal? {
        val fields = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return null
        val name = text(fields, "name") ?: return null
        val description = text(fields, "description") ?: return null
        val body = text(fields, "body") ?: return null
        val createdAtMillis = (fields["createdAtMillis"] as? JsonPrimitive)?.longOrNull ?: return null
        // The file's own name must match, so a copied or renamed file cannot show under another name.
        if (file.name != name + FILE_SUFFIX || !SkillFrontMatter.isValidName(name)) {
            return null
        }
        return SkillProposal(name, description, body, text(fields, "replaces"), createdAtMillis)
    }

    private fun text(fields: JsonObject, key: String): String? = fields[key]?.jsonPrimitive?.contentOrNull

    companion object {
        /** The body is the part that costs tokens when a skill is read, so it stays short. */
        const val MAX_BODY_LENGTH = 6_000

        /** More would pile up unreviewed; a sixth is refused until the user reviews one. */
        const val MAX_WAITING = 5

        private const val FILE_SUFFIX = ".json"
    }
}
