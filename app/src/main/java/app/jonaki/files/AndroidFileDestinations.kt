package app.jonaki.files

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import app.jonaki.core.toolapi.FileTooLargeException
import app.jonaki.core.toolapi.IncomingFiles
import app.jonaki.feature.artifact.StandaloneHtml
import app.jonaki.tools.sharefile.DestinationResult
import app.jonaki.tools.sharefile.FileDestinations
import app.jonaki.tools.sharefile.LinkedEntry
import app.jonaki.tools.sharefile.LinkedListing
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * share_file's destinations on Android: MediaStore for Downloads/Jonaki on
 * Android 10 and later, the "Save as" picker and the share sheet through
 * [VisibleActivity], and the linked folder through its persisted tree grant.
 * None of these needs an Android permission.
 */
class AndroidFileDestinations(
    private val context: Context,
    private val visibleActivity: VisibleActivity,
    private val linkedFolder: LinkedFolder,
) : FileDestinations {
    private val resolver: ContentResolver = context.contentResolver

    override suspend fun saveToDownloads(file: File): DestinationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // MediaStore.Downloads starts at Android 10; earlier phones would need a storage permission (D-044).
            return saveAs(file)
        }
        return withContext(Dispatchers.IO) { insertIntoDownloads(outgoing(file)) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoDownloads(file: File): DestinationResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(file))
            put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_SUBFOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return DestinationResult.Failed("Android did not create the file in Downloads")
        return try {
            copyInto(file, uri)
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            // MediaStore renames a taken name to "name (1).ext", so the final name is read back.
            DestinationResult.Done("Downloads/Jonaki/${displayNameOf(uri) ?: file.name}")
        } catch (failure: IOException) {
            resolver.delete(uri, null, null)
            DestinationResult.Failed(failure.message ?: "writing to Downloads failed")
        }
    }

    override suspend fun saveAs(original: File): DestinationResult {
        val file = withContext(Dispatchers.IO) { outgoing(original) }
        val answer = visibleActivity.launchForResult(ActivityResultContracts.CreateDocument(mimeTypeOf(file)), file.name)
        val uri = when (answer) {
            is VisibleActivity.Answer.Result -> answer.value ?: return DestinationResult.Cancelled
            VisibleActivity.Answer.NotOnScreen -> return DestinationResult.AppNotOnScreen
            VisibleActivity.Answer.NoAppToHandle -> return DestinationResult.Failed(NO_APP)
        }
        return withContext(Dispatchers.IO) {
            try {
                copyInto(file, uri)
                DestinationResult.Done("${displayNameOf(uri) ?: file.name}, in the place the user picked")
            } catch (failure: IOException) {
                DestinationResult.Failed(failure.message ?: "writing the file failed")
            } catch (failure: SecurityException) {
                DestinationResult.Failed("the picked place cannot be written")
            }
        }
    }

    override suspend fun share(original: File): DestinationResult {
        val file = withContext(Dispatchers.IO) { outgoing(original) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeOf(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return when (visibleActivity.start(Intent.createChooser(send, null))) {
            is VisibleActivity.Answer.Result -> DestinationResult.Done("the share sheet")
            VisibleActivity.Answer.NotOnScreen -> DestinationResult.AppNotOnScreen
            VisibleActivity.Answer.NoAppToHandle -> DestinationResult.Failed(NO_APP)
        }
    }

    override suspend fun copyToLinkedFolder(original: File): DestinationResult = withLinkedFolder { info ->
        val file = outgoing(original)
        val rootUri = documentUri(info, DocumentsContract.getTreeDocumentId(info.treeUri))
        val created = DocumentsContract.createDocument(resolver, rootUri, mimeTypeOf(file), file.name)
            ?: return@withLinkedFolder DestinationResult.Failed("the folder's app did not create the file")
        try {
            copyInto(file, created)
        } catch (failure: IOException) {
            DocumentsContract.deleteDocument(resolver, created)
            throw failure
        }
        // The folder's app renames a taken name, so the final name is read back.
        DestinationResult.Done("the linked folder ${info.name}/${displayNameOf(created) ?: file.name}")
    }

    override suspend fun listLinkedFolder(folderPath: String): LinkedListing {
        var entries: List<LinkedEntry> = emptyList()
        var folderName = ""
        val result = withLinkedFolder { info ->
            folderName = info.name
            val folder = findDocument(info, folderPath)
            if (folder == null || !folder.isFolder) {
                return@withLinkedFolder DestinationResult.NotFound
            }
            val prefix = if (folderPath.isEmpty()) "" else "$folderPath/"
            entries = childrenOf(info, folder.id)
                .sortedWith(compareBy<LinkedDocument>({ !it.isFolder }, { it.name.lowercase() }))
                .map { child -> LinkedEntry(prefix + child.name, child.isFolder, child.sizeBytes) }
            DestinationResult.Done(info.name)
        }
        if (result !is DestinationResult.Done) {
            return LinkedListing.Failed(result)
        }
        return LinkedListing.Entries(folderName, entries)
    }

    override suspend fun copyFromLinkedFolder(path: String, target: File): DestinationResult = withLinkedFolder { info ->
        val document = findDocument(info, path) ?: return@withLinkedFolder DestinationResult.NotFound
        if (document.isFolder) {
            return@withLinkedFolder DestinationResult.Failed("it is a folder; list it with list_linked")
        }
        val knownSize = document.sizeBytes
        if (knownSize != null && knownSize > IncomingFiles.MAX_IMPORT_BYTES) {
            return@withLinkedFolder DestinationResult.TooLarge(IncomingFiles.MAX_IMPORT_BYTES)
        }
        val input = resolver.openInputStream(documentUri(info, document.id))
            ?: return@withLinkedFolder DestinationResult.Failed("the file could not be opened")
        try {
            input.use { stream -> IncomingFiles.copyWithLimit(stream, target) }
        } catch (tooLarge: FileTooLargeException) {
            return@withLinkedFolder DestinationResult.TooLarge(tooLarge.limitBytes)
        }
        DestinationResult.Done(path)
    }

    /** Runs [action] on the IO dispatcher with the linked folder, turning a lost grant into its own answer. */
    private suspend fun withLinkedFolder(action: (LinkedFolderInfo) -> DestinationResult): DestinationResult =
        withContext(Dispatchers.IO) {
            val info = linkedFolder.current.value ?: return@withContext DestinationResult.NoLinkedFolder
            if (!linkedFolder.isReachable(info)) {
                return@withContext DestinationResult.LinkedFolderGone
            }
            try {
                action(info)
            } catch (lost: SecurityException) {
                DestinationResult.LinkedFolderGone
            } catch (failure: IOException) {
                DestinationResult.Failed(failure.message ?: "reading or writing the linked folder failed")
            } catch (failure: IllegalArgumentException) {
                // Document providers throw this for a document that disappeared.
                DestinationResult.LinkedFolderGone
            }
        }

    private class LinkedDocument(val id: String, val name: String, val isFolder: Boolean, val sizeBytes: Long?)

    /** Walks [path] one name at a time from the linked folder's top; "" is the top itself. */
    private fun findDocument(info: LinkedFolderInfo, path: String): LinkedDocument? {
        val rootId = DocumentsContract.getTreeDocumentId(info.treeUri)
        var current = LinkedDocument(rootId, info.name, isFolder = true, sizeBytes = null)
        for (name in path.split('/').filter { it.isNotEmpty() }) {
            if (!current.isFolder) {
                return null
            }
            current = childrenOf(info, current.id).firstOrNull { child -> child.name == name } ?: return null
        }
        return current
    }

    private fun childrenOf(info: LinkedFolderInfo, parentId: String): List<LinkedDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(info.treeUri, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        val children = mutableListOf<LinkedDocument>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val isFolder = cursor.getString(2) == Document.MIME_TYPE_DIR
                val size = if (isFolder || cursor.isNull(3)) null else cursor.getLong(3)
                children += LinkedDocument(cursor.getString(0), cursor.getString(1).orEmpty(), isFolder, size)
            }
        }
        return children
    }

    private fun documentUri(info: LinkedFolderInfo, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(info.treeUri, documentId)

    /**
     * The file as it should leave the app: an HTML page that loads the
     * viewer's lib/chart.js gets a copy with Chart.js inside, so its charts
     * work in any browser (D-047). The copy sits in a hidden folder next to
     * the original, inside threads/, where the FileProvider can serve it.
     */
    private fun outgoing(file: File): File {
        if (!file.name.endsWith(".html", ignoreCase = true)) {
            return file
        }
        val standalone = StandaloneHtml.withLibraries(file.readText()) {
            context.assets.open(StandaloneHtml.CHART_LIBRARY_ASSET).bufferedReader().use { reader -> reader.readText() }
        } ?: return file
        val copy = File(file.parentFile, "$STANDALONE_FOLDER/${file.name}")
        copy.parentFile?.mkdirs()
        copy.writeText(standalone)
        return copy
    }

    private fun copyInto(file: File, uri: Uri) {
        val output = resolver.openOutputStream(uri, "w") ?: throw IOException("the target could not be opened")
        output.use { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    companion object {
        /** Must match the provider's authority in AndroidManifest.xml. */
        const val FILE_PROVIDER_SUFFIX = ".files"

        /** MediaStore's path for what the Files app shows as Downloads/Jonaki. */
        private const val DOWNLOADS_SUBFOLDER = "Download/Jonaki"
        private const val NO_APP = "no app on this phone can handle it"
        private const val STANDALONE_FOLDER = ".standalone"

        fun mimeTypeOf(file: File): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
    }
}
