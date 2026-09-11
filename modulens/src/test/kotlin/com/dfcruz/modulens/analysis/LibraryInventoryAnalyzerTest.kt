package com.dfcruz.modulens.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryInventoryAnalyzerTest {

    @Test
    fun `reports declared and resolved version conflicts`() {
        val analysis = LibraryInventoryAnalyzer.analyze(
            declarations = mapOf(
                ":app" to listOf("com.example:library:2.0"),
                ":feature" to listOf("com.example:library:1.0"),
            ),
            resolvedLibraries = listOf("com.example:library:2.0"),
        )

        val entry = analysis.entries.single()
        assertEquals(listOf("1.0", "2.0"), entry.declaredVersions)
        assertEquals("2.0", entry.resolvedVersion)
        assertEquals(listOf(":app", ":feature"), entry.modules)
        assertEquals(listOf(entry), analysis.versionConflicts)
    }

    @Test
    fun `includes resolved transitive libraries without direct declarations`() {
        val analysis = LibraryInventoryAnalyzer.analyze(
            declarations = mapOf(":app" to listOf("com.example:root:1.0")),
            resolvedLibraries = listOf(
                "com.example:root:1.0",
                "com.example:transitive:2.0",
            ),
        )

        val transitive = analysis.entries.single { it.identifier == "com.example:transitive" }
        assertEquals(emptyList<String>(), transitive.declaredVersions)
        assertEquals("2.0", transitive.resolvedVersion)
        assertEquals(emptyList<String>(), transitive.modules)
    }
}
