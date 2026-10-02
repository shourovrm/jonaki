package app.jonaki.runtimes.pyodide

import java.io.File

/**
 * Where Python lives on the phone: [root]/<version>/ holds the core files
 * and the package wheels side by side, as on the CDN, so that Pyodide finds
 * a package next to pyodide-lock.json. Whether something is installed is
 * read from the files themselves; there is no separate record to drift.
 */
class PyodideFolder(
    private val root: File,
    val release: PyodideRelease,
) {
    private val versionFolder: File = File(root, release.version)

    fun coreFile(name: String): File = File(versionFolder, name)

    fun packageFile(lockPackage: LockPackage): File = File(versionFolder, lockPackage.fileName)

    /** Every core file is present at its pinned size; [damagedCoreFiles] checks the bytes. */
    fun isCoreInstalled(): Boolean = release.coreFiles.all { pinned ->
        val file = coreFile(pinned.name)
        file.isFile && file.length() == pinned.sizeBytes
    }

    /** Names of core files whose SHA-256 differs from the pinned one; empty when all are intact. */
    fun damagedCoreFiles(): List<String> = release.coreFiles
        .filter { pinned -> !coreFile(pinned.name).isFile || Checksums.sha256Of(coreFile(pinned.name)) != pinned.sha256 }
        .map { pinned -> pinned.name }

    /** Null until the core is installed. */
    fun lock(): PyodideLock? {
        val lockFile = coreFile(PyodideRelease.LOCK_FILE_NAME)
        if (!lockFile.isFile) {
            return null
        }
        return PyodideLock.parse(lockFile.readText())
    }

    fun installedPackageNames(): List<String> {
        val lock = lock() ?: return emptyList()
        return lock.all.filter { lockPackage -> packageFile(lockPackage).isFile }.map { lockPackage -> lockPackage.name }
    }

    fun storageBytes(): Long = root.walkTopDown().filter { file -> file.isFile }.sumOf { file -> file.length() }

    /** Removes Python with every package, and any older version left from an update. */
    fun remove() {
        root.deleteRecursively()
    }
}
