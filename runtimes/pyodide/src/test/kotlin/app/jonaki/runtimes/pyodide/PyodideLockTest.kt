package app.jonaki.runtimes.pyodide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PyodideLockTest {
    private fun entry(name: String, fileName: String, imports: List<String>, depends: List<String>): String =
        """"$name": {"name": "$name", "version": "1.0", "file_name": "$fileName", "install_dir": "site",
            "sha256": "${"0".repeat(64)}", "package_type": "package",
            "imports": [${imports.joinToString(",") { "\"$it\"" }}],
            "depends": [${depends.joinToString(",") { "\"$it\"" }}]}"""

    private val lock = PyodideLock.parse(
        """{"info": {"python": "3.14.2"}, "packages": {
            ${entry("numpy", "numpy-2.4.6.whl", listOf("numpy"), emptyList())},
            ${entry("pandas", "pandas-3.0.2.whl", listOf("pandas"), listOf("numpy", "python-dateutil", "pytz"))},
            ${entry("python-dateutil", "python_dateutil-2.9.whl", listOf("dateutil"), listOf("six"))},
            ${entry("pytz", "pytz-2026.whl", listOf("pytz"), emptyList())},
            ${entry("six", "six-1.17.whl", listOf("six"), emptyList())},
            ${entry("scikit-learn", "scikit_learn-1.7.whl", listOf("sklearn"), listOf("numpy"))}
        }}""",
    )

    @Test
    fun readsPackageEntries() {
        val pandas = lock.find("pandas")!!

        assertEquals("pandas-3.0.2.whl", pandas.fileName)
        assertEquals("0".repeat(64), pandas.sha256)
        assertEquals(listOf("numpy", "python-dateutil", "pytz"), pandas.depends)
    }

    @Test
    fun findsNamesWrittenAsPipWouldAccept() {
        assertEquals("python-dateutil", lock.find("Python_Dateutil")!!.name)
        assertEquals("scikit-learn", lock.find("scikit_learn")!!.name)
        assertNull(lock.find("requests"))
    }

    @Test
    fun theDataAddOnBringsItsDependencies() {
        val names = lock.withDependencies(listOf("numpy", "pandas")).map { it.name }

        assertEquals(listOf("numpy", "pandas", "python-dateutil", "pytz", "six"), names)
    }

    @Test
    fun mapsImportNamesToPackages() {
        val packageForImport = lock.packageForImport()

        assertEquals("python-dateutil", packageForImport["dateutil"])
        assertEquals("scikit-learn", packageForImport["sklearn"])
    }
}
