package app.jonaki.ui

import android.content.Context
import app.jonaki.core.modelcatalog.ModelCatalog
import app.jonaki.run.ProjectFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.jonaki.core.modelcatalog.ModelKey
import app.jonaki.core.storage.JonakiDatabase
import app.jonaki.core.storage.ProjectEntity
import app.jonaki.feature.threads.ProjectDraft
import app.jonaki.feature.threads.ProjectUi
import java.util.UUID

/** Saving and deleting projects for the thread list (D-110). */
internal object Projects {
    fun uiOf(project: ProjectEntity, catalog: ModelCatalog): ProjectUi = ProjectUi(
        id = project.id,
        name = project.name,
        instructions = project.instructions,
        modelKey = project.modelKey,
        modelName = project.modelKey?.let { key -> catalog.find(key)?.displayName ?: ModelKey.modelOf(key) },
    )

    /** Adds a project when [projectId] is null, else changes it; returns the project's id. */
    suspend fun save(database: JonakiDatabase, projectId: String?, draft: ProjectDraft): String {
        val projectDao = database.projectDao()
        val existing = projectId?.let { id -> projectDao.find(id) }
        if (existing != null) {
            projectDao.update(existing.copy(name = draft.name, instructions = draft.instructions, modelKey = draft.modelKey))
            return existing.id
        }
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = draft.name,
            instructions = draft.instructions,
            modelKey = draft.modelKey,
            createdAtMillis = System.currentTimeMillis(),
        )
        projectDao.insert(project)
        return project.id
    }

    /**
     * Deletes the project, its shared folder and, unless [keepFacts], its
     * facts; kept facts become global (D-135). Its threads are cleared first,
     * so a stop between the steps leaves an empty project, never a thread
     * pointing at a project that is gone.
     */
    suspend fun delete(database: JonakiDatabase, context: Context, projectId: String, keepFacts: Boolean) {
        database.threadDao().clearProject(projectId)
        if (keepFacts) {
            database.memoryDao().makeProjectFactsGlobal(projectId)
        } else {
            database.memoryDao().deleteProjectFacts(projectId)
        }
        withContext(Dispatchers.IO) { ProjectFolders.delete(context, projectId) }
        database.projectDao().delete(projectId)
    }
}
