package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class ModuleDashboardTask : DefaultTask() {
    @get:Input
    abstract val graph: MapProperty<String, List<String>>
    @get:Input
    abstract val libraryDeclarations: MapProperty<String, List<String>>
    @get:Input
    abstract val resolvedLibraries: ListProperty<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        val moduleGraph = ModuleGraph(graph.get())
        val libraries =
            LibraryInventoryAnalyzer.analyze(libraryDeclarations.get(), resolvedLibraries.get())
        val findings = ProjectFindingsAnalyzer.analyze(AnalysisSnapshot(moduleGraph, libraries))
        val dashboardData = ReportJsonExporter.dashboard(
            graph = moduleGraph,
            project = moduleGraph.analyzeProject(),
            libraries = libraries,
            findings = findings,
        )
        HtmlDashboardRenderer.write(outputDirectory.get().asFile, dashboardData)
        logger.lifecycle("ModuLens HTML dashboard: ${outputDirectory.get().asFile.resolve("index.html")}")
    }
}
