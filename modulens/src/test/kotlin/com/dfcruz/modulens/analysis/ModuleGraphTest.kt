package com.dfcruz.modulens.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleGraphTest {

    private val graph = mapOf(
        ":app" to listOf(":feature", ":data"),
        ":feature" to listOf(":domain"),
        ":data" to listOf(":domain"),
        ":domain" to emptyList(),
        ":isolated" to emptyList(),
    )

    @Test
    fun `analyzes direct transitive and impact relationships`() {
        val analysis = ModuleGraph(graph).analyzeModule(":domain")

        assertEquals(emptyList<String>(), analysis.directDependencies)
        assertEquals(setOf(":app", ":feature", ":data"), analysis.allDependents)
        assertEquals(3, analysis.allDependents.size)
        assertEquals(75.0, analysis.projectCoverage, 0.0)
        assertTrue(analysis.isLeaf)
    }

    @Test
    fun `detects cycles without counting the start module as transitive`() {
        val cyclicGraph = graph + (":domain" to listOf(":feature"))

        val analysis = ModuleGraph(cyclicGraph).analyzeModule(":feature")

        assertEquals(setOf(":domain"), analysis.allDependencies)
        assertEquals(listOf(":domain", ":feature", ":domain"), analysis.cycles.single())
    }
}
