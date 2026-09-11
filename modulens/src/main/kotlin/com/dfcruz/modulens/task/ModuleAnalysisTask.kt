package com.dfcruz.modulens.task

import com.dfcruz.modulens.DependencyScope
import com.dfcruz.modulens.analysis.FocusModuleAnalysis
import com.dfcruz.modulens.analysis.ModuleGraph
import com.dfcruz.modulens.report.ReportFileWriter
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

abstract class ModuleAnalysisTask : DefaultTask() {

    @get:Input
    @get:Option(
        option = "module",
        description = "The module path to analyse, for example ':core:database'."
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

    @TaskAction
    fun action() {
        val moduleName = module.get()
        val moduleGraph = ModuleGraph(graph.get())
        val analysis = moduleGraph.analyzeModule(moduleName)
        val inheritedLibrariesByModule = analysis.allDependencies
            .sorted()
            .associateWith { dependency -> declaredLibraries.get()[dependency].orEmpty() }
            .filterValues { it.isNotEmpty() }

        val output = render(
            analysis = analysis,
            declaredLibraries = declaredLibraries.get()[moduleName].orEmpty(),
            transitiveLibraries = transitiveLibraries.get(),
            inheritedLibrariesByModule = inheritedLibrariesByModule,
            scopedModuleDependencies = scopedModuleDependencies.get(),
            scopedLibraries = scopedLibraries.get(),
            redundantModules = redundantModules.get(),
            redundantLibraries = redundantLibraries.get(),
        )

        logger.lifecycle(output)
        if (textReport.isPresent) ReportFileWriter.write(textReport.get().asFile, output)
    }

    private fun render(
        analysis: FocusModuleAnalysis,
        declaredLibraries: List<String>,
        transitiveLibraries: List<String>,
        inheritedLibrariesByModule: Map<String, List<String>>,
        scopedModuleDependencies: Map<String, List<String>>,
        scopedLibraries: Map<String, List<String>>,
        redundantModules: Map<String, List<String>>,
        redundantLibraries: Map<String, List<String>>,
    ): String = buildString {
        val inheritedLibraryCount = inheritedLibrariesByModule.values.flatten().distinct().size
        appendLine()
        appendLine("Module Statistics")
        appendLine("────────────────────────────────────────")
        appendLine("Module: ${analysis.module}")
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
        appendLine("Declared directly        ${declaredLibraries.size}")
        appendLine("Inherited from modules   $inheritedLibraryCount")
        appendLine("Added transitively       ${transitiveLibraries.size}")
        appendAdditionalScopes(scopedModuleDependencies, scopedLibraries)
        appendList("Direct dependencies", analysis.directDependencies, " → ")
        appendList("Direct dependents", analysis.directDependents, " ← ")
        appendList("Affected modules", analysis.allDependents, " ← ")
        appendList("Transitive module dependencies", analysis.transitiveDependencies, " → ")
        appendList("Declared libraries", declaredLibraries, " → ")
        appendList("Transitive libraries", transitiveLibraries, " → ")
        if (inheritedLibrariesByModule.isNotEmpty()) {
            appendLine()
            appendLine("Libraries inherited from modules")
            appendLine("────────────────────────────────────────")
            inheritedLibrariesByModule.toSortedMap().forEach { (dependency, libraries) ->
                appendLine(dependency)
                libraries.sorted().forEach { appendLine(" → $it") }
            }
        }
        if (analysis.cycles.isNotEmpty()) {
            appendLine()
            appendLine("Dependency Cycles")
            appendLine("────────────────────────────────────────")
            analysis.cycles.forEachIndexed { index, cycle -> appendLine("${index + 1}. ${cycle.joinToString(" → ")}") }
        }
        if (redundantModules.isNotEmpty() || redundantLibraries.isNotEmpty()) {
            appendLine()
            appendLine("Potentially redundant declarations")
            appendLine("────────────────────────────────────────")
            appendLine("These declarations may be intentional when they make a direct API or architectural relationship explicit.")
            if (redundantModules.isNotEmpty()) {
                appendLine()
                appendLine("Modules")
                redundantModules.toSortedMap().forEach { (dependency, alternatives) ->
                    appendLine(" → $dependency")
                    alternatives.sorted().forEach { appendLine("   also reachable through $it") }
                }
            }
            if (redundantLibraries.isNotEmpty()) {
                appendLine()
                appendLine("Libraries")
                redundantLibraries.toSortedMap().forEach { (library, sources) ->
                    appendLine(" → $library")
                    sources.sorted().forEach { appendLine("   $it") }
                }
            }
        }
    }

    private fun StringBuilder.appendAdditionalScopes(
        scopedModuleDependencies: Map<String, List<String>>,
        scopedLibraries: Map<String, List<String>>,
    ) {
        val extraModules = scopedModuleDependencies
            .filterKeys { it != DependencyScope.PRODUCTION.displayName }
            .filterValues { it.isNotEmpty() }
        val extraLibraries = scopedLibraries
            .filterKeys { it != DependencyScope.PRODUCTION.displayName }
            .filterValues { it.isNotEmpty() }
        appendLine()
        appendLine("Additional Dependency Scopes")
        appendLine("────────────────────────────────────────")
        if (extraModules.isEmpty() && extraLibraries.isEmpty()) appendLine("None")
        else DependencyScope.entries.filter { it != DependencyScope.PRODUCTION }.forEach { scope ->
            val modules = extraModules[scope.displayName].orEmpty()
            val libraries = extraLibraries[scope.displayName].orEmpty()
            if (modules.isNotEmpty() || libraries.isNotEmpty()) {
                appendLine("${scope.displayName} modules           ${modules.size}")
                appendLine("${scope.displayName} libraries         ${libraries.size}")
            }
        }
    }

    private fun StringBuilder.appendList(title: String, entries: Collection<String>, prefix: String) {
        if (entries.isEmpty()) return
        appendLine()
        appendLine(title)
        appendLine("────────────────────────────────────────")
        entries.sorted().forEach { appendLine("$prefix$it") }
    }

    private fun yesNo(value: Boolean): String = if (value) "YES" else "NO"
}
