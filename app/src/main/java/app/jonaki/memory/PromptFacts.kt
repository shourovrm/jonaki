package app.jonaki.memory

import app.jonaki.core.agent.MemorySection
import app.jonaki.core.agent.PromptFact
import app.jonaki.core.agent.PromptFactScope
import app.jonaki.core.storage.MemoryEntity
import app.jonaki.tools.memory.FactScope
import java.time.Instant
import java.time.ZoneId

/** Saved facts as the prompt's memory section and the context sheet need them. */
object PromptFacts {
    /** [zone] turns the fact's last change into the local day the model reads beside it. */
    fun of(memory: MemoryEntity, zone: ZoneId): PromptFact = PromptFact(
        id = memory.id,
        text = memory.text,
        scope = promptScopeOf(memory),
        pinned = memory.pinned,
        lastUsedAtMillis = memory.lastUsedAtMillis,
        savedOn = Instant.ofEpochMilli(memory.updatedAtMillis).atZone(zone).toLocalDate().toString(),
    )

    private fun promptScopeOf(memory: MemoryEntity): PromptFactScope = when (RoomMemoryStore.scopeOf(memory)) {
        FactScope.GLOBAL -> PromptFactScope.GLOBAL
        FactScope.PROJECT -> PromptFactScope.PROJECT
        FactScope.THREAD -> PromptFactScope.THREAD
    }

    /** The ids of a run's memory section as one text for messages.promptFactIds; null when the section was empty. */
    fun idsText(ids: List<Long>): String? = if (ids.isEmpty()) null else ids.joinToString(",")

    fun idsOf(idsText: String?): List<Long> =
        idsText.orEmpty().split(',').mapNotNull { part -> part.trim().toLongOrNull() }

    /**
     * The facts the context sheet lists: those of the thread's last run when
     * one is recorded ([lastRunIds]), else those the next request would send
     * ([nextRequestIds]). A fact deleted since the run is left out.
     */
    fun sheetLines(facts: List<PromptFact>, lastRunIds: List<Long>?, nextRequestIds: List<Long>): List<String> {
        val shownIds = (lastRunIds ?: nextRequestIds).toSet()
        return facts
            .filter { fact -> fact.id in shownIds }
            .sortedBy { fact -> fact.id }
            .map(MemorySection::displayLineOf)
    }
}
