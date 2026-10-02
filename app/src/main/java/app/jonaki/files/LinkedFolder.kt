package app.jonaki.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The folder the user linked, as Settings shows it. */
data class LinkedFolderInfo(
    val treeUri: Uri,
    val name: String,
)

/**
 * The one folder per app the user linked in Settings (D-017, D-043). The
 * folder comes from Android's folder picker and Jonaki keeps a persisted
 * read and write grant to it, so no storage permission is needed.
 */
class LinkedFolder(private val context: Context) {
    private val preferences = context.getSharedPreferences("linked_folder", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    /** Null while no folder is linked. */
    val current: StateFlow<LinkedFolderInfo?> = state.asStateFlow()

    /** Keeps the grant the folder picker gave and replaces any folder linked before. */
    fun link(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        val previous = state.value
        if (previous != null && previous.treeUri != treeUri) {
            releaseGrant(previous.treeUri)
        }
        val info = LinkedFolderInfo(treeUri, nameOf(treeUri))
        preferences.edit()
            .putString(TREE_URI, treeUri.toString())
            .putString(NAME, info.name)
            .apply()
        state.value = info
    }

    fun unlink() {
        val previous = state.value ?: return
        releaseGrant(previous.treeUri)
        preferences.edit().clear().apply()
        state.value = null
    }

    /** False when the user took the grant back in system settings or the folder's app was removed. */
    fun isReachable(info: LinkedFolderInfo): Boolean =
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == info.treeUri && permission.isReadPermission && permission.isWritePermission
        }

    private fun read(): LinkedFolderInfo? {
        val uriText = preferences.getString(TREE_URI, null) ?: return null
        return LinkedFolderInfo(Uri.parse(uriText), preferences.getString(NAME, null).orEmpty())
    }

    private fun releaseGrant(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // The grant may already be gone; there is nothing more to release then.
        runCatching { context.contentResolver.releasePersistableUriPermission(treeUri, flags) }
    }

    /** The folder's own display name, for example "Documents"; the tree id when the provider gives none. */
    private fun nameOf(treeUri: Uri): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val name = runCatching {
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name ?: DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').substringAfterLast('/')
    }

    private companion object {
        const val TREE_URI = "tree_uri"
        const val NAME = "name"
    }
}
