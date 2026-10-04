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

    fun wheelFile(wheel: PinnedWheel): File = File(versionFolder, wheel.fileName)

    /** Wheels of the release that are present at their pinned size; [damagedWheels] checks the bytes. */
    fun installedWheels(): List<PinnedWheel> = release.addOnWheels.filter { wheel ->
        val file = wheelFile(wheel)
        file.isFile && file.length() == wheel.sizeBytes
    }

    /** Wheels that are present but whose SHA-256 differs from the pinned one, or whose size does. */
    fun damagedWheels(): List<PinnedWheel> = release.addOnWheels.filter { wheel ->
        val file = wheelFile(wheel)
        file.isFile && (file.length() != wheel.sizeBytes || Checksums.sha256Of(file) != wheel.sha256)
    }

    fun removeWheels(wheels: List<PinnedWheel>) {
        for (wheel in wheels) {
            wheelFile(wheel).delete()
        }
    }

    fun installedPackageNames(): List<String> {
        val lock = lock() ?: return emptyList()
        return lock.all.filter { lockPackage -> packageFile(lockPackage).isFile }.map { lockPackage -> lockPackage.name }
    }

    /**
     * Removes the named packages, every installed package that needs one of
     * them (it could no longer load), and their dependencies that no other
     * installed package needs. Removing numpy and pandas also removes
     * python-dateutil, pytz and six unless another package still uses them.
     */
    fun removePackages(names: List<String>) {
        val lock = lock() ?: return
        val installed = lock.all.filter { lockPackage -> packageFile(lockPackage).isFile }
        val namedClosure = lock.withDependencies(names).map { lockPackage -> lockPackage.name }.toSet()
        val namedPackages = names.mapNotNull { name -> lock.find(name)?.name }.toSet()
        val dependents = installed.filter { lockPackage ->
            lock.withDependencies(listOf(lockPackage.name)).any { needed -> needed.name in namedPackages }
        }
        val dependentNames = dependents.map { lockPackage -> lockPackage.name }.toSet()
        val keptRoots = installed.filter { lockPackage ->
            lockPackage.name !in dependentNames && lockPackage.name !in namedClosure
        }
        val stillNeeded = lock.withDependencies(keptRoots.map { lockPackage -> lockPackage.name })
            .map { lockPackage -> lockPackage.name }
            .toSet()
        val removable = installed.filter { lockPackage ->
            lockPackage.name in dependentNames || (lockPackage.name in namedClosure && lockPackage.name !in stillNeeded)
        }
        for (lockPackage in removable) {
            packageFile(lockPackage).delete()
        }
    }

    fun storageBytes(): Long = root.walkTopDown().filter { file -> file.isFile }.sumOf { file -> file.length() }

    /** Removes Python with every package, and any older version left from an update. */
    fun remove() {
        root.deleteRecursively()
    }
}
