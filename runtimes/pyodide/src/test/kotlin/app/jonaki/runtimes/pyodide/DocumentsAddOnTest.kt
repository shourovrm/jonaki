package app.jonaki.runtimes.pyodide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentsAddOnTest {
    private val wheels = PyodideRelease.DOCUMENTS_ADD_ON_WHEELS

    @Test
    fun pinsTheFiveWheelsWithTheirPyPiSizes() {
        val sizes = wheels.associate { wheel -> wheel.packageName to wheel.sizeBytes }

        assertEquals(
            mapOf(
                "python-docx" to 252_987L,
                "openpyxl" to 250_910L,
                "et-xmlfile" to 18_059L,
                "python-pptx" to 472_788L,
                "XlsxWriter" to 175_315L,
            ),
            sizes,
        )
    }

    @Test
    fun everyWheelIsAPlainWheelNameFromPyPiWithAFullHash() {
        for (wheel in wheels) {
            assertTrue(wheel.fileName, wheel.fileName.endsWith(".whl"))
            assertTrue(wheel.fileName, !wheel.fileName.contains('/') && !wheel.fileName.startsWith("."))
            assertTrue(wheel.url, wheel.url.startsWith("https://files.pythonhosted.org/packages/"))
            assertTrue(wheel.url, wheel.url.endsWith("/" + wheel.fileName))
            assertTrue(wheel.sha256, Regex("[0-9a-f]{64}").matches(wheel.sha256))
        }
    }

    @Test
    fun theDownloadSizeIsTheLockPackagesPlusTheWheels() {
        val wheelBytes = wheels.sumOf { wheel -> wheel.sizeBytes }

        assertEquals(1_170_059L, wheelBytes)
        assertEquals(3_373_005L, PyodideRelease.DOCUMENTS_ADD_ON_LOCK_DOWNLOAD_BYTES)
        assertEquals(4_543_064L, PyodideRelease.DOCUMENTS_ADD_ON_DOWNLOAD_BYTES)
    }

    @Test
    fun theReleaseCarriesTheWheelsAndTheNamesListLockPackagesFirst() {
        assertEquals(wheels, PyodideRelease.PINNED.addOnWheels)
        assertEquals(
            listOf("lxml", "pillow", "typing-extensions", "beautifulsoup4", "soupsieve") + wheels.map { wheel -> wheel.packageName },
            PyodideRelease.DOCUMENTS_ADD_ON,
        )
    }

    @Test
    fun importingAWheelModuleOrTheBundledHelperNeedsTheWholeAddOn() {
        val imports = PyodideRelease.DOCUMENTS_ADD_ON_IMPORTS

        assertEquals(setOf("docx", "openpyxl", "et_xmlfile", "pptx", "xlsxwriter", "jonaki_docs"), imports)
    }

    @Test
    fun theBundledModuleIsListedAndShippedInTheResources() {
        assertEquals(listOf("jonaki_docs"), BundledModules.NAMES)
        for (name in BundledModules.NAMES) {
            val resource = "/app/jonaki/runtimes/pyodide/python/${BundledModules.fileNameOf(name)}"
            assertTrue(resource, BundledModules::class.java.getResource(resource) != null)
        }
    }
}
