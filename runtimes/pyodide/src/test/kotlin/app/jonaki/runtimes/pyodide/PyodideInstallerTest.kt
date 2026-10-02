package app.jonaki.runtimes.pyodide

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PyodideInstallerTest {
    private val server = MockWebServer()
    private val root: File = Files.createTempDirectory("pyodide").toFile()

    /** What the fake CDN serves, by file name. */
    private val served = mutableMapOf<String, ByteArray>()

    private val wheels = mapOf(
        "numpy" to "numpy-2.4.6-cp314-cp314-pyemscripten_2026_0_wasm32.whl",
        "pandas" to "pandas-3.0.2-cp314-cp314-pyemscripten_2026_0_wasm32.whl",
        "python-dateutil" to "python_dateutil-2.9.0.post0-py2.py3-none-any.whl",
        "pytz" to "pytz-2026.1.post1-py2.py3-none-any.whl",
        "six" to "six-1.17.0-py2.py3-none-any.whl",
        "six-user" to "six_user-1.0-py3-none-any.whl",
        "native-trick" to "libtrick.so",
        "nested-trick" to "../outside.whl",
    )
    private val depends = mapOf(
        "pandas" to listOf("numpy", "python-dateutil", "pytz"),
        "python-dateutil" to listOf("six"),
        "six-user" to listOf("six"),
    )

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.requestUrl!!.pathSegments.last()
                val body = served[name] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
        server.start()
        for ((_, fileName) in wheels) {
            served[fileName] = "wheel bytes of $fileName".toByteArray()
        }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun lockText(): String {
        val entries = wheels.map { (name, fileName) ->
            val dependsList = depends[name].orEmpty().joinToString(",") { "\"$it\"" }
            """"$name": {"name": "$name", "version": "1", "file_name": "$fileName", "install_dir": "site",
                "sha256": "${Checksums.sha256Of(served.getValue(fileName))}", "package_type": "package",
                "imports": ["${name.replace("-", "_")}"], "depends": [$dependsList]}"""
        }
        return """{"info": {}, "packages": {${entries.joinToString(",")}}}"""
    }

    private fun release(): PyodideRelease {
        served["pyodide.js"] = "loader".toByteArray()
        served["pyodide.asm.wasm"] = "wasm".toByteArray()
        served[PyodideRelease.LOCK_FILE_NAME] = lockText().toByteArray()
        val coreFiles = listOf("pyodide.js", "pyodide.asm.wasm", PyodideRelease.LOCK_FILE_NAME).map { name ->
            val bytes = served.getValue(name)
            PinnedFile(name, Checksums.sha256Of(bytes), bytes.size.toLong())
        }
        return PyodideRelease(version = "test", baseUrl = server.url("/full/").toString(), coreFiles = coreFiles)
    }

    private val folder by lazy { PyodideFolder(root, release()) }
    private val installer by lazy { PyodideInstaller(folder, OkHttpClient()) }

    private fun installCore(): InstallResult = runBlocking { installer.installCore() }

    private fun installPackages(vararg names: String): InstallResult =
        runBlocking { installer.installPackages(names.toList()) }

    @Test
    fun installsTheCoreFiles() {
        assertFalse(folder.isCoreInstalled())

        assertEquals(InstallResult.Installed, installCore())

        assertTrue(folder.isCoreInstalled())
        assertTrue(folder.damagedCoreFiles().isEmpty())
        assertEquals("wasm", folder.coreFile("pyodide.asm.wasm").readText())
    }

    @Test
    fun refusesACoreFileWithTheWrongHash() {
        val release = release()
        served["pyodide.asm.wasm"] = "tampered".toByteArray()

        val result = runBlocking { PyodideInstaller(PyodideFolder(root, release), OkHttpClient()).installCore() }

        assertTrue(result is InstallResult.Failed)
        assertTrue(result.toString(), (result as InstallResult.Failed).message.contains("pyodide.asm.wasm"))
        assertFalse(PyodideFolder(root, release).isCoreInstalled())
        assertTrue(root.walkTopDown().none { file -> file.name.endsWith(".part") })
    }

    @Test
    fun aDownloadFailureSaysWhichFile() {
        val release = release()
        served.remove("pyodide.js")

        val result = runBlocking { PyodideInstaller(PyodideFolder(root, release), OkHttpClient()).installCore() }

        assertTrue(result.toString(), (result as InstallResult.Failed).message.contains("pyodide.js"))
    }

    @Test
    fun noticesACoreFileChangedAfterInstall() {
        installCore()
        folder.coreFile("pyodide.js").writeText("changed")

        assertEquals(listOf("pyodide.js"), folder.damagedCoreFiles())
    }

    @Test
    fun packagesNeedTheCoreFirst() {
        val result = installPackages("pandas")

        assertTrue(result.toString(), (result as InstallResult.Failed).message.contains("Python is not installed"))
    }

    @Test
    fun installsAPackageWithItsDependencies() {
        installCore()

        assertEquals(InstallResult.Installed, installPackages("pandas"))

        assertEquals(
            setOf("numpy", "pandas", "python-dateutil", "pytz", "six"),
            folder.installedPackageNames().toSet(),
        )
    }

    @Test
    fun skipsPackagesAlreadyInstalled() {
        installCore()
        installPackages("numpy")
        val requestsBefore = server.requestCount

        installPackages("numpy")

        assertEquals(requestsBefore, server.requestCount)
    }

    @Test
    fun refusesAPackageWithTheWrongHash() {
        installCore()
        served[wheels.getValue("six")] = "tampered".toByteArray()

        val result = installPackages("six")

        assertTrue(result.toString(), (result as InstallResult.Failed).message.contains("six"))
        assertTrue(folder.installedPackageNames().isEmpty())
    }

    @Test
    fun refusesUnknownPackages() {
        installCore()

        val result = installPackages("requests")

        assertTrue(result.toString(), (result as InstallResult.Failed).message.contains("requests"))
    }

    @Test
    fun neverDownloadsNativeLibrariesOrPathsOutsideTheFolder() {
        installCore()
        val requestsBefore = server.requestCount

        val native = installPackages("native-trick")
        val nested = installPackages("nested-trick")

        assertTrue(native is InstallResult.Failed)
        assertTrue(nested is InstallResult.Failed)
        assertEquals(requestsBefore, server.requestCount)
        assertFalse(File(root, "outside.whl").exists())
    }

    @Test
    fun removeDeletesEverything() {
        installCore()
        installPackages("numpy")
        assertTrue(folder.storageBytes() > 0)

        folder.remove()

        assertFalse(folder.isCoreInstalled())
        assertEquals(0L, folder.storageBytes())
    }

    @Test
    fun removingTheDataAddOnRemovesItsDependencies() {
        installCore()
        installPackages(*PyodideRelease.DATA_ADD_ON.toTypedArray())

        folder.removePackages(PyodideRelease.DATA_ADD_ON)

        assertTrue(folder.installedPackageNames().isEmpty())
        assertTrue(folder.isCoreInstalled())
    }

    @Test
    fun removingPackagesKeepsADependencyAnotherPackageNeeds() {
        installCore()
        installPackages("pandas", "six-user")

        folder.removePackages(listOf("numpy", "pandas"))

        assertEquals(setOf("six", "six-user"), folder.installedPackageNames().toSet())
    }

    @Test
    fun removingADependencyAlsoRemovesThePackagesThatNeedIt() {
        installCore()
        installPackages("pandas")

        folder.removePackages(listOf("numpy"))

        assertEquals(setOf("python-dateutil", "pytz", "six"), folder.installedPackageNames().toSet())
    }
}
