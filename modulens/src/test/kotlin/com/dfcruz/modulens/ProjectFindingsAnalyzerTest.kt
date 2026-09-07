package com.dfcruz.modulens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFindingsAnalyzerTest {

    @Test
    fun `reports a redundant direct dependency with its alternative path`() {
        val findings = ProjectFindingsAnalyzer.analyze(
            snapshot(
                graph = mapOf(
                    ":app" to listOf(":feature", ":domain"),
                    ":feature" to listOf(":domain"),
                    ":domain" to emptyList(),
                ),
            ),
        )

        val finding = findings.single { it.id == FindingId.REDUNDANT_MODULE_DEPENDENCY }
        assertEquals(":app → :domain", finding.subject)
        assertEquals(listOf(":app", ":feature", ":domain"), finding.evidence.dependencyPath)
        assertTrue(finding.suggestedFix!!.contains("Remove the direct dependency"))
    }

    @Test
    fun `reports version conflicts with the declaring modules`() {
        val findings = ProjectFindingsAnalyzer.analyze(
            snapshot(
                declarations = mapOf(
                    ":app" to listOf("com.example:library:2.0"),
                    ":feature" to listOf("com.example:library:1.0"),
                ),
            ),
        )

        val finding = findings.single { it.id == FindingId.VERSION_CONFLICT }
        assertEquals("com.example:library", finding.subject)
        assertEquals(listOf(":app", ":feature"), finding.evidence.declarations)
    }

    private fun snapshot(
        graph: Map<String, List<String>> = mapOf(":app" to emptyList()),
        declarations: Map<String, List<String>> = emptyMap(),
    ) = AnalysisSnapshot(
        graph = ModuleGraph(graph),
        libraryInventory = LibraryInventoryAnalyzer.analyze(declarations, emptyList()),
    )
}
