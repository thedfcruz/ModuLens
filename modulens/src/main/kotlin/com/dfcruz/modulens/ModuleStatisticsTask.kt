package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class ModuleStatisticsTask : DefaultTask() {

    @get:Input
    @get:Option(
        option = "module",
        description = "The module path to analyse, for example ':core:database'.",
    )
    abstract val module: Property<String>

    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:Input
    abstract val declaredLibraries: MapProperty<String, List<String>>

    @get:Input
    abstract val transitiveLibraries: ListProperty<String>

    @get:Input
    abstract val redundantModules: MapProperty<String, List<String>>

    @get:Input
    abstract val redundantLibraries: MapProperty<String, List<String>>

    @get:Input
    abstract val scopedModuleDependencies: MapProperty<String, List<String>>

    @get:Input
    abstract val scopedLibraries: MapProperty<String, List<String>>

    @get:Optional
    @get:OutputFile
    abstract val textReport: RegularFileProperty
    @get:Optional
    @get:OutputFile
    abstract val jsonReport: RegularFileProperty

    @TaskAction
    fun action() {
        val moduleName = module.get()
        val moduleGraph = graph.get()
        val analysis = ModuleGraph(moduleGraph).analyzeModule(moduleName)
        val moduleDeclaredLibraries = declaredLibraries.get()[moduleName].orEmpty()
        val moduleTransitiveLibraries = transitiveLibraries.get()
        val moduleRedundantModules = redundantModules.get()
        val moduleRedundantLibraries = redundantLibraries.get()
        val moduleScopedDependencies = scopedModuleDependencies.get()
        val moduleScopedLibraries = scopedLibraries.get()

        val inheritedLibrariesByModule = analysis.allDependencies
            .sorted()
            .associateWith { dependency ->
                declaredLibraries.get()[dependency].orEmpty()
            }
            .filterValues { libraries -> libraries.isNotEmpty() }
        val inheritedLibraryCount = inheritedLibrariesByModule
            .values
            .flatten()
            .distinct()
            .size

        val output = buildString {
            appendLine()
            appendLine("Module Statistics")
            appendLine("────────────────────────────────────────")
            appendLine("Module: $moduleName")
            appendLine()

            appendLine("Dependencies")
            appendLine("────────────────────────────────────────")
            appendLine("Direct dependencies      ${analysis.directDependencies.size}")
            appendLine("Transitive dependencies  ${analysis.transitiveDependencies.size}")
            appendLine("All dependencies         ${analysis.allDependencies.size}")
            appendLine("Dependency depth         ${analysis.dependencyDepth}")
            appendLine()

            appendLine("Dependents")
            appendLine("────────────────────────────────────────")
            appendLine("Direct dependents        ${analysis.directDependents.size}")
            appendLine("All dependents           ${analysis.allDependents.size}")
            appendLine("Dependent depth          ${analysis.dependentDepth}")
            appendLine("Project coverage         ${"%.1f".format(analysis.projectCoverage)}%")

            appendLine()
            appendLine("Impact Analysis")
            appendLine("────────────────────────────────────────")
            appendLine("Directly affected modules ${analysis.directDependents.size}")
            appendLine("Total affected modules    ${analysis.allDependents.size}")
            appendLine("Project impact             ${"%.1f".format(analysis.projectCoverage)}%")
            appendLine()

            appendLine("Classification")
            appendLine("────────────────────────────────────────")
            appendLine("Leaf module              ${yesNo(analysis.isLeaf)}")
            appendLine("Root module              ${yesNo(analysis.isRoot)}")
            appendLine("Isolated module          ${yesNo(analysis.isIsolated)}")
            appendLine("Dependency cycles        ${analysis.cycles.size}")

            appendLine()
            appendLine("External Libraries")
            appendLine("────────────────────────────────────────")
            appendLine("Declared directly        ${moduleDeclaredLibraries.size}")
            appendLine("Inherited from modules   $inheritedLibraryCount")
            appendLine("Added transitively       ${moduleTransitiveLibraries.size}")

            val additionalScopes = moduleScopedDependencies
                .filterKeys { it != DependencyScope.PRODUCTION.displayName }
                .filterValues { it.isNotEmpty() }
            val additionalLibraryScopes = moduleScopedLibraries
                .filterKeys { it != DependencyScope.PRODUCTION.displayName }
                .filterValues { it.isNotEmpty() }

            appendLine()
            appendLine("Additional Dependency Scopes")
            appendLine("────────────────────────────────────────")
            if (additionalScopes.isEmpty() && additionalLibraryScopes.isEmpty()) {
                appendLine("None")
            } else {
                DependencyScope.entries
                    .filter { it != DependencyScope.PRODUCTION }
                    .forEach { scope ->
                        val modules = additionalScopes[scope.displayName].orEmpty()
                        val libraries = additionalLibraryScopes[scope.displayName].orEmpty()
                        if (modules.isNotEmpty() || libraries.isNotEmpty()) {
                            appendLine("${scope.displayName} modules           ${modules.size}")
                            appendLine("${scope.displayName} libraries         ${libraries.size}")
                        }
                    }
            }

            if (analysis.directDependencies.isNotEmpty()) {
                appendLine()
                appendLine("Direct dependencies")
                appendLine("────────────────────────────────────────")

                analysis.directDependencies
                    .sorted()
                    .forEach {
                        appendLine(" → $it")
                    }
            }

            if (analysis.directDependents.isNotEmpty()) {
                appendLine()
                appendLine("Direct dependents")
                appendLine("────────────────────────────────────────")

                analysis.directDependents
                    .sorted()
                    .forEach {
                        appendLine(" ← $it")
                    }
            }

            if (analysis.allDependents.isNotEmpty()) {
                appendLine()
                appendLine("Affected modules")
                appendLine("────────────────────────────────────────")

                analysis.allDependents
                    .sorted()
                    .forEach { affectedModule ->
                        appendLine(" ← $affectedModule")
                    }
            }

            if (analysis.transitiveDependencies.isNotEmpty()) {
                appendLine()
                appendLine("Transitive module dependencies")
                appendLine("────────────────────────────────────────")

                analysis.transitiveDependencies
                    .sorted()
                    .forEach {
                        appendLine(" → $it")
                    }
            }

            if (moduleDeclaredLibraries.isNotEmpty()) {
                appendLine()
                appendLine("Declared libraries")
                appendLine("────────────────────────────────────────")

                moduleDeclaredLibraries.forEach {
                    appendLine(" → $it")
                }
            }

            if (moduleTransitiveLibraries.isNotEmpty()) {
                appendLine()
                appendLine("Transitive libraries")
                appendLine("────────────────────────────────────────")

                moduleTransitiveLibraries.forEach {
                    appendLine(" → $it")
                }
            }

            if (inheritedLibrariesByModule.isNotEmpty()) {
                appendLine()
                appendLine("Libraries inherited from modules")
                appendLine("────────────────────────────────────────")

                inheritedLibrariesByModule.forEach { (dependency, libraries) ->
                    appendLine(dependency)
                    libraries.forEach { library ->
                        appendLine(" → $library")
                    }
                }
            }

            if (analysis.cycles.isNotEmpty()) {
                appendLine()
                appendLine("Dependency Cycles")
                appendLine("────────────────────────────────────────")

                analysis.cycles.forEachIndexed { index, cycle ->
                    appendLine("${index + 1}. ${cycle.joinToString(" → ")}")
                }
            }

            if (moduleRedundantModules.isNotEmpty() || moduleRedundantLibraries.isNotEmpty()) {
                appendLine()
                appendLine("Potentially redundant declarations")
                appendLine("────────────────────────────────────────")
                appendLine("These declarations may be intentional when they make a direct API or architectural relationship explicit.")

                if (moduleRedundantModules.isNotEmpty()) {
                    appendLine()
                    appendLine("Modules")

                    moduleRedundantModules.forEach { (dependency, alternatives) ->
                        appendLine(" → $dependency")
                        alternatives.forEach { alternative ->
                            appendLine("   also reachable through $alternative")
                        }
                    }
                }

                if (moduleRedundantLibraries.isNotEmpty()) {
                    appendLine()
                    appendLine("Libraries")

                    moduleRedundantLibraries.forEach { (library, sources) ->
                        appendLine(" → $library")
                        sources.forEach { source ->
                            appendLine("   $source")
                        }
                    }
                }
            }
        }

        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
        if (jsonReport.isPresent) ReportFileWriter.write(
            jsonReport.get().asFile,
            ReportJsonExporter.module(analysis)
        )
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"
}
