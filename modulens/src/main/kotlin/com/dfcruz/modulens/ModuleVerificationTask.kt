package com.dfcruz.modulens

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ModuleVerificationTask : DefaultTask() {
    @get:Input
    abstract val graph: MapProperty<String, List<String>>

    @get:Input
    abstract val libraryDeclarations: MapProperty<String, List<String>>

    @get:Input
    abstract val resolvedLibraries: ListProperty<String>

    @get:Input
    abstract val failOnCycles: Property<Boolean>

    @get:Input
    abstract val maxDependencyDepth: Property<Int>

    @get:Input
    abstract val maxDirectDependencies: Property<Int>

    @get:Input
    abstract val failOnVersionConflicts: Property<Boolean>

    @get:Input
    abstract val failOnRedundantDependencies: Property<Boolean>

    @TaskAction
    fun action() {
        val violations = ProjectVerificationEngine.verify(
            graph = ModuleGraph(graph.get()),
            libraryInventory = LibraryInventoryAnalyzer.analyze(
                libraryDeclarations.get(),
                resolvedLibraries.get()
            ),
            policy = VerificationPolicy(
                failOnCycles.get(),
                maxDependencyDepth.get(),
                maxDirectDependencies.get(),
                failOnVersionConflicts.get(),
                failOnRedundantDependencies.get(),
            ),
        )
        if (violations.isEmpty()) {
            logger.lifecycle("ModuLens verification passed.")
            return
        }
        throw GradleException(
            buildString {
                appendLine("ModuLens verification failed with ${violations.size} violation(s):")
                violations.forEach { violation ->
                    appendLine("- [${violation.rule}] ${violation.message}")
                    violation.finding.evidence.dependencyPath.takeIf { it.isNotEmpty() }
                        ?.let { appendLine("  Path: ${it.joinToString(" → ")}") }
                    violation.finding.suggestedFix?.let { appendLine("  Suggested fix: $it") }
                }
            }
        )
    }
}
