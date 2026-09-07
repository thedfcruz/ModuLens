package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ModuleFindingsTask : DefaultTask() {
    @get:Input abstract val graph: MapProperty<String, List<String>>
    @get:Input abstract val libraryDeclarations: MapProperty<String, List<String>>
    @get:Input abstract val resolvedLibraries: ListProperty<String>
    @get:Optional @get:OutputFile abstract val textReport: RegularFileProperty
    @get:Optional @get:OutputFile abstract val jsonReport: RegularFileProperty

    @TaskAction
    fun action() {
        val findings = ProjectFindingsAnalyzer.analyze(
            AnalysisSnapshot(
                graph = ModuleGraph(graph.get()),
                libraryInventory = LibraryInventoryAnalyzer.analyze(libraryDeclarations.get(), resolvedLibraries.get()),
            ),
        )
        val output = buildString {
            appendLine()
            appendLine("ModuLens Findings")
            appendLine("────────────────────────────────────────")
            appendLine("Findings                     ${findings.size}")
            appendLine("Errors                       ${findings.count { it.severity == FindingSeverity.ERROR }}")
            appendLine("Warnings                     ${findings.count { it.severity == FindingSeverity.WARNING }}")
            findings.forEach { finding ->
                appendLine()
                appendLine("${finding.severity}: ${finding.id.ruleName}")
                appendLine("Subject: ${finding.subject}")
                appendLine(finding.message)
                finding.evidence.dependencyPath.takeIf { it.isNotEmpty() }
                    ?.let { appendLine("Path: ${it.joinToString(" → ")}") }
                finding.evidence.declarations.takeIf { it.isNotEmpty() }
                    ?.let { appendLine("Declarations: ${it.joinToString()}") }
                finding.suggestedFix?.let { appendLine("Suggested fix: $it") }
            }
        }
        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
        if (jsonReport.isPresent) ReportFileWriter.write(jsonReport.get().asFile, ReportJsonExporter.findings(findings))
    }
}
