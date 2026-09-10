package com.dfcruz.modulens

import com.dfcruz.modulens.dashboard.HtmlDashboardRenderer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
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

    @get:Input
    abstract val moduleLibraries: MapProperty<String, List<String>>

    @get:Input
    abstract val moduleDependencyDeclarations: ListProperty<String>

    @get:Input
    abstract val libraryDependencyDeclarations: ListProperty<String>

    @get:Input
    abstract val pluginId: Property<String>

    @get:Input
    abstract val gradleVersion: Property<String>

    @get:Input
    abstract val includedScopes: ListProperty<String>

    @get:Input
    abstract val excludedModules: ListProperty<String>

    @get:Input
    abstract val externalLibraryResolutionEnabled: Property<Boolean>

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
            moduleLibraries = moduleLibraries.get(),
            metadata = ReportMetadata(
                pluginId = pluginId.get(),
                gradleVersion = gradleVersion.get(),
                includedScopes = includedScopes.get().sorted(),
                excludedModules = excludedModules.get().sorted(),
                externalLibraryResolutionEnabled = externalLibraryResolutionEnabled.get(),
            ),
            directModuleDependencies = moduleDependencyDeclarations.get()
                .map(ReportContractCodec::moduleDependency),
            directLibraryDeclarations = libraryDependencyDeclarations.get()
                .map(ReportContractCodec::libraryDependency),
        )
        HtmlDashboardRenderer.write(outputDirectory.get().asFile, dashboardData)
        logger.lifecycle("ModuLens HTML dashboard: ${outputDirectory.get().asFile.resolve("index.html")}")
    }
}
