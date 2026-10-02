package app.jonaki

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun toolNamesAreUnique() {
        val names = ToolRegistry.allTools.map { tool -> tool.name }
        assertEquals(names.size, names.toSet().size)
    }
}
