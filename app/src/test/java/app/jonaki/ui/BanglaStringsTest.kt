package app.jonaki.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every English string has a Bangla one with the same placeholders (M11).
 * A missing key would fall back to English on a Bangla phone, so a new
 * string fails this test until values-bn has it too.
 */
class BanglaStringsTest {
    private val projectRoot: File = generateSequence(File("").absoluteFile) { folder -> folder.parentFile }
        .first { folder -> File(folder, "settings.gradle.kts").exists() }

    /** values/strings*.xml of every module, apart from build output and spikes. */
    private val englishFiles: List<File> = projectRoot.walkTopDown()
        .onEnter { folder -> folder.name != "build" && folder.name != "spikes" && !folder.name.startsWith(".") }
        .filter { file -> file.parentFile.name == "values" && file.name.startsWith("strings") && file.extension == "xml" }
        .filter { file -> file.path.contains("src${File.separator}main${File.separator}res") }
        .toList()

    private val placeholder = Regex("""%(\d+\$)?[sd]|%%""")

    @Test
    fun theProjectHasEnglishStringFiles() {
        assertTrue("found ${englishFiles.size} files", englishFiles.size >= 10)
    }

    @Test
    fun everyEnglishKeyHasABanglaTranslationInTheSameFile() {
        val missing = mutableListOf<String>()
        for (englishFile in englishFiles) {
            val banglaFile = banglaFileFor(englishFile)
            val banglaKeys = if (banglaFile.exists()) textsOf(banglaFile).keys else emptySet()
            for (key in textsOf(englishFile).keys - banglaKeys) {
                missing += "${relativePath(banglaFile)}: $key"
            }
        }
        assertEquals("Missing Bangla strings", emptyList<String>(), missing)
    }

    @Test
    fun banglaHasNoKeysThatEnglishLacks() {
        val extra = mutableListOf<String>()
        for (englishFile in englishFiles) {
            val banglaFile = banglaFileFor(englishFile)
            if (!banglaFile.exists()) {
                continue
            }
            for (key in textsOf(banglaFile).keys - textsOf(englishFile).keys) {
                extra += "${relativePath(banglaFile)}: $key"
            }
        }
        assertEquals("Bangla strings with no English original", emptyList<String>(), extra)
    }

    @Test
    fun banglaKeepsEveryPlaceholder() {
        val mismatches = mutableListOf<String>()
        for (englishFile in englishFiles) {
            val banglaFile = banglaFileFor(englishFile)
            if (!banglaFile.exists()) {
                continue
            }
            val bangla = textsOf(banglaFile)
            for ((key, englishText) in textsOf(englishFile)) {
                val banglaText = bangla[key] ?: continue
                if (placeholdersOf(englishText) != placeholdersOf(banglaText)) {
                    mismatches += "$key: \"$englishText\" vs \"$banglaText\""
                }
            }
        }
        assertEquals("Placeholders differ", emptyList<String>(), mismatches)
    }

    @Test
    fun banglaPluralsHaveTheOtherQuantity() {
        val incomplete = mutableListOf<String>()
        for (englishFile in englishFiles) {
            val banglaFile = banglaFileFor(englishFile)
            if (!banglaFile.exists()) {
                continue
            }
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(banglaFile)
            val plurals = document.getElementsByTagName("plurals")
            for (index in 0 until plurals.length) {
                val element = plurals.item(index) as Element
                val items = element.getElementsByTagName("item")
                val quantities = (0 until items.length).map { position -> (items.item(position) as Element).getAttribute("quantity") }
                if ("other" !in quantities) {
                    incomplete += element.getAttribute("name")
                }
            }
        }
        assertEquals("Plurals without \"other\"", emptyList<String>(), incomplete)
    }

    private fun banglaFileFor(englishFile: File): File = File(File(englishFile.parentFile.parentFile, "values-bn"), englishFile.name)

    private fun relativePath(file: File): String = file.relativeTo(projectRoot).path

    /**
     * Translatable strings and plurals by name. A plural's text is its
     * "other" item, which carries the same placeholders as the rest.
     */
    private fun textsOf(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val texts = mutableMapOf<String, String>()
        val strings = document.getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val element = strings.item(index) as Element
            if (element.getAttribute("translatable") != "false") {
                texts[element.getAttribute("name")] = element.textContent
            }
        }
        val plurals = document.getElementsByTagName("plurals")
        for (index in 0 until plurals.length) {
            val element = plurals.item(index) as Element
            val items = element.getElementsByTagName("item")
            val other = (0 until items.length)
                .map { position -> items.item(position) as Element }
                .firstOrNull { item -> item.getAttribute("quantity") == "other" }
            texts[element.getAttribute("name")] = other?.textContent.orEmpty()
        }
        return texts
    }

    private fun placeholdersOf(text: String): List<String> = placeholder.findAll(text).map { match -> match.value }.sorted().toList()
}
