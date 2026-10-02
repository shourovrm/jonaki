package app.jonaki

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun toolNamesAreUnique() {
        val names = ToolRegistry.allTools.map { tool -> tool.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun fileToolsAreRegistered() {
        val names = ToolRegistry.allTools.map { tool -> tool.name }.toSet()
        assertEquals(setOf("read_file", "write_file", "edit_file", "find_files", "search_files"), names)
    }
}
