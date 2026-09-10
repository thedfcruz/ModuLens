package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class FullJsonReportTask : DefaultTask() {
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
    abstract val pluginId: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val gradleVersion: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val includedScopes: ListProperty<String>

    @get:Input
    abstract val excludedModules: ListProperty<String>

    @get:Input
    abstract val externalLibraryResolutionEnabled: org.gradle.api.provider.Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        val moduleGraph = ModuleGraph(graph.get())
        val libraries = LibraryInventoryAnalyzer.analyze(
            declarations = libraryDeclarations.get(),
            resolvedLibraries = resolvedLibraries.get(),
        )
        val findings = ProjectFindingsAnalyzer.analyze(AnalysisSnapshot(moduleGraph, libraries))
        ReportFileWriter.write(
            outputFile.get().asFile,
            ReportJsonExporter.fullReport(
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
            ),
        )
        logger.lifecycle("ModuLens full JSON report: ${outputFile.get().asFile}")
    }
}
