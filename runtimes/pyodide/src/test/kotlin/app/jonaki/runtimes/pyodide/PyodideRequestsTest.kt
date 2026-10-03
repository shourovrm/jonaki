package app.jonaki.runtimes.pyodide

import app.jonaki.core.runtimeapi.InputFile
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PyodideRequestsTest {
    private val root: File = Files.createTempDirectory("pyodide").toFile()
    private val threadFolder: File = Files.createTempDirectory("thread").toFile()
    private val release = PyodideRelease(
        version = "test",
        baseUrl = "https://cdn.example/",
        coreFiles = listOf(
            PinnedFile("pyodide.js", "0".repeat(64), 1),
            PinnedFile("pyodide.asm.wasm", "0".repeat(64), 1),
        ),
    )
    private val folder = PyodideFolder(root, release)
    private val numpy = LockPackage("numpy", "numpy-2.4.6.whl", "0".repeat(64), listOf("numpy"), emptyList())
    private val pandas = LockPackage("pandas", "pandas-3.0.2.whl", "0".repeat(64), listOf("pandas"), emptyList())
    private val sales = File(threadFolder, "inbox/sales report.csv").apply {
        parentFile.mkdirs()
        writeText("a,b")
    }

    private val requests = PyodideRequests(
        folder = folder,
        installedPackages = listOf(numpy),
        inputFiles = listOf(InputFile("inbox/sales report.csv", sales)),
    )

    private fun url(path: String) = "https://${PyodideRequests.HOST}/$path"

    @Test
    fun servesTheHarnessFromTheModule() {
        val answer = requests.resolve(url("harness.html"))

        assertEquals(PyodideResponse.Resource("harness.html", "text/html"), answer)
    }

    @Test
    fun servesCoreFilesWithTheirTypes() {
        val wasm = requests.resolve(url("pyodide/pyodide.asm.wasm")) as PyodideResponse.File
        val loader = requests.resolve(url("pyodide/pyodide.js")) as PyodideResponse.File

        assertEquals(folder.coreFile("pyodide.asm.wasm"), wasm.file)
        assertEquals("application/wasm", wasm.mimeType)
        assertEquals("text/javascript", loader.mimeType)
    }

    @Test
    fun servesOnlyInstalledPackages() {
        assertTrue(requests.resolve(url("pyodide/numpy-2.4.6.whl")) is PyodideResponse.File)
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("pyodide/pandas-3.0.2.whl")))
    }

    @Test
    fun servesOnlyTheGivenInputFiles() {
        val input = requests.resolve(url("input/inbox/sales%20report.csv")) as PyodideResponse.File

        assertEquals(sales, input.file)
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("input/inbox/other.csv")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("input/inbox/../inbox/sales%20report.csv")))
    }

    @Test
    fun blocksEverythingElse() {
        assertEquals(PyodideResponse.Blocked, requests.resolve("https://cdn.jsdelivr.net/pyodide/v314.0.7/full/pyodide.js"))
        assertEquals(PyodideResponse.Blocked, requests.resolve("http://${PyodideRequests.HOST}/harness.html"))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("pyodide/../secret")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("other.js")))
        assertEquals(PyodideResponse.Blocked, requests.resolve("not a url"))
    }

    @Test
    fun theContentPolicyAllowsOnlyTheOwnHost() {
        assertTrue(PyodideRequests.CONTENT_POLICY.contains("connect-src 'self'"))
        assertTrue(PyodideRequests.CONTENT_POLICY.contains("default-src 'none'"))
    }
}
