package com.dfcruz.modulens.report

import com.dfcruz.modulens.analysis.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportJsonExporterTest {
    @Test
    fun `exports the required report contract fields and structured finding references`() {
        val snapshot = ReportAnalysisSnapshotBuilder.build(
            ReportAnalysisInput(
                graph = mapOf(
                    ":app" to listOf(":feature", ":domain"),
                    ":feature" to listOf(":domain"),
                    ":domain" to emptyList(),
                ),
                libraryDeclarations = emptyMap(),
                resolvedLibraries = emptyList(),
                moduleLibraries = emptyMap(),
                metadata = ReportMetadata(
                    pluginId = "com.dfcruz.modulens",
                    gradleVersion = "9.5",
                    includedScopes = listOf("production"),
                    excludedModules = emptyList(),
                    externalLibraryResolutionEnabled = false,
                ),
                directModuleDependencies = listOf(
                    DirectModuleDependencyDeclaration(":app", ":domain", "implementation", "production"),
                ),
                directLibraryDeclarations = listOf(
                    DirectLibraryDependencyDeclaration(
                        module = ":app",
                        identifier = "com.example:library",
                        declaredVersion = "1.0",
                        configuration = "implementation",
                        scope = "production",
                    ),
                ),
            ),
        )

        val report = Json.parseToJsonElement(ReportJsonExporter.fullReport(snapshot)).jsonObject

        assertEquals("1", report.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("modulens-full-report", report.getValue("reportType").jsonPrimitive.content)
        assertEquals(
            "skipped",
            report.getValue("metadata").jsonObject
                .getValue("externalLibraryResolution").jsonObject
                .getValue("status").jsonPrimitive.content,
        )
        assertEquals(1, report.getValue("directModuleDependencies").jsonArray.size)
        assertEquals(1, report.getValue("directLibraryDeclarations").jsonArray.size)
        val finding = report.getValue("findings").jsonObject
            .getValue("findings").jsonArray
            .first { it.jsonObject.getValue("id").jsonPrimitive.content == "REDUNDANT_MODULE_DEPENDENCY" }
            .jsonObject
        assertTrue(finding.getValue("references").jsonObject.getValue("modules").jsonArray.isNotEmpty())
        assertTrue(ReportJsonExporter.fullReport(snapshot).contains("\n    \"metadata\""))
    }

    @Test
    fun `omits expensive report sections and warnings when configured`() {
        val snapshot = ReportAnalysisSnapshotBuilder.build(
            ReportAnalysisInput(
                graph = mapOf(
                    ":app" to listOf(":feature", ":domain"),
                    ":feature" to listOf(":domain"),
                    ":domain" to emptyList(),
                ),
                libraryDeclarations = emptyMap(),
                resolvedLibraries = emptyList(),
                moduleLibraries = mapOf(":app" to listOf("com.example:library:1.0")),
                metadata = ReportMetadata(
                    pluginId = "com.dfcruz.modulens",
                    gradleVersion = "9.5",
                    includedScopes = listOf("production"),
                    excludedModules = emptyList(),
                    externalLibraryResolutionEnabled = false,
                    options = ReportOptions(minimumFindingSeverity = FindingSeverity.ERROR),
                ),
                directModuleDependencies = emptyList(),
                directLibraryDeclarations = emptyList(),
            ),
        )

        val report = Json.parseToJsonElement(ReportJsonExporter.fullReport(snapshot)).jsonObject
        val app = report.getValue("modules").jsonArray
            .first { it.jsonObject.getValue("path").jsonPrimitive.content == ":app" }
            .jsonObject

        assertTrue(app.getValue("transitiveDependencies").jsonArray.isEmpty())
        assertTrue(app.getValue("affectedModules").jsonArray.isEmpty())
        assertTrue(app.getValue("libraries").jsonArray.isEmpty())
        assertTrue(report.getValue("findings").jsonObject.getValue("findings").jsonArray.isEmpty())
    }
}
