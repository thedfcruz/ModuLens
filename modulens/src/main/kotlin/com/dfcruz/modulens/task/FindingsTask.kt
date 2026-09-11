package com.dfcruz.modulens.task

import com.dfcruz.modulens.analysis.FindingAnalysisSnapshot
import com.dfcruz.modulens.analysis.Finding
import com.dfcruz.modulens.analysis.FindingSeverity
import com.dfcruz.modulens.analysis.LibraryInventoryAnalyzer
import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.analysis.ProjectFindingsAnalyzer
import com.dfcruz.modulens.report.ReportFileWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class FindingsTask : DefaultTask() {

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:Input
    abstract val libraryDeclarations: MapProperty<String, List<String>>

    @get:Input
    abstract val resolvedLibraries: ListProperty<String>

    @get:Optional
    @get:OutputFile
    abstract val textReport: RegularFileProperty

    @TaskAction
    fun action() {
        val findings = ProjectFindingsAnalyzer.analyze(
            FindingAnalysisSnapshot(
                graph = ModuleGraph(graph.get()),
                libraryInventory = LibraryInventoryAnalyzer.analyze(
                    libraryDeclarations.get(),
                    resolvedLibraries.get()
                ),
            ),
        )
        val output = render(findings)
        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
    }

    private fun render(findings: List<Finding>): String = buildString {
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
            finding.evidence.dependencyPath.takeIf { it.isNotEmpty() }?.let { appendLine("Path: ${it.joinToString(" → ")}") }
            finding.evidence.declarations.takeIf { it.isNotEmpty() }?.let { appendLine("Declarations: ${it.joinToString()}") }
            finding.suggestedFix?.let { appendLine("Suggested fix: $it") }
        }
    }
}
