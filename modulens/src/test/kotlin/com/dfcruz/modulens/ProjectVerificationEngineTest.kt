package com.dfcruz.modulens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectVerificationEngineTest {

    @Test
    fun `reports cycle depth direct dependency and version violations`() {
        val graph = ModuleGraph(
            mapOf(
                ":app" to listOf(":feature", ":data"),
                ":feature" to listOf(":app"),
                ":data" to emptyList(),
            ),
        )
        val inventory = LibraryInventoryAnalyzer.analyze(
            declarations = mapOf(
                ":app" to listOf("com.example:library:2.0"),
                ":feature" to listOf("com.example:library:1.0"),
            ),
            resolvedLibraries = listOf("com.example:library:2.0"),
        )

        val violations = ProjectVerificationEngine.verify(
            graph = graph,
            libraryInventory = inventory,
            policy = VerificationPolicy(
                failOnCycles = true,
                maxDependencyDepth = 0,
                maxDirectDependencies = 1,
                failOnVersionConflicts = true,
            ),
        )

        assertEquals(
            setOf("cycle", "maximum dependency depth", "maximum direct dependencies", "version conflict"),
            violations.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `only fails redundant dependencies when enabled by policy`() {
        val graph = ModuleGraph(
            mapOf(
                ":app" to listOf(":feature", ":domain"),
                ":feature" to listOf(":domain"),
                ":domain" to emptyList(),
            ),
        )
        val inventory = LibraryInventoryAnalyzer.analyze(emptyMap(), emptyList())

        val ignored = ProjectVerificationEngine.verify(
            graph,
            inventory,
            VerificationPolicy(false, Int.MAX_VALUE, Int.MAX_VALUE, false),
        )
        val enforced = ProjectVerificationEngine.verify(
            graph,
            inventory,
            VerificationPolicy(false, Int.MAX_VALUE, Int.MAX_VALUE, false, failOnRedundantDependencies = true),
        )

        assertTrue(ignored.isEmpty())
        assertEquals(listOf("redundant module dependency"), enforced.map { it.rule })
    }
}
