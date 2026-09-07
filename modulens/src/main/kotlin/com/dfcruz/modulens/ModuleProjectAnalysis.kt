package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ModuleProjectAnalysis : DefaultTask() {

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:Optional
    @get:OutputFile
    abstract val textReport: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val jsonReport: RegularFileProperty

    @TaskAction
    fun action() {
        val analysis = ModuleGraph(graph.get()).analyzeProject()

        val output = buildString {
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

            if (analysis.topDependencies.isEmpty()) {
                appendLine("None")
            } else {
                analysis.topDependencies.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. ${entry.first}  ${entry.second}")
                }
            }

            appendLine()
            appendLine("Highest Fan-in")
            appendLine("────────────────────────────────────────")

            if (analysis.topDependents.isEmpty()) {
                appendLine("None")
            } else {
                analysis.topDependents.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. ${entry.first}  ${entry.second}")
                }
            }

            analysis.moduleWithMostDependencies?.let {
                appendLine()
                appendLine("Most dependencies             ${it.first} (${it.second})")
            }

            analysis.moduleWithMostDependents?.let {
                appendLine("Most dependents               ${it.first} (${it.second})")
            }

            appendLine()
            appendLine("Dependency Cycles")
            appendLine("────────────────────────────────────────")

            if (analysis.cycles.isEmpty()) {
                appendLine("None")
            } else {
                analysis.cycles.forEachIndexed { index, cycle ->
                    appendLine("${index + 1}. ${cycle.joinToString(" → ")}")
                }
            }
        }

        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
        if (jsonReport.isPresent) ReportFileWriter.write(
            jsonReport.get().asFile,
            ReportJsonExporter.project(analysis)
        )
    }

}
