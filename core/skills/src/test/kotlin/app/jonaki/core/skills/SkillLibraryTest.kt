package app.jonaki.core.skills

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillLibraryTest {
    private val root: File = Files.createTempDirectory("skills-test").toFile()
    private val libraryFolder = File(root, "skills")
    private val stateFile = File(root, "skills-builtin.json")

    private fun library() = SkillLibrary(libraryFolder, stateFile)

    private fun skillText(name: String, description: String = "Does $name things.", body: String = "Steps.") =
        "---\nname: $name\ndescription: $description\n---\n$body\n"

    private fun files(name: String, description: String = "Does $name things.", body: String = "Steps."): Map<String, ByteArray> =
        mapOf(SkillLibrary.SKILL_FILE to skillText(name, description, body).toByteArray())

    private fun builtIn(name: String, body: String = "Steps.") = BuiltInSkill(name, files(name, body = body))

    private fun skillFileOf(name: String) = File(libraryFolder, "$name/SKILL.md")

    @Test
    fun installedSkillsAreListedByNameWithTheirDescriptions() {
        val library = library()
        library.install(files("slides"), replace = false)
        library.install(
            files("report") + ("templates/page.html" to "<html></html>".toByteArray()),
            replace = false,
        )

        val entries = library.list()

        assertEquals(listOf("report", "slides"), entries.map { entry -> entry.name })
        assertEquals("Does report things.", entries.first().description)
        assertEquals("<html></html>", File(libraryFolder, "report/templates/page.html").readText())
    }

    @Test
    fun installRefusesAnInvalidSkillMdAndAnExistingNameUnlessReplacing() {
        val library = library()
        library.install(files("report", body = "First"), replace = false)

        val invalid = library.install(mapOf(SkillLibrary.SKILL_FILE to "no front matter".toByteArray()), replace = false)
        val missing = library.install(mapOf("README.md" to "x".toByteArray()), replace = false)
        val existing = library.install(files("report", body = "Second"), replace = false)

        assertEquals(InstallResult.Invalid("SKILL.md must start with ---"), invalid)
        assertEquals(InstallResult.Invalid("no SKILL.md found"), missing)
        assertEquals(InstallResult.AlreadyExists("report"), existing)
        assertTrue(skillFileOf("report").readText().contains("First"))

        assertEquals(InstallResult.Installed("report"), library.install(files("report", body = "Second"), replace = true))
        assertTrue(skillFileOf("report").readText().contains("Second"))
    }

    @Test
    fun installRefusesPathsThatLeaveTheSkillFolder() {
        val result = library().install(files("report") + ("../evil.txt" to "x".toByteArray()), replace = false)

        assertEquals(InstallResult.Invalid("the file path ../evil.txt is not allowed"), result)
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(libraryFolder, "report").exists())
    }

    @Test
    fun aFolderWithABrokenSkillMdIsListedWithItsProblem() {
        File(libraryFolder, "broken").mkdirs()
        skillFileOf("broken").writeText("---\nname: broken\n---\n")

        val entry = library().list().single()

        assertEquals("broken", entry.name)
        assertEquals("description is missing", entry.problem)
    }

    @Test
    fun savingEditedTextChecksTheFrontMatterAndKeepsTheName() {
        val library = library()
        library.install(files("report"), replace = false)

        val renamed = library.saveText("report", skillText("summary"))
        val broken = library.saveText("report", "---\nname: report\n---\n")
        val saved = library.saveText("report", skillText("report", description = "New description."))

        assertEquals(SaveResult.Invalid("the name must stay report"), renamed)
        assertEquals(SaveResult.Invalid("description is missing"), broken)
        assertEquals(SaveResult.Saved, saved)
        assertEquals("New description.", library.list().single().description)
        assertEquals(skillText("report", description = "New description."), library.readText("report"))
    }

    @Test
    fun deleteRemovesTheFolder() {
        val library = library()
        library.install(files("report"), replace = false)

        library.delete("report")

        assertEquals(emptyList<SkillEntry>(), library.list())
        assertNull(library.readText("report"))
    }

    @Test
    fun builtInsAreInstalledAndUpdatedWhileTheUserHasNotEditedThem() {
        library().installBuiltIns(listOf(builtIn("report", body = "Version 1")))
        assertTrue(skillFileOf("report").readText().contains("Version 1"))

        // A new app version ships a new text; the user never touched the old one.
        library().installBuiltIns(listOf(builtIn("report", body = "Version 2")))

        assertTrue(skillFileOf("report").readText().contains("Version 2"))
        val entry = library().list().single()
        assertTrue(entry.isBuiltIn)
        assertFalse(entry.isEdited)
    }

    @Test
    fun anEditedBuiltInIsKeptAndCanBeResetToTheShippedText() {
        library().installBuiltIns(listOf(builtIn("report", body = "Version 1")))
        library().saveText("report", skillText("report", body = "My own steps"))

        library().installBuiltIns(listOf(builtIn("report", body = "Version 2")))

        assertTrue(skillFileOf("report").readText().contains("My own steps"))
        assertTrue(library().list().single().isEdited)

        library().resetBuiltIn(builtIn("report", body = "Version 2"))

        assertTrue(skillFileOf("report").readText().contains("Version 2"))
        assertFalse(library().list().single().isEdited)
    }

    @Test
    fun aDeletedBuiltInStaysDeletedUntilRestored() {
        library().installBuiltIns(listOf(builtIn("report"), builtIn("slides")))
        library().delete("report")

        library().installBuiltIns(listOf(builtIn("report"), builtIn("slides")))

        assertEquals(listOf("slides"), library().list().map { entry -> entry.name })
        assertEquals(listOf("report"), library().deletedBuiltIns())

        library().restoreBuiltIns(listOf(builtIn("report"), builtIn("slides")))

        assertEquals(listOf("report", "slides"), library().list().map { entry -> entry.name })
        assertEquals(emptyList<String>(), library().deletedBuiltIns())
    }

    @Test
    fun anImportedSkillWithABuiltInNameCountsAsEditedAndIsNotOverwritten() {
        library().install(files("report", body = "Imported"), replace = false)

        library().installBuiltIns(listOf(builtIn("report", body = "Shipped")))

        assertTrue(skillFileOf("report").readText().contains("Imported"))
        assertTrue(library().list().single().isEdited)
    }

    @Test
    fun aSkillTheAppNoLongerShipsStaysAsTheUsersOwnSkill() {
        library().installBuiltIns(listOf(builtIn("report"), builtIn("letter")))
        library().delete("report")

        // A new app version ships neither skill any more.
        library().installBuiltIns(emptyList())

        val entry = library().list().single()
        assertEquals("letter", entry.name)
        assertFalse(entry.isBuiltIn)
        assertEquals(emptyList<String>(), library().deletedBuiltIns())
    }

    @Test
    fun hiddenFoldersAreNotSkills() {
        File(libraryFolder, ".incoming-report").mkdirs()
        File(libraryFolder, ".incoming-report/SKILL.md").writeText(skillText("report"))

        assertEquals(emptyList<SkillEntry>(), library().list())
    }
}
