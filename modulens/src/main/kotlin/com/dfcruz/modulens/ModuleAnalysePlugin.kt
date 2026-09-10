package com.dfcruz.modulens

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class ModuleAnalysePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create<ModuLensExtension>("moduLens")

        project.gradle.projectsEvaluated {
            val modules = ProjectModuleGraph.build(project.rootProject)
                .filterKeys { it !in extension.excludedModules.get() }
            val includedPaths = modules.keys
            val selectedScopes = extension.scopes.get()
            val graphsByScope = DependencyScope.entries.associateWith { scope ->
                modules.mapValues { (_, module) ->
                    module.projectDependencies
                        .filter { it.scope == scope }
                        .filter { it.path in includedPaths }
                        .map { it.path }
                        .distinct()
                        .sorted()
                }
            }
            val librariesByScope = DependencyScope.entries.associateWith { scope ->
                modules.mapValues { (_, module) ->
                    module.declaredLibraryDependencies
                        .filter { it.scope == scope }
                        .map { it.coordinate }
                        .distinct()
                        .sorted()
                }
            }
            val graph = modules.mapValues { (_, module) ->
                module.projectDependencies
                    .filter { it.scope in selectedScopes && it.path in includedPaths }
                    .map { it.path }
                    .distinct()
                    .sorted()
            }
            val declaredLibraries = modules.mapValues { (_, module) ->
                module.declaredLibraryDependencies
                    .filter { it.scope in selectedScopes }
                    .map { it.coordinate }
                    .distinct()
                    .sorted()
            }
            val directModuleDependencyDeclarations = modules.values
                .flatMap { module ->
                    module.projectDependencies
                        .filter { it.scope in selectedScopes && it.path in includedPaths }
                        .map { dependency ->
                            ReportContractCodec.moduleDependency(
                                DirectModuleDependencyDeclaration(
                                    from = module.path,
                                    to = dependency.path,
                                    configuration = dependency.configuration,
                                    scope = dependency.scope.optionName,
                                ),
                            )
                        }
                }
                .distinct()
                .sorted()
            val directLibraryDependencyDeclarations = modules.values
                .flatMap { module ->
                    module.declaredLibraryDependencies
                        .filter { it.scope in selectedScopes }
                        .map { dependency ->
                            ReportContractCodec.libraryDependency(
                                DirectLibraryDependencyDeclaration(
                                    module = module.path,
                                    identifier = dependency.moduleIdentifier,
                                    declaredVersion = dependency.version,
                                    configuration = dependency.configuration,
                                    scope = dependency.scope.optionName,
                                ),
                            )
                        }
                }
                .distinct()
                .sorted()
            val resolvedLibraries = project.provider {
                if (extension.analysis.resolveExternalLibraries.get()) {
                    ProjectModuleGraph.resolveLibraryCoordinates(
                        project,
                        declaredLibraries.values.flatten().distinct(),
                    )
                } else {
                    emptyList()
                }
            }
            val resolvedLibrariesByModule = project.provider {
                val moduleGraph = ModuleGraph(graph)
                graph.keys.associateWith { module ->
                    val libraryCoordinates = buildList {
                        addAll(declaredLibraries[module].orEmpty())
                        moduleGraph.transitiveDependencies(module)
                            .forEach { dependency -> addAll(declaredLibraries[dependency].orEmpty()) }
                    }.distinct()
                    if (extension.analysis.resolveExternalLibraries.get()) {
                        ProjectModuleGraph.resolveLibraryCoordinates(project, libraryCoordinates)
                    } else {
                        libraryCoordinates.sorted()
                    }
                }
            }

            val moduleTask = project.tasks.register<ModuleStatisticsTask>("moduLensModule") {
                group = "ModuLens"
                description = "Analyses dependencies, dependents, libraries, and findings for one module."
                this.graph.set(graph)
                this.declaredLibraries.set(declaredLibraries)
                this.scopedModuleDependencies.set(
                    this.module.map { moduleName ->
                        graphsByScope.mapKeys { (scope, _) -> scope.displayName }
                            .mapValues { (_, scopedGraph) -> scopedGraph[moduleName].orEmpty() }
                    },
                )
                this.scopedLibraries.set(
                    this.module.map { moduleName ->
                        librariesByScope.mapKeys { (scope, _) -> scope.displayName }
                            .mapValues { (_, scopedLibraries) -> scopedLibraries[moduleName].orEmpty() }
                    },
                )
                this.redundantModules.set(
                    this.module.map { moduleName ->
                        getRedundantModules(
                            graph = graph,
                            moduleName = moduleName,
                        )
                    },
                )
                this.redundantLibraries.set(
                    this.module.map { moduleName ->
                        getRedundantLibraries(
                            project = project,
                            graph = graph,
                            declaredLibraries = declaredLibraries,
                            moduleName = moduleName,
                        )
                    },
                )
                this.transitiveLibraries.set(
                    this.module.map { moduleName ->
                        val transitiveModules =
                            ModuleGraph(graph).transitiveDependencies(moduleName)
                        val libraryCoordinates = buildList {
                            addAll(declaredLibraries[moduleName].orEmpty())
                            transitiveModules.forEach { dependency ->
                                addAll(declaredLibraries[dependency].orEmpty())
                            }
                        }.distinct()

                        if (extension.analysis.resolveExternalLibraries.get()) {
                            ProjectModuleGraph.resolveTransitiveLibraryDependencies(project, libraryCoordinates)
                        } else {
                            emptyList()
                        }
                    },
                )
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(this.module.map { moduleName ->
                        project.layout.buildDirectory.file("reports/modulens/modules/${moduleName.trim(':').replace(':', '-')}.txt").get()
                    })
                }
            }

            val projectAnalysisTask = project.tasks.register<ModuleProjectAnalysis>("moduLensAnalyze") {
                group = "ModuLens"
                description = "Analyses the complete project module dependency graph."
                this.graph.set(graph)
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(project.layout.buildDirectory.file("reports/modulens/project.txt"))
                }
            }

            val libraryTask = project.tasks.register<ModuleLibraryInventoryTask>("moduLensLibraries") {
                group = "ModuLens"
                description = "Lists external library declarations and version alignment conflicts."
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(project.layout.buildDirectory.file("reports/modulens/libraries.txt"))
                }
            }

            val findingsTask = project.tasks.register<ModuleFindingsTask>("moduLensFindings") {
                group = "ModuLens"
                description = "Reports actionable dependency and module-structure findings."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                if (extension.analysis.exportText.get()) {
                    this.textReport.set(project.layout.buildDirectory.file("reports/modulens/findings.txt"))
                }
            }

            val dashboardTask = project.tasks.register<ModuleDashboardTask>("moduLensDashboard") {
                group = "ModuLens"
                description = "Generates an interactive HTML dashboard for module and dependency analysis."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                this.moduleLibraries.set(resolvedLibrariesByModule)
                this.moduleDependencyDeclarations.set(directModuleDependencyDeclarations)
                this.libraryDependencyDeclarations.set(directLibraryDependencyDeclarations)
                this.pluginId.set("com.dfcruz.modulens")
                this.gradleVersion.set(project.gradle.gradleVersion)
                this.includedScopes.set(selectedScopes.map(DependencyScope::optionName).sorted())
                this.excludedModules.set(extension.excludedModules.map { it.sorted().toList() })
                this.externalLibraryResolutionEnabled.set(extension.analysis.resolveExternalLibraries)
                this.outputDirectory.set(project.layout.buildDirectory.dir("reports/modulens/html"))
            }

            project.tasks.register<FullJsonReportTask>("moduLensReport") {
                group = "ModuLens"
                description = "Exports the complete ModuLens analysis as one JSON report for automation and AI review."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(resolvedLibraries)
                this.moduleLibraries.set(resolvedLibrariesByModule)
                this.moduleDependencyDeclarations.set(directModuleDependencyDeclarations)
                this.libraryDependencyDeclarations.set(directLibraryDependencyDeclarations)
                this.pluginId.set("com.dfcruz.modulens")
                this.gradleVersion.set(project.gradle.gradleVersion)
                this.includedScopes.set(selectedScopes.map(DependencyScope::optionName).sorted())
                this.excludedModules.set(extension.excludedModules.map { it.sorted().toList() })
                this.externalLibraryResolutionEnabled.set(extension.analysis.resolveExternalLibraries)
                this.outputFile.set(project.layout.buildDirectory.file("reports/modulens/report.json"))
            }

            if (extension.analysis.exportHtml.get()) {
                listOf(moduleTask, projectAnalysisTask, libraryTask, findingsTask).forEach { task ->
                    task.configure { finalizedBy(dashboardTask) }
                }
            }

            project.tasks.register<ModuleVerificationTask>("moduLensVerify") {
                group = "ModuLens"
                description = "Verifies configured ModuLens dependency and architecture policies for CI."
                this.graph.set(graph)
                this.libraryDeclarations.set(declaredLibraries)
                this.resolvedLibraries.set(project.provider {
                    if (extension.verification.failOnVersionConflicts.get()) {
                        ProjectModuleGraph.resolveLibraryCoordinates(project, declaredLibraries.values.flatten().distinct())
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

    private fun getRedundantModules(
        graph: Map<String, List<String>>,
        moduleName: String,
    ): Map<String, List<String>> {
        val directDependencies = graph[moduleName].orEmpty().distinct()

        return directDependencies
            .mapNotNull { dependency ->
                val alternativePaths = directDependencies
                    .filterNot { it == dependency }
                    .filter { alternative ->
                        dependency in ModuleGraph(graph).transitiveDependencies(alternative)
                    }

                dependency.takeIf { alternativePaths.isNotEmpty() }
                    ?.let { it to alternativePaths }
            }
            .toMap()
    }

    private fun getRedundantLibraries(
        project: Project,
        graph: Map<String, List<String>>,
        declaredLibraries: Map<String, List<String>>,
        moduleName: String,
    ): Map<String, List<String>> {
        val directLibraries = declaredLibraries[moduleName].orEmpty()
        if (directLibraries.isEmpty()) return emptyMap()

        val sources = linkedMapOf<String, MutableSet<String>>()
        val directLibraryIdentifiers = directLibraries
            .associateBy { libraryIdentifier(it) }
        val transitiveModules = ModuleGraph(graph).transitiveDependencies(moduleName)

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
            ProjectModuleGraph.resolveTransitiveLibraryDependencies(
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

    private fun libraryIdentifier(coordinate: String): String =
        coordinate.substringBeforeLast(":")
}
