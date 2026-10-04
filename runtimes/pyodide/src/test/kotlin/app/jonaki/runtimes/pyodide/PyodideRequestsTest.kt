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
    fun servesABundledModuleFromTheModule() {
        val answer = requests.resolve(url("python/jonaki_docs.py"))

        assertEquals(PyodideResponse.Resource("python/jonaki_docs.py", "text/plain"), answer)
    }

    @Test
    fun refusesEverythingInOrAroundTheBundledFolderThatIsNotListed() {
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/other.py")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/jonaki_docs.py/extra")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/sub/jonaki_docs.py")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/../harness/harness.js")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/%2e%2e/secret.py")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("python/jonaki_docs.pyc")))
        assertEquals(PyodideResponse.Blocked, requests.resolve(url("pyodide/jonaki_docs.py")))
    }

    @Test
    fun servesAnInstalledWheelUnderPyodideAndNoOther() {
        val wheel = PinnedWheel("python-docx", "1.2.0", listOf("docx"), "python_docx-1.2.0-py3-none-any.whl", "https://files.example/x.whl", "0".repeat(64), 5)
        val absent = PinnedWheel("openpyxl", "3.1.5", listOf("openpyxl"), "openpyxl-3.1.5-py2.py3-none-any.whl", "https://files.example/y.whl", "0".repeat(64), 5)
        val withWheels = PyodideFolder(root, release.copy(addOnWheels = listOf(wheel, absent)))
        withWheels.wheelFile(wheel).apply {
            parentFile.mkdirs()
            writeText("12345")
        }

        val filter = PyodideRequests(withWheels, emptyList(), emptyList())

        assertTrue(filter.resolve(url("pyodide/python_docx-1.2.0-py3-none-any.whl")) is PyodideResponse.File)
        assertEquals(PyodideResponse.Blocked, filter.resolve(url("pyodide/openpyxl-3.1.5-py2.py3-none-any.whl")))
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
