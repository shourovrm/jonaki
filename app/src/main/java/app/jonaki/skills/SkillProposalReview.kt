package app.jonaki.skills

import app.jonaki.core.skills.InstallResult
import app.jonaki.core.skills.ProposalResult
import app.jonaki.core.skills.SaveResult
import app.jonaki.core.skills.SkillLibrary
import app.jonaki.core.skills.SkillProposals
import app.jonaki.tools.proposeskill.SkillProposalOutcome
import app.jonaki.tools.proposeskill.SkillProposalSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gives the propose_skill tool the proposal store; the model never touches the library itself. */
class LibrarySkillProposalSink(private val proposals: SkillProposals) : SkillProposalSink {
    override val maxBodyLength: Int = SkillProposals.MAX_BODY_LENGTH
    override val maxWaiting: Int = SkillProposals.MAX_WAITING

    override suspend fun propose(name: String, description: String, body: String, replaces: String?): SkillProposalOutcome =
        withContext(Dispatchers.IO) {
            when (val result = proposals.propose(name, description, body, replaces)) {
                is ProposalResult.Saved -> SkillProposalOutcome.Saved(result.proposal.name, result.proposal.replaces)
                is ProposalResult.Refused -> SkillProposalOutcome.Refused(result.reason, result.nextStep)
            }
        }
}

sealed interface ProposalAddOutcome {
    data class Added(val name: String) : ProposalAddOutcome

    data class Failed(val reason: String) : ProposalAddOutcome
}

/**
 * What the user's Add and Discard do to a proposal. Add writes the skill
 * through the library's own install and save paths; nothing else ever does.
 */
class SkillProposalReview(private val library: SkillLibrary, private val proposals: SkillProposals) {

    fun add(name: String): ProposalAddOutcome {
        val proposal = proposals.find(name) ?: return ProposalAddOutcome.Failed("the proposal is gone")
        val markdown = proposals.skillMarkdownOf(proposal)
        val changesExistingSkill = proposal.replaces != null && library.readText(proposal.name) != null
        val failure = if (changesExistingSkill) saveOver(proposal.name, markdown) else installNew(markdown)
        if (failure != null) {
            return ProposalAddOutcome.Failed(failure)
        }
        proposals.discard(name)
        return ProposalAddOutcome.Added(proposal.name)
    }

    fun discard(name: String) {
        proposals.discard(name)
    }

    /** Replaces only SKILL.md, so the files the skill names stay. */
    private fun saveOver(name: String, markdown: String): String? =
        when (val result = library.saveText(name, markdown)) {
            SaveResult.Saved -> null
            is SaveResult.Invalid -> result.reason
        }

    private fun installNew(markdown: String): String? {
        val files = mapOf(SkillLibrary.SKILL_FILE to markdown.toByteArray())
        return when (val result = library.install(files, replace = false)) {
            is InstallResult.Installed -> null
            is InstallResult.AlreadyExists -> "a skill named ${result.name} already exists"
            is InstallResult.Invalid -> result.reason
        }
    }
}
