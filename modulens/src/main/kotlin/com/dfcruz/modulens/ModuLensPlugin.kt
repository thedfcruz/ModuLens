package com.dfcruz.modulens

import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.gradle.ExternalLibraryResolver
import com.dfcruz.modulens.gradle.GradleProjectDependencyCollector
import com.dfcruz.modulens.task.DashboardTask
import com.dfcruz.modulens.task.FindingsTask
import com.dfcruz.modulens.task.FullReportTask
import com.dfcruz.modulens.task.LibraryInventoryTask
import com.dfcruz.modulens.task.ModuleAnalysisTask
import com.dfcruz.modulens.task.ProjectAnalysisTask
import com.dfcruz.modulens.task.TaskInputCodec
import com.dfcruz.modulens.task.VerificationTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class ModuLensPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create<ModuLensExtension>("moduLens")
        project.gradle.projectsEvaluated {
            val selectedScopes = extension.scopes.get()
            val projectDependencyGraph =
                GradleProjectDependencyCollector.collect(project.rootProject)
                    .excluding(extension.excludedModules.get())
            val moduleGraph = projectDependencyGraph.moduleGraph(selectedScopes)
            val graph = moduleGraph.dependenciesByModule
            val declaredLibraries = projectDependencyGraph.declaredLibraries(selectedScopes)
            val resolvedLibraries = project.provider {
                if (extension.analysis.resolveExternalLibraries.get()) {
                    ExternalLibraryResolver.resolveLibraryCoordinates(
                        project,
                        declaredLibraries.values.flatten().distinct(),
                    )
                } else {
                    emptyList()
                }
            }

            val moduleTask = project.tasks.register<ModuleAnalysisTask>("moduLensModule") {
                group = "ModuLens"
                description =
                    "Analyses dependencies, dependents, libraries, and findings for one module."
                this.graph.set(graph)
                this.declaredLibraries.set(declaredLibraries)
                this.scopedModuleDependencies.set(
                    this.module.map { moduleName ->
                        DependencyScope.entries.associate { scope ->
                            scope.displayName to projectDependencyGraph.moduleDependencies(
                                moduleName,
                                scope
                            )
                        }
                    },
                )
                this.scopedLibraries.set(
                    this.module.map { moduleName ->
                        DependencyScope.entries.associate { scope ->
                            scope.displayName to projectDependencyGraph.libraryDependencies(
                                moduleName,
                                scope
                            )
                        }
                    },
                )
                this.redundantModules.set(
                    this.module.map { moduleName ->
                        moduleGraph.redundantDirectDependencies(moduleName)
                    },
                )
                this.redundantLibraries.set(
                    this.module.map { moduleName ->
                        findRedundantLibraryDependencies(
                            project = project,
                            moduleGraph = moduleGraph,
                            declaredLibraries = declaredLibraries,
                            moduleName = moduleName,
                        )
                    },
                )
                this.transitiveLibraries.set(
                    this.module.map { moduleName ->
                        val libraryCoordinates = projectDependencyGraph
                            .libraryCoordinatesUsedBy(moduleName, selectedScopes)

                        if (extension.analysis.resolveExternalLibraries.get()) {
                            ExternalLibraryResolver.resolveTransitiveLibraryDependencies(
                                project,
                                libraryCoordinates
                            )
                        } else {
                            emptyList()
                        }
                    },
                )
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(this.module.map { moduleName ->
                        project.layout.buildDirectory.file(
                            "reports/modulens/modules/${
                                moduleName.trim(
                                    ':'
                                ).replace(':', '-')
                            }.txt"
                        ).get()
                    })
                }
            }

            val projectAnalysisTask =
                project.tasks.register<ProjectAnalysisTask>("moduLensAnalyze") {
                    group = "ModuLens"
                    description = "Analyses the complete project module dependency graph."
                    this.graph.set(graph)
                    if (extension.analysis.exportText.get()) {
                        this.textReport.set(project.layout.buildDirectory.file("reports/modulens/project.txt"))
                    }
                }

            val libraryTask = project.tasks.register<LibraryInventoryTask>("moduLensLibraries") {
                group = "ModuLens"
                description = "Lists external library declarations and version alignment conflicts."
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(project.layout.buildDirectory.file("reports/modulens/libraries.txt"))
                }
            }

            val findingsTask = project.tasks.register<FindingsTask>("moduLensFindings") {
                group = "ModuLens"
                description = "Reports actionable dependency and module-structure findings."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(project.layout.buildDirectory.file("reports/modulens/findings.txt"))
                }
            }

            val fullReportTask = project.tasks.register<FullReportTask>("moduLensReport") {
                group = "ModuLens"
                description =
                    "Exports the complete ModuLens analysis as one JSON report for automation and AI review."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                this.moduleLibraries.set(project.provider {
                    graph.keys.associateWith { module ->
                        val libraryCoordinates = projectDependencyGraph
                            .libraryCoordinatesUsedBy(module, selectedScopes)
                        if (extension.analysis.resolveExternalLibraries.get()) {
                            ExternalLibraryResolver.resolveLibraryCoordinates(
                                project,
                                libraryCoordinates
                            )
                        } else {
                            libraryCoordinates.sorted()
                        }
                    }
                })
                this.moduleDependencyDeclarations.set(
                    projectDependencyGraph.directModuleDeclarations(selectedScopes)
                        .map(TaskInputCodec::moduleDependency),
                )
                this.libraryDependencyDeclarations.set(
                    projectDependencyGraph.directLibraryDeclarations(selectedScopes)
                        .map(TaskInputCodec::libraryDependency),
                )
                this.pluginId.set("com.dfcruz.modulens")
                this.gradleVersion.set(project.gradle.gradleVersion)
                this.includedScopes.set(selectedScopes.map(DependencyScope::optionName).sorted())
                this.excludedModules.set(extension.excludedModules.map { it.sorted().toList() })
                this.externalLibraryResolutionEnabled.set(extension.analysis.resolveExternalLibraries)
                this.outputFile.set(project.layout.buildDirectory.file("reports/modulens/report.json"))
            }

            val dashboardTask = project.tasks.register<DashboardTask>("moduLensDashboard") {
                group = "ModuLens"
                description =
                    "Generates an interactive HTML dashboard from the complete ModuLens report."
                dependsOn(fullReportTask)
                this.reportFile.set(fullReportTask.flatMap { it.outputFile })
                this.outputDirectory.set(project.layout.buildDirectory.dir("reports/modulens/html"))
            }

            if (extension.analysis.exportHtml.get()) {
                listOf(moduleTask, projectAnalysisTask, libraryTask, findingsTask).forEach { task ->
                    task.configure { finalizedBy(dashboardTask) }
                }
            }

            project.tasks.register<VerificationTask>("moduLensVerify") {
                group = "ModuLens"
                description =
                    "Verifies configured ModuLens dependency and architecture policies for CI."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(project.provider {
                    if (extension.verification.failOnVersionConflicts.get()) {
                        ExternalLibraryResolver.resolveLibraryCoordinates(
                            project,
                            projectDependencyGraph.libraryCoordinates(selectedScopes)
                        )
                    } else emptyList()
                })
                this.failOnCycles.set(extension.verification.failOnCycles)
                this.maxDependencyDepth.set(extension.verification.maxDependencyDepth)
                this.maxDirectDependencies.set(extension.verification.maxDirectDependencies)
                this.failOnVersionConflicts.set(extension.verification.failOnVersionConflicts)
                this.failOnRedundantDependencies.set(extension.verification.failOnRedundantDependencies)
            }
        }
    }

    private fun findRedundantLibraryDependencies(
        project: Project,
        moduleGraph: ModuleGraph,
        declaredLibraries: Map<String, List<String>>,
        moduleName: String,
    ): Map<String, List<String>> {
        val directLibraries = declaredLibraries[moduleName].orEmpty()
        if (directLibraries.isEmpty()) return emptyMap()

        val sources = linkedMapOf<String, MutableSet<String>>()
        val directLibraryIdentifiers = directLibraries
            .associateBy { libraryIdentifier(it) }
        val transitiveModules = moduleGraph.transitiveDependencies(moduleName)

        transitiveModules.forEach { dependency ->
            declaredLibraries[dependency].orEmpty().forEach { library ->
                directLibraryIdentifiers[libraryIdentifier(library)]?.let { directLibrary ->
                    sources
                        .getOrPut(directLibrary) { linkedSetOf() }
                        .add("declared by $dependency as $library")
                }
            }
        }

        directLibraries.forEach { library ->
            ExternalLibraryResolver.resolveTransitiveLibraryDependencies(
                project = project,
                declaredLibraryCoordinates = listOf(library),
            ).forEach { transitiveLibrary ->
                directLibraryIdentifiers[libraryIdentifier(transitiveLibrary)]?.let { directLibrary ->
                    if (directLibrary != library) {
                        sources
                            .getOrPut(directLibrary) { linkedSetOf() }
                            .add("brought by $library as $transitiveLibrary")
                    }
                }
            }
        }

        return sources.mapValues { (_, source) -> source.sorted() }
    }

    private fun libraryIdentifier(coordinate: String): String = coordinate.substringBeforeLast(":")
}
