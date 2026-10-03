package app.jonaki.run

import app.jonaki.runtimes.pyodide.Checksums
import app.jonaki.runtimes.pyodide.PinnedFile
import app.jonaki.runtimes.pyodide.PyodideFolder
import app.jonaki.runtimes.pyodide.PyodideInstaller
import app.jonaki.runtimes.pyodide.PyodideRelease
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonSetupTest {
    private val server = MockWebServer()
    private val root: File = Files.createTempDirectory("python-setup").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What the fake CDN serves, by file name. */
    private val served = mutableMapOf<String, ByteArray>()

    /** File names served slowly, so a test can act while they download. */
    private val slowFiles = mutableSetOf<String>()

    private val wheels = mapOf(
        "numpy" to "numpy-2.4.6-cp314-cp314-pyemscripten_2026_0_wasm32.whl",
        "pandas" to "pandas-3.0.2-cp314-cp314-pyemscripten_2026_0_wasm32.whl",
        "python-dateutil" to "python_dateutil-2.9.0.post0-py2.py3-none-any.whl",
        "pytz" to "pytz-2026.1.post1-py2.py3-none-any.whl",
        "six" to "six-1.17.0-py2.py3-none-any.whl",
        "regex" to "regex-2026.1-cp314-cp314-pyemscripten_2026_0_wasm32.whl",
    )
    private val depends = mapOf(
        "pandas" to listOf("numpy", "python-dateutil", "pytz"),
        "python-dateutil" to listOf("six"),
    )

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.requestUrl!!.pathSegments.last()
                val body = served[name] ?: return MockResponse().setResponseCode(404)
                val response = MockResponse().setBody(Buffer().write(body))
                if (name in slowFiles) {
                    response.throttleBody(1_024, 50, TimeUnit.MILLISECONDS)
                }
                return response
            }
        }
        server.start()
        for ((_, fileName) in wheels) {
            served[fileName] = "wheel bytes of $fileName".toByteArray()
        }
    }

    @After
    fun stop() {
        scope.cancel()
        server.shutdown()
        root.deleteRecursively()
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

    private val release: PyodideRelease by lazy {
        served["pyodide.js"] = "loader".toByteArray()
        // Large enough that a slow download is still running when a test acts on it.
        served["pyodide.asm.wasm"] = ByteArray(64 * 1_024) { index -> index.toByte() }
        served[PyodideRelease.LOCK_FILE_NAME] = lockText().toByteArray()
        val coreFiles = listOf("pyodide.js", "pyodide.asm.wasm", PyodideRelease.LOCK_FILE_NAME).map { name ->
            val bytes = served.getValue(name)
            PinnedFile(name, Checksums.sha256Of(bytes), bytes.size.toLong())
        }
        PyodideRelease(version = "test", baseUrl = server.url("/full/").toString(), coreFiles = coreFiles)
    }

    private val folder by lazy { PyodideFolder(root, release) }
    private val setup by lazy { PythonSetup(folder, PyodideInstaller(folder, OkHttpClient()), scope) }

    private fun refreshed(): PythonState = runBlocking {
        setup.refresh()
        setup.state.value
    }

    @Test
    fun readsANewPhoneAsNotInstalled() {
        assertEquals(PythonCoreStatus.CHECKING, setup.state.value.coreStatus)

        assertEquals(PythonCoreStatus.NOT_INSTALLED, refreshed().coreStatus)
    }

    @Test
    fun installsTheCore() {
        runBlocking { setup.installCore()!!.join() }

        val state = setup.state.value
        assertEquals(PythonCoreStatus.INSTALLED, state.coreStatus)
        assertNull(state.download)
        assertNull(state.problem)
        assertTrue(state.storageBytes > 0)
        assertEquals(setOf("numpy", "pandas", "python-dateutil", "pytz", "six"), state.dataAddOnPackages)
    }

    @Test
    fun showsTheDownloadWithItsSizeAtOnce() {
        slowFiles += "pyodide.asm.wasm"
        refreshed()

        setup.installDataAddOn()

        val download = setup.state.value.download
        assertNotNull(download)
        assertEquals(release.coreDownloadBytes + PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES, download!!.totalBytes)
        assertEquals(PyodideRelease.DATA_ADD_ON, download.packageNames)
    }

    @Test
    fun aSecondInstallWhileOneRunsStartsNothing() {
        slowFiles += "pyodide.asm.wasm"

        val first = setup.installCore()
        val second = setup.installDataAddOn()

        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun aFailedDownloadKeepsTheInstallersMessage() {
        // The release is pinned first, then its loader goes missing from the server.
        assertTrue(release.coreFiles.isNotEmpty())
        served.remove("pyodide.js")

        runBlocking { setup.installCore()!!.join() }

        val state = setup.state.value
        assertEquals(PythonCoreStatus.NOT_INSTALLED, state.coreStatus)
        assertTrue(state.problem.toString(), state.problem!!.contains("pyodide.js"))
        assertNull(state.download)
    }

    @Test
    fun cancelStopsTheDownloadWithoutAProblemOrAPartFile() {
        slowFiles += "pyodide.asm.wasm"
        val job = setup.installCore()!!
        runBlocking {
            withTimeout(10_000) {
                while ((setup.state.value.download?.doneBytes ?: 0) < served.getValue("pyodide.js").size + 1) {
                    delay(20)
                }
            }
        }

        setup.cancel()
        runBlocking {
            job.join()
            // cancel() reads the files again in the background.
            setup.refresh()
        }

        val state = setup.state.value
        assertNull(state.download)
        assertNull(state.problem)
        assertEquals(PythonCoreStatus.NOT_INSTALLED, state.coreStatus)
        val leftovers = root.walkTopDown().filter { file -> file.name.endsWith(".part") }.toList()
        assertTrue(leftovers.toString(), leftovers.isEmpty())
    }

    @Test
    fun aDownloadStartedRightAfterCancelKeepsShowing() {
        slowFiles += "pyodide.asm.wasm"
        val cancelled = setup.installCore()!!

        setup.cancel()
        val second = setup.installCore()
        runBlocking { cancelled.join() }

        assertNotNull(second)
        assertNotNull(setup.state.value.download)
        second!!.cancel()
    }

    @Test
    fun theDataAddOnInstallsTheCoreFirst() {
        runBlocking { setup.installDataAddOn()!!.join() }

        val state = setup.state.value
        assertEquals(PythonCoreStatus.INSTALLED, state.coreStatus)
        assertTrue(state.isDataAddOnInstalled)
        assertTrue(state.hasPackages(listOf("pandas", "numpy")))
    }

    @Test
    fun aPackageOutsideTheLockFileIsAClearProblem() {
        runBlocking { setup.installCore()!!.join() }

        runBlocking { setup.installPackages(listOf(" requests "))!!.join() }

        val problem = setup.state.value.problem
        assertEquals("requests is not available for Pyodide test", problem)
    }

    @Test
    fun blankPackageNamesStartNothing() {
        assertNull(setup.installPackages(listOf(" ", "")))
    }

    @Test
    fun onlyTheDataAddOnHasAKnownSize() {
        runBlocking { setup.installCore()!!.join() }

        assertEquals(PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES, setup.totalBytesFor(listOf("pandas")))
        assertEquals(PyodideRelease.DATA_ADD_ON_DOWNLOAD_BYTES, setup.totalBytesFor(listOf("six", "numpy")))
        assertNull(setup.totalBytesFor(listOf("regex")))
        assertNull(setup.totalBytesFor(listOf("pandas", "regex")))
    }

    @Test
    fun removingTheDataAddOnKeepsPythonAndOtherPackages() {
        runBlocking {
            setup.installDataAddOn()!!.join()
            setup.installPackages(listOf("regex"))!!.join()
            setup.removeDataAddOn().join()
        }

        val state = setup.state.value
        assertEquals(PythonCoreStatus.INSTALLED, state.coreStatus)
        assertFalse(state.isDataAddOnInstalled)
        assertEquals(listOf("regex"), state.installedPackages)
    }

    @Test
    fun removeDeletesEverything() {
        runBlocking {
            setup.installDataAddOn()!!.join()
            setup.remove().join()
        }

        val state = setup.state.value
        assertEquals(PythonCoreStatus.NOT_INSTALLED, state.coreStatus)
        assertEquals(0L, state.storageBytes)
        assertTrue(state.installedPackages.isEmpty())
    }

    @Test
    fun aChangedCoreFileIsReadAsDamaged() {
        runBlocking { setup.installCore()!!.join() }
        val loader = File(File(root, "test"), "pyodide.js")
        loader.writeText("LOADER")

        val state = refreshed()

        assertEquals(PythonCoreStatus.DAMAGED, state.coreStatus)
        assertEquals(listOf("pyodide.js"), state.damagedFiles)
    }

    @Test
    fun installingAgainRepairsADamagedCore() {
        runBlocking { setup.installCore()!!.join() }
        File(File(root, "test"), "pyodide.js").writeText("LOADER")
        refreshed()

        runBlocking { setup.installCore()!!.join() }

        assertEquals(PythonCoreStatus.INSTALLED, setup.state.value.coreStatus)
    }
}
