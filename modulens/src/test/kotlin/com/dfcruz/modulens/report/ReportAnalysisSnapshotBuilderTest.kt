package com.dfcruz.modulens.report

import com.dfcruz.modulens.analysis.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAnalysisSnapshotBuilderTest {
    @Test
    fun `builds a complete deterministic analysis snapshot`() {
        val snapshot = ReportAnalysisSnapshotBuilder.build(
            ReportAnalysisInput(
                graph = mapOf(
                    ":app" to listOf(":feature", ":domain"),
                    ":feature" to listOf(":domain"),
                    ":domain" to emptyList(),
                ),
                libraryDeclarations = mapOf(
                    ":app" to listOf("com.example:library:2.0"),
                    ":feature" to listOf("com.example:library:1.0"),
                ),
                resolvedLibraries = emptyList(),
                moduleLibraries = mapOf(":app" to listOf("b:library:1", "a:library:1", "a:library:1")),
                metadata = ReportMetadata(
                    pluginId = "com.dfcruz.modulens",
                    gradleVersion = "9.5",
                    includedScopes = listOf("production"),
                    excludedModules = emptyList(),
                    externalLibraryResolutionEnabled = false,
                ),
                directModuleDependencies = emptyList(),
                directLibraryDeclarations = emptyList(),
            ),
        )

        assertEquals(listOf("a:library:1", "b:library:1"), snapshot.moduleLibraries.getValue(":app"))
        assertEquals(3, snapshot.project.totalModules)
        assertTrue(snapshot.findings.any { it.id == FindingId.REDUNDANT_MODULE_DEPENDENCY })
        assertTrue(snapshot.findings.any { it.id == FindingId.VERSION_CONFLICT })
    }
}
