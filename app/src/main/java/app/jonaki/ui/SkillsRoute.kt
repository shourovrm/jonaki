package app.jonaki.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.jonaki.JonakiApplication
import app.jonaki.core.skills.SaveResult
import app.jonaki.core.skills.SkillEntry
import app.jonaki.feature.skills.ImportUi
import app.jonaki.feature.skills.SkillEditorActions
import app.jonaki.feature.skills.SkillEditorScreen
import app.jonaki.feature.skills.SkillEditorUiState
import app.jonaki.feature.skills.SkillRowUi
import app.jonaki.feature.skills.SkillsActions
import app.jonaki.feature.skills.SkillsScreen
import app.jonaki.feature.skills.SkillsUiState
import app.jonaki.skills.ImportOutcome
import app.jonaki.skills.PickedFiles
import app.jonaki.skills.ThreadSkills
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The skill library, or one thread's skill switches when [threadId] is set
 * (opened from the chat's ⋮ menu).
 */
@Composable
internal fun SkillsRoute(
    application: JonakiApplication,
    threadId: String?,
    onBack: () -> Unit,
    onOpenSkill: (name: String) -> Unit,
) {
    val library = application.skillLibrary
    val importer = application.skillImporter
    val scope = rememberCoroutineScope()
    val contentResolver = LocalContext.current.contentResolver
    var entries by remember { mutableStateOf<List<SkillEntry>>(emptyList()) }
    var deletedBuiltIns by remember { mutableStateOf<List<String>>(emptyList()) }
    // The library is plain files with no change feed, so the list is read again after each change.
    var reloadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadCount) {
        withContext(Dispatchers.IO) {
            entries = library.list()
            deletedBuiltIns = library.deletedBuiltIns()
        }
    }
    val thread by remember(threadId) {
        if (threadId == null) flowOf(null) else application.database.threadDao().observe(threadId)
    }.collectAsState(initial = null)
    var importUi by remember { mutableStateOf(ImportUi()) }
    var filesWaitingForReplace by remember { mutableStateOf<Map<String, ByteArray>?>(null) }

    fun show(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Added -> {
                importUi = ImportUi()
                filesWaitingForReplace = null
                reloadCount++
            }
            is ImportOutcome.NameTaken -> {
                filesWaitingForReplace = outcome.files
                importUi = ImportUi(replaceName = outcome.name)
            }
            is ImportOutcome.Failed -> importUi = ImportUi(isOpen = true, error = outcome.reason)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importUi = importUi.copy(isWorking = true, error = null)
            val picked = withContext(Dispatchers.IO) { PickedFiles.read(contentResolver, uri) }
            when (picked) {
                is PickedFiles.ReadResult.Failed -> show(ImportOutcome.Failed(picked.reason))
                is PickedFiles.ReadResult.Bytes -> show(importer.fromFile(picked.bytes))
            }
        }
    }

    val disabled = ThreadSkills.disabledNames(thread?.disabledSkills.orEmpty())
    val state = SkillsUiState(
        threadTitle = if (threadId == null) null else thread?.title.orEmpty(),
        skills = entries.map { entry ->
            SkillRowUi(
                name = entry.name,
                description = entry.description,
                problem = entry.problem,
                isBuiltIn = entry.isBuiltIn,
                isEdited = entry.isEdited,
                enabledInThread = entry.name !in disabled,
            )
        },
        canRestoreBuiltIns = deletedBuiltIns.isNotEmpty(),
        import = importUi,
    )
    val actions = SkillsActions(
        onBack = onBack,
        onOpenSkill = onOpenSkill,
        onEnabledChange = { name, enabled ->
            if (threadId != null) {
                scope.launch { application.runner.setSkillEnabled(threadId, name, enabled) }
            }
        },
        onRestoreBuiltIns = {
            scope.launch {
                withContext(Dispatchers.IO) { library.restoreBuiltIns(application.builtInSkills()) }
                reloadCount++
            }
        },
        onOpenImport = { importUi = ImportUi(isOpen = true) },
        onCloseImport = {
            importUi = ImportUi()
            filesWaitingForReplace = null
        },
        onImportLink = { link ->
            scope.launch {
                importUi = importUi.copy(isWorking = true, error = null)
                show(importer.fromLink(link))
            }
        },
        // Any type: a SKILL.md often has no Markdown type on Android, and a zip may come as a .skill file.
        onChooseFile = { filePicker.launch(arrayOf("*/*")) },
        onConfirmReplace = {
            val files = filesWaitingForReplace
            if (files != null) {
                scope.launch { show(importer.replace(files)) }
            }
        },
    )
    SkillsScreen(state = state, actions = actions)
}

/** One skill's SKILL.md in an editor; Save, Reset to built-in and Delete write to the library. */
@Composable
internal fun SkillEditorRoute(application: JonakiApplication, name: String, onBack: () -> Unit) {
    val library = application.skillLibrary
    val scope = rememberCoroutineScope()
    var savedText by remember(name) { mutableStateOf<String?>(null) }
    var entry by remember(name) { mutableStateOf<SkillEntry?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    var reloadCount by remember(name) { mutableIntStateOf(0) }
    LaunchedEffect(name, reloadCount) {
        val text = withContext(Dispatchers.IO) { library.readText(name) }
        if (text == null) {
            onBack()
            return@LaunchedEffect
        }
        entry = withContext(Dispatchers.IO) { library.list().firstOrNull { skill -> skill.name == name } }
        savedText = text
    }
    val state = SkillEditorUiState(
        name = name,
        savedText = savedText,
        isBuiltIn = entry?.isBuiltIn == true,
        isEdited = entry?.isEdited == true,
        error = error,
    )
    val actions = SkillEditorActions(
        onBack = onBack,
        onSave = { text ->
            scope.launch {
                when (val result = withContext(Dispatchers.IO) { library.saveText(name, text) }) {
                    SaveResult.Saved -> {
                        error = null
                        reloadCount++
                    }
                    is SaveResult.Invalid -> error = result.reason
                }
            }
        },
        onDelete = {
            scope.launch {
                withContext(Dispatchers.IO) { library.delete(name) }
                onBack()
            }
        },
        onReset = {
            scope.launch {
                val builtIn = withContext(Dispatchers.IO) {
                    application.builtInSkills().firstOrNull { skill -> skill.name == name }
                } ?: return@launch
                withContext(Dispatchers.IO) { library.resetBuiltIn(builtIn) }
                error = null
                reloadCount++
            }
        },
    )
    SkillEditorScreen(state = state, actions = actions)
}
