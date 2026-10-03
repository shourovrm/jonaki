package app.jonaki.localmodels

import android.app.ActivityManager
import android.content.Context
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException

/** This phone's memory, as Android reports it, for the fit labels (D-133). */
data class DeviceMemory(
    /** RAM visible to Android, for example 7.6 GB on the 8 GB A059. */
    val totalBytes: Long,
    /** What Android could give an app now without killing others (ActivityManager's availMem). */
    val availableBytes: Long,
)

object DeviceResources {
    fun memory(context: Context): DeviceMemory {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return DeviceMemory(totalBytes = info.totalMem, availableBytes = info.availMem)
    }

    /**
     * The bytes the app could write to [folder]'s volume. getAllocatableBytes
     * counts cache files Android would clear for us, so it can be more than
     * the plain free space. It reads the disk, so call it off the main thread.
     */
    fun allocatableBytes(context: Context, folder: File): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return try {
            storage.getAllocatableBytes(storage.getUuidForPath(folder))
        } catch (exception: IOException) {
            folder.usableSpace
        }
    }
}
