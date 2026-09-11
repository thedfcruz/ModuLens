package com.dfcruz.modulens.task


import com.dfcruz.modulens.report.ReportAnalysisInput
import com.dfcruz.modulens.report.ReportAnalysisSnapshotBuilder
import com.dfcruz.modulens.report.ReportFileWriter
import com.dfcruz.modulens.report.ReportJsonExporter
import com.dfcruz.modulens.report.ReportMetadata
import com.dfcruz.modulens.report.ReportOptions
import com.dfcruz.modulens.analysis.FindingSeverity
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class FullReportTask : DefaultTask() {
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

    @get:Input
    abstract val minimumFindingSeverity: org.gradle.api.provider.Property<FindingSeverity>

    @get:Input
    abstract val includeResolvedLibraries: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val includeTransitiveModuleRelationships: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val includeLibraryUsageModules: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val ignoredLibraryPatterns: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun action() {
        val snapshot = ReportAnalysisSnapshotBuilder.build(
            ReportAnalysisInput(
                graph = graph.get(),
                libraryDeclarations = libraryDeclarations.get(),
                resolvedLibraries = resolvedLibraries.get(),
                moduleLibraries = moduleLibraries.get(),
                metadata = ReportMetadata(
                    pluginId = pluginId.get(),
                    gradleVersion = gradleVersion.get(),
                    includedScopes = includedScopes.get(),
                    excludedModules = excludedModules.get(),
                    externalLibraryResolutionEnabled = externalLibraryResolutionEnabled.get(),
                    options = ReportOptions(
                        minimumFindingSeverity = minimumFindingSeverity.get(),
                        includeResolvedLibraries = includeResolvedLibraries.get(),
                        includeTransitiveModuleRelationships = includeTransitiveModuleRelationships.get(),
                        includeLibraryUsageModules = includeLibraryUsageModules.get(),
                        ignoredLibraryPatterns = ignoredLibraryPatterns.get().toSet(),
                    ),
                ),
                directModuleDependencies = moduleDependencyDeclarations.get()
                    .map(TaskInputCodec::moduleDependency),
                directLibraryDeclarations = libraryDependencyDeclarations.get()
                    .map(TaskInputCodec::libraryDependency),
            ),
        )
        ReportFileWriter.write(
            outputFile.get().asFile,
            ReportJsonExporter.fullReport(snapshot),
        )
        logger.lifecycle("ModuLens full JSON report: ${outputFile.get().asFile}")
    }
}
