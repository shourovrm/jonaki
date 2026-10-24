package app.jonaki.memory

import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.feature.memory.MemoryLogRowUi

/** Reads the memory log: every time Jonaki read a thread for facts, newest first. */
object MemoryLog {
    const val MAX_ROWS = 100

    private val successPatterns = listOf(
        Regex("""\d+ added"""),
        Regex("""\d+ to review"""),
        Regex("""\d+ added, \d+ to review"""),
        Regex("Nothing new"),
    )

    /** [modelName] turns a model key into the name shown; the key's model part is used when it is not given. */
    suspend fun load(
        database: JonakiDatabase,
        modelName: (modelKey: String) -> String = { modelKey -> ModelKey.modelOf(modelKey) },
    ): List<MemoryLogRowUi> =
        database.messageDao().listMemoryLog(MAX_ROWS).map { row ->
            MemoryLogRowUi(
                threadTitle = row.threadTitle,
                atMillis = row.createdAtMillis,
                modelName = row.model?.let(modelName).orEmpty(),
                result = row.backgroundResult.orEmpty(),
                isFailure = row.backgroundResult != null && !isSuccess(row.backgroundResult!!),
            )
        }

    internal fun isSuccess(result: String): Boolean = successPatterns.any { pattern -> pattern.matches(result) }
}
