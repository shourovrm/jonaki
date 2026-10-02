package app.jonaki.tools.sharefile

import java.io.File

/**
 * The places outside the app that share_file reaches. The app implements
 * this with Android's storage, pickers and share sheet; tests use a fake.
 * Every call suspends until the file is written or the user has answered a
 * picker.
 */
interface FileDestinations {
    /** Saves into Downloads/Jonaki/ (on Android 8 and 9 through the "Save as" picker instead). */
    suspend fun saveToDownloads(file: File): DestinationResult

    /** Lets the user pick the place and name with the system "Save as" picker. */
    suspend fun saveAs(file: File): DestinationResult

    /** Opens Android's share sheet for the file; [DestinationResult.Done] once it is open. */
    suspend fun share(file: File): DestinationResult

    /** Copies the file into the top of the folder the user linked in Settings. */
    suspend fun copyToLinkedFolder(file: File): DestinationResult

    /** Lists one folder of the linked folder; [folderPath] "" is its top. */
    suspend fun listLinkedFolder(folderPath: String): LinkedListing

    /** Copies one file of the linked folder into [target], at most [IncomingFiles.MAX_IMPORT_BYTES]. */
    suspend fun copyFromLinkedFolder(path: String, target: File): DestinationResult
}

sealed interface DestinationResult {
    /** [location] is where the file is now, as the user would find it, for example "Downloads/Jonaki/report.pdf". */
    data class Done(val location: String) : DestinationResult

    /** The user closed the picker without choosing. */
    data object Cancelled : DestinationResult

    /** The action needs Jonaki's window on screen and it is not. */
    data object AppNotOnScreen : DestinationResult

    data object NoLinkedFolder : DestinationResult

    /** The linked folder was moved or deleted, or its access was taken back. */
    data object LinkedFolderGone : DestinationResult

    data object NotFound : DestinationResult

    data class TooLarge(val limitBytes: Long) : DestinationResult

    data class Failed(val reason: String) : DestinationResult
}

sealed interface LinkedListing {
    data class Entries(val folderName: String, val entries: List<LinkedEntry>) : LinkedListing

    data class Failed(val problem: DestinationResult) : LinkedListing
}

/** One file or folder in the linked folder; [path] is relative to the linked folder's top. */
data class LinkedEntry(
    val path: String,
    val isFolder: Boolean,
    val sizeBytes: Long?,
)
