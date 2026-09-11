package com.dfcruz.modulens.task

import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.analysis.ProjectGraphAnalysis
import com.dfcruz.modulens.report.ReportFileWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ProjectAnalysisTask : DefaultTask() {

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:Optional
    @get:OutputFile
    abstract val textReport: RegularFileProperty

    @TaskAction
    fun action() {
        val output = render(ModuleGraph(graph.get()).analyzeProject())
        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
    }

    private fun render(analysis: ProjectGraphAnalysis): String = buildString {
        appendLine()
        appendLine("Project Module Statistics")
        appendLine("────────────────────────────────────────")
        appendLine("Modules                       ${analysis.totalModules}")
        appendLine("Dependency edges              ${analysis.totalDependencies}")
        appendLine("Average dependencies          ${"%.1f".format(analysis.averageDependencies)}")
        appendLine("Average dependents            ${"%.1f".format(analysis.averageDependencies)}")
        appendLine("Maximum dependency depth      ${analysis.maximumDependencyDepth}")
        appendLine("Dependency cycles             ${analysis.cycles.size}")
        appendLine()
        appendLine("Module Classification")
        appendLine("────────────────────────────────────────")
        appendLine("Leaf modules                  ${analysis.leafModules}")
        appendLine("Root modules                  ${analysis.rootModules}")
        appendLine("Isolated modules              ${analysis.isolatedModules}")
        appendLine()
        appendLine("Highest Fan-out")
        appendLine("────────────────────────────────────────")
        appendRankedEntries(analysis.topDependencies)
        appendLine()
        appendLine("Highest Fan-in")
        appendLine("────────────────────────────────────────")
        appendRankedEntries(analysis.topDependents)
        analysis.moduleWithMostDependencies?.let {
            appendLine()
            appendLine("Most dependencies             ${it.first} (${it.second})")
        }
        analysis.moduleWithMostDependents?.let { appendLine("Most dependents               ${it.first} (${it.second})") }
        appendLine()
        appendLine("Dependency Cycles")
        appendLine("────────────────────────────────────────")
        if (analysis.cycles.isEmpty()) appendLine("None")
        else analysis.cycles.forEachIndexed { index, cycle -> appendLine("${index + 1}. ${cycle.joinToString(" → ")}") }
    }

    private fun StringBuilder.appendRankedEntries(entries: List<Pair<String, Int>>) {
        if (entries.isEmpty()) appendLine("None")
        else entries.forEachIndexed { index, entry -> appendLine("${index + 1}. ${entry.first}  ${entry.second}") }
    }
}
